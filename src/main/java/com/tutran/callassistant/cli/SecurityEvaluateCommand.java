package com.tutran.callassistant.cli;

import com.tutran.callassistant.ai.FakeAiProvider;
import com.tutran.callassistant.ai.NoAiProvider;
import com.tutran.callassistant.application.CallAnalysisPipeline;
import com.tutran.callassistant.application.ChatAnalysisService;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.evaluation.CaseLoader;
import com.tutran.callassistant.evaluation.CaseMaterializer;
import com.tutran.callassistant.evaluation.EvaluationCase;
import com.tutran.callassistant.evaluation.EvaluationResult;
import com.tutran.callassistant.evaluation.EvaluationRunner;
import com.tutran.callassistant.evaluation.LeakyAiProvider;
import com.tutran.callassistant.evaluation.RecordingAiProvider;
import com.tutran.callassistant.evaluation.SecurityReportWriter;
import com.tutran.callassistant.ingest.file.CallDirectoryIndex;
import com.tutran.callassistant.ingest.file.LocalFileSignalingSource;
import com.tutran.callassistant.sanitize.SensitiveDataSanitizer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Chạy lại toàn bộ S01-S08 qua pipeline thật và ghi báo cáo leakage (Sprint 3 T6).
 *
 * <p>Hai lượt trên cùng bộ case:
 * <ol>
 *   <li>AI provider đang cấu hình (OpenAI thật nếu có; chưa cấu hình thì AI giả) - đo leakage input/output/log
 *       của S01-S06, S08.</li>
 *   <li>AI giả cố tình chèn mọi giá trị nhạy cảm vào câu trả lời (S07) - đo khả năng chặn ở phía output.</li>
 *   <li>Như lượt 2 nhưng chỉ các giá trị không có chữ số, để vượt qua Guardrails G05 và thử riêng Output Sanitizer.</li>
 * </ol>
 */
@Component
@Order(35)
public class SecurityEvaluateCommand implements CliCommand {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ChatAnalysisService service;
    private final CallAnalysisPipeline pipeline;
    private final LocalFileSignalingSource localFile;
    private final AiProvider aiProvider;
    private final SensitiveDataSanitizer detector;
    private final ConsoleWriter console;

    public SecurityEvaluateCommand(ChatAnalysisService service, CallAnalysisPipeline pipeline,
                                   LocalFileSignalingSource localFile, AiProvider aiProvider,
                                   SensitiveDataSanitizer detector, ConsoleWriter console) {
        this.service = service;
        this.pipeline = pipeline;
        this.localFile = localFile;
        this.aiProvider = aiProvider;
        this.detector = detector;
        this.console = console;
    }

    @Override
    public String name() {
        return "security-eval";
    }

    @Override
    public String usage() {
        return "security-eval --data-root <dir> [--cases benchmark/security/cases.yaml] [--out <dir>]";
    }

    @Override
    public void execute(List<String> arguments) {
        Map<String, String> options = Options.parse(arguments);
        if (!options.containsKey("data-root")) {
            console.line("Usage: " + usage());
            return;
        }
        Path casesFile = Path.of(options.getOrDefault("cases", "benchmark/security/cases.yaml"));
        Path dataRoot = Path.of(options.get("data-root"));
        Path outDir = Path.of(options.getOrDefault("out", "reports/security-" + LocalDateTime.now().format(STAMP)));

        List<EvaluationCase> cases = new CaseLoader().load(casesFile);
        CallDirectoryIndex index = new CallDirectoryIndex(dataRoot);
        ChatAnalysisService base = service.withPipeline(
                pipeline.withSignalingSources(EvaluateCommand.treeResolver(index, localFile)));
        ChatAnalysisService ruleService = base.withAiProvider(NoAiProvider.ruleOnly("rule baseline"));

        AiProvider configured = "none".equals(aiProvider.modelName()) ? new FakeAiProvider() : aiProvider;
        List<String> allValues = cases.stream().flatMap(c -> c.sensitiveValues().stream()).distinct().toList();
        AiProvider leaky = new LeakyAiProvider(new FakeAiProvider(), allValues);

        SecurityReportWriter.Pass normal = new SecurityReportWriter.Pass(
                "Lượt 1 - S01-S06, S08 với AI provider đang cấu hình", run(cases, index, base, ruleService, configured));
        SecurityReportWriter.Pass leaking = new SecurityReportWriter.Pass(
                "Lượt 2 - S07: AI cố tình trả lại mọi giá trị nhạy cảm", run(cases, index, base, ruleService, leaky));
        // Lượt 2 thường bị Guardrails G05 chặn trước (giá trị có chữ số = "số liệu lạ"), nên Output Sanitizer chưa
        // thật sự được thử. Lượt 3 chỉ rò các giá trị không có chữ số (email) - lọt qua Guardrails, buộc Output
        // Sanitizer phải tự che.
        List<String> digitFree = allValues.stream().filter(v -> v.chars().noneMatch(Character::isDigit)).toList();
        SecurityReportWriter.Pass pastGuardrails = new SecurityReportWriter.Pass(
                "Lượt 3 - S07: AI trả lại giá trị không có chữ số (vượt qua Guardrails, chỉ còn Output Sanitizer chặn)",
                run(cases, index, base, ruleService, new LeakyAiProvider(new FakeAiProvider(), digitFree)));
        try {
            Path summary = new SecurityReportWriter().write(outDir, casesFile.toString(),
                    List.of(normal, leaking, pastGuardrails));
            console.line("Đã ghi báo cáo bảo mật vào " + summary.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write security report to " + outDir, e);
        }
    }

    private EvaluationResult run(List<EvaluationCase> cases, CallDirectoryIndex index, ChatAnalysisService base,
                                 ChatAnalysisService ruleService, AiProvider provider) {
        RecordingAiProvider recorder = new RecordingAiProvider(provider);
        return new EvaluationRunner(base.withAiProvider(recorder), ruleService, recorder,
                new CaseMaterializer(index), detector, 1, 5, "structured", console::line).run(cases);
    }
}
