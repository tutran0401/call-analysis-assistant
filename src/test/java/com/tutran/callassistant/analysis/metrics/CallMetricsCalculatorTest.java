package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.metrics.LegQualityMetrics;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Mọi giá trị kỳ vọng ở đây đều được tính tay trực tiếp từ file mẫu thật, đúng theo acceptance
 * criteria của Sprint 1 là số liệu phải khớp tính tay trên tối thiểu 5 cuộc gọi - chứ không chỉ so
 * khớp với chính output của code.
 *
 * <p>Cố tình dùng 5 cuộc gọi trong {@code sample-data/for_test/} thay vì {@code success/}/{@code fail/}:
 * hai thư mục kia đã bị mentor gắn nhãn kết quả sẵn ngay trong tên thư mục, nên đối chiếu trên đó
 * không chứng minh được nhiều - tên thư mục không nói cho biết setup time là bao nhiêu mili-giây hay
 * MOS là bao nhiêu. {@code for_test/} không gắn nhãn, nên khớp tính tay trên tập này là bằng chứng
 * thuyết phục hơn rằng phép tính đúng, không phải "đoán đúng vì đã biết đáp số".
 */
class CallMetricsCalculatorTest {

    private final CallMetricsCalculator calculator = CallMetricsCalculator.withDefaults();

    /**
     * Cuộc gọi thứ nhất: thành công đầy đủ, cả hai leg có end-call + webrtc log - dùng cho phần lớn
     * số liệu tính tay (thời lượng, retransmit, bên kết thúc, chất lượng thoại từng bên).
     */
    @Test
    void computesMetricsForAFullyLoggedRealCallMatchingHandCalculation() {
        CallTimeline timeline = SampleCalls.timelineWithEndCallLogs(
                SampleCalls.forTest(SampleCalls.FOR_TEST_FULL_LOGS), SampleCalls.FOR_TEST_FULL_LOGS);
        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.setupTime().isAvailable()).isTrue();
        assertThat(metrics.setupTime().value().toMillis()).isEqualTo(3840);
        assertThat(metrics.ringingTime().value().toMillis()).isEqualTo(3032);
        assertThat(metrics.connectedDuration().value().toMillis()).isEqualTo(80358);

        // Không có sự kiện TRYING nào trong signaling export của cuộc gọi này - một chỉ số Core hoàn
        // toàn hợp lệ khi trả N/A, không phải lỗi tính toán.
        assertThat(metrics.timeToReachCallee().isAvailable()).isFalse();

        assertThat(metrics.inviteRetransmitCount().value()).isEqualTo(1);
        assertThat(metrics.byeRetransmitCount().value()).isEqualTo(1);

        assertThat(metrics.terminator().isAvailable()).isTrue();
        assertThat(metrics.terminator().value()).isEqualTo(Leg.CALLER);

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isTrue();
        assertThat(metrics.noSessionsFoundCount().value()).isEqualTo(0);

        LegQualityMetrics caller = metrics.qualityOf(Leg.CALLER);
        assertThat(caller.mos().value()).isCloseTo(4.15346, within(0.00001));
        assertThat(caller.packetLossPercent().value()).isEqualTo(0.0);
        assertThat(caller.rttMs().value()).isEqualTo(16.0);
        assertThat(caller.jitterMs().value()).isCloseTo(0.012, within(0.0001));

        LegQualityMetrics callee = metrics.qualityOf(Leg.CALLEE);
        assertThat(callee.mos().value()).isCloseTo(4.37331, within(0.00001));
        assertThat(callee.packetLossPercent().value()).isEqualTo(0.0);
        assertThat(callee.rttMs().value()).isEqualTo(18.0);
        assertThat(callee.jitterMs().value()).isCloseTo(0.003, within(0.0001));

