package com.tutran.callassistant.taxonomy;

import java.util.List;

/**
 * The taxonomy entry for one {@link IssueCategory}: what it means, how it presents,
 * what evidence proves it, the deterministic condition {@code RuleVerdictEngine} checks,
 * and where the category is known to be ambiguous with another one. Mirrored in
 * human-readable form in {@code docs/verdict-issue-taxonomy.md}.
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
