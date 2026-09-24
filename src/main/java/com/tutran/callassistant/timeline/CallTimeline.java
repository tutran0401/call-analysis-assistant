package com.tutran.callassistant.timeline;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;

import java.util.List;

/**
 * A single call's events, deduplicated and ordered by {@link TimelineBuilder}. Events
 * that could not be anchored to any absolute time (see {@link TimestampConfidence#UNKNOWN})
 * are kept at the end, in their original per-file order, rather than dropped.
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
