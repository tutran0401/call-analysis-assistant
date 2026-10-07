package com.tutran.callassistant.application;

import com.tutran.callassistant.ai.FakeAiProvider;
import com.tutran.callassistant.ai.NoAiProvider;
import com.tutran.callassistant.analysis.metrics.CallMetricsCalculator;
import com.tutran.callassistant.analysis.verdict.IssueCategoryRegistry;
import com.tutran.callassistant.analysis.verdict.RuleVerdictEngine;
import com.tutran.callassistant.application.ai.AiContextBuilder;
import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiPromptFactory;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.ai.TimeLimitedAiCaller;
import com.tutran.callassistant.application.guardrail.AiResponseGuard;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.application.request.Intent;
import com.tutran.callassistant.application.request.RuleRequestParser;
import com.tutran.callassistant.application.validation.UploadedFile;
import com.tutran.callassistant.ingest.file.ContentBasedLogFileTypeDetector;
import com.tutran.callassistant.ingest.file.ContentUploadValidator;
import com.tutran.callassistant.ingest.file.DirectoryClientLogSource;
import com.tutran.callassistant.ingest.file.LocalFileSignalingSource;
import com.tutran.callassistant.report.ReportSchemaGuard;
import com.tutran.callassistant.report.RuleBasedReportAssembler;
import com.tutran.callassistant.report.TextReportRenderer;
import com.tutran.callassistant.sanitize.InputSanitizer;
import com.tutran.callassistant.sanitize.OutputSanitizer;
import com.tutran.callassistant.sanitize.Pseudonymizer;
import com.tutran.callassistant.sanitize.SensitiveDataSanitizer;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Orchestration end-to-end ở tầng service: parser thật, validator thật, pipeline thật trên cuộc gọi thật
 * (signaling lấy từ file vì không có Elasticsearch), sanitizer/guardrail/composer thật; chỉ AI là giả.
 */
class ChatAnalysisServiceTest {

    private static final String CALL = SampleCalls.SUCCESS_FULL_LOGS;
    private static final Path DIR = SampleCalls.success(CALL);

    private final Pseudonymizer pseudonymizer = new Pseudonymizer("test-salt");
    private final SensitiveDataSanitizer engine = new SensitiveDataSanitizer(pseudonymizer);

    private ChatAnalysisService service(AiProvider provider, long timeoutMillis) {
        // Chỗ của Elasticsearch: đọc signaling.json của cuộc gọi mẫu theo Call-ID, như ES sẽ trả về.
        return service(provider, timeoutMillis, call ->
                LocalFileSignalingSource.withDefaults().fetch(new CallReference(call.callId(), DIR)));
    }

    private ChatAnalysisService service(AiProvider provider, long timeoutMillis, SignalingEventSource indexedStandIn) {
        return com.tutran.callassistant.testsupport.ChatServices.create(provider, timeoutMillis, indexedStandIn);
    }

    private ChatAnalysisService service(AiProvider provider) {
        return service(provider, 5000);
    }

