package com.tutran.callassistant.report;

/** Renders a {@link Report} to the fixed Vietnamese text layout in PROJECT_SPEC.md §4.5. */
public final class ReportRenderer {

    public String render(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Báo cáo phân tích cuộc gọi\n");
        sb.append("Call-ID: ").append(report.callId()).append('\n');
        sb.append("Kết luận: ").append(report.verdict()).append('\n');
        sb.append("Cờ chất lượng: ")
                .append(report.qualityFlag() ? "Có - " + report.issueCategory() : "Không")
                .append('\n');
        sb.append("Độ tin cậy: ").append(report.confidenceLevel()).append('\n');
        sb.append("Tóm tắt: ").append(report.summary()).append('\n');
        sb.append('\n');

        sb.append("## Evidence chính\n");
        int i = 1;
        for (EvidenceItem e : report.evidence()) {
            sb.append(i++).append(". [").append(e.id()).append("][").append(e.source()).append(' ')
                    .append(e.timestamp()).append("] ").append(e.description()).append('\n');
        }
        sb.append('\n');

        sb.append("## Chỉ số cuộc gọi\n");
        sb.append("| Chỉ số | Giá trị | Nguồn |\n");
        for (MetricRow row : report.metrics()) {
            sb.append("| ").append(row.name()).append(" | ").append(row.value()).append(" | ")
                    .append(row.source()).append(" |\n");
        }
        sb.append('\n');

        sb.append("## Vấn đề chất lượng / nguyên nhân khả dĩ\n");
        if (report.issueCategory() != null) {
            sb.append("- Chính: ").append(report.issueCategory()).append('\n');
        } else {
            sb.append("- Không phát hiện vấn đề.\n");
        }
        sb.append('\n');

        sb.append("## Đề xuất\n");
        if (report.suggestions().isEmpty()) {
            sb.append("- Không có đề xuất bổ sung.\n");
        } else {
            for (String suggestion : report.suggestions()) {
                sb.append("- ").append(suggestion).append('\n');
            }
        }
        sb.append('\n');

        sb.append("## Giới hạn dữ liệu\n");
        if (report.dataLimitations().isEmpty()) {
            sb.append("- Không có.\n");
        } else {
            for (String limitation : report.dataLimitations()) {
                sb.append("- ").append(limitation).append('\n');
            }
        }

        return sb.toString();
    }
}
