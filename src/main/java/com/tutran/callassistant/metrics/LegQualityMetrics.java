package com.tutran.callassistant.metrics;

/** Quality metrics for one leg, sourced from the end-call log's periodic stats. */
public record LegQualityMetrics(
        MetricResult<Double> mos,
        MetricResult<Double> packetLossPercent,
        MetricResult<Double> rttMs,
        MetricResult<Double> jitterMs,
        MetricResult<String> webrtcKeyEvents
) {
}
