package com.tutran.callassistant.domain.verdict;

import java.util.List;

/**
 * Một mục taxonomy cho một {@link IssueCategory}: ý nghĩa là gì, biểu hiện ra sao,
 * evidence nào chứng minh được, điều kiện xác định (deterministic) mà rule engine kiểm
 * tra, và category này thường bị nhầm lẫn với category nào khác. Được mirror ở dạng
 * đọc-hiểu-được trong {@code docs/verdict-issue-taxonomy.md}.
 */
public record IssueCategoryDefinition(
        IssueCategory category,
        String definition,
        List<String> symptoms,
        List<String> requiredEvidence,
        String detectionCondition,
        String knownAmbiguity
) {
}
