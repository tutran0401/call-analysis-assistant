package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.metrics.LegQualityMetrics;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Mọi giá trị kỳ vọng ở đây đều được tính tay trực tiếp từ file mẫu thật, đúng theo acceptance
 * criteria của Sprint 1 là số liệu phải khớp tính tay trên cuộc gọi thật - chứ không chỉ so khớp với
 * chính output của code.
 */
class CallMetricsCalculatorTest {

    private static final String CALL_ID = SampleCalls.SUCCESS_FULL_LOGS;
    private static final Path CALL_DIR = SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS);

    private final CallMetricsCalculator calculator = CallMetricsCalculator.withDefaults();

    private CallMetrics metricsOfFullyLoggedCall() {
        return calculator.calculate(SampleCalls.timelineWithEndCallLogs(CALL_DIR, CALL_ID));
    }

    @Test
    void computesSetupAndCallProgressDurationsMatchingHandCalculation() {
        CallMetrics metrics = metricsOfFullyLoggedCall();

        assertThat(metrics.setupTime().isAvailable()).isTrue();
        assertThat(metrics.setupTime().value().toMillis()).isEqualTo(8581);

        assertThat(metrics.timeToReachCallee().value().toMillis()).isEqualTo(2991);
        assertThat(metrics.ringingTime().value().toMillis()).isEqualTo(4749);
        assertThat(metrics.connectedDuration().value().toMillis()).isEqualTo(361311);
    }

    @Test
    void computesRetransmitCountsMatchingHandCalculation() {
        CallMetrics metrics = metricsOfFullyLoggedCall();

        assertThat(metrics.inviteRetransmitCount().value()).isEqualTo(0);
        assertThat(metrics.byeRetransmitCount().value()).isEqualTo(1);
    }

    @Test
    void identifiesCalleeAsTerminatorMatchingHandCalculation() {
        CallMetrics metrics = metricsOfFullyLoggedCall();

        assertThat(metrics.terminator().isAvailable()).isTrue();
        assertThat(metrics.terminator().value()).isEqualTo(Leg.CALLEE);
    }

    @Test
    void computesPerLegAudioQualityMatchingHandCalculation() {
        CallMetrics metrics = metricsOfFullyLoggedCall();

        LegQualityMetrics caller = metrics.qualityOf(Leg.CALLER);
        assertThat(caller.mos().value()).isCloseTo(4.42397, within(0.00001));
        assertThat(caller.packetLossPercent().value()).isEqualTo(0.0);
        assertThat(caller.rttMs().value()).isEqualTo(54.0);
        assertThat(caller.jitterMs().value()).isCloseTo(0.006, within(0.0001));

        LegQualityMetrics callee = metrics.qualityOf(Leg.CALLEE);
        assertThat(callee.mos().value()).isCloseTo(4.42201, within(0.00001));
        assertThat(callee.rttMs().value()).isEqualTo(63.0);
        assertThat(callee.jitterMs().value()).isCloseTo(0.01, within(0.0001));
    }

    @Test
    void countsNoSessionsFoundAsZeroForThisSuccessfulCall() {
        // Các dòng LOG_MESSAGE trong end-call log có field "msg" dạng text tự do (khác với signaling
        // export vốn hoàn toàn có cấu trúc), nên chỉ số này ở đây là một con số thật, có sẵn - chỉ là
        // bằng 0 vì cuộc gọi này chưa bao giờ gặp tình huống đó.
        CallMetrics metrics = metricsOfFullyLoggedCall();

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isTrue();
        assertThat(metrics.noSessionsFoundCount().value()).isEqualTo(0);
    }

    @Test
    void reportsNoSessionsFoundAsNotAvailableWhenOnlySignalingEventsArePresent() {
        // Riêng signaling export hoàn toàn không có field msg/message nào (chỉ có các field có cấu
        // trúc cmd/service/csid/...) - xác nhận báo N/A thay vì trả về 0 sai khi end-call log (nguồn
        // duy nhất có text tự do) bị thiếu hoàn toàn.
        CallTimeline timeline = SampleCalls.signalingOnlyTimeline(CALL_DIR, CALL_ID);

        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isFalse();
        assertThat(metrics.noSessionsFoundCount().naReason()).contains("no free-text log message field");
    }

    @Test
    void reportsWebrtcQualityAsNotAvailableWhenNoWebrtcLogWasParsedForThatLeg() {
        // Test này chỉ đưa vào signaling + end-call log (không có webrtc.log), nên chỉ số suy ra từ
        // webrtc của cả hai bên phải là N/A tường minh, không phải giá trị rỗng âm thầm.
        CallMetrics metrics = metricsOfFullyLoggedCall();

        assertThat(metrics.qualityOf(Leg.CALLER).webrtcKeyEvents().isAvailable()).isFalse();
        assertThat(metrics.qualityOf(Leg.CALLEE).webrtcKeyEvents().isAvailable()).isFalse();
    }

    /**
     * Cuộc gọi thật thứ hai, tính tay độc lập với cuộc gọi ở trên, để đáp ứng acceptance criteria của
     * Sprint 1 là khớp số liệu tính tay trên tối thiểu 5 cuộc gọi thật (xem thêm hai test bên dưới,
     * cộng với cuộc gọi bị từ chối trong RuleVerdictEngineTest - 5 cuộc gọi khác nhau đã kiểm chứng
     * tay, trải đều cả case success và fail).
     */
    @Test
    void computesSignalingDerivedMetricsForASecondRealCallWithNoEndCallLogsAtAll() {
        String callId = SampleCalls.SUCCESS_NO_CLIENT_LOGS;
        CallTimeline timeline = SampleCalls.signalingOnlyTimeline(SampleCalls.success(callId), callId);

        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.setupTime().value().toMillis()).isEqualTo(9301);
        assertThat(metrics.ringingTime().value().toMillis()).isEqualTo(4818);
        assertThat(metrics.connectedDuration().value().toMillis()).isEqualTo(32818);
        assertThat(metrics.timeToReachCallee().value().toMillis()).isEqualTo(3685);
        assertThat(metrics.inviteRetransmitCount().value()).isEqualTo(0);
        assertThat(metrics.byeRetransmitCount().value()).isEqualTo(1);
    }

    @Test
    void computesSignalingDerivedMetricsForAThirdRealCallWithASingleEndCallLog() {
        String callId = SampleCalls.SUCCESS_CALLER_LOG_ONLY;
        CallTimeline timeline = SampleCalls.timelineWithEndCallLogs(SampleCalls.success(callId), callId);

        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.setupTime().value().toMillis()).isEqualTo(11958);
        assertThat(metrics.ringingTime().value().toMillis()).isEqualTo(10942);
        assertThat(metrics.connectedDuration().value().toMillis()).isEqualTo(15809);
        assertThat(metrics.byeRetransmitCount().value()).isEqualTo(1);
        assertThat(metrics.terminator().value()).isEqualTo(Leg.CALLER);
    }

    @Test
    void reportsSetupTimeAsNotAvailableForAFourthRealCallThatNeverProgressedPastInitCall() {
        // Signaling export của cuộc gọi này chỉ có đúng INIT_CALL và CANCEL - caller đã huỷ trước khi
        // kịp gửi INVITE (một dạng lỗi khác với việc bị từ chối sau INVITE, đã kiểm tra ở
        // RuleVerdictEngineTest).
        String callId = SampleCalls.FAIL_CANCELLED_BEFORE_INVITE;
        CallTimeline timeline = SampleCalls.signalingOnlyTimeline(SampleCalls.fail(callId), callId);

        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.setupTime().isAvailable()).isFalse();
        assertThat(metrics.timeToReachCallee().isAvailable()).isFalse();
        assertThat(metrics.inviteRetransmitCount().isAvailable()).isFalse();
    }
}
