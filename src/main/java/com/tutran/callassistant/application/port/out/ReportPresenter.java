package com.tutran.callassistant.application.port.out;

import com.tutran.callassistant.application.AnalysisOutcome;

/**
 * Đưa kết quả phân tích tới người dùng. Sprint 1 có một implementation in ra console;
 * Sprint 2 thêm Web UI. Nhờ port này mà use case phân tích không chứa một dòng
 * {@code System.out} nào.
 */
public interface ReportPresenter {

    void present(AnalysisOutcome outcome);
}
