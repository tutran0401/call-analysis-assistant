package com.tutran.callassistant.metrics;

import com.tutran.callassistant.domain.Leg;

import java.time.Duration;
import java.util.Map;

/** All Sprint 1 Core call metrics per PROJECT_SPEC.md §4.3, for one call. */
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
