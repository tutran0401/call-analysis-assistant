package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.testsupport.SampleCalls;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutran.callassistant.ai.FakeAiProvider;
import com.tutran.callassistant.ai.NoAiProvider;
import com.tutran.callassistant.application.ChatAnalysisService;
import com.tutran.callassistant.ingest.file.CallDirectoryIndex;
import com.tutran.callassistant.ingest.file.LocalFileSignalingSource;
import com.tutran.callassistant.ingest.file.TreeSignalingSource;
import com.tutran.callassistant.testsupport.ChatServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Chạy runner thật trên vài cuộc gọi thật: harness phải đo đúng và không để lọt dữ liệu nhạy cảm vào kết quả. */
class EvaluationRunnerTest {

    private static final Path DATA = Path.of("sample-data");
    private static final String SECRET_PHONE = "0987654321";

    private static final String CASES = """
            cases:
              - case_id: ok
                call_id: DE7DD314-F432-45CB-BCB4-AE9103CC0919
                files: [caller_endcall.log, callee_endcall.log, caller_webrtc.log, callee_webrtc.log]
                questions: ["Phân tích cuộc gọi này giúp mình", "Cuộc gọi này có lỗi gì không?", "Chất lượng có ổn không?"]
                expected_verdict: SUCCESS
                expected_quality_flag: false
                expected_issue_category: NONE
                split: dev
                label_source: test
              - case_id: failed-webrtc-only
                call_id: 7B56D7AD-1FF1-4EDB-B212-06EC12CA73FB
                files: [caller_webrtc.log]
                questions: ["Vì sao cuộc gọi không kết nối được?", "Phân tích giúp mình", "Có lỗi gì không?"]
                expected_verdict: FAIL
                expected_issue_category: TURN_FAILURE
                split: dev
              - case_id: secrets
                call_id: DE7DD314-F432-45CB-BCB4-AE9103CC0919
                files:
                  - from: caller_webrtc.log
                    append: ["[999:001][1] (RTCLogging.mm:34): phone=0987654321 Authorization: Bearer abcdefghijklmnop123456"]
                  - caller_endcall.log
                  - callee_endcall.log
                  - callee_webrtc.log
                questions: ["Phân tích giúp mình", "Cuộc gọi này có lỗi gì không?", "Đánh giá giúp mình"]
                expected_verdict: SUCCESS
                sensitive_values: ["0987654321", "abcdefghijklmnop123456"]
                split: dev
              - case_id: oos
                questions: ["Viết giúp mình một email", "Hôm nay thời tiết thế nào?", "Dịch sang tiếng Anh giúp mình"]
                expected_intent: OUT_OF_SCOPE
                split: dev
            """;

    private ChatAnalysisService baseService() {
        CallDirectoryIndex index = new CallDirectoryIndex(SampleCalls.requireSampleData());
        return ChatServices.create(new NoAiProvider("unused"), 5000,
                new TreeSignalingSource(index, LocalFileSignalingSource.withDefaults()));
    }

    private EvaluationResult run(Path dir, com.tutran.callassistant.application.port.out.AiProvider ai, int repeat)
            throws Exception {
        Path file = dir.resolve("cases.yaml");
        Files.writeString(file, CASES);
        List<EvaluationCase> cases = new CaseLoader().load(file);
        ChatAnalysisService base = baseService();
        RecordingAiProvider recorder = new RecordingAiProvider(ai);
        List<String> progress = new ArrayList<>();
        return new EvaluationRunner(base.withAiProvider(recorder), base.withAiProvider(new NoAiProvider("rule")),
                recorder, new CaseMaterializer(new CallDirectoryIndex(DATA)), ChatServices.ENGINE, repeat, 5,
                "structured", progress::add).run(cases);
    }

    @Test
    void measuresRealCallsAndRepeatsOnlyTheConsistencyQuestions(@TempDir Path dir) throws Exception {
        EvaluationResult result = run(dir, new FakeAiProvider(), 2);

        // 3 case phân tích x 3 câu x 2 lần + 3 câu OOS x 2 lần
        assertThat(result.records()).hasSize(3 * 3 * 2 + 3 * 2);
        assertThat(result.rules()).containsOnlyKeys("ok", "failed-webrtc-only", "secrets");
        EvaluationMetrics m = result.metrics();
        assertThat(m.verdictAccuracy()).isEqualTo(new Ratio(18, 18));
        assertThat(m.ruleVerdictAccuracy()).isEqualTo(new Ratio(3, 3));
        assertThat(m.issueCategoryAccuracy()).isEqualTo(new Ratio(6, 6));
        assertThat(m.intentAccuracy()).isEqualTo(new Ratio(24, 24));
        assertThat(m.consistencyCases()).isEqualTo(new Ratio(3, 3));
        assertThat(m.metricCorrectness()).isEqualTo(new Ratio(18, 18));
        assertThat(m.templateCompliance()).isEqualTo(new Ratio(18, 18));
        assertThat(m.pipelineSuccess()).isEqualTo(new Ratio(24, 24));
        assertThat(m.unsupportedClaims()).isEqualTo(new Ratio(0, 18));
        assertThat(m.degradedRate()).isEqualTo(new Ratio(0, 18));
        assertThat(m.latencyP95Millis()).isNotNull();
        assertThat(result.samples()).containsKeys("ok", "failed-webrtc-only", "secrets").doesNotContainKey("oos");
        assertThat(result.contextMode()).isEqualTo("structured");
    }

