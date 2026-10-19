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
import com.tutran.callassistant.application.port.out.AnalysisResultArchive;
import com.tutran.callassistant.application.port.out.AnalysisResultRecord;
import com.tutran.callassistant.application.port.out.SignalingFetchError;
import com.tutran.callassistant.application.port.out.UploadValidator;
import com.tutran.callassistant.application.request.ParsedRequest;
import com.tutran.callassistant.application.request.RequestParser;
import com.tutran.callassistant.application.validation.ValidatedUpload;
import com.tutran.callassistant.domain.event.EventSource;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final AnalysisResultArchive resultArchive;
    private final String channel;

    @Autowired
    public ChatAnalysisService(RequestParser requestParser, UploadValidator uploadValidator,
                               CallAnalysisPipeline pipeline, AiContextBuilder contextBuilder,
                               InputSanitizer inputSanitizer, AiPromptFactory promptFactory,
                               AiProvider aiProvider, TimeLimitedAiCaller aiCaller, AiResponseGuard aiGuard,
                               AnalysisReportComposer composer, OutputSanitizer outputSanitizer,
                               ReportGuard reportGuard, ReportRenderer renderer, Pseudonymizer pseudonymizer,
                               AnalysisResultArchive resultArchive) {
        this(requestParser, uploadValidator, pipeline, contextBuilder, inputSanitizer, promptFactory, aiProvider,
                aiCaller, aiGuard, composer, outputSanitizer, reportGuard, renderer, pseudonymizer, resultArchive, "web");
    }

    /** Không lưu kết quả (test, chỗ không có Spring container). */
    public ChatAnalysisService(RequestParser requestParser, UploadValidator uploadValidator,
                               CallAnalysisPipeline pipeline, AiContextBuilder contextBuilder,
                               InputSanitizer inputSanitizer, AiPromptFactory promptFactory,
                               AiProvider aiProvider, TimeLimitedAiCaller aiCaller, AiResponseGuard aiGuard,
                               AnalysisReportComposer composer, OutputSanitizer outputSanitizer,
                               ReportGuard reportGuard, ReportRenderer renderer, Pseudonymizer pseudonymizer) {
        this(requestParser, uploadValidator, pipeline, contextBuilder, inputSanitizer, promptFactory, aiProvider,
                aiCaller, aiGuard, composer, outputSanitizer, reportGuard, renderer, pseudonymizer,
                AnalysisResultArchive.NONE, "web");
    }

    private ChatAnalysisService(RequestParser requestParser, UploadValidator uploadValidator,
                                CallAnalysisPipeline pipeline, AiContextBuilder contextBuilder,
                                InputSanitizer inputSanitizer, AiPromptFactory promptFactory,
                                AiProvider aiProvider, TimeLimitedAiCaller aiCaller, AiResponseGuard aiGuard,
                                AnalysisReportComposer composer, OutputSanitizer outputSanitizer,
                                ReportGuard reportGuard, ReportRenderer renderer, Pseudonymizer pseudonymizer,
                                AnalysisResultArchive resultArchive, String channel) {
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
        this.resultArchive = resultArchive;
        this.channel = channel;
    }

    /** Bản sao dùng pipeline khác (Evaluation Runner đổi nguồn signaling); mọi thành phần còn lại giữ nguyên. */
    public ChatAnalysisService withPipeline(CallAnalysisPipeline other) {
        return new ChatAnalysisService(requestParser, uploadValidator, other, contextBuilder, inputSanitizer,
                promptFactory, aiProvider, aiCaller, aiGuard, composer, outputSanitizer, reportGuard, renderer,
                pseudonymizer, resultArchive, channel);
    }

    /** Bản sao dùng AI provider khác (Evaluation Runner bọc provider để ghi lại request, hoặc chạy rule-only). */
    public ChatAnalysisService withAiProvider(AiProvider other) {
        return new ChatAnalysisService(requestParser.withAiProvider(other), uploadValidator, pipeline, contextBuilder,
                inputSanitizer, promptFactory, other, aiCaller, aiGuard, composer, outputSanitizer, reportGuard, renderer,
                pseudonymizer, resultArchive, channel);
    }

    /** Bản sao ghi kết quả vào kho khác, gắn nhãn kênh khác (lệnh batch dùng {@code "batch"}). */
    public ChatAnalysisService withResultArchive(AnalysisResultArchive archive, String newChannel) {
        return new ChatAnalysisService(requestParser, uploadValidator, pipeline, contextBuilder, inputSanitizer,
                promptFactory, aiProvider, aiCaller, aiGuard, composer, outputSanitizer, reportGuard, renderer,
                pseudonymizer, archive, newChannel);
    }

    /**
     * Xử lý một lượt hỏi và ghi đúng một dòng {@code chat_request} tóm tắt kết quả (Sprint 3 T5) - kể cả khi
     * request bị lỗi, để mọi request đều có dấu vết đo latency / tỉ lệ fallback / lỗi.
     */
    public ChatResponse handle(ChatRequest request) {
        String requestId = RequestIds.current();
        long started = System.nanoTime();
        try {
            ChatResponse response = handle(requestId, request);
            logRequest(response, started);
            archive(response, elapsedMillis(started));
            return response;
        } catch (RuntimeException e) {
            long latency = elapsedMillis(started);
            log.warn("chat_request request_id={} result=ERROR error_kind={} latency_ms={}", requestId,
                    e.getClass().getSimpleName(), latency);
            resultArchive.record(new AnalysisResultRecord(Instant.now(), requestId, channel, "ERROR", null, null,
                    null, null, null, null, false, null, aiProvider.modelName(), null, latency, 0,
                    e.getClass().getSimpleName()));
            throw e;
        }
    }

    private ChatResponse handle(String requestId, ChatRequest request) {
        ParsedRequest parsed = requestParser.parse(request.message(), !request.files().isEmpty());

        if (parsed.isOutOfScope()) {
            return new ChatResponse(requestId, parsed.intent(), Set.of(), null, ParsedRequest.REFUSAL_MESSAGE,
                    false, null, List.of(), "", null, List.of());
        }

        String requestedCallId = parsed.callIdInText() != null ? parsed.callIdInText() : request.callId();
        try (ValidatedUpload upload = uploadValidator.validate(request.files(), requestedCallId)) {
            CallAnalysisDetail detail = pipeline.analyzeDetailed(
                    new CallReference(upload.callId(), upload.directory()), SignalingSourcePreference.INDEXED);
            Report rule = composer.withUploadLimitations(detail.report(),
                    withSignalingFetchError(upload.limitations(), detail));

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
            return new AiAttempt(composer.fromAi(rule, decision), null, result.totalTokens(), decision.flags());
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
        return new AiAttempt(null, reason, result == null ? null : result.totalTokens(), List.of());
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
        // Report rơi về rule-based (kể cả do schema hỏng) thì không còn gì của AI để kiểm tra.
        List<String> reviewFlags = safe.degraded() ? List.of()
                : attempt.reviewFlags().stream().map(outputSanitizer::sanitize).distinct().toList();
        return new ChatResponse(requestId, parsed.intent(), parsed.focus(), safe, null, safe.degraded(),
                fallbackReason, cleanWarnings, renderer.render(safe), attempt.tokens(), reviewFlags);
    }

    private void logRequest(ChatResponse response, long startedNanos) {
        Report report = response.report();
        log.info("chat_request request_id={} result={} intent={} call_id={} verdict={} issue_category={} "
                        + "confidence={} degraded={} fallback_reason={} warnings={} latency_ms={}",
                response.requestId(), report == null ? "REFUSED" : "OK", response.intent(),
                report == null ? null : pseudonymizer.pseudonym("CALL", report.callId()),
                report == null ? null : report.verdict(), report == null ? null : report.issueCategory(),
                report == null ? null : report.confidenceLevel(), response.degraded(), response.fallbackReason(),
                response.warnings().size(), elapsedMillis(startedNanos));
    }

    /** Chỉ field tóm tắt đã sanitize (Call-ID dạng pseudonym) - xem {@link AnalysisResultRecord}. */
    private void archive(ChatResponse response, long latencyMs) {
        Report report = response.report();
        resultArchive.record(new AnalysisResultRecord(Instant.now(), response.requestId(), channel,
                report == null ? "REFUSED" : "OK", response.intent().name(),
                report == null ? null : pseudonymizer.pseudonym("CALL", report.callId()),
                report == null ? null : report.verdict(), report == null ? null : report.issueCategory(),
                report == null ? null : report.qualityFlag(), report == null ? null : report.confidenceLevel(),
                response.degraded(), response.fallbackReason(), aiProvider.modelName(), response.aiTokens(),
                latencyMs, response.warnings().size(), null));
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private void logAiCall(String requestId, String callRef, String model, long startedNanos, String status,
                           Integer tokens, String fallbackReason) {
        log.info("ai_call request_id={} call_id={} model={} latency_ms={} result_status={} token_usage={} "
                        + "fallback_reason={}", requestId, callRef, model, elapsedMillis(startedNanos),
                status, tokens, fallbackReason);
    }

    /**
     * Không lấy được signaling vì Elasticsearch lỗi (và nguồn dự phòng cũng không có): nêu rõ lý do trong
     * "Giới hạn dữ liệu", thay vì chỉ một câu "không có signaling" khiến người dùng tưởng Call-ID sai.
     */
    private static List<String> withSignalingFetchError(List<String> limitations, CallAnalysisDetail detail) {
        // Lỗi "không lấy được" chỉ đáng nêu khi signaling rỗng (nguồn dự phòng có thể đã bù); bị cắt thì luôn nêu.
        boolean signalingEmpty = detail.timeline().forSource(EventSource.SIGNALING).isEmpty();
        List<String> merged = new ArrayList<>(limitations);
        detail.processingWarnings().stream()
                .map(SignalingFetchError::fromWarning)
                .flatMap(Optional::stream)
                .filter(error -> error.partial() || signalingEmpty)
                .map(SignalingFetchError::limitation)
                .distinct()
                .forEach(merged::add);
        return merged;
    }

    private record AiAttempt(Report report, String fallbackReason, Integer tokens, List<String> reviewFlags) {
    }
}
