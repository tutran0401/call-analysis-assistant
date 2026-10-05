package com.tutran.callassistant.domain.report;

import java.util.List;

/**
 * Report Schema v2 (PROJECT_SPEC.md mục 4.5 / mục 5.1 T8), dạng POJO được validate theo
 * {@code report-schema-v2.json}. Rule engine điền toàn bộ field; khi có AI, AI Analysis Engine chỉ điền
 * {@code summary}, {@code analysis} và {@code suggestions} (sau khi qua Guardrails), còn verdict,
 * evidence và chỉ số vẫn là của code.
 *
 * <p>Đây là <b>hợp đồng (contract)</b> giữa tầng phân tích và mọi cách trình bày: CLI của
 * Sprint 1 và Web UI của Sprint 2 đều chỉ tiêu thụ đúng record này.
 *
 * <p>{@code alternativeCauses} là các nguyên nhân khả dĩ khác ngoài {@code issueCategory} - mẫu
 * report ở mục 4.5 có dòng "Khả dĩ khác" cho việc này. Nội dung lấy từ phần "điểm mơ hồ đã biết"
 * của taxonomy (T5), tức là chỗ ghi nhận category này dễ bị nhầm với cái gì.
 *
 * <p>{@code analysis} rỗng và {@code degraded = false} là giá trị mặc định cho report rule-based
 * (constructor 11 tham số); {@code degraded = true} nghĩa là AI không dùng được và đây là kết quả fallback.
 */
public record Report(
        String callId,
        String verdict,
        boolean qualityFlag,
        String issueCategory,
        List<String> alternativeCauses,
        String confidenceLevel,
        String summary,
        List<EvidenceItem> evidence,
        List<MetricRow> metrics,
        List<String> suggestions,
        List<String> dataLimitations,
        String analysis,
        boolean degraded
) {

    public Report {
        analysis = analysis == null ? "" : analysis;
    }

    /** Report rule-based: không có phần phân tích AI, không degraded. */
    public Report(String callId, String verdict, boolean qualityFlag, String issueCategory,
                  List<String> alternativeCauses, String confidenceLevel, String summary,
                  List<EvidenceItem> evidence, List<MetricRow> metrics, List<String> suggestions,
                  List<String> dataLimitations) {
        this(callId, verdict, qualityFlag, issueCategory, alternativeCauses, confidenceLevel, summary,
                evidence, metrics, suggestions, dataLimitations, "", false);
    }
}
