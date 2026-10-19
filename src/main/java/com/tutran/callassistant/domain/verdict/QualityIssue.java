package com.tutran.callassistant.domain.verdict;

import com.tutran.callassistant.domain.event.Leg;

/**
 * Một vấn đề chất lượng phát hiện được trên cuộc gọi đã kết nối thành công: category nào
 * và vì sao (kèm con số cụ thể đã vượt ngưỡng). Tồn tại riêng để việc "phát hiện vấn đề
 * chất lượng" tách khỏi việc "quyết định verdict" - xem
 * {@code analysis.verdict.quality.QualityCheck}.
 *
 * @param leg         bên bị ảnh hưởng - rule dùng để ghi dòng stats của bên đó làm evidence (mục 3.3: mọi kết
 *                    luận phải trace được về evidence); {@code null} nếu không gắn với một bên
 * @param description câu tiếng Việt cho evidence, kèm số liệu đã vượt ngưỡng
 */
public record QualityIssue(IssueCategory category, String reason, Leg leg, String description) {

    public QualityIssue(IssueCategory category, String reason) {
        this(category, reason, null, reason);
    }
}
