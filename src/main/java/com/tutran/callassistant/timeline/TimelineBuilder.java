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
 * Sorts, deduplicates and correlates a call's events from all sources into one timeline.
 *
 * <p><b>Clock-skew handling.</b> Signaling (server clock) and end-call log (device clock)
 * both carry absolute timestamps, so they are trusted as-is - drift between server and
 * device clocks is a known limitation we do not attempt to correct in Sprint 1, only
 * document. WebRTC engine logs carry no absolute clock at all, only an elapsed
 * {@code [seconds:millis]} offset since process start; those events are anchored by
 * adding that offset to the earliest absolute timestamp seen for the same leg (preferring
 * the end-call log, falling back to the call's earliest signaling event if that leg has no
 * end-call log). This is a best-effort proxy, not an exact correlation, so anchored events
 * are marked {@link TimestampConfidence#ANCHORED} rather than {@link TimestampConfidence#EXACT};
 * if no timed event exists at all for a leg, its WebRTC events are left
 * {@link TimestampConfidence#UNKNOWN} and ordered last rather than guessed at.
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
