package com.tutran.callassistant.application;

import com.tutran.callassistant.domain.report.Report;

import java.util.List;

/**
 * Kết quả một lượt phân tích: report chính thức, cộng với các ghi chú xử lý (thiếu file,
 * dòng log lỗi định dạng, fallback nguồn dữ liệu...). Ghi chú <b>không thuộc</b> report
 * theo mục 4.5 nhưng cần cho người chạy biết dữ liệu vào có vấn đề gì, nên được trả về
 * tách riêng chứ không trộn vào report.
 */
public record AnalysisOutcome(Report report, List<String> processingWarnings) {

    public AnalysisOutcome {
        processingWarnings = List.copyOf(processingWarnings);
    }
}
