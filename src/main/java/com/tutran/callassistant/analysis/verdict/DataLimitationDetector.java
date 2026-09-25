package com.tutran.callassistant.analysis.verdict;

/**
 * Phát hiện những gì còn thiếu trong dữ liệu vào và ghi vào mục "Giới hạn dữ liệu" của report
 * (mục 4.5).
 *
 * <p>Tách khỏi {@link com.tutran.callassistant.analysis.verdict.rule.VerdictRule} vì đây là hai
 * việc khác nhau: rule <i>quyết định</i> verdict, còn detector chỉ <i>mô tả</i> dữ liệu thiếu -
 * và giới hạn dữ liệu phải được nêu ra kể cả khi cuộc gọi vẫn kết luận được SUCCESS bình
 * thường. Mọi detector đều chạy, không có chuyện dừng ở cái đầu tiên khớp.
 */
public interface DataLimitationDetector {

    void detect(VerdictContext context);
}
