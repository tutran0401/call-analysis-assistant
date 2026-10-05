package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.application.validation.UploadedFile;
import com.tutran.callassistant.ingest.file.CallDirectoryIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseLoaderTest {

    private static final Path DEV_CASES = Path.of("benchmark", "dev", "cases.yaml");

    private final CaseLoader loader = new CaseLoader();

    private Path write(Path dir, String yaml) throws Exception {
        Path file = dir.resolve("cases.yaml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void parsesTheSpecFormatIncludingVietnameseQuestions(@TempDir Path dir) throws Exception {
        List<EvaluationCase> cases = loader.load(write(dir, """
                - case_id: case-001
                  call_id: CALL-EXAMPLE-001
                  files: [caller_endcall.log, callee_endcall.log]
                  questions:
                    - "Phân tích cuộc gọi này giúp mình"
                    - "Vì sao bên nhận nghe bị rè?"
                  expected_verdict: SUCCESS
                  expected_quality_flag: true
                  expected_issue_category: NETWORK_PACKET_LOSS
                  expected_evidence: [EV002, EV005]
                  split: dev
                """));

        EvaluationCase c = cases.get(0);
        assertThat(c.caseId()).isEqualTo("case-001");
        assertThat(c.callId()).isEqualTo("CALL-EXAMPLE-001");
        assertThat(c.files()).extracting(FileRef::attachedName).containsExactly("caller_endcall.log", "callee_endcall.log");
        assertThat(c.questions()).containsExactly("Phân tích cuộc gọi này giúp mình", "Vì sao bên nhận nghe bị rè?");
        assertThat(c.expectedVerdict()).isEqualTo("SUCCESS");
        assertThat(c.expectedQualityFlag()).isTrue();
        assertThat(c.expectedIssueCategory()).isEqualTo("NETWORK_PACKET_LOSS");
        assertThat(c.expectedEvidence()).containsExactly("EV002", "EV005");
        assertThat(c.split()).isEqualTo("dev");
    }

    @Test
    void acceptsCasesUnderACasesKeyAndObjectFileRefs(@TempDir Path dir) throws Exception {
        List<EvaluationCase> cases = loader.load(write(dir, """
                cases:
                  - case_id: v1
                    call_id: C1
                    files:
                      - from: OTHER/callee_endcall.log
                        as: callee_endcall.log
                      - from: caller_webrtc.log
                        append: ["line one", "line two"]
                    questions: ["Q"]
                    sensitive_values: ["0987654321"]
                """));

        FileRef foreign = cases.get(0).files().get(0);
        assertThat(foreign.from()).isEqualTo("OTHER/callee_endcall.log");
        assertThat(foreign.attachedName()).isEqualTo("callee_endcall.log");
        assertThat(cases.get(0).files().get(1).append()).containsExactly("line one", "line two");
        assertThat(cases.get(0).sensitiveValues()).containsExactly("0987654321");
    }

    @Test
    void rejectsMalformedCasesWithAMessageNamingTheCase(@TempDir Path dir) throws Exception {
        assertThatThrownBy(() -> loader.load(write(dir, "- case_id: a\n  call_id: X\n  questions: []\n")))
                .hasMessageContaining("a").hasMessageContaining("no questions");
        assertThatThrownBy(() -> loader.load(write(dir, "- case_id: b\n  questions: [Q]\n")))
                .hasMessageContaining("call_id");
        assertThatThrownBy(() -> loader.load(write(dir, "- case_id: c\n  call_id: X\n  questions: [Q]\n  expected_verdict: MAYBE\n")))
                .hasMessageContaining("expected_verdict");
        assertThatThrownBy(() -> loader.load(write(dir,
                "- {case_id: d, call_id: X, questions: [Q]}\n- {case_id: d, call_id: Y, questions: [Q]}\n")))
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> loader.load(write(dir, "just a string")))
                .hasMessageContaining("list of cases");
        assertThatThrownBy(() -> loader.load(dir.resolve("missing.yaml"))).hasMessageContaining("Cannot read");
    }

    @Test
    void outOfScopeCaseNeedsNoCallId(@TempDir Path dir) throws Exception {
        List<EvaluationCase> cases = loader.load(write(dir,
                "- case_id: oos\n  questions: [\"Viết email\"]\n  expected_intent: OUT_OF_SCOPE\n"));

        assertThat(cases.get(0).expectsOutOfScope()).isTrue();
    }

    @Test
    void materializerResolvesOwnForeignAndAppendedFiles(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("success").resolve("C1"));
        Files.createDirectories(root.resolve("fail").resolve("C2"));
        Files.writeString(root.resolve("success").resolve("C1").resolve("a.log"), "AAA");
        Files.writeString(root.resolve("fail").resolve("C2").resolve("b.log"), "BBB");
        EvaluationCase c = new EvaluationCase("x", "C1", List.of(
                new FileRef("a.log", null, List.of()),
                new FileRef("C2/b.log", "renamed.log", List.of()),
                new FileRef("a.log", "extra.log", List.of("secret=1"))),
                List.of("Q"), null, null, null, null, null, "dev", null, null, null);

        List<UploadedFile> files = new CaseMaterializer(new CallDirectoryIndex(root)).materialize(c);

        assertThat(files).extracting(UploadedFile::name).containsExactly("a.log", "renamed.log", "extra.log");
        assertThat(new String(files.get(1).content(), StandardCharsets.UTF_8)).isEqualTo("BBB");
        assertThat(new String(files.get(2).content(), StandardCharsets.UTF_8)).startsWith("AAA").contains("secret=1");
        assertThat(Files.readString(root.resolve("success").resolve("C1").resolve("a.log"))).isEqualTo("AAA");
    }

    @Test
    void materializerReportsMissingFilesClearly(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("success").resolve("C1"));
        EvaluationCase c = new EvaluationCase("x", "C1", List.of(new FileRef("nope.log", null, List.of())),
                List.of("Q"), null, null, null, null, null, "dev", null, null, null);

        assertThatThrownBy(() -> new CaseMaterializer(new CallDirectoryIndex(root)).materialize(c))
                .hasMessageContaining("x").hasMessageContaining("nope.log");
    }

    @Test
    void theDevBenchmarkMeetsTheSpecCoverageAndEveryReferencedFileExists() {
        List<EvaluationCase> cases = loader.load(DEV_CASES);

        assertThat(cases).hasSizeGreaterThanOrEqualTo(20);
        assertThat(cases).allMatch(c -> "dev".equals(c.split()));
        assertThat(cases).allMatch(c -> c.questions().size() >= 3 && c.questions().size() <= 5);

        long outOfScopeQuestions = cases.stream().filter(EvaluationCase::expectsOutOfScope)
                .mapToLong(c -> c.questions().size()).sum();
        assertThat(outOfScopeQuestions).isGreaterThanOrEqualTo(5);

        List<EvaluationCase> analysed = cases.stream().filter(c -> !c.expectsOutOfScope()).toList();
        assertThat(analysed).extracting(EvaluationCase::expectedVerdict).contains("SUCCESS", "FAIL", "UNKNOWN");
        assertThat(analysed).extracting(EvaluationCase::expectedIssueCategory)
                .contains("NONE", "NETWORK_PACKET_LOSS", "ICE_FAILURE", "TURN_FAILURE", "SIGNALING_FAILURE");
        assertThat(analysed).anyMatch(c -> c.expectedQualityFlag() != null && c.expectedQualityFlag());
        assertThat(analysed).anyMatch(c -> !c.sensitiveValues().isEmpty());
        assertThat(analysed).filteredOn(c -> c.caseId().startsWith("var-")).hasSizeGreaterThanOrEqualTo(5);

        CaseMaterializer materializer = new CaseMaterializer(new CallDirectoryIndex(Path.of("sample-data")));
        for (EvaluationCase c : cases) {
            assertThat(materializer.materialize(c)).as(c.caseId()).hasSize(c.files().size());
        }
    }
}
