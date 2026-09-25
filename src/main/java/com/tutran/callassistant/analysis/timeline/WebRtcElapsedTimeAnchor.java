package com.tutran.callassistant.analysis.timeline;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo sự kiện log engine WebRTC vào thời gian tuyệt đối.
 *
 * <p>Signaling (đồng hồ server) và end-call log (đồng hồ thiết bị) đều có timestamp tuyệt
 * đối nên được tin dùng trực tiếp - độ lệch giữa hai đồng hồ là giới hạn đã biết, Sprint 1
 * không cố sửa mà chỉ ghi nhận lại. Log engine WebRTC thì hoàn toàn không có đồng hồ tuyệt
 * đối, chỉ có mốc đã trôi qua {@code [seconds:millis]} tính từ lúc process khởi động; các
 * sự kiện này được neo bằng cách cộng mốc đó vào timestamp tuyệt đối sớm nhất quan sát được
 * của cùng một bên (ưu tiên end-call log của chính bên đó, nếu bên đó không có thì lấy sự
 * kiện signaling sớm nhất của cả cuộc gọi làm phương án dự phòng).
 *
 * <p>Đây là xấp xỉ tốt nhất có thể, không phải tương quan chính xác, nên sự kiện đã neo
 * được đánh dấu {@link TimestampConfidence#ANCHORED} chứ không phải
 * {@link TimestampConfidence#EXACT}; nếu một bên hoàn toàn không có sự kiện nào có thời
 * gian thì sự kiện WebRTC của bên đó giữ nguyên {@link TimestampConfidence#UNKNOWN} và bị
 * xếp cuối, thay vì đoán bừa.
 */
@Component
public class WebRtcElapsedTimeAnchor implements TimestampAnchor {

    private static final String ELAPSED_SUFFIX = "ms";

    @Override
    public List<CanonicalEvent> anchor(List<CanonicalEvent> events) {
        Map<Leg, Instant> anchors = computeAnchors(events);
        List<CanonicalEvent> result = new ArrayList<>(events.size());
        for (CanonicalEvent event : events) {
            result.add(anchorIfNeeded(event, anchors));
        }
        return result;
    }

    private Map<Leg, Instant> computeAnchors(List<CanonicalEvent> events) {
        Instant earliestOverall = events.stream()
                .filter(e -> e.source() != EventSource.WEBRTC && e.timestamp() != null)
                .map(CanonicalEvent::timestamp)
                .min(Instant::compareTo)
                .orElse(null);

        Map<Leg, Instant> anchors = new HashMap<>();
        for (Leg leg : Leg.values()) {
            Instant legAnchor = events.stream()
                    .filter(e -> e.leg() == leg && e.source() == EventSource.END_CALL && e.timestamp() != null)
                    .map(CanonicalEvent::timestamp)
                    .min(Instant::compareTo)
                    .orElse(earliestOverall);
            if (legAnchor != null) {
                anchors.put(leg, legAnchor);
            }
        }
        return anchors;
    }

    private CanonicalEvent anchorIfNeeded(CanonicalEvent event, Map<Leg, Instant> anchors) {
        if (event.source() != EventSource.WEBRTC || event.timestampConfidence() != TimestampConfidence.UNKNOWN) {
            return event;
        }
        Instant anchor = anchors.get(event.leg());
        if (anchor == null) {
            return event;
        }
        Long elapsedMillis = parseElapsedMillis(event.rawTimestamp());
        if (elapsedMillis == null) {
            return event;
        }
        return event.withTimestamp(anchor.plusMillis(elapsedMillis), TimestampConfidence.ANCHORED);
    }

    private Long parseElapsedMillis(String rawTimestamp) {
        if (rawTimestamp == null || !rawTimestamp.endsWith(ELAPSED_SUFFIX)) {
            return null;
        }
        try {
            return Long.parseLong(rawTimestamp.substring(0, rawTimestamp.length() - ELAPSED_SUFFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
