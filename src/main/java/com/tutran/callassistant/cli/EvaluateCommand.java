package com.tutran.callassistant.cli;

import com.tutran.callassistant.application.ai.AiContextBuilder;
import com.tutran.callassistant.application.CallAnalysisPipeline;
import com.tutran.callassistant.application.ChatAnalysisService;
import com.tutran.callassistant.application.SignalingSourcePreference;
import com.tutran.callassistant.application.SignalingSourceResolver;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.ai.NoAiProvider;
import com.tutran.callassistant.evaluation.CaseLoader;
import com.tutran.callassistant.evaluation.CaseMaterializer;
import com.tutran.callassistant.evaluation.EvaluationCase;
import com.tutran.callassistant.evaluation.EvaluationReportWriter;
import com.tutran.callassistant.evaluation.EvaluationResult;
import com.tutran.callassistant.evaluation.EvaluationRunner;
import com.tutran.callassistant.evaluation.RecordingAiProvider;
import com.tutran.callassistant.ingest.file.CallDirectoryIndex;
import com.tutran.callassistant.ingest.file.LocalFileSignalingSource;
import com.tutran.callassistant.ingest.file.TreeSignalingSource;
import com.tutran.callassistant.sanitize.SensitiveDataSanitizer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Chạy Evaluation Runner từ dòng lệnh - lệnh Mentor dùng để chạy tập {@code held-out} bằng file case của
 * họ (PROJECT_SPEC.md mục 6.1 T9, mục 11).
 *
 * <p>Mặc định signaling lấy từ {@code signaling.json} trong {@code --data-root} (offline, không cần
 * Elasticsearch); {@code --signaling es} dùng Elasticsearch đã import như ứng dụng thật.
 */
@Component
@Order(30)
public class EvaluateCommand implements CliCommand {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ChatAnalysisService service;
    private final CallAnalysisPipeline pipeline;
    private final LocalFileSignalingSource localFile;
    private final AiProvider aiProvider;
    private final SensitiveDataSanitizer detector;
    private final ConsoleWriter console;
    private final AiContextBuilder contextBuilder;

    public EvaluateCommand(ChatAnalysisService service, CallAnalysisPipeline pipeline,
                           LocalFileSignalingSource localFile, AiProvider aiProvider,
                           SensitiveDataSanitizer detector, ConsoleWriter console,
                           AiContextBuilder contextBuilder) {
        this.service = service;
        this.pipeline = pipeline;
        this.localFile = localFile;
        this.aiProvider = aiProvider;
        this.detector = detector;
        this.console = console;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public String name() {
        return "evaluate";
    }

    @Override
    public String usage() {
        return "evaluate --cases <cases.yaml> --data-root <dir> [--repeat 5] [--max-questions 5] "
                + "[--out <dir>] [--signaling tree|es]  (đổi cách dựng context: "
                + "--call-analysis.ai.context-mode=semi)";
    }

    @Override
    public void execute(List<String> arguments) {
        Map<String, String> options = Options.parse(arguments);
        if (!options.containsKey("cases") || !options.containsKey("data-root")) {
            console.line("Usage: " + usage());
            return;
        }
        Path casesFile = Path.of(options.get("cases"));
        Path dataRoot = Path.of(options.get("data-root"));
        Path outDir = Path.of(options.getOrDefault("out", "reports/eval-" + LocalDateTime.now().format(STAMP)));
        int repeat = Options.intOf(options, "repeat", 5);
        int maxQuestions = Options.intOf(options, "max-questions", 5);

        List<EvaluationCase> cases = new CaseLoader().load(casesFile);
        CallDirectoryIndex index = new CallDirectoryIndex(dataRoot);

        ChatAnalysisService base = "es".equals(options.get("signaling")) ? service
                : service.withPipeline(pipeline.withSignalingSources(treeResolver(index)));
        RecordingAiProvider recorder = new RecordingAiProvider(aiProvider);
        ChatAnalysisService aiService = base.withAiProvider(recorder);
        ChatAnalysisService ruleService = base.withAiProvider(new NoAiProvider("rule baseline"));

        if ("none".equals(aiProvider.modelName())) {
            console.line("CẢNH BÁO: chưa có AI provider (call-analysis.ai.provider=none) - chỉ đo được rule-based.");
        }
        EvaluationResult result = new EvaluationRunner(aiService, ruleService, recorder,
                new CaseMaterializer(index), detector, repeat, maxQuestions,
                contextBuilder.mode().name().toLowerCase(), console::line).run(cases);
        try {
            new EvaluationReportWriter().write(outDir, result, casesFile.toString(), dataRoot.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write evaluation report to " + outDir, e);
        }
        console.line("Đã ghi kết quả vào " + outDir.toAbsolutePath() + " (summary.md, results.json, samples/)");
    }

    private SignalingSourceResolver treeResolver(CallDirectoryIndex index) {
        Map<SignalingSourcePreference, SignalingEventSource> sources = new EnumMap<>(SignalingSourcePreference.class);
        SignalingEventSource tree = new TreeSignalingSource(index, localFile);
        sources.put(SignalingSourcePreference.INDEXED, tree);
        sources.put(SignalingSourcePreference.LOCAL_FILE, localFile);
        return new SignalingSourceResolver(sources);
    }
}
