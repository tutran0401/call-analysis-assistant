package com.tutran.callassistant.evidence;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;
import com.tutran.callassistant.metrics.CallMetrics;
import com.tutran.callassistant.metrics.CallMetricsCalculator;
import com.tutran.callassistant.parser.EndCallLogParser;
import com.tutran.callassistant.parser.SignalingJsonParser;
import com.tutran.callassistant.taxonomy.IssueCategory;
import com.tutran.callassistant.taxonomy.Verdict;
import com.tutran.callassistant.timeline.CallTimeline;
import com.tutran.callassistant.timeline.TimelineBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleVerdictEngineTest {

    private final RuleVerdictEngine engine = new RuleVerdictEngine();
    private final CallMetricsCalculator metricsCalculator = new CallMetricsCalculator();

    private CallTimeline buildTimeline(Path callDir, String callId) {
        List<CanonicalEvent> all = new ArrayList<>();
        all.addAll(new SignalingJsonParser().parse(callDir.resolve("signaling.json"), callId).events());
        if (callDir.resolve("caller_endcall.log").toFile().exists()) {
            all.addAll(new EndCallLogParser().parse(callDir.resolve("caller_endcall.log"), callId).events());
        }
        if (callDir.resolve("callee_endcall.log").toFile().exists()) {
            all.addAll(new EndCallLogParser().parse(callDir.resolve("callee_endcall.log"), callId).events());
        }
        return new TimelineBuilder().build(callId, all);
    }

    @Test
    void classifiesARealSuccessfulCallAsSuccessWithNoQualityFlag() {
        String callId = "DE7DD314-F432-45CB-BCB4-AE9103CC0919";
        CallTimeline timeline = buildTimeline(Path.of("success", callId), callId);
        CallMetrics metrics = metricsCalculator.calculate(timeline);

        RuleVerdictResult result = engine.evaluate(timeline, metrics);

        assertThat(result.verdict()).isEqualTo(Verdict.SUCCESS);
        assertThat(result.qualityFlag()).isFalse();
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.evidence()).anySatisfy(e -> assertThat(e.description()).contains("OK_ACK_OK"));
        assertThat(result.evidence()).anySatisfy(e -> assertThat(e.description()).contains("BYE"));
    }

    @Test
    void classifiesARealPreInviteRejectionAsFailWithSignalingFailure() {
        String callId = "1B009D42-49CD-479E-B26C-3A2994AEB720";
        CallTimeline timeline = buildTimeline(Path.of("fail", callId), callId);
        CallMetrics metrics = metricsCalculator.calculate(timeline);

        RuleVerdictResult result = engine.evaluate(timeline, metrics);

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(result.issueCategory()).isEqualTo(IssueCategory.SIGNALING_FAILURE);
    }

    @Test
    void returnsUnknownWhenNoSignalingDataExistsAtAll() {
        CallTimeline emptyTimeline = new TimelineBuilder().build("CALL-EMPTY", List.of());
        CallMetrics metrics = metricsCalculator.calculate(emptyTimeline);

        RuleVerdictResult result = engine.evaluate(emptyTimeline, metrics);

        assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(result.dataLimitations()).anyMatch(l -> l.contains("No signaling data"));
    }

    @Test
    void returnsUnknownWhenCallConnectedButNoByeWasObserved() {
        CanonicalEvent initCall = signalingEvent("INIT_CALL", "2026-01-01T00:00:00Z", 1);
        CanonicalEvent okAck = signalingEvent("OK_ACK_OK", "2026-01-01T00:00:05Z", 2);
        CanonicalEvent callSummary = new CanonicalEvent("CALL-1", Leg.CALLER, EventSource.END_CALL,
                "caller_endcall.log", Instant.parse("2026-01-01T00:00:00Z"), "1735689600000",
                TimestampConfidence.EXACT, 3, "CALL_SUMMARY", Map.of("role", "caller"), "raw-summary");

        CallTimeline timeline = new TimelineBuilder().build("CALL-1", List.of(initCall, okAck, callSummary));
        CallMetrics metrics = metricsCalculator.calculate(timeline);

        RuleVerdictResult result = engine.evaluate(timeline, metrics);

        assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(result.dataLimitations()).anyMatch(l -> l.contains("no BYE was found"));
    }

    private CanonicalEvent signalingEvent(String cmd, String timestamp, long seq) {
        return new CanonicalEvent("CALL-1", Leg.SERVER, EventSource.SIGNALING, "signaling.json",
                Instant.parse(timestamp), timestamp, TimestampConfidence.EXACT, seq, cmd, Map.of(), "raw-" + seq);
    }
}
