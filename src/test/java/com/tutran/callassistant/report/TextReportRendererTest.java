package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.report.EvidenceItem;
import com.tutran.callassistant.domain.report.MetricRow;
import com.tutran.callassistant.domain.report.Report;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextReportRendererTest {

    private final ReportRenderer renderer = new TextReportRenderer();

    @Test
    void rendersAllSectionsInTemplateOrder() {
        Report report = new Report(
                "CALL-1",
                "SUCCESS",
                true,
                "NETWORK_PACKET_LOSS",
                List.of("NETWORK_DELAY_JITTER - chưa xác nhận được do thiếu RTT/jitter"),
                "MEDIUM",
                "Call established and ended normally, but quality degraded.",
                List.of(new EvidenceItem("EV001", "signaling", "2026-01-01 00:00:00.000", "OK_ACK_OK: confirmed")),
                List.of(new MetricRow("Thời lượng kết nối", "95.300 s", "Signaling")),
                List.of("Kiểm tra chất lượng mạng."),
                List.of("Missing callee_webrtc.log")
        );

        String rendered = renderer.render(report);

        assertThat(rendered).containsSubsequence(
                "# Báo cáo phân tích cuộc gọi",
                "Call-ID: CALL-1",
                "Kết luận: SUCCESS",
                "Cờ chất lượng: Có - NETWORK_PACKET_LOSS",
                "Độ tin cậy: MEDIUM",
                "## Evidence chính",
                "[EV001][signaling",
                "## Chỉ số cuộc gọi",
                "Thời lượng kết nối",
                "## Vấn đề chất lượng / nguyên nhân khả dĩ",
                "- Chính: NETWORK_PACKET_LOSS",
                "- Khả dĩ khác: NETWORK_DELAY_JITTER - chưa xác nhận được do thiếu RTT/jitter",
                "## Đề xuất",
                "Kiểm tra chất lượng mạng.",
                "## Giới hạn dữ liệu",
                "Missing callee_webrtc.log"
        );
    }

    @Test
    void rendersNoQualityFlagAndNoLimitationsGracefully() {
        Report report = new Report("CALL-2", "SUCCESS", false, "NONE", List.of(), "HIGH",
                "Cuộc gọi tốt.", List.of(), List.of(), List.of(), List.of());

        String rendered = renderer.render(report);

        assertThat(rendered).contains("Cờ chất lượng: Không");
        // Cuộc gọi sạch vẫn nêu category, kèm diễn giải tiếng Việt cho tên hằng số kỹ thuật.
        assertThat(rendered).contains("- Chính: NONE - không phát hiện vấn đề chất lượng nào");
        assertThat(rendered).contains("- Không có đề xuất bổ sung.");
        assertThat(rendered).contains("- Không có.");
    }
}
