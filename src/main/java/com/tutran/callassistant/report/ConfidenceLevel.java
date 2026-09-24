package com.tutran.callassistant.report;

import com.tutran.callassistant.evidence.RuleVerdictResult;
import com.tutran.callassistant.taxonomy.IssueCategory;
import com.tutran.callassistant.taxonomy.Verdict;

/**
 * Deterministic HIGH/MEDIUM/LOW confidence, never an AI-generated number
 * (PROJECT_SPEC.md §7.2). Sprint 1 has no AI to compare against yet, so this is a
 * provisional heuristic based only on data completeness and how definite the rule
 * engine's category is; the full design (factoring in AI-vs-rule agreement) is Sprint 3
 * T3.
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
