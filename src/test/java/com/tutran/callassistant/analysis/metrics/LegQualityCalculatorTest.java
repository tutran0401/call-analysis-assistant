package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.testsupport.Events;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Peer evaluation issue 2: ngưỡng chất lượng dùng giá trị cả cuộc, không dùng mẫu đỉnh. */
class LegQualityCalculatorTest {

    private final LegQualityCalculator calculator = new LegQualityCalculator();

    private static CanonicalEvent stats(int second, Map<String, String> fields) {
        return Events.endCall(Leg.CALLER, "PERIODIC_STATS", "2026-10-13T08:00:%02dZ".formatted(second), second, fields);
    }

    @Test
    void wholeCallLossComesFromTheCumulativeCountersNotThePeakInterval() {
        // Một khoảng đo mất 38 % (8 gói) giữa cuộc; bộ đếm cộng dồn cuối: 18 mất / 4982 nhận = 0.36 %.
        List<CanonicalEvent> samples = List.of(
                stats(1, Map.of("audio.packetLostPercent", "0", "audio.packetsLost", "2", "audio.packetsReceived", "1000")),
                stats(2, Map.of("audio.packetLostPercent", "38.571", "audio.packetsLost", "10", "audio.packetsReceived", "1013")),
                stats(3, Map.of("audio.packetLostPercent", "0.1", "audio.packetsLost", "18", "audio.packetsReceived", "4982")));

        MetricResult<Double> loss = calculator.wholeCallPacketLoss(samples);

        assertThat(loss.isAvailable()).isTrue();
        assertThat(loss.value()).isEqualTo(0.36);
    }

    @Test
    void counterResetDoesNotHideTheLargestStream() {
        // Stream khởi tạo lại giữa cuộc: bộ đếm về 0. Lấy mẫu có tổng gói lớn nhất, không phải mẫu cuối.
        List<CanonicalEvent> samples = List.of(
                stats(1, Map.of("audio.packetsLost", "100", "audio.packetsReceived", "900")),
                stats(2, Map.of("audio.packetsLost", "0", "audio.packetsReceived", "50")));

        assertThat(calculator.wholeCallPacketLoss(samples).value()).isEqualTo(10.0);
    }

    @Test
    void missingCountersAreNotAvailableNotZero() {
        MetricResult<Double> loss = calculator.wholeCallPacketLoss(
                List.of(stats(1, Map.of("audio.packetLostPercent", "12.5"))));

        assertThat(loss.isAvailable()).isFalse();
        assertThat(loss.naReason()).contains("bộ đếm cộng dồn");
    }

    @Test
    void rttAndJitterUseTheMeanOfMediaSamples() {
        List<CanonicalEvent> samples = List.of(
                stats(1, Map.of("transport.currentRttMs", "40", "audio.jitter", "0.010")),
                stats(2, Map.of("transport.currentRttMs", "710", "audio.jitter", "0.030")),
                stats(3, Map.of("transport.currentRttMs", "40", "audio.jitter", "0.020")));

        assertThat(calculator.meanOf(samples, "transport.currentRttMs", 1.0).value()).isEqualTo(263.3);
        assertThat(calculator.meanOf(samples, "audio.jitter", 1000.0).value()).isEqualTo(20.0);
    }
}
