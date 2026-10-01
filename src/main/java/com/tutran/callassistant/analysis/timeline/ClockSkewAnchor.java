package com.tutran.callassistant.analysis.timeline;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hiệu chỉnh lệch đồng hồ giữa server (signaling) và thiết bị (end-call log) - cách xử lý
 * "lệch đồng hồ" mà Plan T4 yêu cầu tự đề xuất.
 *
 * <p>Ý tưởng: cùng một lệnh signaling (INIT_CALL, INVITE, RINGING...) được ghi hai lần, một lần
 * ở server và một lần ở thiết bị. Với mỗi lệnh có mặt ở cả hai phía, hiệu {@code server - thiết bị}
 * là độ lệch đồng hồ cộng thêm trễ mạng một chiều. Lấy <b>trung vị</b> các hiệu đó cho mỗi leg để
 * một lệnh bị gửi lại hay trễ bất thường không kéo lệch cả kết quả.
 *
 * <p>Trễ mạng một chiều chỉ cỡ vài chục tới vài trăm ms, còn đồng hồ thiết bị lệch thật thường
 * tính bằng giây tới phút. Vì vậy chỉ hiệu chỉnh khi độ lệch vượt {@link #TOLERANCE}: dưới ngưỡng
 * đó, coi như hai đồng hồ đã khớp và không đụng vào timestamp, tránh "sửa" nhầm trễ mạng thành
 * lệch đồng hồ. Khi hiệu chỉnh, toàn bộ sự kiện end-call của leg đó được dịch cùng một độ lệch và
 * ghi lại trong attribute {@value #CORRECTION_ATTRIBUTE} để còn truy vết. Phải chạy trước
 * {@link WebRtcElapsedTimeAnchor} để các sự kiện WebRTC được neo vào mốc đã hiệu chỉnh.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClockSkewAnchor implements TimestampAnchor {

    static final Duration TOLERANCE = Duration.ofSeconds(2);
    static final String CORRECTION_ATTRIBUTE = "clockSkewCorrectionMs";

    private static final String SIGNALING_CMD_TYPE = "SIGNALING_CMD";
    private static final String CMD_FIELD = "cmd";
    /** ACK chỉ là phản hồi cho lệnh khác, trùng tên giữa nhiều lệnh nên không dùng làm mốc ghép cặp. */
    private static final String ACK = "ACK";

    @Override
    public List<CanonicalEvent> anchor(List<CanonicalEvent> events) {
        Map<String, Instant> serverFirstSeen = firstSeenByCommand(events, EventSource.SIGNALING, null);
        if (serverFirstSeen.isEmpty()) {
            return events;
        }
        Map<Leg, Long> corrections = new HashMap<>();
        for (Leg leg : List.of(Leg.CALLER, Leg.CALLEE)) {
            Map<String, Instant> deviceFirstSeen = firstSeenByCommand(events, EventSource.END_CALL, leg);
            estimateOffsetMillis(serverFirstSeen, deviceFirstSeen)
                    .filter(offset -> Math.abs(offset) > TOLERANCE.toMillis())
                    .ifPresent(offset -> corrections.put(leg, offset));
        }
        if (corrections.isEmpty()) {
            return events;
        }
        List<CanonicalEvent> corrected = new ArrayList<>(events.size());
        for (CanonicalEvent event : events) {
            Long offset = event.source() == EventSource.END_CALL ? corrections.get(event.leg()) : null;
            corrected.add(offset == null || event.timestamp() == null ? event : shift(event, offset));
        }
        return corrected;
    }

    private java.util.Optional<Long> estimateOffsetMillis(Map<String, Instant> server, Map<String, Instant> device) {
        List<Long> diffs = new ArrayList<>();
        device.forEach((command, deviceTime) -> {
            Instant serverTime = server.get(command);
            if (serverTime != null) {
                diffs.add(Duration.between(deviceTime, serverTime).toMillis());
            }
        });
        if (diffs.isEmpty()) {
            return java.util.Optional.empty();
        }
        diffs.sort(Long::compare);
        return java.util.Optional.of(diffs.get(diffs.size() / 2));
    }

    private Map<String, Instant> firstSeenByCommand(List<CanonicalEvent> events, EventSource source, Leg leg) {
        Map<String, Instant> firstSeen = new HashMap<>();
        for (CanonicalEvent event : events) {
            if (event.source() != source || event.timestamp() == null || (leg != null && event.leg() != leg)) {
                continue;
            }
            String command = commandOf(event);
            if (command != null) {
                firstSeen.merge(command, event.timestamp(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        return firstSeen;
    }

    private String commandOf(CanonicalEvent event) {
        String command = event.source() == EventSource.SIGNALING
                ? event.eventType()
                : SIGNALING_CMD_TYPE.equals(event.eventType()) ? event.attribute(CMD_FIELD) : null;
        return command == null || command.isBlank() || ACK.equals(command) ? null : command;
    }

    private CanonicalEvent shift(CanonicalEvent event, long offsetMillis) {
        Map<String, String> attributes = new java.util.LinkedHashMap<>(event.attributes());
        attributes.put(CORRECTION_ATTRIBUTE, String.valueOf(offsetMillis));
        return new CanonicalEvent(event.callId(), event.leg(), event.source(), event.sourceFile(),
                event.timestamp().plusMillis(offsetMillis), event.rawTimestamp(), event.timestampConfidence(),
                event.sequenceNumber(), event.eventType(), attributes, event.rawLine());
    }
}
