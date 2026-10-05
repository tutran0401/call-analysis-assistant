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
    void acceptedAiOnlyReplacesWordingAndOrdersCitedEvidenceFirst() {
        GuardDecision decision = new GuardDecision(GuardDecision.Outcome.ACCEPT, ai("SUCCESS"), List.of(), List.of(), null);

        Report report = composer.fromAi(rule, decision);

        assertThat(report.degraded()).isFalse();
        assertThat(report.summary()).isEqualTo("Tóm tắt AI");
        assertThat(report.analysis()).isEqualTo("Phân tích AI");
        assertThat(report.suggestions()).containsExactly("Đề xuất AI");
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
