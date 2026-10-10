package com.tutran.callassistant.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Ghi kết quả benchmark: {@code results.json} (từng lần chạy + metric, để kiểm toán), {@code summary.md}
 * (bảng metric, so sánh AI vs Rule vs Ground truth, danh sách lỗi, lưu ý về nguồn nhãn) và
 * {@code samples/<case>.md} (report đã render để soi bằng mắt).
 */
public final class EvaluationReportWriter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(Path outDir, EvaluationResult result, String casesFile, String dataRoot) throws IOException {
        Files.createDirectories(outDir.resolve("samples"));
        Files.writeString(outDir.resolve("summary.md"), summary(result, casesFile, dataRoot), StandardCharsets.UTF_8);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("model", result.model());
        json.put("contextMode", result.contextMode());
        json.put("repeat", result.repeat());
        json.put("metrics", result.metrics());
        json.put("ruleBaselines", result.rules());
        json.put("records", result.records());
        mapper.writeValue(outDir.resolve("results.json").toFile(), json);

        for (Map.Entry<String, String> sample : result.samples().entrySet()) {
            Files.writeString(outDir.resolve("samples").resolve(sample.getKey() + ".md"), sample.getValue(),
                    StandardCharsets.UTF_8);
        }
    }

    String summary(EvaluationResult r, String casesFile, String dataRoot) {
        EvaluationMetrics m = r.metrics();
        StringBuilder out = new StringBuilder();
        out.append("# Báo cáo đánh giá\n\n");
        out.append("- Bộ case: `").append(casesFile).append("` (").append(r.cases().size()).append(" case, ")
                .append(r.records().size()).append(" lần chạy)\n");
        out.append("- Dữ liệu: `").append(dataRoot).append("`\n");
        out.append("- Model AI: `").append(r.model()).append("`; mỗi câu hỏi trong ")
                .append(MetricsCalculator.CONSISTENCY_QUESTIONS).append(" câu đầu chạy lặp ").append(r.repeat())
                .append(" lần; cách dựng context: `").append(r.contextMode()).append("`\n");
        if ("none".equals(r.model())) {
            out.append("- **Cảnh báo:** không có AI provider - mọi kết quả dưới đây là rule-based (degraded).\n");
        }
        out.append('\n');

        out.append("## Metric (PROJECT_SPEC.md mục 6.5)\n\n");
        out.append("| Metric | Giá trị | Mẫu | Target | Loại | Kết quả |\n| --- | --- | --- | --- | --- | --- |\n");
        row(out, "Verdict Accuracy", m.verdictAccuracy(), "≥ 85%", "Bắt buộc", atLeast(m.verdictAccuracy(), 85));
        row(out, "Verdict Accuracy - rule baseline (đối chứng)", m.ruleVerdictAccuracy(), "—", "Báo cáo", "—");
        row(out, "Metric Correctness", m.metricCorrectness(), "100%", "Bắt buộc", atLeast(m.metricCorrectness(), 100));
        out.append("| Security Leakage (input) | ").append(m.inputLeakage()).append(" | ")
                .append(m.sensitiveValuesInjected()).append(" giá trị đã chèn | 0 | Bắt buộc | ")
                .append(zero(m.inputLeakage())).append(" |\n");
        out.append("| Security Leakage (output) | ").append(m.outputLeakage()).append(" | | 0 | Bắt buộc | ")
                .append(zero(m.outputLeakage())).append(" |\n");
        out.append("| Security Leakage (log, sau bộ lọc) | ").append(m.logLeakage()).append(" | ")
                .append(r.logProbeMentionsRaw()).append(" event chứa giá trị gốc trước bộ lọc | 0 | Bắt buộc | ")
                .append(zero(m.logLeakage())).append(" |\n");
        row(out, "Issue Category Accuracy", m.issueCategoryAccuracy(), "≥ 70%", "Báo cáo", atLeast(m.issueCategoryAccuracy(), 70));
        row(out, "Quality Flag Accuracy", m.qualityFlagAccuracy(), "—", "Báo cáo", "—");
        row(out, "Consistency (theo case)", m.consistencyCases(), "≥ 95%", "Báo cáo", atLeast(m.consistencyCases(), 95));
        row(out, "Consistency (theo lần chạy)", m.consistencyRuns(), "—", "Báo cáo", "—");
        row(out, "Template Compliance", m.templateCompliance(), "100%", "Báo cáo", atLeast(m.templateCompliance(), 100));
        row(out, "Intent Accuracy", m.intentAccuracy(), "≥ 90%", "Báo cáo", atLeast(m.intentAccuracy(), 90));
        row(out, "Unsupported Claim Rate - report giao cho người dùng (kiểm lại độc lập)", m.deliveredUnsupportedClaims(),
                "0%", "Báo cáo", m.deliveredUnsupportedClaims().measured()
                        ? (m.deliveredUnsupportedClaims().hit() == 0 ? "Đạt" : "Chưa đạt") : "Chưa đo");
        row(out, "Phản hồi AI thô bị Guardrails bắt vì G01/G05 (đã fallback, không tới người dùng)",
                m.unsupportedClaims(), "—", "Báo cáo", "—");
        row(out, "Pipeline Success Rate", m.pipelineSuccess(), "≥ 95%", "Báo cáo", atLeast(m.pipelineSuccess(), 95));
        row(out, "Tỉ lệ fallback (degraded)", m.degradedRate(), "—", "Báo cáo", "—");
        out.append("| Token AI trung bình / lần gọi | ")
                .append(m.avgAiTokens() == null ? "—" : String.format(Locale.ROOT, "%.0f", m.avgAiTokens()))
                .append(" | | — | Báo cáo | — |\n");
        out.append("| Latency P50 / P95 | ").append(ms(m.latencyP50Millis())).append(" / ").append(ms(m.latencyP95Millis()))
                .append(" | | — | Báo cáo | — |\n\n");

        appendComparison(out, r);
        appendMismatches(out, r);
        appendLabelNotes(out, r);
        return out.toString();
    }

    private void appendComparison(StringBuilder out, EvaluationResult r) {
        out.append("## So sánh AI vs Rule vs Ground truth\n\n");
        out.append("AI = kết luận cuối (sau Guardrails) ở câu hỏi đầu tiên, lần chạy đầu. ")
                .append("`degraded` = AI không dùng được nên báo cáo là của rule.\n\n");
        out.append("| Case | Rule | AI | Ground truth | Degraded |\n| --- | --- | --- | --- | --- |\n");
        for (EvaluationCase c : r.cases()) {
            if (c.expectsOutOfScope()) {
                continue;
            }
            Optional<RunRecord> first = r.records().stream()
                    .filter(x -> x.caseId().equals(c.caseId()) && x.questionIndex() == 0 && x.run() == 0).findFirst();
            RuleBaseline rule = r.rules().get(c.caseId());
            out.append("| ").append(c.caseId()).append(" | ").append(rule == null ? "—" : rule.verdict())
                    .append(" | ").append(first.map(x -> x.pipelineOk() ? String.valueOf(x.verdict()) : "LỖI").orElse("—"))
                    .append(" | ").append(c.expectedVerdict() == null ? "—" : c.expectedVerdict())
                    .append(" | ").append(first.map(x -> x.degraded() ? "có" : "không").orElse("—")).append(" |\n");
        }
        out.append('\n');
    }

    private void appendMismatches(StringBuilder out, EvaluationResult r) {
        Map<String, EvaluationCase> byId = new LinkedHashMap<>();
        r.cases().forEach(c -> byId.put(c.caseId(), c));
        List<String> lines = new ArrayList<>();
        for (RunRecord rec : r.records()) {
            EvaluationCase c = byId.get(rec.caseId());
            if (rec.run() != 0) {
                continue;
            }
            if (!rec.pipelineOk()) {
                lines.add("| " + rec.caseId() + " | " + rec.questionIndex() + " | LỖI | " + rec.error() + " |");
            } else if (!c.expectsOutOfScope() && c.expectedVerdict() != null && !c.expectedVerdict().equals(rec.verdict())) {
                lines.add("| " + rec.caseId() + " | " + rec.questionIndex() + " | verdict " + rec.verdict()
                        + " (kỳ vọng " + c.expectedVerdict() + ") | " + String.valueOf(rec.fallbackReason()) + " |");
            } else if (!c.expectsOutOfScope() && c.expectedIssueCategory() != null
                    && rec.verdict() != null && rec.verdict().equals(c.expectedVerdict())
                    && !c.expectedIssueCategory().equals(rec.issueCategory())) {
                lines.add("| " + rec.caseId() + " | " + rec.questionIndex() + " | category " + rec.issueCategory()
                        + " (kỳ vọng " + c.expectedIssueCategory() + ") | " + String.valueOf(rec.fallbackReason()) + " |");
            } else if (c.expectsOutOfScope() != "OUT_OF_SCOPE".equals(rec.intent())) {
                lines.add("| " + rec.caseId() + " | " + rec.questionIndex() + " | intent " + rec.intent() + " | |");
            }
        }
        out.append("## Lần chạy sai so với nhãn (lần chạy đầu của mỗi câu hỏi)\n\n");
        if (lines.isEmpty()) {
            out.append("Không có.\n\n");
            return;
        }
        out.append("| Case | Câu hỏi # | Sai ở đâu | Fallback / ghi chú |\n| --- | --- | --- | --- |\n");
        lines.forEach(l -> out.append(l).append('\n'));
        out.append('\n');
    }

    private void appendLabelNotes(StringBuilder out, EvaluationResult r) {
        Map<String, Integer> sources = new TreeMap<>();
        r.cases().forEach(c -> sources.merge(c.labelSource() == null ? "(không ghi)" : c.labelSource(), 1, Integer::sum));
        out.append("## Nguồn nhãn (đọc trước khi diễn giải accuracy)\n\n");
        sources.forEach((source, count) -> out.append("- ").append(count).append(" case: ").append(source).append('\n'));
        out.append("\nNhãn `rule-output-hand-verified` là kết quả của chính rule engine đã được kiểm tay, nên "
                + "Issue Category Accuracy của rule trên tập đó mang tính đối chiếu, chưa phải ground truth "
                + "độc lập từ mentor.\n");
    }

    private static void row(StringBuilder out, String name, Ratio ratio, String target, String kind, String status) {
        out.append("| ").append(name).append(" | ")
                .append(ratio.measured() ? String.format(Locale.ROOT, "%.1f%%", ratio.percent()) : "chưa đo")
                .append(" | ").append(ratio.hit()).append('/').append(ratio.total())
                .append(" | ").append(target).append(" | ").append(kind).append(" | ").append(status).append(" |\n");
    }

    private static String atLeast(Ratio ratio, double target) {
        if (!ratio.measured()) {
            return "Chưa đo";
        }
        return ratio.percent() >= target ? "Đạt" : "Chưa đạt";
    }

    private static String zero(int value) {
        return value == 0 ? "Đạt" : "Chưa đạt";
    }

    private static String ms(Long value) {
        return value == null ? "—" : value + " ms";
    }
}
