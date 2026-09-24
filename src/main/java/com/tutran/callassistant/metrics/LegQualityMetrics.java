package com.tutran.callassistant.metrics;

/** Chỉ số chất lượng của một bên (leg), lấy từ periodic stats của end-call log. */
public record LegQualityMetrics(
        MetricResult<Double> mos,
        MetricResult<Double> packetLossPercent,
        MetricResult<Double> rttMs,
        MetricResult<Double> jitterMs,
        MetricResult<String> webrtcKeyEvents
) {
}
