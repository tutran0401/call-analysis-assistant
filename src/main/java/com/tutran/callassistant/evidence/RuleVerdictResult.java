package com.tutran.callassistant.evidence;

import com.tutran.callassistant.taxonomy.IssueCategory;
import com.tutran.callassistant.taxonomy.Verdict;

import java.util.List;

/**
 * The rule-based baseline conclusion for one call: the deterministic counterpart AI's
 * verdict is checked against in Sprint 2 (PROJECT_SPEC.md §3.2).
 */
public record RuleVerdictResult(
        Verdict verdict,
        boolean qualityFlag,
        IssueCategory issueCategory,
        String summary,
        List<Evidence> evidence,
        List<String> dataLimitations
) {
}
