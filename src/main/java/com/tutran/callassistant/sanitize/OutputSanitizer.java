package com.tutran.callassistant.sanitize;

import com.tutran.callassistant.domain.report.EvidenceItem;
import com.tutran.callassistant.domain.report.MetricRow;
import com.tutran.callassistant.domain.report.Report;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Output Sanitizer (mục 6.2): áp lên phản hồi AI, report hiển thị trên Web UI, thông báo lỗi và application
 * log. Không có giá trị {@code SENSITIVE}/{@code SECRET} dạng gốc nào được ra khỏi hệ thống.
 *
 * <p>{@code callId} của report được giữ nguyên: Call-ID là khoá tra cứu mà người dùng đã tự cung cấp và
 * mẫu report (mục 4.5) hiển thị nó. Chỉ log của AI call mới dùng Call-ID dạng pseudonym.
 */
@Component
public class OutputSanitizer {

    private final SensitiveDataSanitizer engine;

    public OutputSanitizer(SensitiveDataSanitizer engine) {
        this.engine = engine;
    }

    public String sanitize(String text) {
        return engine.sanitizeText(text, new SanitizationSummary());
    }

    public String sanitize(String text, SanitizationSummary summary) {
        return engine.sanitizeText(text, summary);
    }

    public Report sanitize(Report report) {
        return sanitize(report, new SanitizationSummary());
    }

    public Report sanitize(Report report, SanitizationSummary summary) {
        UnaryOperator<String> clean = text -> engine.sanitizeText(text, summary);
        return new Report(
                report.callId(),
                report.verdict(),
                report.qualityFlag(),
                report.issueCategory(),
                mapAll(report.alternativeCauses(), clean),
                report.confidenceLevel(),
                clean.apply(report.summary()),
                report.evidence().stream()
                        .map(e -> new EvidenceItem(e.id(), clean.apply(e.source()), e.timestamp(),
                                clean.apply(e.description())))
                        .toList(),
                report.metrics().stream()
                        .map(m -> new MetricRow(m.name(), clean.apply(m.value()), m.source()))
                        .toList(),
                mapAll(report.suggestions(), clean),
                mapAll(report.dataLimitations(), clean),
                clean.apply(report.analysis()),
                report.degraded(),
                mapAll(report.confidenceReasons(), clean),
                mapAll(report.explanation(), clean));
    }

    /** Số giá trị nhạy cảm còn sót trong text; dùng cho metric Security Leakage (output). */
    public int countResidual(String text) {
        return engine.countResidual(text);
    }

    private static List<String> mapAll(List<String> values, UnaryOperator<String> clean) {
        return values.stream().map(clean).toList();
    }
}
