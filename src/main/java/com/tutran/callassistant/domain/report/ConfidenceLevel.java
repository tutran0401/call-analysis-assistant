package com.tutran.callassistant.domain.report;

/**
 * Độ tin cậy HIGH/MEDIUM/LOW xác định (deterministic), không bao giờ là một con số do AI
 * tự sinh ra (PROJECT_SPEC.md mục 7.2).
 *
 * <p>Enum này chỉ là <i>thang đo</i>; cách suy ra nằm ở
 * {@code report.confidence.ConfidencePolicy} để Sprint 3 T3 thay chính sách (có tính tới
 * mức độ đồng thuận AI-vs-rule) mà không phải sửa kiểu dữ liệu này.
 */
public enum ConfidenceLevel {
    HIGH, MEDIUM, LOW
}
