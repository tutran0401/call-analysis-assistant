package com.tutran.callassistant.domain.metrics;

import com.tutran.callassistant.domain.event.Leg;

import java.time.Duration;
import java.util.List;
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
    /** Hai bên có chỉ số chất lượng, theo thứ tự cố định khi cần quét tuần tự. */
    public static final List<Leg> QUALITY_LEGS = List.of(Leg.CALLER, Leg.CALLEE);

    public LegQualityMetrics qualityOf(Leg leg) {
        return qualityByLeg.get(leg);
    }
}
