package com.tutran.callassistant.analysis.verdict.quality;

import com.tutran.callassistant.analysis.verdict.QualityThresholds;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.verdict.QualityIssue;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Chạy lần lượt các {@link QualityCheck} theo thứ tự ưu tiên và lấy kết quả đầu tiên khớp
 * (Chain of Responsibility).
 *
 * <p>Một cuộc gọi kém chất lượng có thể vi phạm nhiều tiêu chí cùng lúc; report chỉ nêu một
 * nguyên nhân chính, nên thứ tự các check chính là thứ tự ưu tiên quy kết nguyên nhân.
 */
@Component
public class QualityInspector {

    private final List<QualityCheck> checks;

    public QualityInspector(List<QualityCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    /** Bộ check mặc định theo đúng thứ tự ưu tiên, dùng cho test và chỗ không có Spring container. */
    public static QualityInspector withDefaults(QualityThresholds thresholds) {
        return new QualityInspector(List.of(
                new PacketLossQualityCheck(thresholds),
                new DelayJitterQualityCheck(thresholds),
                new UnexplainedLowMosQualityCheck(thresholds)));
    }

    public Optional<QualityIssue> inspect(CallMetrics metrics) {
        for (QualityCheck check : checks) {
            Optional<QualityIssue> issue = check.check(metrics);
            if (issue.isPresent()) {
                return issue;
            }
        }
        return Optional.empty();
    }
}
