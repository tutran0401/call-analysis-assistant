package com.tutran.callassistant.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tutran.callassistant.application.ai.AiContextBuilder;
import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiPromptFactory;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.ai.TimeLimitedAiCaller;
import com.tutran.callassistant.application.guardrail.AiResponseGuard;
import com.tutran.callassistant.application.guardrail.GuardDecision;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.application.port.out.UploadValidator;
import com.tutran.callassistant.application.request.ParsedRequest;
import com.tutran.callassistant.application.request.RequestParser;
import com.tutran.callassistant.application.validation.ValidatedUpload;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.report.ReportGuard;
import com.tutran.callassistant.report.ReportRenderer;
import com.tutran.callassistant.sanitize.OutputSanitizer;
import com.tutran.callassistant.sanitize.Pseudonymizer;
import com.tutran.callassistant.sanitize.SanitizedContext;
import com.tutran.callassistant.sanitize.InputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Điều phối một lượt hỏi trên Web UI - kiến trúc mục tiêu mục 3.1, từ trên xuống:
 *
 * <pre>
 *   Request Parser -&gt; File Validator -&gt; pipeline rule-based (Sprint 1)
 *   -&gt; Input Sanitizer -&gt; AI Analysis -&gt; Guardrails -&gt; Output Sanitizer -&gt; Report Schema Guard
 * </pre>
 *
 * <p><b>AI không bao giờ làm hỏng request:</b> mọi nhánh lỗi (chưa cấu hình, timeout, trả lời hỏng, bị
 * Guardrails từ chối, context chưa sạch) đều rơi về report rule-based đánh dấu {@code degraded}.
 *
 * <p>Log AI call chỉ gồm {@code request_id, call_id (pseudonym), model, latency, result_status,
 * token_usage, fallback_reason} (mục 6.2) - không bao giờ ghi nội dung request/response của AI.
 */
