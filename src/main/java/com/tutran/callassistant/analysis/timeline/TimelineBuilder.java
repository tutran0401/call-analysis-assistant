package com.tutran.callassistant.analysis.timeline;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Dựng {@link CallTimeline} của một cuộc gọi từ mọi nguồn (T4).
 *
 * <p>Ba việc khác nhau được giao cho ba chỗ khác nhau, thay vì nằm chung một class như
 * trước: loại trùng ({@link EventDeduplicator}), gán thời gian tuyệt đối còn thiếu
 * ({@link TimestampAnchor}), và sắp thứ tự (comparator dưới đây). Lớp này chỉ còn việc
 * ghép ba bước đó lại.
 */
@Component
public class TimelineBuilder {

    /**
     * Khi hai sự kiện cùng timestamp, thứ tự được quyết định theo nguồn (signaling là góc
     * nhìn server nên đứng trước), rồi tới leg và số thứ tự dòng trong file - để cùng một
     * tập dữ liệu vào luôn cho ra cùng một thứ tự ra (yêu cầu nhất quán ở mục 3.2).
     */
    private static final Map<EventSource, Integer> SOURCE_PRIORITY = Map.of(
            EventSource.SIGNALING, 0,
            EventSource.END_CALL, 1,
            EventSource.WEBRTC, 2
    );

    private static final Comparator<CanonicalEvent> CHRONOLOGICAL = Comparator
            .comparing((CanonicalEvent e) -> e.timestamp() == null ? Instant.MAX : e.timestamp())
            .thenComparing(e -> SOURCE_PRIORITY.getOrDefault(e.source(), 99))
            .thenComparing(e -> e.leg().name())
            .thenComparingLong(CanonicalEvent::sequenceNumber);

    private final EventDeduplicator deduplicator;
    private final List<TimestampAnchor> anchors;

    public TimelineBuilder(EventDeduplicator deduplicator, List<TimestampAnchor> anchors) {
        this.deduplicator = deduplicator;
        this.anchors = List.copyOf(anchors);
    }

    public CallTimeline build(String callId, List<CanonicalEvent> rawEvents) {
        List<CanonicalEvent> events = deduplicator.deduplicate(rawEvents);
        for (TimestampAnchor anchor : anchors) {
            events = anchor.anchor(events);
        }
        List<CanonicalEvent> ordered = new ArrayList<>(events);
        ordered.sort(CHRONOLOGICAL);
        return new CallTimeline(callId, ordered);
    }
}
