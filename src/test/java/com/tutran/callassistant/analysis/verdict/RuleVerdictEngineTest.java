package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.analysis.metrics.CallMetricsCalculator;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.report.ConfidenceLevel;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import com.tutran.callassistant.domain.verdict.Verdict;
import com.tutran.callassistant.report.confidence.DataCompletenessConfidencePolicy;
import com.tutran.callassistant.testsupport.Events;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleVerdictEngineTest {

    private final VerdictEngine engine = RuleVerdictEngine.withDefaults();
    private final CallMetricsCalculator metricsCalculator = CallMetricsCalculator.withDefaults();

    private RuleVerdictResult evaluate(CallTimeline timeline) {
        CallMetrics metrics = metricsCalculator.calculate(timeline);
        return engine.evaluate(timeline, metrics);
    }

    private RuleVerdictResult evaluateCall(Path callDirectory, String callId) {
        return evaluate(SampleCalls.timelineWithEndCallLogs(callDirectory, callId));
    }

    @Test
    void classifiesARealSuccessfulCallAsSuccessWithNoQualityFlag() {
        RuleVerdictResult result = evaluateCall(
                SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS), SampleCalls.SUCCESS_FULL_LOGS);

        assertThat(result.verdict()).isEqualTo(Verdict.SUCCESS);
        assertThat(result.qualityFlag()).isFalse();
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.evidence()).anySatisfy(e -> assertThat(e.description()).contains("OK_ACK_OK"));
        assertThat(result.evidence()).anySatisfy(e -> assertThat(e.description()).contains("BYE"));
    }

    @Test
    void assignsStableSequentialEvidenceIdsInCallProgressOrder() {
        RuleVerdictResult result = evaluateCall(
                SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS), SampleCalls.SUCCESS_FULL_LOGS);

        assertThat(result.evidence()).extracting(e -> e.id())
                .containsExactly("EV001", "EV002", "EV003");
        assertThat(result.evidence()).extracting(e -> e.description())
                .satisfiesExactly(
                        first -> assertThat(first).contains("INIT_CALL"),
                        second -> assertThat(second).contains("OK_ACK_OK"),
                        third -> assertThat(third).contains("BYE"));
    }

    @Test
    void classifiesARealPreInviteRejectionAsFailWithSignalingFailure() {
        RuleVerdictResult result = evaluateCall(
                SampleCalls.fail(SampleCalls.FAIL_REJECTED_AFTER_INVITE), SampleCalls.FAIL_REJECTED_AFTER_INVITE);

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(result.issueCategory()).isEqualTo(IssueCategory.SIGNALING_FAILURE);
    }

    @Test
    void classifiesASecondRealFailCallAsSignalingFailureViaADifferentFailureShape() {
        // Cuộc gọi này chưa từng gửi INVITE (caller huỷ ngay trong lúc INIT_CALL) - một dạng lỗi thật
        // khác với việc bị từ chối sau INVITE ở trên, cả hai đều đúng khi fallback về
        // SIGNALING_FAILURE vì không có evidence ICE nào.
        RuleVerdictResult result = evaluateCall(
                SampleCalls.fail(SampleCalls.FAIL_CANCELLED_BEFORE_INVITE),
                SampleCalls.FAIL_CANCELLED_BEFORE_INVITE);

        assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
        assertThat(result.issueCategory()).isEqualTo(IssueCategory.SIGNALING_FAILURE);
    }

    @Test
    void classifiesARealCallWithBothEndCallLogsMissingAsUnknownDespiteCleanSignaling() {
        // Cuộc gọi này có luồng signaling nhìn hoàn toàn bình thường (đủ INIT_CALL...BYE) nhưng không
        // có end-call log cho bên nào cả trong data mẫu - test này xác nhận vẫn từ chối kết luận
        // SUCCESS chỉ dựa vào signaling, đúng theo kịch bản "UNKNOWN do thiếu file" trong acceptance
        // criteria, dùng data thật (không phải giả lập).
        RuleVerdictResult result = evaluateCall(
                SampleCalls.success(SampleCalls.SUCCESS_NO_CLIENT_LOGS), SampleCalls.SUCCESS_NO_CLIENT_LOGS);

        assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(result.dataLimitations()).contains("Missing caller_endcall.log", "Missing callee_endcall.log");
        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void classifiesARealSuccessWithOneMissingEndCallLogAsMediumConfidence() {
        RuleVerdictResult result = evaluateCall(
                SampleCalls.success(SampleCalls.SUCCESS_CALLER_LOG_ONLY), SampleCalls.SUCCESS_CALLER_LOG_ONLY);

        assertThat(result.verdict()).isEqualTo(Verdict.SUCCESS);
        assertThat(result.dataLimitations()).containsExactly("Missing callee_endcall.log");
        assertThat(new DataCompletenessConfidencePolicy().confidenceFor(result))
                .isEqualTo(ConfidenceLevel.MEDIUM);
    }

    @Test
    void returnsUnknownWhenNoSignalingDataExistsAtAll() {
        CallTimeline emptyTimeline = SampleCalls.timelineBuilder().build("CALL-EMPTY", List.of());

        RuleVerdictResult result = evaluate(emptyTimeline);

        assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(result.dataLimitations()).anyMatch(l -> l.contains("No signaling data"));
    }

    @Test
    void returnsUnknownWhenCallConnectedButNoByeWasObserved() {
        CanonicalEvent initCall = Events.signaling("INIT_CALL", "2026-01-01T00:00:00Z", 1);
        CanonicalEvent okAck = Events.signaling("OK_ACK_OK", "2026-01-01T00:00:05Z", 2);
        CanonicalEvent callSummary = Events.endCall(Leg.CALLER, "CALL_SUMMARY", "2026-01-01T00:00:00Z", 3,
                Map.of("role", "caller"));

        CallTimeline timeline = SampleCalls.timelineBuilder()
                .build(Events.CALL_ID, List.of(initCall, okAck, callSummary));

        RuleVerdictResult result = evaluate(timeline);

        assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(result.dataLimitations()).anyMatch(l -> l.contains("no BYE was found"));
    }

    @Test
    void flagsPoorQualityOnAnOtherwiseSuccessfulCallWhenPacketLossExceedsTheThreshold() {
        RuleVerdictResult result = evaluate(connectedCallWithCallerStats(
                Map.of("audio.packetLostPercent", "12.5", "audio.audioMos", "4.4")));

        assertThat(result.verdict()).isEqualTo(Verdict.SUCCESS);
        assertThat(result.qualityFlag()).isTrue();
        assertThat(result.issueCategory()).isEqualTo(IssueCategory.NETWORK_PACKET_LOSS);
    }

    @Test
    void prefersPacketLossOverDelayWhenBothBreachTheirThresholds() {
        // Mất gói cao và jitter cao thường đi cùng nhau; report chỉ nêu một nguyên nhân chính, và mất
        // gói là dấu hiệu trực tiếp hơn nên phải được ưu tiên.
        RuleVerdictResult result = evaluate(connectedCallWithCallerStats(
                Map.of("audio.packetLostPercent", "12.5", "audio.jitter", "95.0",
                        "transport.currentRttMs", "800.0")));

        assertThat(result.issueCategory()).isEqualTo(IssueCategory.NETWORK_PACKET_LOSS);
    }

    @Test
    void flagsLowMosAsUnknownCategoryWhenNoNetworkMetricExplainsIt() {
        RuleVerdictResult result = evaluate(connectedCallWithCallerStats(
                Map.of("audio.audioMos", "2.1", "audio.packetLostPercent", "0.0")));

        assertThat(result.verdict()).isEqualTo(Verdict.SUCCESS);
        assertThat(result.qualityFlag()).isTrue();
        assertThat(result.issueCategory()).isEqualTo(IssueCategory.UNKNOWN);
    }

    /** Một cuộc gọi đã kết nối và kết thúc bình thường, với periodic stats do test chỉ định cho caller. */
    private CallTimeline connectedCallWithCallerStats(Map<String, String> stats) {
        return SampleCalls.timelineBuilder().build(Events.CALL_ID, List.of(
                Events.signaling("INIT_CALL", "2026-01-01T00:00:00Z", 1),
                Events.signaling("OK_ACK_OK", "2026-01-01T00:00:05Z", 2),
                Events.signaling("BYE", "2026-01-01T00:01:05Z", 3),
                Events.endCall(Leg.CALLER, "CALL_SUMMARY", "2026-01-01T00:00:00Z", 4, Map.of("role", "caller")),
                Events.endCall(Leg.CALLER, "PERIODIC_STATS", "2026-01-01T00:01:00Z", 5, stats)));
    }
}