        // timelineWithEndCallLogs() cố tình không nạp webrtc log (xem Javadoc của nó) - xác nhận N/A
        // tường minh, không phải giá trị rỗng âm thầm.
        assertThat(caller.webrtcKeyEvents().isAvailable()).isFalse();
        assertThat(callee.webrtcKeyEvents().isAvailable()).isFalse();
    }

    /**
     * Cuộc gọi thứ hai: bị từ chối cứng (FAIL_HARD) sau RINGING, thiếu hẳn caller_endcall.log - vẫn
     * tính được "thời gian với tới callee" từ signaling dù cuộc gọi thất bại, và phải báo đúng N/A cho
     * mọi chỉ số phụ thuộc vào các mốc chưa từng xảy ra (OK_ACK_OK, BYE).
     */
    @Test
    void computesSignalingDerivedMetricsForARealFailHardCallWithAnAsymmetricLogSet() {
        CallTimeline timeline = SampleCalls.timelineWithEndCallLogs(
                SampleCalls.forTest(SampleCalls.FOR_TEST_FAIL_HARD), SampleCalls.FOR_TEST_FAIL_HARD);
        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.timeToReachCallee().isAvailable()).isTrue();
        assertThat(metrics.timeToReachCallee().value().toMillis()).isEqualTo(9402);

        assertThat(metrics.setupTime().isAvailable()).isFalse();
        assertThat(metrics.ringingTime().isAvailable()).isFalse();
        assertThat(metrics.connectedDuration().isAvailable()).isFalse();
        assertThat(metrics.byeRetransmitCount().isAvailable()).isFalse();
        assertThat(metrics.terminator().isAvailable()).isFalse();

        assertThat(metrics.inviteRetransmitCount().value()).isEqualTo(0);

        // callee_endcall.log có mặt nhưng cuộc gọi chưa từng vào tầng media (từ chối ngay sau
        // RINGING), nên không có dòng PERIODIC_STATS nào - N/A đúng vì thiếu dữ liệu, không phải vì
        // thiếu file.
        assertThat(metrics.qualityOf(Leg.CALLER).mos().isAvailable()).isFalse();
        assertThat(metrics.qualityOf(Leg.CALLEE).mos().isAvailable()).isFalse();

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isTrue();
        assertThat(metrics.noSessionsFoundCount().value()).isEqualTo(0);
    }

    /**
     * Cuộc gọi thứ ba: chỉ có đúng một sự kiện INIT_CALL trong toàn bộ signaling export, không có
     * end-call log nào - trường hợp "khô" nhất có thể, mọi chỉ số Core phải là N/A tường minh.
     */
    @Test
    void reportsEveryCoreMetricAsNotAvailableForARealCallThatNeverProgressedPastInitCall() {
        CallTimeline timeline = SampleCalls.signalingOnlyTimeline(
                SampleCalls.forTest(SampleCalls.FOR_TEST_INIT_ONLY), SampleCalls.FOR_TEST_INIT_ONLY);
        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.setupTime().isAvailable()).isFalse();
        assertThat(metrics.timeToReachCallee().isAvailable()).isFalse();
        assertThat(metrics.ringingTime().isAvailable()).isFalse();
        assertThat(metrics.connectedDuration().isAvailable()).isFalse();
        assertThat(metrics.inviteRetransmitCount().isAvailable()).isFalse();
        assertThat(metrics.byeRetransmitCount().isAvailable()).isFalse();
        assertThat(metrics.terminator().isAvailable()).isFalse();

        // Signaling export hoàn toàn có cấu trúc, không có field msg/message nào để tìm.
        assertThat(metrics.noSessionsFoundCount().isAvailable()).isFalse();
        assertThat(metrics.noSessionsFoundCount().naReason()).contains("no free-text log message field");
    }

    /**
     * Cuộc gọi thứ tư: caller huỷ ngay trong lúc INIT_CALL (chưa từng gửi INVITE), nhưng có
     * caller_endcall.log - xác nhận end-call log có mặt vẫn không "tự chế" ra được chỉ số nào không
     * có căn cứ.
     */
    @Test
    void reportsCoreMetricsAsNotAvailableForARealCallCancelledDuringInitCall() {
        CallTimeline timeline = SampleCalls.timelineWithEndCallLogs(
                SampleCalls.forTest(SampleCalls.FOR_TEST_CANCELLED_A), SampleCalls.FOR_TEST_CANCELLED_A);
        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.setupTime().isAvailable()).isFalse();
        assertThat(metrics.inviteRetransmitCount().isAvailable()).isFalse();
        assertThat(metrics.terminator().isAvailable()).isFalse();

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isTrue();
        assertThat(metrics.noSessionsFoundCount().value()).isEqualTo(0);

        assertThat(metrics.qualityOf(Leg.CALLER).mos().isAvailable()).isFalse();
    }

    /**
     * Cuộc gọi thứ năm: cùng dạng với cuộc gọi thứ tư (huỷ trong lúc INIT_CALL) nhưng là một cuộc gọi
     * thật độc lập khác - xác nhận hành vi N/A nhất quán, không phải trùng hợp của riêng một file.
     */
    @Test
    void reportsCoreMetricsAsNotAvailableForASecondIndependentRealCallCancelledDuringInitCall() {
        CallTimeline timeline = SampleCalls.timelineWithEndCallLogs(
                SampleCalls.forTest(SampleCalls.FOR_TEST_CANCELLED_B), SampleCalls.FOR_TEST_CANCELLED_B);
        CallMetrics metrics = calculator.calculate(timeline);

        assertThat(metrics.setupTime().isAvailable()).isFalse();
        assertThat(metrics.inviteRetransmitCount().isAvailable()).isFalse();
        assertThat(metrics.terminator().isAvailable()).isFalse();

        assertThat(metrics.noSessionsFoundCount().isAvailable()).isTrue();
        assertThat(metrics.noSessionsFoundCount().value()).isEqualTo(0);

        assertThat(metrics.qualityOf(Leg.CALLER).mos().isAvailable()).isFalse();
    }
}
