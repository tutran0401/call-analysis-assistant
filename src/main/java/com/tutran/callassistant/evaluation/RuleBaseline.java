package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.domain.report.MetricRow;

import java.util.List;

/** Kết quả rule-based (không AI) của một case - mốc để so AI vs Rule và kiểm Metric Correctness. */
public record RuleBaseline(String caseId, String verdict, String issueCategory, boolean qualityFlag,
                           List<MetricRow> metrics) {

    public RuleBaseline {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }
}
