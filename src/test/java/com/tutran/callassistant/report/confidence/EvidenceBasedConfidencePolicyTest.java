package com.tutran.callassistant.report.confidence;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.report.ConfidenceLevel;
import com.tutran.callassistant.domain.verdict.Evidence;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import com.tutran.callassistant.domain.verdict.Verdict;
import com.tutran.callassistant.testsupport.Events;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Bảng mục 7.2: mỗi nhánh của chính sách độ tin cậy. */
class EvidenceBasedConfidencePolicyTest {

    private final EvidenceBasedConfidencePolicy policy = new EvidenceBasedConfidencePolicy();

    private static final Evidence CONFIRMED = evidence("EV001", Events.signaling("OK_ACK_OK", "2026-01-01T10:00:06Z", 1));
    private static final Evidence BYE = evidence("EV002", Events.signaling("BYE", "2026-01-01T10:01:41Z", 2));
    private static final Evidence PACKET_LOSS = evidence("EV003",
            Events.endCall(Leg.CALLEE, "CALL_SUMMARY", "2026-01-01T10:00:41Z", 3, Map.of()));
    private static final Evidence UNANCHORED_ICE = evidence("EV004", Events.unanchoredWebRtc(Leg.CALLER, 1200, 4));
    private static final Evidence ICE = evidence("EV005",
            Events.timed(EventSource.WEBRTC, Leg.CALLER, "2026-01-01T10:00:07Z", 5, "ICE_CONNECTION_STATE_CHANGE"));

    @Test
    void strongEvidenceAndFullFilesIsHigh() {
        ConfidenceAssessment result = policy.assess(success(List.of(CONFIRMED, BYE), List.of()));

        assertThat(result.level()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.reasons()).singleElement().asString().contains("Evidence mạnh");
    }

    @Test
    void qualityIssueWithEvidenceFromPrimarySourceIsHigh() {
        RuleVerdictResult verdict = new RuleVerdictResult(Verdict.SUCCESS, true, IssueCategory.NETWORK_PACKET_LOSS,
                "s", List.of(CONFIRMED, PACKET_LOSS, BYE), List.of());

        assertThat(policy.strengthOf(verdict)).isEqualTo(EvidenceStrength.STRONG);
        assertThat(policy.assess(verdict).level()).isEqualTo(ConfidenceLevel.HIGH);
    }

    @Test
    void qualityIssueWithoutPrimarySourceIsPartialAndMedium() {
        // Packet loss mà evidence chỉ có signaling: thiếu End Call log - nguồn chính của category này.
        RuleVerdictResult verdict = new RuleVerdictResult(Verdict.SUCCESS, true, IssueCategory.NETWORK_PACKET_LOSS,
                "s", List.of(CONFIRMED, BYE), List.of());

        assertThat(policy.strengthOf(verdict)).isEqualTo(EvidenceStrength.PARTIAL);
        ConfidenceAssessment result = policy.assess(verdict);
        assertThat(result.level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(result.reasons()).anyMatch(r -> r.contains("END_CALL"));
    }

    @Test
    void singleEvidenceIsPartial() {
        RuleVerdictResult verdict = new RuleVerdictResult(Verdict.FAIL, false, IssueCategory.ICE_FAILURE, "s",
                List.of(ICE), List.of());

        assertThat(policy.strengthOf(verdict)).isEqualTo(EvidenceStrength.PARTIAL);
    }

    @Test
    void missingFileIsMedium() {
        ConfidenceAssessment result = policy.assess(success(List.of(CONFIRMED, BYE), List.of("Thiếu callee_endcall.log")));

        assertThat(result.level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(result.reasons()).anyMatch(r -> r.contains("Thiếu callee_endcall.log"));
    }

    @Test
    void unanchoredEvidenceIsMedium() {
        RuleVerdictResult verdict = new RuleVerdictResult(Verdict.FAIL, false, IssueCategory.ICE_FAILURE, "s",
                List.of(CONFIRMED, UNANCHORED_ICE), List.of());

        ConfidenceAssessment result = policy.assess(verdict);

        assertThat(result.level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(result.reasons()).anyMatch(r -> r.contains("thời gian tuyệt đối"));
    }

    @Test
    void unknownVerdictIsLow() {
        RuleVerdictResult verdict = new RuleVerdictResult(Verdict.UNKNOWN, false, IssueCategory.UNKNOWN, "s",
                List.of(), List.of("Thiếu signaling"));

        ConfidenceAssessment result = policy.assess(verdict);

        assertThat(result.level()).isEqualTo(ConfidenceLevel.LOW);
        // Tín hiệu MEDIUM vẫn được liệt kê dù mức đã do tín hiệu LOW quyết định.
        assertThat(result.reasons()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void failWithoutCategoryIsWeakAndLow() {
        RuleVerdictResult verdict = new RuleVerdictResult(Verdict.FAIL, false, IssueCategory.UNKNOWN, "s",
                List.of(CONFIRMED, BYE), List.of());

        assertThat(policy.strengthOf(verdict)).isEqualTo(EvidenceStrength.WEAK);
        assertThat(policy.assess(verdict).level()).isEqualTo(ConfidenceLevel.LOW);
    }

    @Test
    void aiAgreementKeepsLevelAndAddsReason() {
        ConfidenceAssessment base = policy.assess(success(List.of(CONFIRMED, BYE), List.of()));

        ConfidenceAssessment result = policy.withAi(base, AiAgreement.AGREES);

        assertThat(result.level()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.reasons()).last().asString().contains("AI");
    }

    @Test
    void aiDisagreementIsLow() {
        ConfidenceAssessment base = policy.assess(success(List.of(CONFIRMED, BYE), List.of()));

        ConfidenceAssessment result = policy.withAi(base, AiAgreement.DISAGREES);

        assertThat(result.level()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.reasons()).first().asString().contains("không cùng kết luận");
    }

    @Test
    void degradedReportCannotBeHigh() {
        ConfidenceAssessment base = policy.assess(success(List.of(CONFIRMED, BYE), List.of()));

        assertThat(policy.withAi(base, AiAgreement.UNAVAILABLE).level()).isEqualTo(ConfidenceLevel.MEDIUM);
        ConfidenceAssessment low = new ConfidenceAssessment(ConfidenceLevel.LOW, List.of("x"));
        assertThat(policy.withAi(low, AiAgreement.UNAVAILABLE).level()).isEqualTo(ConfidenceLevel.LOW);
    }

    @Test
    void ruleOnlyReportIsUnchanged() {
        ConfidenceAssessment base = policy.assess(success(List.of(CONFIRMED, BYE), List.of()));

        assertThat(policy.withAi(base, AiAgreement.NOT_COMPARED)).isEqualTo(base);
    }

    @Test
    void sameInputGivesSameAssessment() {
        RuleVerdictResult verdict = success(List.of(CONFIRMED, BYE), List.of("Thiếu caller_webrtc.log"));

        assertThat(policy.assess(verdict)).isEqualTo(policy.assess(verdict));
    }

    private static RuleVerdictResult success(List<Evidence> evidence, List<String> limitations) {
        return new RuleVerdictResult(Verdict.SUCCESS, false, IssueCategory.NONE, "s", evidence, limitations);
    }

    private static Evidence evidence(String id, CanonicalEvent event) {
        return new Evidence(id, "src", "10:00:00", "d", event);
    }
}
