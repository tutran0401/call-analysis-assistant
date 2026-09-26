package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.report.EvidenceItem;
import com.tutran.callassistant.domain.report.MetricRow;
import com.tutran.callassistant.domain.report.Report;
import org.springframework.stereotype.Component;

/**
 * Render report thành bố cục văn bản tiếng Việt cố định ở PROJECT_SPEC.md mục 4.5.
 *
 * <p>Thứ tự và tên các mục là <b>cố định, do code quyết định</b> (mục 3.2: "Bố cục report - Code
 * (template)"), để cùng một loại vấn đề luôn được trình bày y như nhau ở mọi cuộc gọi.
 */
@Component
public class TextReportRenderer implements ReportRenderer {

    @Override
    public String render(Report report) {
        StringBuilder out = new StringBuilder();
        appendHeader(out, report);
        appendEvidence(out, report);
        appendMetrics(out, report);
        appendIssue(out, report);
        appendSuggestions(out, report);
        appendDataLimitations(out, report);
        return out.toString();
    }

    private void appendHeader(StringBuilder out, Report report) {
        out.append("# Báo cáo phân tích cuộc gọi\n");
        out.append("Call-ID: ").append(report.callId()).append('\n');
        out.append("Kết luận: ").append(report.verdict()).append('\n');
        out.append("Cờ chất lượng: ")
                .append(report.qualityFlag() ? "Có - " + report.issueCategory() : "Không")
                .append('\n');
        out.append("Độ tin cậy: ").append(report.confidenceLevel()).append('\n');
        out.append("Tóm tắt: ").append(report.summary()).append('\n');
        out.append('\n');
    }

    private void appendEvidence(StringBuilder out, Report report) {
        out.append("## Evidence chính\n");
        int index = 1;
        for (EvidenceItem evidence : report.evidence()) {
            out.append(index++).append(". [").append(evidence.id()).append("][")
                    .append(evidence.source()).append(' ').append(evidence.timestamp()).append("] ")
                    .append(evidence.description()).append('\n');
        }
        out.append('\n');
    }

    private void appendMetrics(StringBuilder out, Report report) {
        out.append("## Chỉ số cuộc gọi\n");
        out.append("| Chỉ số | Giá trị | Nguồn |\n");
        for (MetricRow row : report.metrics()) {
            out.append("| ").append(row.name()).append(" | ").append(row.value()).append(" | ")
                    .append(row.source()).append(" |\n");
        }
        out.append('\n');
    }

    /**
     * Mục này <b>luôn</b> nêu một issue category, kể cả cuộc gọi sạch (khi đó là {@code NONE}) - để
     * người đọc phân biệt được "đã kiểm tra, không có vấn đề" với "chưa kiểm tra". Kèm một câu diễn
     * giải tiếng Việt vì tên category là hằng số kỹ thuật, không tự giải thích được.
     */
    private void appendIssue(StringBuilder out, Report report) {
        out.append("## Vấn đề chất lượng / nguyên nhân khả dĩ\n");
        String category = report.issueCategory();
        out.append("- Chính: ").append(category)
                .append(" - ").append(explain(category)).append('\n');
        out.append('\n');
    }

    private String explain(String category) {
        return switch (category) {
            case "NONE" -> "không phát hiện vấn đề chất lượng nào";
            case "NETWORK_PACKET_LOSS" -> "mất gói trên đường truyền";
            case "NETWORK_DELAY_JITTER" -> "độ trễ hoặc jitter mạng cao";
            case "ICE_FAILURE" -> "không thiết lập được kết nối media (ICE)";
            case "TURN_FAILURE" -> "lỗi phía TURN server";
            case "SIGNALING_FAILURE" -> "lỗi ở tầng signaling";
            case "UNKNOWN" -> "chưa quy được nguyên nhân cụ thể";
            default -> "không có mô tả cho category này";
        };
    }

    private void appendSuggestions(StringBuilder out, Report report) {
        out.append("## Đề xuất\n");
        appendBullets(out, report.suggestions(), "- Không có đề xuất bổ sung.\n");
        out.append('\n');
    }

    private void appendDataLimitations(StringBuilder out, Report report) {
        out.append("## Giới hạn dữ liệu\n");
        appendBullets(out, report.dataLimitations(), "- Không có.\n");
    }

    private void appendBullets(StringBuilder out, java.util.List<String> items, String emptyText) {
        if (items.isEmpty()) {
            out.append(emptyText);
            return;
        }
        for (String item : items) {
            out.append("- ").append(item).append('\n');
        }
    }
}
