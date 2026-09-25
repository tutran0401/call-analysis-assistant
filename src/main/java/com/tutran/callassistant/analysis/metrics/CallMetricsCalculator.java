package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.metrics.LegQualityMetrics;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tính toàn bộ chỉ số Core của Sprint 1 (mục 4.3) từ một {@link CallTimeline} đã dựng xong.
 *
 * <p>Đây là một <b>facade</b>: nó không tự tính gì cả, chỉ gom kết quả của bốn calculator
 * chuyên trách - thời lượng, đếm, bên kết thúc, chất lượng từng bên. Nhờ vậy thêm một chỉ số
 * mới là sửa (hoặc thêm) đúng một calculator nhỏ, và mỗi calculator test được độc lập, thay
 * vì cả catalog chỉ số nằm trong một class hơn 200 dòng như trước.
 */
@Component
public class CallMetricsCalculator {

    private final SignalingDurationCalculator durations;
    private final SignalingCountCalculator counts;
    private final CallTerminatorIdentifier terminator;
    private final LegQualityCalculator legQuality;

    public CallMetricsCalculator(SignalingDurationCalculator durations,
                                 SignalingCountCalculator counts,
                                 CallTerminatorIdentifier terminator,
                                 LegQualityCalculator legQuality) {
        this.durations = durations;
        this.counts = counts;
        this.terminator = terminator;
        this.legQuality = legQuality;
    }

    /** Bộ calculator mặc định, dùng cho test và cho chỗ nào không có Spring container. */
    public static CallMetricsCalculator withDefaults() {
        return new CallMetricsCalculator(new SignalingDurationCalculator(), new SignalingCountCalculator(),
                new CallTerminatorIdentifier(), new LegQualityCalculator());
    }

    public CallMetrics calculate(CallTimeline timeline) {
        Map<Leg, LegQualityMetrics> qualityByLeg = new EnumMap<>(Leg.class);
        for (Leg leg : CallMetrics.QUALITY_LEGS) {
            qualityByLeg.put(leg, legQuality.calculate(timeline, leg));
        }

        return new CallMetrics(
                timeline.callId(),
                durations.setupTime(timeline),
                durations.timeToReachCallee(timeline),
                counts.inviteRetransmitCount(timeline),
                counts.noSessionsFoundCount(timeline),
                durations.ringingTime(timeline),
                durations.connectedDuration(timeline),
                terminator.identify(timeline),
                counts.byeRetransmitCount(timeline),
                qualityByLeg
        );
    }
}