    @Test
    void injectedSecretsNeverReachTheAiOrTheOutputOrTheLogs(@TempDir Path dir) throws Exception {
        EvaluationResult result = run(dir, new FakeAiProvider(), 1);

        assertThat(result.metrics().sensitiveValuesInjected()).isEqualTo(2);
        assertThat(result.metrics().inputLeakage()).isZero();
        assertThat(result.metrics().outputLeakage()).isZero();
        assertThat(result.metrics().logLeakage()).isZero();
        assertThat(result.records()).filteredOn(r -> r.caseId().equals("secrets")).allMatch(RunRecord::aiCalled);
    }

    @Test
    void withoutAnAiProviderEverythingIsDegradedButStillMeasured(@TempDir Path dir) throws Exception {
        EvaluationResult result = run(dir, new NoAiProvider("no key"), 1);

        assertThat(result.metrics().degradedRate()).isEqualTo(new Ratio(9, 9));
        assertThat(result.metrics().verdictAccuracy()).isEqualTo(new Ratio(9, 9));
        assertThat(result.metrics().pipelineSuccess().percent()).isEqualTo(100.0);
        assertThat(result.metrics().unsupportedClaims().measured()).isFalse();
    }

    @Test
    void aGuardrailRejectingTheAiIsCountedAsUnsupportedClaimAndFallsBack(@TempDir Path dir) throws Exception {
        EvaluationResult result = run(dir, new FakeAiProvider(FakeAiProvider.Mode.INVENTED_NUMBER), 1);

        assertThat(result.metrics().unsupportedClaims()).isEqualTo(new Ratio(9, 9));
        assertThat(result.metrics().degradedRate()).isEqualTo(new Ratio(9, 9));
        assertThat(result.metrics().verdictAccuracy()).isEqualTo(new Ratio(9, 9));
    }

    @Test
    void writesSummaryResultsAndSamplesWithoutAnySensitiveValue(@TempDir Path dir) throws Exception {
        EvaluationResult result = run(dir, new FakeAiProvider(), 1);
        Path out = dir.resolve("out");

        new EvaluationReportWriter().write(out, result, "cases.yaml", "sample-data");

        String summary = Files.readString(out.resolve("summary.md"));
        assertThat(summary).contains("Verdict Accuracy").contains("So sánh AI vs Rule vs Ground truth")
                .contains("Nguồn nhãn").contains("| ok |");
        JsonNode json = new ObjectMapper().readTree(out.resolve("results.json").toFile());
        assertThat(json.at("/metrics/verdictAccuracy/percent").asDouble()).isEqualTo(100.0);
        assertThat(json.get("records")).hasSize(result.records().size());
        assertThat(json.get("records").get(0).has("report")).isFalse();
        assertThat(Files.exists(out.resolve("samples").resolve("ok.md"))).isTrue();

        for (String file : List.of("summary.md", "results.json", "samples/ok.md", "samples/secrets.md")) {
            assertThat(Files.readString(out.resolve(file))).as(file)
                    .doesNotContain(SECRET_PHONE).doesNotContain("abcdefghijklmnop123456");
        }
    }

    @Test
    void aMissingDataFileFailsThatCaseOnlyNotTheWholeRun(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("cases.yaml");
        Files.writeString(file, """
                - case_id: broken
                  call_id: DE7DD314-F432-45CB-BCB4-AE9103CC0919
                  files: [no_such_file.log]
                  questions: ["Phân tích giúp mình", "q2", "q3"]
                - case_id: oos
                  questions: ["Viết email giúp mình", "Thời tiết hôm nay", "Dịch sang tiếng Anh"]
                  expected_intent: OUT_OF_SCOPE
                """);
        ChatAnalysisService base = baseService();
        RecordingAiProvider recorder = new RecordingAiProvider(new FakeAiProvider());

        EvaluationResult result = new EvaluationRunner(base.withAiProvider(recorder),
                base.withAiProvider(new NoAiProvider("rule")), recorder,
                new CaseMaterializer(new CallDirectoryIndex(DATA)), ChatServices.ENGINE, 1, 5, "structured", s -> { })
                .run(new CaseLoader().load(file));

        assertThat(result.records()).filteredOn(r -> r.caseId().equals("broken")).singleElement()
                .satisfies(r -> assertThat(r.pipelineOk()).isFalse());
        assertThat(result.records()).filteredOn(r -> r.caseId().equals("oos")).hasSize(3).allMatch(RunRecord::pipelineOk);
        assertThat(result.metrics().pipelineSuccess()).isEqualTo(new Ratio(3, 4));
    }
}
