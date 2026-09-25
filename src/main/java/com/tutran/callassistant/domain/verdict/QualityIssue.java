package com.tutran.callassistant.domain.verdict;

/**
 * Một vấn đề chất lượng phát hiện được trên cuộc gọi đã kết nối thành công: category nào
 * và vì sao (kèm con số cụ thể đã vượt ngưỡng). Tồn tại riêng để việc "phát hiện vấn đề
 * chất lượng" tách khỏi việc "quyết định verdict" - xem
 * {@code analysis.verdict.quality.QualityCheck}.
 */
public record QualityIssue(IssueCategory category, String reason) {
}