    private static List<UploadedFile> logsOf(Path dir) throws IOException {
        List<UploadedFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().matches(".*_(endcall|webrtc)\\.log")).toList()) {
                files.add(new UploadedFile(path.getFileName().toString(), Files.readAllBytes(path)));
            }
        }
        return files;
    }

    @Test
    void agreeingAiProducesAnAiReportWithSameVerdictAsRules() throws IOException {
        ChatResponse response = service(new FakeAiProvider()).handle(
                new ChatRequest("Phân tích cuộc gọi này giúp mình", logsOf(DIR)));

        assertThat(response.degraded()).isFalse();
        assertThat(response.fallbackReason()).isNull();
        assertThat(response.report().callId()).isEqualTo(CALL);
        assertThat(response.report().verdict()).isEqualTo("SUCCESS");
        assertThat(response.report().analysis()).isNotBlank();
        assertThat(response.renderedText()).contains("# Báo cáo phân tích cuộc gọi").contains("Phân tích:");
        assertThat(response.requestId()).startsWith("req-");
    }

    @Test
    void tokenUsageOfTheAnalysisCallIsReportedAndAbsentWithoutAi() throws IOException {
        AiProvider counting = new AiProvider() {
            @Override
            public String modelName() {
                return "counting";
            }

            @Override
            public AiResult analyze(AiPrompt prompt) {
                AiResult plain = new FakeAiProvider().analyze(prompt);
                return new AiResult(plain.rawJson(), "counting", 5, 700, 120);
            }
        };

        assertThat(service(counting).handle(new ChatRequest("Phân tích giúp mình", logsOf(DIR))).aiTokens())
                .isEqualTo(820);
        assertThat(service(new NoAiProvider("x")).handle(new ChatRequest("Phân tích giúp mình", logsOf(DIR)))
                .aiTokens()).isNull();
    }

    @Test
    void questionIsUnderstoodAndFocusIsReturned() throws IOException {
        ChatResponse response = service(new FakeAiProvider()).handle(
                new ChatRequest("Vì sao bên nhận không nghe được?", logsOf(DIR)));

        assertThat(response.intent()).isEqualTo(Intent.ANALYZE_WITH_FOCUS);
        assertThat(response.focus()).isNotEmpty();
    }

    @Test
    void noAiProviderFallsBackToRulesAndMarksDegraded() throws IOException {
        ChatResponse response = service(new NoAiProvider("chưa có key")).handle(
                new ChatRequest("Phân tích giúp mình", logsOf(DIR)));

        assertThat(response.degraded()).isTrue();
        assertThat(response.fallbackReason()).isEqualTo("AI_UNAVAILABLE");
        assertThat(response.report().verdict()).isEqualTo("SUCCESS");
        assertThat(response.report().degraded()).isTrue();
        assertThat(response.report().dataLimitations()).contains(AnalysisReportComposer.DEGRADED_NOTE);
    }

    @Test
    void aiTimeoutFallsBackWithinTheDeadline() throws IOException {
        AiProvider hanging = new AiProvider() {
            @Override
            public String modelName() {
                return "hanging";
            }

            @Override
            public AiResult analyze(AiPrompt prompt) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }
        };
        long started = System.nanoTime();

        ChatResponse response = service(hanging, 300).handle(new ChatRequest("Phân tích giúp mình", logsOf(DIR)));

        assertThat(response.degraded()).isTrue();
        assertThat(response.fallbackReason()).isEqualTo("AI_TIMEOUT");
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(5000);
    }

    @Test
    void invalidAiResponseIsRejectedByGuardrailsAndFallsBack() throws IOException {
        ChatResponse response = service(new FakeAiProvider(FakeAiProvider.Mode.BAD_FORMAT)).handle(
                new ChatRequest("Phân tích giúp mình", logsOf(DIR)));

        assertThat(response.degraded()).isTrue();
        assertThat(response.fallbackReason()).isEqualTo("GUARDRAIL_REJECTED");
        assertThat(response.warnings()).anyMatch(w -> w.startsWith("G02"));
        assertThat(response.report().verdict()).isEqualTo("SUCCESS");
    }

    @Test
    void hallucinatedEvidenceAndNumbersAreRejected() throws IOException {
        for (FakeAiProvider.Mode mode : List.of(FakeAiProvider.Mode.UNKNOWN_EVIDENCE,
                FakeAiProvider.Mode.INVENTED_NUMBER, FakeAiProvider.Mode.BAD_ENUM, FakeAiProvider.Mode.MISSING_FIELD)) {
            ChatResponse response = service(new FakeAiProvider(mode)).handle(
                    new ChatRequest("Phân tích giúp mình", logsOf(DIR)));

            assertThat(response.degraded()).as(mode.name()).isTrue();
            assertThat(response.fallbackReason()).as(mode.name()).isEqualTo("GUARDRAIL_REJECTED");
        }
    }

    @Test
    void aiContradictingRulesYieldsUnknownWithLowConfidenceAndFlag() throws IOException {
        ChatResponse response = service(new FakeAiProvider(FakeAiProvider.Mode.CONFLICT)).handle(
                new ChatRequest("Phân tích giúp mình", logsOf(DIR)));

        assertThat(response.degraded()).isFalse();
        assertThat(response.report().verdict()).isEqualTo("UNKNOWN");
        assertThat(response.report().confidenceLevel()).isEqualTo("LOW");
        assertThat(response.report().dataLimitations()).anyMatch(l -> l.startsWith("G04"));
        assertThat(response.warnings()).anyMatch(w -> w.startsWith("G04"));
    }

    @Test
    void leakyAiOutputNeverReachesTheResponse() throws IOException {
        ChatResponse response = service(new FakeAiProvider(FakeAiProvider.Mode.LEAKY)).handle(
                new ChatRequest("Phân tích giúp mình", logsOf(DIR)));

        String everything = response.report() + response.renderedText() + response.warnings();
        assertThat(everything).doesNotContain("0987654321").doesNotContain("113.161.45.67");
    }

    @Test
    void outOfScopeQuestionIsRefusedWithoutTouchingFilesOrAi() throws IOException {
        AtomicInteger aiCalls = new AtomicInteger();
        AiProvider counting = new AiProvider() {
            @Override
            public String modelName() {
                return "counting";
            }

            @Override
            public AiResult analyze(AiPrompt prompt) {
                aiCalls.incrementAndGet();
                return null;
            }
        };

        ChatResponse response = service(counting).handle(new ChatRequest("Viết giúp mình một email", logsOf(DIR)));

        assertThat(response.intent()).isEqualTo(Intent.OUT_OF_SCOPE);
        assertThat(response.report()).isNull();
        assertThat(response.message()).startsWith("Mình chỉ hỗ trợ phân tích cuộc gọi");
        assertThat(aiCalls).hasValue(0);
    }

    @Test
    void onlySanitizedMinimalContextIsSentToTheAi() throws IOException {
        List<String> seen = new ArrayList<>();
        AiProvider spy = new AiProvider() {
            @Override
            public String modelName() {
                return "spy";
            }

            @Override
            public AiResult analyze(AiPrompt prompt) {
                seen.add(prompt.userContent());
                return new FakeAiProvider().analyze(prompt);
            }
        };

        service(spy).handle(new ChatRequest("Cuộc gọi này lỗi gì, liên hệ 0987654321 nhé", logsOf(DIR)));

        assertThat(seen).hasSize(1);
        assertThat(engine.countResidual(seen.get(0))).isZero();
        assertThat(seen.get(0)).doesNotContain("0987654321").doesNotContain("rawLine").doesNotContain("sdp");
    }

    @Test
    void missingAndForeignFilesAreReportedAsDataLimitationsNotCrashes() throws IOException {
        List<UploadedFile> files = new ArrayList<>(logsOf(DIR).stream()
                .filter(f -> !f.name().startsWith("callee_")).toList());
        files.add(new UploadedFile("broken_endcall.log", new byte[]{0, 1, 2, 3}));
        files.add(new UploadedFile("other_endcall.log",
                ("#H1\t#ts\t#tag\tcallId\trole\n1\t1000\tinfo\t" + "AAAAAAAA-1111-2222-3333-444444444444\tcallee\n")
                        .getBytes(StandardCharsets.UTF_8)));

        ChatResponse response = service(new FakeAiProvider()).handle(new ChatRequest(
                "Phân tích cuộc gọi này", files));

        assertThat(response.report().callId()).isEqualTo(CALL);
        assertThat(response.report().dataLimitations())
                .anyMatch(l -> l.contains("broken_endcall.log"))
                .anyMatch(l -> l.contains("other_endcall.log") && l.contains("AAAAAAAA"))
                .anyMatch(l -> l.contains("callee_webrtc.log"));
    }

    @Test
    void processingWarningsDoNotRevealTheServersTempDirectory() throws IOException {
        SignalingEventSource noisy = call -> com.tutran.callassistant.domain.event.NormalizedEvents.unreadable(
                "No signaling.json found in " + call.directory());

        ChatResponse response = service(new FakeAiProvider(), 5000, noisy).handle(
                new ChatRequest("Phân tích giúp mình", logsOf(DIR)));

        assertThat(response.warnings()).anyMatch(w -> w.contains("No signaling.json found in <upload>"));
        assertThat(response.warnings()).noneMatch(w -> w.contains(System.getProperty("java.io.tmpdir"))
                || w.contains("call-upload-"));
    }

    @Test
    void webRtcOnlyUploadNeedsAnExplicitCallIdToReachSignaling() throws IOException {
        // WebRTC log không chứa Call-ID: không khai báo thì không có cách tra signaling -> UNKNOWN;
        // khai báo (ô Call-ID / call_id của case) thì phân tích được như bình thường.
        Path failDir = SampleCalls.fail(SampleCalls.FAIL_CANCELLED_NO_CLIENT_LOGS);
        List<UploadedFile> webRtcOnly = logsOf(failDir);
        SignalingEventSource stand = call -> call.callId().equals(SampleCalls.FAIL_CANCELLED_NO_CLIENT_LOGS)
                ? LocalFileSignalingSource.withDefaults().fetch(new CallReference(call.callId(), failDir))
                : com.tutran.callassistant.domain.event.NormalizedEvents.empty(); // như ES: không có Call-ID thì không có dữ liệu
        ChatAnalysisService svc = service(new FakeAiProvider(), 5000, stand);

        ChatResponse withoutId = svc.handle(new ChatRequest("Phân tích giúp mình", webRtcOnly));
        ChatResponse withId = svc.handle(new ChatRequest("Phân tích giúp mình", webRtcOnly,
                SampleCalls.FAIL_CANCELLED_NO_CLIENT_LOGS));

        assertThat(withoutId.report().callId()).isEqualTo("UNKNOWN");
        assertThat(withoutId.report().verdict()).isEqualTo("UNKNOWN");
        assertThat(withoutId.report().dataLimitations()).anyMatch(l -> l.contains("Call-ID"));
        assertThat(withId.report().callId()).isEqualTo(SampleCalls.FAIL_CANCELLED_NO_CLIENT_LOGS);
        assertThat(withId.report().verdict()).isEqualTo("FAIL");
    }

    @Test
    void callIdWrittenInTheQuestionBeatsTheDeclaredOne() throws IOException {
        ChatResponse response = service(new FakeAiProvider()).handle(new ChatRequest(
                "Phân tích cuộc gọi " + CALL, logsOf(DIR), "SOME-OTHER-ID"));

        assertThat(response.report().callId()).isEqualTo(CALL);
    }

    @Test
    void noFilesAtAllStillReturnsAReportInsteadOfFailing() {
        ChatResponse response = service(new FakeAiProvider()).handle(new ChatRequest(
                "Phân tích cuộc gọi " + CALL, List.of()));

        assertThat(response.report()).isNotNull();
        assertThat(response.report().callId()).isEqualTo(CALL);
        assertThat(response.report().dataLimitations()).isNotEmpty();
    }

    @Test
    void responseNeverContainsRawAttachmentContent() throws IOException {
        String secret = "Authorization: Bearer abcdefghijklmnop123456";
        List<UploadedFile> files = new ArrayList<>(logsOf(DIR));
        files.add(new UploadedFile("note_webrtc.log",
                ("[000:000][1] (RTCLogging.mm:34): " + secret + "\n").getBytes(StandardCharsets.UTF_8)));

        ChatResponse response = service(new FakeAiProvider()).handle(new ChatRequest("Phân tích giúp mình", files));

        assertThat(response.report().toString() + response.renderedText() + response.warnings())
                .doesNotContain("abcdefghijklmnop123456");
    }
}
