package com.tutran.callassistant.metrics;

import com.tutran.callassistant.domain.Leg;

import java.time.Duration;
import java.util.Map;

/** Toàn bộ chỉ số Core của Sprint 1 theo PROJECT_SPEC.md mục 4.3, cho một cuộc gọi. */
public record CallMetrics(
        String callId,
        MetricResult<Duration> setupTime,
        MetricResult<Duration> timeToReachCallee,
        MetricResult<Integer> inviteRetransmitCount,
        MetricResult<Integer> noSessionsFoundCount,
        MetricResult<Duration> ringingTime,
        MetricResult<Duration> connectedDuration,
        MetricResult<Leg> terminator,
        MetricResult<Integer> byeRetransmitCount,
        Map<Leg, LegQualityMetrics> qualityByLeg
) {
}
