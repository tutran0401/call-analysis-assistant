package com.tutran.callassistant.analysis.timeline;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.testsupport.Events;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineBuilderTest {

    private final TimelineBuilder builder = SampleCalls.timelineBuilder();

    @Test
    void sortsEventsAcrossSourcesByAbsoluteTime() {
        CanonicalEvent init = Events.timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:02Z", 1, "INIT_CALL");
        CanonicalEvent bye = Events.timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:10Z", 2, "BYE");
        CanonicalEvent callSummary =
                Events.timed(EventSource.END_CALL, Leg.CALLER, "2026-01-01T00:00:01Z", 3, "CALL_SUMMARY");

        CallTimeline timeline = builder.build(Events.CALL_ID, List.of(bye, init, callSummary));

        assertThat(timeline.events()).extracting(CanonicalEvent::eventType)
                .containsExactly("CALL_SUMMARY", "INIT_CALL", "BYE");
    }

    @Test
    void deduplicatesExactRepeatedRawLinesFromTheSameSourceFile() {
        CanonicalEvent event = Events.timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:02Z", 1, "INIT_CALL");
        CanonicalEvent duplicate = new CanonicalEvent(event.callId(), event.leg(), event.source(),
                event.sourceFile(), event.timestamp(), event.rawTimestamp(), event.timestampConfidence(),
                99, event.eventType(), event.attributes(), event.rawLine());

        CallTimeline timeline = builder.build(Events.CALL_ID, List.of(event, duplicate));

        assertThat(timeline.events()).hasSize(1);
    }

    @Test
    void anchorsWebRtcEventsToTheEndCallLogOfTheSameLeg() {
        CanonicalEvent callerSummary =
                Events.timed(EventSource.END_CALL, Leg.CALLER, "2026-01-01T00:00:00Z", 1, "CALL_SUMMARY");
        CanonicalEvent webrtcEvent = Events.unanchoredWebRtc(Leg.CALLER, 5_000, 2);

        CallTimeline timeline = builder.build(Events.CALL_ID, List.of(callerSummary, webrtcEvent));

        CanonicalEvent anchored = onlyWebRtcEvent(timeline);
        assertThat(anchored.timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:05Z"));
        assertThat(anchored.timestampConfidence()).isEqualTo(TimestampConfidence.ANCHORED);
        assertThat(timeline.hasLowConfidenceOrdering()).isFalse();
    }

    @Test
    void fallsBackToEarliestSignalingEventWhenLegHasNoEndCallLog() {
        CanonicalEvent signalingEvent =
                Events.timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:00Z", 1, "INIT_CALL");
        CanonicalEvent webrtcEvent = Events.unanchoredWebRtc(Leg.CALLEE, 2_000, 2);

        CallTimeline timeline = builder.build(Events.CALL_ID, List.of(signalingEvent, webrtcEvent));

        CanonicalEvent anchored = onlyWebRtcEvent(timeline);
        assertThat(anchored.timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:02Z"));
        assertThat(anchored.timestampConfidence()).isEqualTo(TimestampConfidence.ANCHORED);
    }

    @Test
    void leavesWebRtcEventsUnknownAndOrderedLastWhenNoAnchorExistsAtAll() {
        CanonicalEvent webrtcEvent = Events.unanchoredWebRtc(Leg.CALLER, 0, 1);

        CallTimeline timeline = builder.build(Events.CALL_ID, List.of(webrtcEvent));

        assertThat(timeline.events()).hasSize(1);
        assertThat(timeline.events().get(0).timestamp()).isNull();
        assertThat(timeline.events().get(0).timestampConfidence()).isEqualTo(TimestampConfidence.UNKNOWN);
        assertThat(timeline.hasLowConfidenceOrdering()).isTrue();
    }

    @Test
    void unknownConfidenceEventsWithoutAnyAnchorSortDeterministicallyByLegThenSequence() {
        CanonicalEvent calleeEvent = Events.unanchoredWebRtc(Leg.CALLEE, 500, 1);
        CanonicalEvent callerEvent = Events.unanchoredWebRtc(Leg.CALLER, 100, 2);

        CallTimeline timeline = builder.build(Events.CALL_ID, List.of(calleeEvent, callerEvent));

        // CALLEE < CALLER theo bảng chữ cái, các tiêu chí khác đều hoà
        assertThat(timeline.events()).extracting(CanonicalEvent::leg)
                .containsExactly(Leg.CALLEE, Leg.CALLER);
        assertThat(timeline.events()).allSatisfy(e ->
                assertThat(e.timestampConfidence()).isEqualTo(TimestampConfidence.UNKNOWN));
    }

    @Test
    void exposesEarliestSignalingLookupUsedByBothMetricsAndRules() {
        CanonicalEvent firstInvite =
                Events.timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:03Z", 1, "INVITE");
        CanonicalEvent secondInvite =
                Events.timed(EventSource.SIGNALING, Leg.SERVER, "2026-01-01T00:00:09Z", 2, "INVITE");

        CallTimeline timeline = builder.build(Events.CALL_ID, List.of(secondInvite, firstInvite));

        assertThat(timeline.earliestSignaling("INVITE")).contains(firstInvite);
        assertThat(timeline.earliestSignaling("INVITE", Instant.parse("2026-01-01T00:00:05Z")))
                .contains(secondInvite);
        assertThat(timeline.earliestSignaling("BYE")).isEmpty();
    }

    private CanonicalEvent onlyWebRtcEvent(CallTimeline timeline) {
        return timeline.events().stream()
                .filter(e -> e.source() == EventSource.WEBRTC)
                .findFirst()
                .orElseThrow();
    }
}
