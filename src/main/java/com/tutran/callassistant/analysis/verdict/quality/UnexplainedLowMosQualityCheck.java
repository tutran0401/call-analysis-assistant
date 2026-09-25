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
 * MOS thấp nhưng không có chỉ số mạng nào giải thích được. Cố tình trả về
 * {@link IssueCategory#UNKNOWN}: vẫn gắn cờ chất lượng kém để không bỏ sót, nhưng không quy
 * kết bừa cho một nguyên nhân mà evidence không chứng minh được.
 */
@Component
@Order(30)
public class UnexplainedLowMosQualityCheck implements QualityCheck {

    private final QualityThresholds thresholds;

    public UnexplainedLowMosQualityCheck(QualityThresholds thresholds) {
        this.thresholds = thresholds;
    }

    @Override
    public Optional<QualityIssue> check(CallMetrics metrics) {
        for (Leg leg : CallMetrics.QUALITY_LEGS) {
            LegQualityMetrics quality = metrics.qualityOf(leg);
            if (quality == null) {
                continue;
            }
            if (quality.mos().fallsBelow(thresholds.mos())) {
                return Optional.of(new QualityIssue(IssueCategory.UNKNOWN,
                        leg + " MOS " + quality.mos().value() + " is below " + thresholds.mos()
                                + " but no specific network metric explains it"));
            }
        }
        return Optional.empty();
    }
}
