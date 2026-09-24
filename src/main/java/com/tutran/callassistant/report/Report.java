package com.tutran.callassistant.report;

import java.util.List;

/**
 * Report Schema v1 (PROJECT_SPEC.md §4.5 / §5.1 T8), as a POJO validated against
 * {@code report-schema-v1.json}. In Sprint 1 this is filled entirely by the rule engine;
 * from Sprint 2 the AI Analysis Engine fills {@code summary}, {@code analysis} and
 * {@code suggestions} instead, with Guardrails checking the rest still holds.
 */
public record Report(
        String callId,
        String verdict,
        boolean qualityFlag,
        String issueCategory,
        String confidenceLevel,
        String summary,
        List<EvidenceItem> evidence,
        List<MetricRow> metrics,
        List<String> suggestions,
        List<String> dataLimitations
) {
}
