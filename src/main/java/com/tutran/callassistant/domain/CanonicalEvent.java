package com.tutran.callassistant.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A single normalized event, common to all three raw log sources (signaling, end-call,
 * webrtc). Every field that a rule, metric or evidence citation depends on must trace
 * back to {@link #rawLine()} in {@link #sourceFile()}.
 */
public record CanonicalEvent(
        String callId,
        Leg leg,
        EventSource source,
        String sourceFile,
        Instant timestamp,
        String rawTimestamp,
        TimestampConfidence timestampConfidence,
        long sequenceNumber,
        String eventType,
        Map<String, String> attributes,
        String rawLine
) {
    public CanonicalEvent {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(leg, "leg");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(rawTimestamp, "rawTimestamp");
        Objects.requireNonNull(timestampConfidence, "timestampConfidence");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(rawLine, "rawLine");
        attributes = attributes == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new TreeMap<>(attributes));
    }

    public String attribute(String key) {
        return attributes.get(key);
    }

    /** Returns a copy of this event with its absolute timestamp anchored/overridden. */
    public CanonicalEvent withTimestamp(Instant newTimestamp, TimestampConfidence newConfidence) {
        return new CanonicalEvent(callId, leg, source, sourceFile, newTimestamp, rawTimestamp,
                newConfidence, sequenceNumber, eventType, attributes, rawLine);
    }
}
