package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.VerdictContext;
import com.tutran.callassistant.analysis.verdict.quality.QualityInspector;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.QualityIssue;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cuộc gọi đã xác nhận và kết thúc bằng BYE thì kết luận SUCCESS, có gắn cờ chất lượng kém nếu
 * {@link QualityInspector} phát hiện chỉ số của một trong hai bên vượt ngưỡng.
 *
 * <p>Là rule cuối chuỗi và <b>luôn khớp</b>: tới được đây nghĩa là đã có cả OK_ACK_OK lẫn BYE,
 * nên không còn tình huống nào để rule sau xét.
 */
@Component
@Order(50)
public class ConnectedSuccessfullyRule implements VerdictRule {

    private final QualityInspector qualityInspector;

    public ConnectedSuccessfullyRule(QualityInspector qualityInspector) {
        this.qualityInspector = qualityInspector;
    }

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        context.recordCallSetupStarted();
        context.recordCallConfirmed(context.callConfirmed().orElseThrow());
        context.recordCallEnded(context.callEnded().orElseThrow());

        Optional<QualityIssue> issue = qualityInspector.inspect(context.metrics());
        if (issue.isEmpty()) {
            return Optional.of(context.success(false, IssueCategory.UNKNOWN,
                    "Call established and ended normally with no quality issues detected."));
        }
        IssueCategory category = issue.get().category();
        return Optional.of(context.success(true, category,
                "Call established and ended normally, but quality degraded (" + category + ")."));
    }
}
