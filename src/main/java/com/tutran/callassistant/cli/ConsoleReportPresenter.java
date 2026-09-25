package com.tutran.callassistant.cli;

import com.tutran.callassistant.application.AnalysisOutcome;
import com.tutran.callassistant.application.port.out.ReportPresenter;
import com.tutran.callassistant.report.ReportRenderer;
import org.springframework.stereotype.Component;

/**
 * In report ra console, kèm phần ghi chú xử lý ở cuối.
 *
 * <p>Ghi chú xử lý (thiếu file, dòng log lỗi định dạng, phải dùng nguồn dự phòng) được in <b>tách
 * riêng và nói rõ là không thuộc report chính thức</b>: report theo mục 4.5 có bố cục cố định, nhưng
 * người đang debug thì vẫn cần biết dữ liệu vào có vấn đề gì.
 */
@Component
public class ConsoleReportPresenter implements ReportPresenter {

    private final ConsoleWriter console;
    private final ReportRenderer renderer;

    public ConsoleReportPresenter(ConsoleWriter console, ReportRenderer renderer) {
        this.console = console;
        this.renderer = renderer;
    }

    @Override
    public void present(AnalysisOutcome outcome) {
        console.line(renderer.render(outcome.report()));
        if (outcome.processingWarnings().isEmpty()) {
            return;
        }
        console.line("## Ghi chú xử lý (parser warnings, không thuộc report chính thức)");
        outcome.processingWarnings().forEach(warning -> console.line("- " + warning));
    }
}
