package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.metrics.LegQualityMetrics;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Mọi giá trị kỳ vọng ở đây đều được tính tay trực tiếp từ file mẫu thật, đúng theo acceptance
 * criteria của Sprint 1 là số liệu phải khớp tính tay trên tối thiểu 5 cuộc gọi - chứ không chỉ so
 * khớp với chính output của code. Bảng tính tay (mốc thời gian gốc, phép trừ, dòng log lấy chỉ số
 * chất lượng) nằm ở {@code docs/metrics-hand-calculation.md}.
 *
 * <p>Có hai nhóm cuộc gọi:
 * <ul>
 *   <li>5 cuộc gọi trong {@code sample-data/for_test/} (không gắn nhãn), gồm cả các ca chỉ số phải
 *       là N/A;</li>
 *   <li>8 cuộc gọi có <b>giá trị số</b> cho mọi chỉ số signaling và chất lượng. Phần lớn nằm trong
 *       {@code success/}/{@code fail/}, vì {@code for_test/} chỉ có 2 cuộc gọi đi đủ xa để có số
 *       liệu. Nhãn của mentor chỉ là verdict, không tiết lộ setup time hay MOS, nên khớp tính tay ở
 *       đây vẫn là kiểm chứng độc lập.</li>
 * </ul>
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

        // Chỉ số chất lượng lấy giá trị TỆ NHẤT quan sát được trong cả cuộc gọi, không phải dòng
        // periodic stats cuối cùng. Chính cuộc gọi này cho thấy vì sao: dòng cuối báo packet loss
        // 0.0% ở cả hai bên (lúc đó stream đã tắt), trong khi giữa cuộc có 18 mẫu vượt ngưỡng 5%,
        // đỉnh 11.32% phía caller - lấy dòng cuối sẽ giấu mất một cuộc gọi chất lượng kém thật sự.
        LegQualityMetrics caller = metrics.qualityOf(Leg.CALLER);
        assertThat(caller.mos().value()).isCloseTo(4.10477, within(0.00001));
        assertThat(caller.packetLossPercent().value()).isCloseTo(11.3208, within(0.0001));
        assertThat(caller.rttMs().value()).isEqualTo(115.0);
        assertThat(caller.jitterMs().value()).isCloseTo(0.022, within(0.0001));

        LegQualityMetrics callee = metrics.qualityOf(Leg.CALLEE);
        assertThat(callee.mos().value()).isCloseTo(4.34143, within(0.00001));
        assertThat(callee.packetLossPercent().value()).isCloseTo(7.54717, within(0.0001));
        assertThat(callee.rttMs().value()).isEqualTo(82.0);
        assertThat(callee.jitterMs().value()).isCloseTo(0.012, within(0.0001));

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

        // 15 dòng INVITE dùng chung 1 requestId, nhưng là 5 đợt gửi ở +0 / +0.522 / +1.546 / +3.567 /
        // +7.586s (backoff 0.5s → 1s → 2s → 4s) - tức 4 lần gửi lại. Cách đếm cũ theo số requestId
        // khác nhau trả 0 cho đúng cuộc gọi có nhiều lần gửi lại nhất.
        assertThat(metrics.inviteRetransmitCount().value()).isEqualTo(4);

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
        assertThat(metrics.noSessionsFoundCount().naReason()).contains("không có field text tự do");
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

    /**
     * Chỉ số signaling tính tay trên 8 cuộc gọi, chỉ dùng signaling export (không end-call log) - để
     * chứng minh đúng như mục 4.3: các chỉ số này có nguồn là Signaling, không cần log client.
     * Thời lượng là mili-giây cắt phần lẻ ({@code Duration.toMillis()}) của hiệu hai timestamp
     * nano-giây; -1 nghĩa là phải N/A (thiếu một đầu mốc, ví dụ không có TRYING).
     */
    @ParameterizedTest(name = "{1}")
    @CsvSource({
            // folder,  callId,                                 setup,  reach,  ring,  connected, inviteRt, byeRt, terminator
            "for_test, 271D1FAF-26D4-4150-AC3F-9204505BD84B,   3840,     -1,  3032,     80358,        1,     1, CALLER",
            "success,  DE7DD314-F432-45CB-BCB4-AE9103CC0919,   8581,   2991,  4749,    361311,        3,     1, CALLEE",
            "success,  EE129C8F-EAD0-4302-AB68-920D32F8B8B7,  13530,   1505,  5987,     36307,        3,     1, CALLEE",
            "success,  C8CF631E-0C6B-46E4-92E7-280E7B6A5394,  11958,     -1, 10942,     15809,        1,     1, CALLER",
            "success,  70A1F889-9371-4179-8C27-125ED39BA415,  32594,  11362, 20257,     40564,        4,     1, CALLER",
            "success,  5E0800AE-F9D4-497D-9FA1-CD56C3E901E8,  14757,  10069,  3533,    117756,        4,     1, CALLER",
            "success,  6A7CE985-4A1A-44D1-84B1-DBB0B0B90448,   9301,   3685,  4818,     32818,        4,     1, CALLEE",
            "fail,     2D9057AA-C496-48B2-946A-98FA2896D086,   6774,     -1,  5565,     33892,        1,     4, CALLEE",
    })
    void signalingMetricsMatchHandCalculation(String folder, String callId, long setupMs, long reachMs,
                                              long ringMs, long connectedMs, int inviteRetransmits,
                                              int byeRetransmits, Leg terminator) {
        CallMetrics metrics = calculator.calculate(SampleCalls.signalingOnlyTimeline(callDir(folder, callId), callId));

        assertDuration(metrics.setupTime(), setupMs);
        assertDuration(metrics.timeToReachCallee(), reachMs);
        assertDuration(metrics.ringingTime(), ringMs);
        assertDuration(metrics.connectedDuration(), connectedMs);
        assertThat(metrics.inviteRetransmitCount().value()).isEqualTo(inviteRetransmits);
        assertThat(metrics.byeRetransmitCount().value()).isEqualTo(byeRetransmits);
        // Không có end-call log trong timeline này: bên kết thúc phải suy ra được chỉ từ signaling
        // (người gửi INIT_CALL là caller, người gửi TRYING/RINGING/OK là callee).
        assertThat(metrics.terminator().value()).isEqualTo(terminator);
    }

    /**
     * MOS / packet loss / RTT / jitter tính tay: giá trị TỆ NHẤT (MOS thấp nhất, còn lại cao nhất) trong
     * các dòng PERIODIC_STATS đã có media của end-call log từng bên. Comment cuối dòng là số dòng trong
     * file gốc nơi xuất hiện giá trị đó (mos / loss / rtt / jitter), "-" là mọi dòng đều bằng 0.
     */
    @ParameterizedTest(name = "{1} {2}")
    @CsvSource({
            // folder, callId,                                leg,    mos,     loss,    rtt,  jitter
            "success, DE7DD314-F432-45CB-BCB4-AE9103CC0919, CALLER, 4.41438, 2.04082, 107,  0.02",   // 595 / 274 / 209 / 158
            "success, DE7DD314-F432-45CB-BCB4-AE9103CC0919, CALLEE, 4.4013,  2.0,     152,  0.022",  // 568 / 455 / 569 / 215
            "success, EE129C8F-EAD0-4302-AB68-920D32F8B8B7, CALLER, 4.40913, 0,       134,  0.02",   // 101 / -   / 101 / 142
            "success, EE129C8F-EAD0-4302-AB68-920D32F8B8B7, CALLEE, 4.33531, 3.7037,  181,  0.026",  //  91 /  91 / 129 / 126
            "success, C8CF631E-0C6B-46E4-92E7-280E7B6A5394, CALLER, 4.40396, 0,       156,  0.012",  //  82 / -   /  82 /  87
            "success, 70A1F889-9371-4179-8C27-125ED39BA415, CALLEE, 4.37638, 0,       266,  0.028",  //  84 / -   /  84 /  96
    })
    void qualityMetricsMatchHandCalculation(String folder, String callId, Leg leg, double mos, double loss,
                                            double rtt, double jitter) {
        CallMetrics metrics = calculator.calculate(SampleCalls.timelineWithEndCallLogs(callDir(folder, callId), callId));
        LegQualityMetrics quality = metrics.qualityOf(leg);

        assertThat(quality.mos().value()).isCloseTo(mos, within(0.00001));
        assertThat(quality.packetLossPercent().value()).isEqualTo(loss);
        assertThat(quality.rttMs().value()).isEqualTo(rtt);
        assertThat(quality.jitterMs().value()).isCloseTo(jitter, within(0.00001));
    }

    /**
     * Cuộc gọi rớt media ({@code 2D9057AA}, ICE failed): callee_endcall.log có 25 dòng periodic stats
     * nhưng tất cả đều bằng 0, kể cả {@code audio.bytesReceived} - callee chưa từng nhận được media.
     * Báo "MOS 0, packet loss 0%, RTT 0ms" sẽ đúng là lỗi "mặc định về 0" mục 4.3 cấm; phải là N/A.
     */
    @Test
    void reportsQualityAsNotAvailableWhenPeriodicStatsAreAllZeroPlaceholders() {
        CallTimeline timeline = SampleCalls.timelineWithEndCallLogs(
                SampleCalls.fail(SampleCalls.FAIL_MEDIA_NEVER_CONNECTED), SampleCalls.FAIL_MEDIA_NEVER_CONNECTED);
        LegQualityMetrics callee = calculator.calculate(timeline).qualityOf(Leg.CALLEE);

        assertThat(callee.mos().isAvailable()).isFalse();
        assertThat(callee.packetLossPercent().isAvailable()).isFalse();
        assertThat(callee.rttMs().isAvailable()).isFalse();
        assertThat(callee.jitterMs().isAvailable()).isFalse();
        assertThat(callee.packetLossPercent().naReason()).contains("chưa nhận được media", "25 dòng");
    }

    /** Cùng một người gửi: các dòng cách nhau không quá 250ms là một lần gửi, cách xa hơn là một lần gửi lại. */
    @Test
    void groupsLogLinesOfOneTransmissionAndCountsBackoffRetransmissions() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(SignalingCountCalculator.countTransmissions(List.of(
                t0, t0.plusMillis(5), t0.plusMillis(30),          // lần gửi đầu, nhiều service ghi lại
                t0.plusMillis(522), t0.plusMillis(528),           // gửi lại lần 1
                t0.plusMillis(1546)                               // gửi lại lần 2
        ))).isEqualTo(3);
        assertThat(SignalingCountCalculator.countTransmissions(List.of(t0, t0.plusMillis(250)))).isEqualTo(1);
        assertThat(SignalingCountCalculator.countTransmissions(List.of(t0, t0.plusMillis(251)))).isEqualTo(2);
    }

    private static Path callDir(String folder, String callId) {
        return switch (folder) {
            case "success" -> SampleCalls.success(callId);
            case "fail" -> SampleCalls.fail(callId);
            default -> SampleCalls.forTest(callId);
        };
    }

    private static void assertDuration(MetricResult<Duration> metric, long expectedMs) {
        if (expectedMs < 0) {
            assertThat(metric.isAvailable()).isFalse();
        } else {
            assertThat(metric.value().toMillis()).isEqualTo(expectedMs);
        }
    }
}
