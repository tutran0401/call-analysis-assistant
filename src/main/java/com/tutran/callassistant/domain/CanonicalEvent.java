package com.tutran.callassistant.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Một sự kiện đã chuẩn hoá, dùng chung cho cả 3 nguồn log gốc (signaling, end-call,
 * webrtc). Mọi field mà một rule, chỉ số hay evidence phụ thuộc vào đều phải trace được
 * về {@link #rawLine()} trong {@link #sourceFile()}.
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

    /** Trả về bản sao của sự kiện này với timestamp tuyệt đối đã được neo/ghi đè. */
    public CanonicalEvent withTimestamp(Instant newTimestamp, TimestampConfidence newConfidence) {
        return new CanonicalEvent(callId, leg, source, sourceFile, newTimestamp, rawTimestamp,
                newConfidence, sequenceNumber, eventType, attributes, rawLine);
    }
}
