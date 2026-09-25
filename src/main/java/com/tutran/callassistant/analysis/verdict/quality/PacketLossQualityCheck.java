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

/** Mất gói vượt ngưỡng ở một trong hai bên - nguyên nhân được ưu tiên xét trước nhất. */
@Component
@Order(10)
public class PacketLossQualityCheck implements QualityCheck {

    private final QualityThresholds thresholds;

    public PacketLossQualityCheck(QualityThresholds thresholds) {
        this.thresholds = thresholds;
    }

    @Override
    public Optional<QualityIssue> check(CallMetrics metrics) {
        for (Leg leg : CallMetrics.QUALITY_LEGS) {
            LegQualityMetrics quality = metrics.qualityOf(leg);
            if (quality == null) {
                continue;
            }
            if (quality.packetLossPercent().exceeds(thresholds.packetLossPercent())) {
                return Optional.of(new QualityIssue(IssueCategory.NETWORK_PACKET_LOSS,
                        leg + " packet loss " + quality.packetLossPercent().value() + "% exceeds "
                                + thresholds.packetLossPercent() + "%"));
            }
        }
        return Optional.empty();
    }
}
