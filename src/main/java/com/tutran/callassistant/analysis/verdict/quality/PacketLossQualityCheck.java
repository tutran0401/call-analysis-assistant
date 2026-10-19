package com.tutran.callassistant.analysis.verdict.quality;

import com.tutran.callassistant.analysis.verdict.QualityThresholds;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.metrics.LegQualityMetrics;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.QualityIssue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Mất gói vượt ngưỡng ở một trong hai bên - nguyên nhân được ưu tiên xét trước nhất.
 *
 * <p>So ngưỡng với mất gói <b>cả cuộc</b> (bộ đếm cộng dồn), không phải mẫu cao nhất của
 * {@code audio.packetLostPercent}: một khoảng đo ngắn mất nhiều gói không làm cả cuộc "chất lượng kém". Chỉ khi log
 * không có bộ đếm cộng dồn mới quay về mẫu cao nhất, và lý do ghi rõ điều đó.
 */
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
            boolean wholeCall = quality.wholeCallPacketLossPercent().isAvailable();
            MetricResult<Double> loss = wholeCall ? quality.wholeCallPacketLossPercent() : quality.packetLossPercent();
            if (loss.exceeds(thresholds.packetLossPercent())) {
                String basis = wholeCall ? "mất gói cả cuộc" : "mẫu packet loss cao nhất (log không có bộ đếm cộng dồn)";
                return Optional.of(new QualityIssue(IssueCategory.NETWORK_PACKET_LOSS,
                        leg + " packet loss " + loss.value() + "% exceeds " + thresholds.packetLossPercent() + "%",
                        leg, "Periodic stats của " + leg + ": " + basis + " " + loss.value() + "% vượt ngưỡng "
                                + thresholds.packetLossPercent() + "%"));
            }
        }
        return Optional.empty();
    }
}
