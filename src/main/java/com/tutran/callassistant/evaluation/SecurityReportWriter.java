package com.tutran.callassistant.evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Báo cáo Security Evaluation (Sprint 3 T6): mỗi mã S01-S08 lọt bao nhiêu giá trị nhạy cảm vào AI request,
 * vào report / response, và vào application log. Mục tiêu của mọi cột là 0 (mục 6.5, "Bắt buộc").
 *
 * <p>Hai cột cho mỗi đích: <i>giá trị đã chèn</i> (đúng chuỗi gốc trong case) và <i>detector còn bắt được</i>
 * (giá trị nhạy cảm nào khác mà Sanitizer vẫn nhận ra trong văn bản đã xử lý) - cột thứ hai bắt cả trường hợp
 * chuỗi bị biến dạng nhẹ nhưng vẫn là dữ liệu nhạy cảm.
 */
public final class SecurityReportWriter {

    /** Một lượt chạy bộ case với một AI provider. */
    public record Pass(String title, EvaluationResult result) {
    }

    public Path write(Path outDir, String casesFile, List<Pass> passes) throws IOException {
        Files.createDirectories(outDir);
        StringBuilder md = new StringBuilder();
        md.append("# Security Evaluation (S01-S08)\n\n");
        md.append("- Bộ case: `").append(casesFile).append("`\n");
        md.append("- Thời điểm chạy: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("\n");
        md.append("- Mục tiêu: mọi cột bằng 0 (PROJECT_SPEC.md mục 6.5, Security Leakage - Bắt buộc)\n\n");

        boolean allClean = true;
        for (Pass pass : passes) {
            allClean &= appendPass(md, pass);
        }
        md.append("## Kết luận\n\n");
        md.append(allClean
                ? "**PASS** - không giá trị nhạy cảm nào lọt vào AI request, report / response hay application log.\n"
                : "**FAIL** - có giá trị nhạy cảm bị lọt; xem các dòng khác 0 ở trên.\n");

        Path summary = outDir.resolve("summary.md");
        Files.writeString(summary, md.toString(), StandardCharsets.UTF_8);
        return summary;
    }

    /** @return {@code true} khi lượt này không lọt gì */
    private boolean appendPass(StringBuilder md, Pass pass) {
        EvaluationResult result = pass.result();
        md.append("## ").append(pass.title()).append("\n\n");
        md.append("Model: `").append(result.model()).append("`\n\n");
        md.append("| Mã | Case | Giá trị đã chèn | AI request: giá trị gốc | AI request: detector còn bắt | "
                + "Report/response: giá trị gốc | Report/response: detector còn bắt | Fallback |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");

        Map<String, List<RunRecord>> byCase = result.records().stream()
                .collect(Collectors.groupingBy(RunRecord::caseId));
        int input = 0;
        int output = 0;
        for (EvaluationCase c : result.cases()) {
            List<RunRecord> runs = byCase.getOrDefault(c.caseId(), List.of());
            int inProbe = runs.stream().mapToInt(RunRecord::inputProbeHits).sum();
            int inResidual = runs.stream().mapToInt(RunRecord::inputResidual).sum();
            int outProbe = runs.stream().mapToInt(RunRecord::outputProbeHits).sum();
            int outResidual = runs.stream().mapToInt(RunRecord::outputResidual).sum();
            input += inProbe + inResidual;
            output += outProbe + outResidual;
            String fallback = runs.stream().map(r -> r.fallbackReason() == null ? "-" : r.fallbackReason())
                    .distinct().collect(Collectors.joining(", "));
            md.append("| ").append(code(c)).append(" | ").append(c.caseId()).append(" | ")
                    .append(c.sensitiveValues().size()).append(" | ").append(inProbe).append(" | ")
                    .append(inResidual).append(" | ").append(outProbe).append(" | ").append(outResidual)
                    .append(" | ").append(fallback).append(" |\n");
        }
        int log = result.metrics().logLeakage();
        md.append("\n| Đích | Số giá trị lọt | Kết quả |\n| --- | --- | --- |\n");
        md.append("| AI request (input) | ").append(input).append(" | ").append(verdict(input)).append(" |\n");
        md.append("| Report / response (output) | ").append(output).append(" | ").append(verdict(output)).append(" |\n");
        md.append("| Application log, text + JSON (output) | ").append(log).append(" | ").append(verdict(log))
                .append(" |\n");
        md.append("\nSự kiện log chứa giá trị gốc <i>trước</i> bộ lọc: ").append(result.logProbeMentionsRaw())
                .append(" (khác 0 nghĩa là bộ lọc log thực sự đã được thử).\n\n");
        return input == 0 && output == 0 && log == 0;
    }

    private static String code(EvaluationCase c) {
        String notes = c.notes() == null ? "" : c.notes().strip();
        return notes.matches("^S0\\d.*") ? notes.substring(0, 3) : "-";
    }

    private static String verdict(int leaks) {
        return leaks == 0 ? "Đạt" : "KHÔNG ĐẠT";
    }
}