@Service
public class ChatAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ChatAnalysisService.class);

    private final RequestParser requestParser;
    private final UploadValidator uploadValidator;
    private final CallAnalysisPipeline pipeline;
    private final AiContextBuilder contextBuilder;
    private final InputSanitizer inputSanitizer;
    private final AiPromptFactory promptFactory;
    private final AiProvider aiProvider;
    private final TimeLimitedAiCaller aiCaller;
    private final AiResponseGuard aiGuard;
    private final AnalysisReportComposer composer;
    private final OutputSanitizer outputSanitizer;
    private final ReportGuard reportGuard;
    private final ReportRenderer renderer;
    private final Pseudonymizer pseudonymizer;

    public ChatAnalysisService(RequestParser requestParser, UploadValidator uploadValidator,
                               CallAnalysisPipeline pipeline, AiContextBuilder contextBuilder,
                               InputSanitizer inputSanitizer, AiPromptFactory promptFactory,
                               AiProvider aiProvider, TimeLimitedAiCaller aiCaller, AiResponseGuard aiGuard,
                               AnalysisReportComposer composer, OutputSanitizer outputSanitizer,
                               ReportGuard reportGuard, ReportRenderer renderer, Pseudonymizer pseudonymizer) {
        this.requestParser = requestParser;
        this.uploadValidator = uploadValidator;
        this.pipeline = pipeline;
        this.contextBuilder = contextBuilder;
        this.inputSanitizer = inputSanitizer;
        this.promptFactory = promptFactory;
        this.aiProvider = aiProvider;
        this.aiCaller = aiCaller;
        this.aiGuard = aiGuard;
        this.composer = composer;
        this.outputSanitizer = outputSanitizer;
        this.reportGuard = reportGuard;
        this.renderer = renderer;
        this.pseudonymizer = pseudonymizer;
    }

    /** Bản sao dùng pipeline khác (Evaluation Runner đổi nguồn signaling); mọi thành phần còn lại giữ nguyên. */
    public ChatAnalysisService withPipeline(CallAnalysisPipeline other) {
        return new ChatAnalysisService(requestParser, uploadValidator, other, contextBuilder, inputSanitizer,
                promptFactory, aiProvider, aiCaller, aiGuard, composer, outputSanitizer, reportGuard, renderer,
                pseudonymizer);
    }

    /** Bản sao dùng AI provider khác (Evaluation Runner bọc provider để ghi lại request, hoặc chạy rule-only). */
    public ChatAnalysisService withAiProvider(AiProvider other) {
        return new ChatAnalysisService(requestParser, uploadValidator, pipeline, contextBuilder, inputSanitizer,
                promptFactory, other, aiCaller, aiGuard, composer, outputSanitizer, reportGuard, renderer,
                pseudonymizer);
    }

    public ChatResponse handle(ChatRequest request) {
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        ParsedRequest parsed = requestParser.parse(request.message(), !request.files().isEmpty());

        if (parsed.isOutOfScope()) {
            return new ChatResponse(requestId, parsed.intent(), Set.of(), null, ParsedRequest.REFUSAL_MESSAGE,
                    false, null, List.of(), "");
        }

        String requestedCallId = parsed.callIdInText() != null ? parsed.callIdInText() : request.callId();
        try (ValidatedUpload upload = uploadValidator.validate(request.files(), requestedCallId)) {
            CallAnalysisDetail detail = pipeline.analyzeDetailed(
                    new CallReference(upload.callId(), upload.directory()), SignalingSourcePreference.INDEXED);
            Report rule = withLimitations(detail.report(), upload.limitations());

            // Ghi chú xử lý của pipeline có thể nhắc tới thư mục tạm của lượt upload (có cả tên user của máy chủ).
            String tempPath = upload.directory().toString();
            List<String> warnings = new ArrayList<>(detail.processingWarnings().stream()
                    .map(w -> w.replace(tempPath, "<upload>")).toList());
            AiAttempt attempt = attemptAi(requestId, parsed, detail.timeline(), rule, warnings);
            return finish(requestId, parsed, rule, attempt, warnings);
        }
    }

    private AiAttempt attemptAi(String requestId, ParsedRequest parsed, CallTimeline timeline, Report rule,
                                List<String> warnings) {
        long started = System.nanoTime();
        String callRef = pseudonymizer.pseudonym("CALL", rule.callId());
        try {
            ObjectNode context = contextBuilder.build(parsed, timeline, rule);
            SanitizedContext safe = inputSanitizer.sanitize(context);
            if (safe.residual() > 0) {
                // Không bao giờ gửi đi một context còn giá trị nhạy cảm, dù chỉ một.
                return fallback(requestId, callRef, "none", started, "INPUT_NOT_SANITIZED", null);
            }
            AiPrompt prompt = promptFactory.create(safe.content().toString());
            AiResult result = aiCaller.call(aiProvider, prompt);

            GuardDecision decision = aiGuard.check(result.rawJson(), rule);
            if (decision.isRejected()) {
                warnings.addAll(decision.reasons());
                return fallback(requestId, callRef, result.model(), started, "GUARDRAIL_REJECTED", result);
            }
            warnings.addAll(decision.flags());
            logAiCall(requestId, callRef, result.model(), started, decision.flags().isEmpty() ? "ACCEPTED"
                    : "ACCEPTED_WITH_FLAGS", result.totalTokens(), null);
            return new AiAttempt(composer.fromAi(rule, decision), null);
        } catch (AiProviderException e) {
            warnings.add("AI: " + e.getMessage());
            return fallback(requestId, callRef, aiProvider.modelName(), started, "AI_" + e.kind().name(), null);
        } catch (RuntimeException e) {
            // Lỗi không lường trước ở tầng AI không được giết request.
            warnings.add("AI: lỗi không dự kiến (" + e.getClass().getSimpleName() + ")");
            return fallback(requestId, callRef, aiProvider.modelName(), started, "AI_ERROR", null);
        }
    }

    private AiAttempt fallback(String requestId, String callRef, String model, long started, String reason,
                               AiResult result) {
        logAiCall(requestId, callRef, model, started, "FALLBACK", result == null ? null : result.totalTokens(),
                reason);
        return new AiAttempt(null, reason);
    }

    private ChatResponse finish(String requestId, ParsedRequest parsed, Report rule, AiAttempt attempt,
                                List<String> warnings) {
        Report candidate = attempt.report() != null ? attempt.report() : composer.degraded(rule);
        String fallbackReason = attempt.fallbackReason();

        Report safe = outputSanitizer.sanitize(candidate);
        List<String> violations = reportGuard.inspect(safe);
        if (!violations.isEmpty() && !safe.degraded()) {
            warnings.addAll(violations);
            safe = outputSanitizer.sanitize(composer.degraded(rule));
            fallbackReason = "OUTPUT_SCHEMA_INVALID";
            violations = reportGuard.inspect(safe);
        }
        warnings.addAll(violations);

        List<String> cleanWarnings = warnings.stream().map(outputSanitizer::sanitize).distinct().toList();
        return new ChatResponse(requestId, parsed.intent(), parsed.focus(), safe, null, safe.degraded(),
                fallbackReason, cleanWarnings, renderer.render(safe));
    }

    private void logAiCall(String requestId, String callRef, String model, long startedNanos, String status,
                           Integer tokens, String fallbackReason) {
        log.info("ai_call request_id={} call_id={} model={} latency_ms={} result_status={} token_usage={} "
                        + "fallback_reason={}", requestId, callRef, model, (System.nanoTime() - startedNanos) / 1_000_000,
                status, tokens, fallbackReason);
    }

    private static Report withLimitations(Report report, List<String> extra) {
        List<String> merged = new ArrayList<>(report.dataLimitations());
        extra.stream().filter(item -> !merged.contains(item)).forEach(merged::add);
        return new Report(report.callId(), report.verdict(), report.qualityFlag(), report.issueCategory(),
                report.alternativeCauses(), report.confidenceLevel(), report.summary(), report.evidence(),
                report.metrics(), report.suggestions(), merged, report.analysis(), report.degraded());
    }

    private record AiAttempt(Report report, String fallbackReason) {
    }
}
