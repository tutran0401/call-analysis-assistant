package com.tutran.callassistant.analysis.verdict.quality;

import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.verdict.QualityIssue;

import java.util.Optional;

/**
 * Một phép kiểm tra chất lượng trên cuộc gọi đã kết nối thành công (Strategy).
 *
 * <p>Mỗi implementation trả lời đúng một câu hỏi ("có mất gói quá ngưỡng không?", "trễ/jitter
 * có quá ngưỡng không?") và trả về {@link QualityIssue} kèm lý do có số liệu cụ thể, hoặc
 * {@link Optional#empty()} nếu không phát hiện gì. Trước đây cả ba phép kiểm tra nằm trong một
 * method dài với ba vòng lặp; giờ thêm một tiêu chí chất lượng mới là thêm một class, không
 * phải sửa rule engine (Open/Closed).
 *
 * <p>Thứ tự ưu tiên khai báo bằng {@link org.springframework.core.annotation.Order} - kiểm tra
 * nào khớp trước thì category của nó được chọn làm nguyên nhân chính.
 */
public interface QualityCheck {

    Optional<QualityIssue> check(CallMetrics metrics);
}
