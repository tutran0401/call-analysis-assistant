package com.tutran.callassistant.metrics;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.parser.EndCallLogParser;
import com.tutran.callassistant.parser.SignalingJsonParser;
import com.tutran.callassistant.timeline.CallTimeline;
import com.tutran.callassistant.timeline.TimelineBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Every expected value here was hand-computed directly from the real sample files for
 * call DE7DD314-F432-45CB-BCB4-AE9103CC0919 (signaling.json + caller/callee end-call
 * logs), per Sprint 1's acceptance criteria of matching hand-calculated numbers on real
 * calls.
 */
class CallMetricsCalculatorTest {

    private static final String CALL_ID = "DE7DD314-F432-45CB-BCB4-AE9103CC0919";
    private static final Path CALL_DIR = Path.of("success", CALL_ID);

    private final CallMetricsCalculator calculator = new CallMetricsCalculator();

    private CallTimeline buildTimeline() {
        List<CanonicalEvent> all = new ArrayList<>();
        all.addAll(new SignalingJsonParser().parse(CALL_DIR.resolve("signaling.json"), CALL_ID).events());
        all.addAll(new EndCallLogParser().parse(CALL_DIR.resolve("caller_endcall.log"), CALL_ID).events());
        all.addAll(new EndCallLogParser().parse(CALL_DIR.resolve("callee_endcall.log"), CALL_ID).events());
        return new TimelineBuilder().build(CALL_ID, all);
    }

    @Test
    void computesSetupAndCallProgressDurationsMatchingHandCalculation() {
        CallMetrics metrics = calculator.calculate(buildTimeline());

        assertThat(metrics.setupTime().isAvailable()).isTrue();
        assertThat(metrics.setupTime().value().toMillis()).isEqualTo(8581);

        assertThat(metrics.timeToReachCallee().value().toMillis()).isEqualTo(2991);
        assertThat(metrics.ringingTime().value().toMillis()).isEqualTo(4749);
        assertThat(metrics.connectedDuration().value().toMillis()).isEqualTo(361311);
    }

    @Test
    void computesRetransmitCountsMatchingHandCalculation() {
        CallMetrics metrics = calculator.calculate(buildTimeline());

        assertThat(metrics.inviteRetransmitCount().value()).isEqualTo(0);
        assertThat(metrics.byeRetransmitCount().value()).isEqualTo(1);
    }

    @Test
    void identifiesCalleeAsTerminatorMatchingHandCalculation() {
        CallMetrics metrics = calculator.calculate(buildTimeline());

        assertThat(metrics.terminator().isAvailable()).isTrue();
        assertThat(metrics.terminator().value()).isEqualTo(Leg.CALLEE);
    }

    @Test
    void computesPerLegAudioQualityMatchingHandCalculation() {
        CallMetrics metrics = calculator.calculate(buildTimeline());

        LegQualityMetrics caller = metrics.qualityByLeg().get(Leg.CALLER);
        assertThat(caller.mos().value()).isCloseTo(4.42397, within(0.00001));
        assertThat(caller.packetLossPercent().value()).isEqualTo(0.0);
        assertThat(caller.rttMs().value()).isEqualTo(54.0);
        assertThat(caller.jitterMs().value()).isCloseTo(0.006, within(0.0001));

        LegQualityMetrics callee = metrics.qualityByLeg().get(Leg.CALLEE);
        assertThat(callee.mos().value()).isCloseTo(4.42201, within(0.00001));
        assertThat(callee.rttMs().value()).isEqualTo(63.0);
        assertThat(callee.jitterMs().value()).isCloseTo(0.01, within(0.0001));
    }

    @Test
    void countsNoSessionsFoundAsZeroForThisSuccessfulCall() {
        // The end-call log's LOG_MESSAGE rows do carry free-text "msg" fields (unlike the
        // signaling export, which is purely structured), so this metric is a real,
        // available count here - just zero, since this call never hit that condition.
        CallMetrics metrics = calculator.calculate(buildTimeline());

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isTrue();
        assertThat(metrics.noSessionsFoundCount().value()).isEqualTo(0);
    }

    @Test
    void reportsNoSessionsFoundAsNotAvailableWhenOnlySignalingEventsArePresent() {
        // The signaling export alone has no msg/message field at all (only structured
        // cmd/service/csid/... fields) - confirms we say N/A rather than a false 0 when
        // end-call logs (the only source with free text) are missing entirely.
        List<CanonicalEvent> signalingOnly =
                new SignalingJsonParser().parse(CALL_DIR.resolve("signaling.json"), CALL_ID).events();
        CallTimeline timeline = new TimelineBuilder().build(CALL_ID, signalingOnly);

        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isFalse();
        assertThat(metrics.noSessionsFoundCount().naReason()).contains("no free-text log message field");
    }

    @Test
    void reportsWebrtcQualityAsNotAvailableWhenNoWebrtcLogWasParsedForThatLeg() {
        // This test only feeds signaling + end-call logs (no webrtc.log), so both legs'
        // webrtc-derived metric must be an explicit N/A, not a silent empty value.
        CallMetrics metrics = calculator.calculate(buildTimeline());

        assertThat(metrics.qualityByLeg().get(Leg.CALLER).webrtcKeyEvents().isAvailable()).isFalse();
        assertThat(metrics.qualityByLeg().get(Leg.CALLEE).webrtcKeyEvents().isAvailable()).isFalse();
    }
}
