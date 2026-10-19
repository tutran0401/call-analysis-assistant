package com.tutran.callassistant.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutran.callassistant.application.ChatAnalysisService;
import com.tutran.callassistant.application.ChatRequest;
import com.tutran.callassistant.application.ChatResponse;
import com.tutran.callassistant.application.validation.UploadedFile;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.sanitize.SensitiveDataSanitizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Evaluation Runner (T9): chạy bộ case qua <b>đúng đường đi của một request thật</b>
 * ({@link ChatAnalysisService}) - không gọi tắt vào tầng phân tích - rồi đo các metric ở mục 6.5.
 *
 * <p>Lặp lại được: cùng bộ case + cùng dữ liệu + AI temperature 0 cho cùng kết quả (tới mức provider cho
 * phép); và kiểm toán được: {@code results.json} có từng lần chạy, {@code summary.md} nói rõ nguồn nhãn.
 *
 * <p>Câu hỏi 0..{@value MetricsCalculator#CONSISTENCY_QUESTIONS}-1 của mỗi case được chạy {@code repeat}
 * lần để đo Consistency; các câu còn lại chạy một lần (chỉ để đo accuracy) - giữ chi phí gọi AI hợp lý.
 */
public final class EvaluationRunner {

    private final ChatAnalysisService aiService;
    private final ChatAnalysisService ruleService;
    private final RecordingAiProvider recorder;
    private final CaseMaterializer materializer;
    private final SensitiveDataSanitizer detector;
    private final MetricsCalculator calculator = new MetricsCalculator();
    private final RawValueOracle oracle = new RawValueOracle();
    private final ObjectMapper mapper = new ObjectMapper();
    private final int repeat;
    private final int maxQuestions;
    private final String contextMode;
    private final Consumer<String> progress;

    /**
     * @param aiService   service dùng {@code recorder} làm AI provider
     * @param ruleService cùng cấu hình nhưng không AI - nguồn của rule baseline
     */
    public EvaluationRunner(ChatAnalysisService aiService, ChatAnalysisService ruleService,
                            RecordingAiProvider recorder, CaseMaterializer materializer,
                            SensitiveDataSanitizer detector, int repeat, int maxQuestions,
                            String contextMode, Consumer<String> progress) {
        this.aiService = aiService;
        this.ruleService = ruleService;
        this.recorder = recorder;
        this.materializer = materializer;
        this.detector = detector;
        this.repeat = Math.max(1, repeat);
        this.maxQuestions = Math.max(1, maxQuestions);
        this.contextMode = contextMode;
        this.progress = progress;
    }

    public EvaluationResult run(List<EvaluationCase> cases) {
        List<RunRecord> records = new ArrayList<>();
        Map<String, RuleBaseline> rules = new LinkedHashMap<>();
        Map<String, String> samples = new LinkedHashMap<>();
        List<String> allProbes = cases.stream().flatMap(c -> c.sensitiveValues().stream()).distinct().toList();

        int logLeakage;
        int rawMentions;
        Independent independent = new Independent();
        try (LogCapture logs = new LogCapture()) {
            for (EvaluationCase c : cases) {
                progress.accept("[" + c.caseId() + "] " + c.questions().size() + " câu hỏi");
                runCase(c, records, rules, samples, independent);
            }
            logLeakage = logs.leakedAfterSanitizing(allProbes, detector);
            rawMentions = logs.rawMentions(allProbes);
            independent.logLeaks = logs.independentLeaks(independent.allValues, independent.examples);
            independent.logRaw = logs.independentRawMentions(independent.allValues);
        }
        EvaluationMetrics metrics = calculator.compute(cases, records, rules, logLeakage);
        return new EvaluationResult(recorder.modelName(), contextMode, repeat, cases, records, rules, metrics, samples,
                rawMentions, independent.result());
    }

    /** Bộ đếm của phép đo độc lập ({@link RawValueOracle}) trong một lần chạy. */
    private static final class Independent {
        final Set<String> allValues = new LinkedHashSet<>();
        final List<String> examples = new ArrayList<>();
        Set<String> current = Set.of();
        int inputLeaks;
        int outputLeaks;
        int logLeaks;
        int logRaw;

        void count(String target, String text, boolean input) {
            List<String> found = RawValueOracle.foundIn(text, current);
            if (input) {
                inputLeaks += found.size();
            } else {
                outputLeaks += found.size();
            }
            found.forEach(v -> examples.add(target + ": " + RawValueOracle.describe(v)));
        }

        IndependentLeakage result() {
            return new IndependentLeakage(allValues.size(), inputLeaks, outputLeaks, logLeaks, logRaw,
                    examples.stream().distinct().limit(10).toList());
        }
    }

    private void runCase(EvaluationCase c, List<RunRecord> records, Map<String, RuleBaseline> rules,
                         Map<String, String> samples, Independent independent) {
        List<UploadedFile> files;
        try {
            files = materializer.materialize(c);
        } catch (RuntimeException e) {
            // Lỗi cấu hình case (thiếu file dữ liệu) ghi nhận là request lỗi, không dừng cả lần chạy.
            records.add(failed(c, 0, 0, c.questions().get(0), "Case data error: " + e.getMessage()));
            progress.accept("  ! " + e.getMessage());
            return;
        }
        independent.current = oracle.extract(files, c.callId());
        independent.allValues.addAll(independent.current);
        if (!c.expectsOutOfScope()) {
            captureRuleBaseline(c, files, rules);
        }
        int questions = Math.min(maxQuestions, c.questions().size());
        for (int q = 0; q < questions; q++) {
            int runs = q < MetricsCalculator.CONSISTENCY_QUESTIONS ? repeat : 1;
            for (int run = 0; run < runs; run++) {
                RunRecord record = execute(c, files, q, run, samples, independent);
                records.add(record);
            }
        }
    }

    private void captureRuleBaseline(EvaluationCase c, List<UploadedFile> files, Map<String, RuleBaseline> rules) {
        try {
            ChatResponse response = ruleService.handle(new ChatRequest(c.questions().get(0), files, c.callId()));
            Report report = response.report();
            if (report != null) {
                rules.put(c.caseId(), new RuleBaseline(c.caseId(), report.verdict(), report.issueCategory(),
                        report.qualityFlag(), report.metrics()));
            }
        } catch (RuntimeException e) {
            progress.accept("  ! rule baseline failed for " + c.caseId() + ": " + e.getClass().getSimpleName());
        }
    }

    private RunRecord execute(EvaluationCase c, List<UploadedFile> files, int q, int run,
                              Map<String, String> samples, Independent independent) {
        String question = c.questions().get(q);
        recorder.reset();
        long started = System.nanoTime();
        try {
            ChatResponse response = aiService.handle(new ChatRequest(question, files, c.callId()));
            long latency = (System.nanoTime() - started) / 1_000_000;
            String aiRequest = recorder.lastRequest();
            String output = outputText(response);
            independent.count("AI request " + c.caseId(), aiRequest, true);
            independent.count("response " + c.caseId(), output, false);
            Report report = response.report();
            if (q == 0 && run == 0 && response.renderedText() != null && !response.renderedText().isEmpty()) {
                samples.put(c.caseId(), response.renderedText());
            }
            return new RunRecord(c.caseId(), q, question, run, response.intent().name(),
                    report == null ? null : report.verdict(),
                    report == null ? null : report.issueCategory(),
                    report != null && report.qualityFlag(),
                    report == null ? null : report.confidenceLevel(),
                    response.degraded(), response.fallbackReason(), latency, true, null, aiRequest != null,
                    LogCapture.countProbes(aiRequest, c.sensitiveValues()),
                    aiRequest == null ? 0 : detector.countResidual(aiRequest),
                    LogCapture.countProbes(output, c.sensitiveValues()), detector.countResidual(output),
                    response.warnings(), report, response.renderedText(), response.aiTokens());
        } catch (RuntimeException e) {
            return failed(c, q, run, question, e.getClass().getSimpleName());
        }
    }

    private static RunRecord failed(EvaluationCase c, int q, int run, String question, String error) {
        return new RunRecord(c.caseId(), q, question, run, null, null, null, false, null, false, null, 0, false,
                error, false, 0, 0, 0, 0, List.of(), null, null, null);
    }

    /** Mọi thứ người dùng nhìn thấy trong một response, để quét rò rỉ. */
    private String outputText(ChatResponse response) {
        StringBuilder text = new StringBuilder();
        if (response.message() != null) {
            text.append(response.message()).append('\n');
        }
        if (response.report() != null) {
            try {
                text.append(mapper.writeValueAsString(response.report())).append('\n');
            } catch (JsonProcessingException e) {
                text.append(response.report()).append('\n');
            }
        }
        text.append(response.renderedText()).append('\n');
        response.warnings().forEach(w -> text.append(w).append('\n'));
        return text.toString();
    }
}
