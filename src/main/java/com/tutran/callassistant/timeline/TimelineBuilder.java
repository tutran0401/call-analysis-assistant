package com.tutran.callassistant.timeline;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sắp thứ tự, loại trùng và liên kết sự kiện của một cuộc gọi từ mọi nguồn thành một
 * timeline duy nhất.
 *
 * <p><b>Xử lý lệch đồng hồ.</b> Signaling (đồng hồ server) và end-call log (đồng hồ thiết
 * bị) đều có timestamp tuyệt đối, nên được tin dùng trực tiếp - độ lệch giữa đồng hồ
 * server và thiết bị là giới hạn đã biết, Sprint 1 không cố sửa mà chỉ ghi nhận lại. Log
 * engine WebRTC thì hoàn toàn không có đồng hồ tuyệt đối, chỉ có mốc thời gian đã trôi qua
 * {@code [seconds:millis]} tính từ lúc process khởi động; các sự kiện này được neo bằng
 * cách cộng thêm mốc đó vào timestamp tuyệt đối sớm nhất quan sát được của cùng một bên
 * (ưu tiên lấy từ end-call log, nếu bên đó không có end-call log thì lấy sự kiện signaling
 * sớm nhất của cả cuộc gọi làm phương án dự phòng). Đây là một cách xấp xỉ tốt nhất có
 * thể, không phải sự tương quan chính xác, nên các sự kiện đã neo được đánh dấu
 * {@link TimestampConfidence#ANCHORED} chứ không phải {@link TimestampConfidence#EXACT};
 * nếu một bên hoàn toàn không có sự kiện nào có thời gian, các sự kiện WebRTC của bên đó
 * giữ nguyên {@link TimestampConfidence#UNKNOWN} và xếp cuối cùng thay vì đoán bừa.
 */
public final class TimelineBuilder {

    private static final Map<EventSource, Integer> SOURCE_PRIORITY = Map.of(
            EventSource.SIGNALING, 0,
            EventSource.END_CALL, 1,
            EventSource.WEBRTC, 2
    );

    public CallTimeline build(String callId, List<CanonicalEvent> rawEvents) {
        List<CanonicalEvent> deduplicated = deduplicate(rawEvents);
        Map<Leg, Instant> anchors = computeAnchors(deduplicated);

        List<CanonicalEvent> anchored = new ArrayList<>(deduplicated.size());
        for (CanonicalEvent event : deduplicated) {
            anchored.add(anchorIfNeeded(event, anchors));
        }

        anchored.sort(Comparator
                .comparing((CanonicalEvent e) -> e.timestamp() == null ? Instant.MAX : e.timestamp())
                .thenComparing(e -> SOURCE_PRIORITY.getOrDefault(e.source(), 99))
                .thenComparing(e -> e.leg().name())
                .thenComparingLong(CanonicalEvent::sequenceNumber));

        return new CallTimeline(callId, List.copyOf(anchored));
    }

    private List<CanonicalEvent> deduplicate(List<CanonicalEvent> events) {
        Set<String> seen = new HashSet<>();
        List<CanonicalEvent> result = new ArrayList<>(events.size());
        for (CanonicalEvent event : events) {
            String key = event.source() + "|" + event.sourceFile() + "|" + event.rawLine();
            if (seen.add(key)) {
                result.add(event);
            }
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
        if (rawTimestamp == null || !rawTimestamp.endsWith("ms")) {
            return null;
        }
        try {
            return Long.parseLong(rawTimestamp.substring(0, rawTimestamp.length() - 2));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
