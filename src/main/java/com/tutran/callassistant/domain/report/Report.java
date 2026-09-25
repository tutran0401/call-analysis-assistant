package com.tutran.callassistant.domain.report;

import java.util.List;

/**
 * Report Schema v1 (PROJECT_SPEC.md mục 4.5 / mục 5.1 T8), dạng POJO được validate theo
 * {@code report-schema-v1.json}. Ở Sprint 1, toàn bộ field này do rule engine điền; từ
 * Sprint 2 trở đi, AI Analysis Engine sẽ điền {@code summary}, {@code analysis} và
 * {@code suggestions} thay vào đó, còn Guardrails kiểm tra phần còn lại vẫn đúng.
 *
 * <p>Đây là <b>hợp đồng (contract)</b> giữa tầng phân tích và mọi cách trình bày: CLI của
 * Sprint 1 và Web UI của Sprint 2 đều chỉ tiêu thụ đúng record này.
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
