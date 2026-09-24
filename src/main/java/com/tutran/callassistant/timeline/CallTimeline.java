package com.tutran.callassistant.timeline;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;

import java.util.List;

/**
 * Toàn bộ sự kiện của một cuộc gọi, đã loại trùng và sắp thứ tự bởi {@link TimelineBuilder}.
 * Những sự kiện không neo được vào thời gian tuyệt đối nào (xem
 * {@link TimestampConfidence#UNKNOWN}) được giữ lại ở cuối, theo đúng thứ tự gốc trong
 * từng file, chứ không bị bỏ đi.
 */
public record CallTimeline(String callId, List<CanonicalEvent> events) {

    public List<CanonicalEvent> forLeg(Leg leg) {
        return events.stream().filter(e -> e.leg() == leg).toList();
    }

    public List<CanonicalEvent> forSource(EventSource source) {
        return events.stream().filter(e -> e.source() == source).toList();
    }

    public boolean hasLowConfidenceOrdering() {
        return events.stream().anyMatch(e -> e.timestampConfidence() == TimestampConfidence.UNKNOWN);
    }
}
