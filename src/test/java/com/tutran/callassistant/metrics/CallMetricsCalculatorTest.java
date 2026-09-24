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
 * Mọi giá trị kỳ vọng ở đây đều được tính tay trực tiếp từ file mẫu thật của cuộc gọi
 * DE7DD314-F432-45CB-BCB4-AE9103CC0919 (signaling.json + end-call log của caller/callee),
 * đúng theo acceptance criteria của Sprint 1 là số liệu phải khớp tính tay trên cuộc gọi thật.
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
        // Các dòng LOG_MESSAGE trong end-call log có field "msg" dạng text tự do (khác với
        // signaling export vốn hoàn toàn có cấu trúc), nên chỉ số này ở đây là một con số
        // thật, có sẵn - chỉ là bằng 0 vì cuộc gọi này chưa bao giờ gặp tình huống đó.
        CallMetrics metrics = calculator.calculate(buildTimeline());

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isTrue();
        assertThat(metrics.noSessionsFoundCount().value()).isEqualTo(0);
    }

    @Test
    void reportsNoSessionsFoundAsNotAvailableWhenOnlySignalingEventsArePresent() {
        // Riêng signaling export hoàn toàn không có field msg/message nào (chỉ có các
        // field có cấu trúc cmd/service/csid/...) - xác nhận báo N/A thay vì trả về 0 sai
        // khi end-call log (nguồn duy nhất có text tự do) bị thiếu hoàn toàn.
        List<CanonicalEvent> signalingOnly =
                new SignalingJsonParser().parse(CALL_DIR.resolve("signaling.json"), CALL_ID).events();
        CallTimeline timeline = new TimelineBuilder().build(CALL_ID, signalingOnly);

        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isFalse();
        assertThat(metrics.noSessionsFoundCount().naReason()).contains("no free-text log message field");
    }

    @Test
    void reportsWebrtcQualityAsNotAvailableWhenNoWebrtcLogWasParsedForThatLeg() {
        // Test này chỉ đưa vào signaling + end-call log (không có webrtc.log), nên chỉ số
        // suy ra từ webrtc của cả hai bên phải là N/A tường minh, không phải giá trị rỗng âm thầm.
        CallMetrics metrics = calculator.calculate(buildTimeline());

        assertThat(metrics.qualityByLeg().get(Leg.CALLER).webrtcKeyEvents().isAvailable()).isFalse();
        assertThat(metrics.qualityByLeg().get(Leg.CALLEE).webrtcKeyEvents().isAvailable()).isFalse();
    }

    /**
     * Cuộc gọi thật thứ hai, tính tay độc lập với DE7DD314 ở trên, để đáp ứng acceptance
     * criteria của Sprint 1 là khớp số liệu tính tay trên tối thiểu 5 cuộc gọi thật (xem
     * thêm 703100CF và C8CF631E bên dưới, cộng với 1B009D42 trong RuleVerdictEngineTest và
     * DE7DD314 ở trên - 4 cuộc gọi khác nhau đã kiểm chứng tay số liệu suy ra từ signaling,
     * trải đều cả case success và fail).
     */
    @Test
    void computesSignalingDerivedMetricsForASecondRealCallWithNoEndCallLogsAtAll() {
        String callId = "6A7CE985-4A1A-44D1-84B1-DBB0B0B90448";
        Path callDir = Path.of("success", callId);
        List<CanonicalEvent> events = new SignalingJsonParser().parse(callDir.resolve("signaling.json"), callId).events();
        CallTimeline timeline = new TimelineBuilder().build(callId, events);

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
        String callId = "C8CF631E-0C6B-46E4-92E7-280E7B6A5394";
        Path callDir = Path.of("success", callId);
        List<CanonicalEvent> events = new ArrayList<>();
        events.addAll(new SignalingJsonParser().parse(callDir.resolve("signaling.json"), callId).events());
        events.addAll(new EndCallLogParser().parse(callDir.resolve("caller_endcall.log"), callId).events());
        CallTimeline timeline = new TimelineBuilder().build(callId, events);

        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.setupTime().value().toMillis()).isEqualTo(11958);
        assertThat(metrics.ringingTime().value().toMillis()).isEqualTo(10942);
        assertThat(metrics.connectedDuration().value().toMillis()).isEqualTo(15809);
        assertThat(metrics.byeRetransmitCount().value()).isEqualTo(1);
        assertThat(metrics.terminator().value()).isEqualTo(Leg.CALLER);
    }

    @Test
    void reportsSetupTimeAsNotAvailableForAFourthRealCallThatNeverProgressedPastInitCall() {
        // Signaling export của 703100CF chỉ có đúng INIT_CALL và CANCEL - caller đã huỷ
        // trước khi kịp gửi INVITE (một dạng lỗi khác với việc 1B009D42 bị từ chối sau
        // INVITE, đã kiểm tra ở RuleVerdictEngineTest).
        String callId = "703100CF-5742-467E-9E0E-34E45F60FF58";
        Path callDir = Path.of("fail", callId);
        List<CanonicalEvent> events = new SignalingJsonParser().parse(callDir.resolve("signaling.json"), callId).events();
        CallTimeline timeline = new TimelineBuilder().build(callId, events);

        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.setupTime().isAvailable()).isFalse();
        assertThat(metrics.timeToReachCallee().isAvailable()).isFalse();
        assertThat(metrics.inviteRetransmitCount().isAvailable()).isFalse();
    }
}
