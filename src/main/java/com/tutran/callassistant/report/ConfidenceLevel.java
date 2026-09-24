package com.tutran.callassistant.report;

import com.tutran.callassistant.evidence.RuleVerdictResult;
import com.tutran.callassistant.taxonomy.IssueCategory;
import com.tutran.callassistant.taxonomy.Verdict;

/**
 * Độ tin cậy HIGH/MEDIUM/LOW xác định (deterministic), không bao giờ là một con số do AI
 * tự sinh ra (PROJECT_SPEC.md mục 7.2). Sprint 1 chưa có AI để đối chiếu, nên đây chỉ là
 * heuristic tạm thời dựa trên độ đầy đủ của dữ liệu và mức độ chắc chắn của category mà
 * rule engine đưa ra; thiết kế đầy đủ (có tính đến mức độ đồng thuận AI-vs-rule) là việc
 * của Sprint 3 T3.
 */
public enum ConfidenceLevel {
    HIGH, MEDIUM, LOW;

    public static ConfidenceLevel derive(RuleVerdictResult result) {
        if (result.verdict() == Verdict.UNKNOWN) {
            return LOW;
        }
        boolean categoryExpected = result.verdict() == Verdict.FAIL || result.qualityFlag();
        boolean categoryIsDefinite = !categoryExpected || result.issueCategory() != IssueCategory.UNKNOWN;
        if (!categoryIsDefinite) {
            return LOW;
        }
        return result.dataLimitations().isEmpty() ? HIGH : MEDIUM;
    }
}
