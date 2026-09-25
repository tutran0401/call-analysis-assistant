package com.tutran.callassistant.analysis.verdict.quality;

import com.tutran.callassistant.analysis.verdict.QualityThresholds;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.metrics.LegQualityMetrics;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.QualityIssue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Trễ hoặc jitter vượt ngưỡng, khi mất gói vẫn trong mức bình thường. Xét sau
 * {@link PacketLossQualityCheck} vì mất gói cao và jitter cao thường đi cùng nhau, và mất gói
 * là dấu hiệu trực tiếp hơn.
 */
@Component
@Order(20)
public class DelayJitterQualityCheck implements QualityCheck {

    private final QualityThresholds thresholds;

    public DelayJitterQualityCheck(QualityThresholds thresholds) {
        this.thresholds = thresholds;
    }

    @Override
    public Optional<QualityIssue> check(CallMetrics metrics) {
        for (Leg leg : CallMetrics.QUALITY_LEGS) {
            LegQualityMetrics quality = metrics.qualityOf(leg);
            if (quality == null) {
                continue;
            }
            if (quality.jitterMs().exceeds(thresholds.jitterMs())
                    || quality.rttMs().exceeds(thresholds.rttMs())) {
                return Optional.of(new QualityIssue(IssueCategory.NETWORK_DELAY_JITTER,
                        leg + " jitter/RTT exceeds warning thresholds"));
            }
        }
        return Optional.empty();
    }
}
