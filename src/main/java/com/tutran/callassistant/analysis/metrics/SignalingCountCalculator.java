package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.analysis.SignalingCommands;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Các chỉ số dạng đếm (mục 4.3): số lần gửi lại INVITE/BYE, và số lần gặp
 * "No sessions found".
 *
 * <p><b>Số lần gửi lại</b> được đếm theo <i>đợt gửi</i> (burst), không theo {@code requestId}.
 * Soát toàn bộ 20 signaling export mẫu cho thấy hai điều khiến cách đếm theo requestId sai:
 * <ul>
 *   <li>Một lần gửi được <b>nhiều service ghi lại</b> (và một service có thể ghi 2-3 dòng),
 *       các dòng này nằm gọn trong khoảng &lt; 35ms;</li>
 *   <li>Lần gửi lại <b>giữ nguyên requestId</b> của lần gửi đầu, còn một service khác lại dùng
 *       chung một requestId cho mọi lệnh của cả phiên. Ví dụ {@code 0EC7B700}: 15 dòng INVITE,
 *       1 requestId, nhưng thực chất là 5 đợt gửi cách nhau 0.5s → 1s → 2s → 4s (backoff kiểu
 *       timer SIP).</li>
 * </ul>
 * Vì vậy các dòng của cùng một người gửi cách nhau không quá {@link #SAME_TRANSMISSION_WINDOW}
 * được gộp thành một đợt; số lần gửi lại = số đợt - 1, cộng dồn theo từng người gửi (hai bên
 * cùng gửi BYE một lần là 0 lần gửi lại, không phải 1).
 */
@Component
public class SignalingCountCalculator {

    /**
     * Khoảng cách tối đa giữa hai dòng log của cùng một lần gửi. Chọn nằm giữa độ trải rộng lớn
     * nhất quan sát được của một lần gửi (~30ms) và khoảng cách ngắn nhất giữa hai lần gửi lại
     * (~520ms), để lệch vài chục ms theo tải server không làm đổi kết quả.
     */
    static final Duration SAME_TRANSMISSION_WINDOW = Duration.ofMillis(250);

    private static final String UNIT_COUNT = "lần";
    private static final String APP_USER_ID = "appUserId";
    private static final String UNKNOWN_SENDER = "";
    private static final String NO_SESSIONS_MARKER = "no session";
    /** Hai field text tự do duy nhất trong data mẫu: {@code msg} (end-call log), {@code message} (WebRTC log). */
    private static final List<String> FREE_TEXT_FIELDS = List.of("msg", "message");

    public MetricResult<Integer> inviteRetransmitCount(CallTimeline timeline) {
        return retransmitCount(timeline, SignalingCommands.INVITE);
    }

    public MetricResult<Integer> byeRetransmitCount(CallTimeline timeline) {
        return retransmitCount(timeline, SignalingCommands.BYE);
    }

    private MetricResult<Integer> retransmitCount(CallTimeline timeline, String cmd) {
        List<CanonicalEvent> sent = timeline.forSource(EventSource.SIGNALING).stream()
                .filter(e -> cmd.equals(e.eventType()))
                .filter(e -> e.timestamp() != null)
                .toList();
        if (sent.isEmpty()) {
            return MetricResult.notAvailable(UNIT_COUNT, MetricSources.SIGNALING,
                    "không có lệnh " + cmd + " nào trong signaling");
        }
        Map<String, List<Instant>> timesBySender = sent.stream()
                .collect(Collectors.groupingBy(
                        e -> Objects.requireNonNullElse(e.attribute(APP_USER_ID), UNKNOWN_SENDER),
                        Collectors.mapping(CanonicalEvent::timestamp, Collectors.toList())));
        int retransmits = timesBySender.values().stream()
                .mapToInt(times -> countTransmissions(times) - 1)
                .sum();
        return MetricResult.of(retransmits, UNIT_COUNT, MetricSources.SIGNALING);
    }

    /** Số đợt gửi: một đợt mới bắt đầu khi dòng kế tiếp cách dòng trước quá cửa sổ gộp. */
    static int countTransmissions(List<Instant> times) {
        List<Instant> sorted = times.stream().sorted(Comparator.naturalOrder()).toList();
        int transmissions = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (Duration.between(sorted.get(i - 1), sorted.get(i)).compareTo(SAME_TRANSMISSION_WINDOW) > 0) {
                transmissions++;
            }
        }
        return transmissions;
    }

    /**
     * Chỉ đếm được khi trong dữ liệu có ít nhất một field text tự do để mà tìm. Signaling
     * export mẫu hoàn toàn có cấu trúc (chỉ có cmd/service/csid/...), nên nếu thiếu hẳn log
     * client thì phải báo N/A thay vì trả 0 - một con số 0 sai ở đây sẽ bị đọc thành "không hề
     * gặp lỗi session". Nếu signaling export sau này có field text tự do, field đó cũng được
     * tìm vì phép tìm chạy trên mọi nguồn.
     */
    public MetricResult<Integer> noSessionsFoundCount(CallTimeline timeline) {
        List<CanonicalEvent> events = timeline.events();
        boolean anyFreeTextField = events.stream().anyMatch(this::hasFreeTextField);
        if (!anyFreeTextField) {
            return MetricResult.notAvailable(UNIT_COUNT, MetricSources.SIGNALING_OR_END_CALL,
                    "các log được cung cấp không có field text tự do (msg/message) nào để tìm");
        }
        long count = events.stream()
                .flatMap(this::freeTextValues)
                .filter(text -> text != null && text.toLowerCase().contains(NO_SESSIONS_MARKER))
                .count();
        return MetricResult.of((int) count, UNIT_COUNT, MetricSources.SIGNALING_OR_END_CALL);
    }

    private boolean hasFreeTextField(CanonicalEvent event) {
        return FREE_TEXT_FIELDS.stream().anyMatch(field -> event.attribute(field) != null);
    }

    private Stream<String> freeTextValues(CanonicalEvent event) {
        return FREE_TEXT_FIELDS.stream().map(event::attribute);
    }
}
