package com.tutran.callassistant.testsupport;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.TimestampConfidence;

import java.time.Instant;
import java.util.Map;

/** Dựng {@link CanonicalEvent} tổng hợp cho các tình huống không có sẵn trong data mẫu thật. */
public final class Events {

    public static final String CALL_ID = "CALL-1";

    private Events() {
    }

    public static CanonicalEvent signaling(String cmd, String timestamp, long sequenceNumber) {
        return new CanonicalEvent(CALL_ID, Leg.SERVER, EventSource.SIGNALING, "signaling.json",
                Instant.parse(timestamp), timestamp, TimestampConfidence.EXACT, sequenceNumber, cmd,
                Map.of(), "raw-" + sequenceNumber);
    }

    public static CanonicalEvent endCall(Leg leg, String eventType, String timestamp, long sequenceNumber,
                                         Map<String, String> attributes) {
        Instant instant = Instant.parse(timestamp);
        return new CanonicalEvent(CALL_ID, leg, EventSource.END_CALL,
                leg.name().toLowerCase(java.util.Locale.ROOT) + "_endcall.log", instant,
                String.valueOf(instant.toEpochMilli()), TimestampConfidence.EXACT, sequenceNumber, eventType,
                attributes, "raw-endcall-" + sequenceNumber);
    }

    public static CanonicalEvent timed(EventSource source, Leg leg, String timestamp, long sequenceNumber,
                                       String eventType) {
        return new CanonicalEvent(CALL_ID, leg, source, "file-" + source, Instant.parse(timestamp), timestamp,
                TimestampConfidence.EXACT, sequenceNumber, eventType, Map.of(), "raw-" + sequenceNumber);
    }

    /** Sự kiện WebRTC chưa neo: chỉ có mốc thời gian tương đối, không có timestamp tuyệt đối. */
    public static CanonicalEvent unanchoredWebRtc(Leg leg, long elapsedMillis, long sequenceNumber) {
        return new CanonicalEvent(CALL_ID, leg, EventSource.WEBRTC, "file-webrtc", null, elapsedMillis + "ms",
                TimestampConfidence.UNKNOWN, sequenceNumber, "ENGINE_LOG", Map.of(),
                "raw-webrtc-" + sequenceNumber);
    }
}
