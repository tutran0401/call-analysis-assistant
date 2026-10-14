package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.ai.FakeAiProvider;
import com.tutran.callassistant.ai.NoAiProvider;
import com.tutran.callassistant.application.ChatAnalysisService;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.ingest.file.CallDirectoryIndex;
import com.tutran.callassistant.ingest.file.LocalFileSignalingSource;
import com.tutran.callassistant.ingest.file.TreeSignalingSource;
import com.tutran.callassistant.testsupport.ChatServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 3 T6: chạy lại toàn bộ bộ case S01-S08 ({@code benchmark/security/cases.yaml}) qua pipeline thật - đây là
 * cổng hồi quy bảo mật, mọi thay đổi làm lọt một giá trị đều làm test này đỏ.
 */
class SecurityEvaluationTest {

    private static final Path DATA = Path.of("sample-data");
    private static final Path CASES = Path.of("benchmark/security/cases.yaml");

    private final List<EvaluationCase> cases = new CaseLoader().load(CASES);

    private EvaluationResult run(AiProvider provider) {
        CallDirectoryIndex index = new CallDirectoryIndex(DATA);
        ChatAnalysisService base = ChatServices.create(new NoAiProvider("unused"), 5000,
                new TreeSignalingSource(index, LocalFileSignalingSource.withDefaults()));
        RecordingAiProvider recorder = new RecordingAiProvider(provider);
        return new EvaluationRunner(base.withAiProvider(recorder), base.withAiProvider(new NoAiProvider("rule")),
                recorder, new CaseMaterializer(index), ChatServices.ENGINE, 1, 5, "structured", s -> { }).run(cases);
    }

    @Test
    void caseFileCoversEveryRequiredSecurityScenario() {
        assertThat(cases).extracting(EvaluationCase::notes)
                .anyMatch(n -> n.startsWith("S01")).anyMatch(n -> n.startsWith("S02"))
                .anyMatch(n -> n.startsWith("S03")).anyMatch(n -> n.startsWith("S04"))
                .anyMatch(n -> n.startsWith("S05")).anyMatch(n -> n.startsWith("S06"))
                .anyMatch(n -> n.startsWith("S08"));
        assertThat(cases).allMatch(c -> !c.sensitiveValues().isEmpty());
    }

    @Test
    void nothingLeaksToTheAiTheReportOrTheLog() {
        EvaluationResult result = run(new FakeAiProvider());

        assertThat(result.records()).allMatch(RunRecord::pipelineOk).allMatch(RunRecord::aiCalled);
        assertThat(result.metrics().inputLeakage()).isZero();
        assertThat(result.metrics().outputLeakage()).isZero();
        assertThat(result.metrics().logLeakage()).isZero();
        assertThat(result.records()).allMatch(r -> r.inputResidual() == 0 && r.outputResidual() == 0);
    }

    @Test
    void s07_aiThatEchoesSensitiveValuesNeverReachesTheUser() {
        List<String> values = cases.stream().flatMap(c -> c.sensitiveValues().stream()).distinct().toList();

        EvaluationResult result = run(new LeakyAiProvider(new FakeAiProvider(), values));

        assertThat(result.records()).allMatch(RunRecord::pipelineOk);
        assertThat(result.records()).allMatch(r -> r.outputProbeHits() == 0 && r.outputResidual() == 0);
        assertThat(result.metrics().logLeakage()).isZero();
    }

    @Test
    void s07_leakThatPassesGuardrailsIsStillMaskedByTheOutputSanitizer() {
        List<String> digitFree = cases.stream().flatMap(c -> c.sensitiveValues().stream())
                .filter(v -> v.chars().noneMatch(Character::isDigit)).distinct().toList();
        assertThat(digitFree).isNotEmpty();

        EvaluationResult result = run(new LeakyAiProvider(new FakeAiProvider(), digitFree));

        // Guardrails không chặn (không có số lạ) -> report AI được giao, và Output Sanitizer là lớp duy nhất còn lại.
        assertThat(result.records()).anyMatch(r -> !r.degraded());
        assertThat(result.records()).allMatch(r -> r.outputProbeHits() == 0 && r.outputResidual() == 0);
    }

    @Test
    void reportSaysPassOnlyWhenEveryColumnIsZero(@TempDir Path dir) throws Exception {
        EvaluationResult clean = run(new FakeAiProvider());

        Path summary = new SecurityReportWriter().write(dir, CASES.toString(),
                List.of(new SecurityReportWriter.Pass("Lượt 1", clean)));

        String text = Files.readString(summary);
        assertThat(text).contains("| S01 | sec-s01-jwt |").contains("| S08 | sec-s08-error-message |")
                .contains("**PASS**").doesNotContain("KHÔNG ĐẠT");
    }
}
