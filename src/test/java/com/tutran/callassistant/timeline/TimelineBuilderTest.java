package com.tutran.callassistant.timeline;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineBuilderTest {

    private final TimelineBuilder builder = new TimelineBuilder();

    private static CanonicalEvent timed(EventSource source, Leg leg, String ts, long seq, String eventType) {
        return new CanonicalEvent("CALL-1", leg, source, "file-" + source, Instant.parse(ts), ts,
                TimestampConfidence.EXACT, seq, eventType, Map.of(), "raw-" + seq);
    }

    private static CanonicalEvent webrtc(Leg leg, long elapsedMillis, long seq) {
        return new CanonicalEvent("CALL-1", leg, EventSource.WEBRTC, "file-webrtc", null, elapsedMillis + "ms",
                TimestampConfidence.UNKNOWN, seq, "ENGINE_LOG", Map.of(), "raw-webrtc-" + seq);
    }

    @Test
    void sortsEventsAcrossSourcesByAbsoluteTime() {
        CanonicalEvent init = timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:02Z", 1, "INIT_CALL");
        CanonicalEvent bye = timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:10Z", 2, "BYE");
        CanonicalEvent callSummary = timed(EventSource.END_CALL, Leg.CALLER, "2026-01-01T00:00:01Z", 3, "CALL_SUMMARY");

        CallTimeline timeline = builder.build("CALL-1", List.of(bye, init, callSummary));

        assertThat(timeline.events()).extracting(CanonicalEvent::eventType)
                .containsExactly("CALL_SUMMARY", "INIT_CALL", "BYE");
    }

    @Test
    void deduplicatesExactRepeatedRawLinesFromTheSameSourceFile() {
        CanonicalEvent a = timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:02Z", 1, "INIT_CALL");
        CanonicalEvent duplicate = new CanonicalEvent(a.callId(), a.leg(), a.source(), a.sourceFile(), a.timestamp(),
                a.rawTimestamp(), a.timestampConfidence(), 99, a.eventType(), a.attributes(), a.rawLine());

        CallTimeline timeline = builder.build("CALL-1", List.of(a, duplicate));

        assertThat(timeline.events()).hasSize(1);
    }

    @Test
    void anchorsWebRtcEventsToTheEndCallLogOfTheSameLeg() {
        CanonicalEvent callerSummary = timed(EventSource.END_CALL, Leg.CALLER, "2026-01-01T00:00:00Z", 1, "CALL_SUMMARY");
        CanonicalEvent webrtcEvent = webrtc(Leg.CALLER, 5_000, 2);

        CallTimeline timeline = builder.build("CALL-1", List.of(callerSummary, webrtcEvent));

        CanonicalEvent anchoredWebrtc = timeline.events().stream()
                .filter(e -> e.source() == EventSource.WEBRTC)
                .findFirst().orElseThrow();
        assertThat(anchoredWebrtc.timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:05Z"));
        assertThat(anchoredWebrtc.timestampConfidence()).isEqualTo(TimestampConfidence.ANCHORED);
        assertThat(timeline.hasLowConfidenceOrdering()).isFalse();
    }

    @Test
    void fallsBackToEarliestSignalingEventWhenLegHasNoEndCallLog() {
        CanonicalEvent signalingEvent = timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:00Z", 1, "INIT_CALL");
        CanonicalEvent webrtcEvent = webrtc(Leg.CALLEE, 2_000, 2);

        CallTimeline timeline = builder.build("CALL-1", List.of(signalingEvent, webrtcEvent));

        CanonicalEvent anchoredWebrtc = timeline.events().stream()
                .filter(e -> e.source() == EventSource.WEBRTC)
                .findFirst().orElseThrow();
        assertThat(anchoredWebrtc.timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:02Z"));
        assertThat(anchoredWebrtc.timestampConfidence()).isEqualTo(TimestampConfidence.ANCHORED);
    }

    @Test
    void leavesWebRtcEventsUnknownAndOrderedLastWhenNoAnchorExistsAtAll() {
        CanonicalEvent webrtcEvent = webrtc(Leg.CALLER, 0, 1);

        CallTimeline timeline = builder.build("CALL-1", List.of(webrtcEvent));

        assertThat(timeline.events()).hasSize(1);
        assertThat(timeline.events().get(0).timestamp()).isNull();
        assertThat(timeline.events().get(0).timestampConfidence()).isEqualTo(TimestampConfidence.UNKNOWN);
        assertThat(timeline.hasLowConfidenceOrdering()).isTrue();
    }

    @Test
    void unknownConfidenceEventsWithoutAnyAnchorSortDeterministicallyByLegThenSequence() {
        CanonicalEvent calleeEvent = webrtc(Leg.CALLEE, 500, 1);
        CanonicalEvent callerEvent = webrtc(Leg.CALLER, 100, 2);

        CallTimeline timeline = builder.build("CALL-1", List.of(calleeEvent, callerEvent));

        assertThat(timeline.events()).extracting(CanonicalEvent::leg)
                .containsExactly(Leg.CALLEE, Leg.CALLER); // CALLEE < CALLER theo bảng chữ cái, các tiêu chí khác đều hoà
        assertThat(timeline.events()).allSatisfy(e ->
                assertThat(e.timestampConfidence()).isEqualTo(TimestampConfidence.UNKNOWN));
    }
}
