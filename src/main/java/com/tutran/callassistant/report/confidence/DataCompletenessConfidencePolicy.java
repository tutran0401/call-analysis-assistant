package com.tutran.callassistant.report.confidence;

import com.tutran.callassistant.domain.report.ConfidenceLevel;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import com.tutran.callassistant.domain.verdict.Verdict;
import org.springframework.stereotype.Component;

/**
 * Chính sách độ tin cậy của Sprint 1: chỉ dựa trên độ đầy đủ của dữ liệu và mức độ chắc chắn của
 * issue category mà rule engine đưa ra.
 *
 * <ul>
 *   <li>UNKNOWN thì luôn LOW - đã không kết luận được thì không thể tự tin.</li>
 *   <li>Đáng lẽ phải chỉ ra được nguyên nhân mà vẫn là UNKNOWN thì LOW.</li>
 *   <li>Còn lại: không thiếu dữ liệu gì thì HIGH, thiếu thì MEDIUM.</li>
 * </ul>
 *
 * <p>Đây là heuristic tạm thời và được ghi rõ trong Known Limitations: Sprint 1 chưa có AI để đối
 * chiếu, nên chưa có tín hiệu đồng thuận nào để tính vào.
 */
@Component
public class DataCompletenessConfidencePolicy implements ConfidencePolicy {

    @Override
    public ConfidenceLevel confidenceFor(RuleVerdictResult verdict) {
        if (verdict.verdict() == Verdict.UNKNOWN) {
            return ConfidenceLevel.LOW;
        }
        boolean categoryExpected = verdict.hasIssueToReport();
        boolean categoryIsDefinite = !categoryExpected || verdict.issueCategory() != IssueCategory.UNKNOWN;
        if (!categoryIsDefinite) {
            return ConfidenceLevel.LOW;
        }
        return verdict.dataLimitations().isEmpty() ? ConfidenceLevel.HIGH : ConfidenceLevel.MEDIUM;
    }
}
