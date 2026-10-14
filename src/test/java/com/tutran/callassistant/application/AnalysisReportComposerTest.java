package com.tutran.callassistant.application;

import com.tutran.callassistant.application.guardrail.AiAnalysis;
import com.tutran.callassistant.application.guardrail.GuardDecision;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.testsupport.TestReports;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisReportComposerTest {

    private final AnalysisReportComposer composer = new AnalysisReportComposer();
    private final Report rule = TestReports.successWithPacketLoss();

    private AiAnalysis ai(String verdict) {
        return new AiAnalysis(verdict, true, "NETWORK_PACKET_LOSS", "HIGH", "Tóm tắt AI", List.of("EV003", "EV002"),
                "Phân tích AI", List.of("Đề xuất AI"));
    }

    @Test
    void degradedKeepsRuleContentAndMarksTheReport() {
        Report report = composer.degraded(rule);

        assertThat(report.degraded()).isTrue();
        assertThat(report.verdict()).isEqualTo(rule.verdict());
        assertThat(report.summary()).isEqualTo(rule.summary());
        assertThat(report.analysis()).isEmpty();
        assertThat(report.dataLimitations()).contains(AnalysisReportComposer.DEGRADED_NOTE).containsAll(rule.dataLimitations());
        assertThat(composer.degraded(report).dataLimitations()).filteredOn(AnalysisReportComposer.DEGRADED_NOTE::equals)
                .hasSize(1);
    }

    @Test
    void duplicateAiSuggestionsAreDropped() {
        AiAnalysis echo = new AiAnalysis("SUCCESS", true, "NETWORK_PACKET_LOSS", "HIGH", "x", List.of("EV001"), "y",
                List.of("Kiểm tra chất lượng mạng phía callee.", " "));
        GuardDecision decision = new GuardDecision(GuardDecision.Outcome.ACCEPT, echo, List.of(), List.of(), null);

        assertThat(composer.fromAi(rule, decision).suggestions()).containsExactly("Kiểm tra chất lượng mạng phía callee.");
    }

    @Test
    void conflictKeepsRuleExplanationAndSaysWhatTheAiProposed() {
        Report explained = rule.withExplanation(List.of("Chuyện gì đã xảy ra: x"));
        GuardDecision decision = new GuardDecision(GuardDecision.Outcome.ACCEPT_WITH_FLAGS, ai("FAIL"), List.of(),
                List.of("G04: AI lệch rule"), "UNKNOWN");

        Report report = composer.fromAi(explained, decision);

        assertThat(report.analysis()).isEmpty();
        assertThat(report.explanation()).hasSize(2).last().asString().contains("AI đề xuất FAIL");
    }

    @Test
    void degradedReportKeepsTheDeterministicExplanation() {
        Report explained = rule.withExplanation(List.of("Vì sao kết luận SUCCESS: y"));

        assertThat(composer.degraded(explained).explanation()).containsExactly("Vì sao kết luận SUCCESS: y");
    }

    @Test
    void uploadLimitationsAddDataSuggestionsAndLowerConfidence() {
        Report high = rule.withConfidence("HIGH", List.of("Evidence mạnh từ nguồn chính (SIGNALING) và đủ file log."));

        Report report = composer.withUploadLimitations(high, List.of("Thiếu caller_webrtc.log"));

        assertThat(report.dataLimitations()).contains("Thiếu caller_webrtc.log");
        assertThat(report.suggestions()).last().asString().startsWith("Bổ sung caller_webrtc.log");
        assertThat(report.confidenceLevel()).isEqualTo("MEDIUM");
        assertThat(report.confidenceReasons()).noneMatch(r -> r.startsWith("Evidence mạnh"));
        assertThat(composer.withUploadLimitations(high, List.of())).isSameAs(high);
    }

    @Test
    void degradedReportIsNeverHighConfidenceAndSaysWhy() {
        Report high = rule.withConfidence("HIGH", List.of("Evidence mạnh"));

        Report report = composer.degraded(high);

        assertThat(report.confidenceLevel()).isEqualTo("MEDIUM");
        assertThat(report.confidenceReasons()).contains("Evidence mạnh").anyMatch(r -> r.contains("degraded"));
    }

    @Test
    void agreeingAiKeepsRuleConfidenceAndAddsReason() {
        Report high = rule.withConfidence("HIGH", List.of("Evidence mạnh"));
        GuardDecision decision = new GuardDecision(GuardDecision.Outcome.ACCEPT, ai("SUCCESS"), List.of(), List.of(), null);

        Report report = composer.fromAi(high, decision);

        assertThat(report.confidenceLevel()).isEqualTo("HIGH");
        assertThat(report.confidenceReasons()).last().asString().contains("cùng kết luận");
    }

    @Test
    void acceptedAiOnlyReplacesWordingAndOrdersCitedEvidenceFirst() {
        GuardDecision decision = new GuardDecision(GuardDecision.Outcome.ACCEPT, ai("SUCCESS"), List.of(), List.of(), null);

        Report report = composer.fromAi(rule, decision);

        assertThat(report.degraded()).isFalse();
        assertThat(report.summary()).isEqualTo("Tóm tắt AI");
        assertThat(report.analysis()).isEqualTo("Phân tích AI");
        // Bộ đề xuất chuẩn đứng trước, đề xuất AI nối thêm sau.
        assertThat(report.suggestions()).containsExactly("Kiểm tra chất lượng mạng phía callee.", "Đề xuất AI");
        assertThat(report.verdict()).isEqualTo("SUCCESS");
        assertThat(report.issueCategory()).isEqualTo("NETWORK_PACKET_LOSS");
        assertThat(report.confidenceLevel()).isEqualTo("MEDIUM");
        assertThat(report.metrics()).isEqualTo(rule.metrics());
        assertThat(report.evidence()).extracting(e -> e.id()).containsExactly("EV003", "EV002", "EV001");
    }

    @Test
    void aiCannotOverrideConfidenceOrCategory() {
        AiAnalysis optimistic = new AiAnalysis("SUCCESS", false, "NONE", "HIGH", "x", List.of("EV001"), "y", List.of());
        GuardDecision decision = new GuardDecision(GuardDecision.Outcome.ACCEPT, optimistic, List.of(), List.of(), null);

        Report report = composer.fromAi(rule, decision);

        assertThat(report.qualityFlag()).isTrue();
        assertThat(report.issueCategory()).isEqualTo("NETWORK_PACKET_LOSS");
        assertThat(report.confidenceLevel()).isEqualTo("MEDIUM");
        assertThat(report.suggestions()).isEqualTo(rule.suggestions());
    }

    @Test
    void conflictingVerdictBecomesUnknownLowConfidenceWithFlagAndNoAiProse() {
        GuardDecision decision = new GuardDecision(GuardDecision.Outcome.ACCEPT_WITH_FLAGS, ai("FAIL"), List.of(),
                List.of("G04: verdict AI (FAIL) mâu thuẫn rule (SUCCESS)"), "UNKNOWN");

        Report report = composer.fromAi(rule, decision);

        assertThat(report.verdict()).isEqualTo("UNKNOWN");
        assertThat(report.issueCategory()).isEqualTo("UNKNOWN");
        assertThat(report.qualityFlag()).isFalse();
        assertThat(report.confidenceLevel()).isEqualTo("LOW");
        assertThat(report.summary()).contains("mâu thuẫn").doesNotContain("Tóm tắt AI");
        assertThat(report.analysis()).isEmpty();
        assertThat(report.dataLimitations()).anyMatch(l -> l.startsWith("G04"));
    }

    @Test
    void categoryFlagKeepsRuleVerdictButLowersConfidence() {
        GuardDecision decision = new GuardDecision(GuardDecision.Outcome.ACCEPT_WITH_FLAGS, ai("SUCCESS"), List.of(),
                List.of("G04: AI đề xuất nhóm vấn đề X khác rule"), null);

        Report report = composer.fromAi(rule, decision);

        assertThat(report.verdict()).isEqualTo("SUCCESS");
        assertThat(report.confidenceLevel()).isEqualTo("LOW");
    }

    @Test
    void refusesToComposeFromARejectedDecision() {
        assertThatThrownBy(() -> composer.fromAi(rule, GuardDecision.reject(List.of("G02"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
