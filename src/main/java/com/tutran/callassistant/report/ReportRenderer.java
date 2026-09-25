package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.report.Report;

/**
 * Render {@link Report} thành một định dạng trình bày cụ thể.
 *
 * <p>Sprint 1 có bản text cho CLI; Sprint 2 thêm bản cho Web UI. Cả hai cùng tiêu thụ đúng một
 * {@link Report} nên không thể xảy ra chuyện hai mặt trình bày nói hai kết luận khác nhau.
 */
public interface ReportRenderer {

    String render(Report report);
}
