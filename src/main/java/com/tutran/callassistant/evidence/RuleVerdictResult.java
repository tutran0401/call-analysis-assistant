package com.tutran.callassistant.evidence;

import com.tutran.callassistant.taxonomy.IssueCategory;
import com.tutran.callassistant.taxonomy.Verdict;

import java.util.List;

/**
 * Kết luận baseline theo rule cho một cuộc gọi: đây là đối chứng xác định (deterministic)
 * mà verdict của AI sẽ được đối chiếu vào ở Sprint 2 (PROJECT_SPEC.md mục 3.2).
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
