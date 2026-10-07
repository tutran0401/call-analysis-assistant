package com.tutran.callassistant.application.guardrail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tutran.callassistant.application.ai.AiPromptFactory;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.testsupport.TestReports;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** G01-G05 của PROJECT_SPEC.md mục 6.4 (kèm các biến thể: field thiếu, enum lạ, input quá khổ). */
class AiResponseGuardTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiResponseGuard guard = new AiResponseGuard(new AiPromptFactory());
    private final Report rule = TestReports.successWithPacketLoss();

    private String with(String field, Object value) throws Exception {
        ObjectNode node = (ObjectNode) mapper.readTree(TestReports.agreeingAiJson());
        node.set(field, mapper.valueToTree(value));
        return mapper.writeValueAsString(node);
    }

    private String without(String field) throws Exception {
        ObjectNode node = (ObjectNode) mapper.readTree(TestReports.agreeingAiJson());
        node.remove(field);
        return mapper.writeValueAsString(node);
    }

    @Test
    void validAgreeingResponseIsAccepted() {
        GuardDecision decision = guard.check(TestReports.agreeingAiJson(), rule);

        assertThat(decision.outcome()).isEqualTo(GuardDecision.Outcome.ACCEPT);
        assertThat(decision.analysis().evidenceIds()).containsExactly("EV002", "EV001");
        assertThat(decision.verdictOverride()).isNull();
        assertThat(decision.flags()).isEmpty();
    }

    @Test
    void g01_unknownEvidenceIdIsRejected() throws Exception {
        GuardDecision decision = guard.check(with("evidenceIds", java.util.List.of("EV002", "EV999")), rule);

        assertThat(decision.isRejected()).isTrue();
        assertThat(decision.reasons()).singleElement().asString().startsWith("G01").contains("EV999");
    }

    @Test
    void g01_definiteVerdictWithoutAnyEvidenceIsRejected() throws Exception {
        GuardDecision decision = guard.check(with("evidenceIds", java.util.List.of()), rule);

        assertThat(decision.isRejected()).isTrue();
        assertThat(decision.reasons().get(0)).startsWith("G01");
    }

    @Test
    void g01_unknownVerdictMayCiteNoEvidence() throws Exception {
        ObjectNode node = (ObjectNode) mapper.readTree(TestReports.agreeingAiJson());
        node.put("verdict", "UNKNOWN").set("evidenceIds", mapper.createArrayNode());

        GuardDecision decision = guard.check(mapper.writeValueAsString(node), rule);

        assertThat(decision.isRejected()).isFalse();
    }

    @Test
    void g02_notJsonOrNotAnObjectIsRejected() {
        assertThat(guard.check("Đây không phải JSON", rule).reasons().get(0)).startsWith("G02");
        assertThat(guard.check("[1,2,3]", rule).reasons().get(0)).startsWith("G02");
        assertThat(guard.check("", rule).isRejected()).isTrue();
        assertThat(guard.check(null, rule).isRejected()).isTrue();
    }

    @Test
    void g02_missingFieldAndUnexpectedFieldAreRejected() throws Exception {
        GuardDecision missing = guard.check(without("analysis"), rule);
        assertThat(missing.isRejected()).isTrue();
        assertThat(missing.reasons()).anyMatch(r -> r.startsWith("G02") && r.contains("analysis"));

        GuardDecision extra = guard.check(with("confidence", 0.93), rule);
        assertThat(extra.isRejected()).isTrue();
        assertThat(extra.reasons()).anyMatch(r -> r.startsWith("G02"));
    }

    @Test
    void g02_wrongTypeIsRejected() throws Exception {
        assertThat(guard.check(with("qualityFlag", "yes"), rule).reasons()).anyMatch(r -> r.startsWith("G02"));
    }

    @Test
    void g03_verdictOrCategoryOutsideTaxonomyIsRejected() throws Exception {
        GuardDecision badVerdict = guard.check(with("verdict", "MAYBE"), rule);
        assertThat(badVerdict.isRejected()).isTrue();
        assertThat(badVerdict.reasons()).anyMatch(r -> r.startsWith("G03"));

        GuardDecision badCategory = guard.check(with("issueCategory", "BAD_WIFI"), rule);
        assertThat(badCategory.reasons()).anyMatch(r -> r.startsWith("G03"));

        assertThat(guard.check(with("confidenceLevel", "VERY_HIGH"), rule).reasons())
                .anyMatch(r -> r.startsWith("G03"));
    }

    @Test
    void g04_verdictContradictingRuleBecomesUnknownWithFlag() throws Exception {
        GuardDecision decision = guard.check(with("verdict", "FAIL"), rule);

        assertThat(decision.outcome()).isEqualTo(GuardDecision.Outcome.ACCEPT_WITH_FLAGS);
        assertThat(decision.verdictOverride()).isEqualTo("UNKNOWN");
        assertThat(decision.flags()).singleElement().asString().startsWith("G04").contains("FAIL").contains("SUCCESS");
    }

    @Test
    void g04_sameVerdictButDifferentCategoryKeepsRuleAndFlags() throws Exception {
        GuardDecision decision = guard.check(with("issueCategory", "NETWORK_DELAY_JITTER"), rule);

        assertThat(decision.outcome()).isEqualTo(GuardDecision.Outcome.ACCEPT_WITH_FLAGS);
        assertThat(decision.verdictOverride()).isNull();
        assertThat(decision.flags().get(0)).contains("NETWORK_DELAY_JITTER");
    }

    @Test
    void g05_numberNotInComputedMetricsIsRejected() throws Exception {
        GuardDecision decision = guard.check(
                with("analysis", "Packet loss phía callee lên tới 77.7% (EV002)."), rule);

        assertThat(decision.isRejected()).isTrue();
        assertThat(decision.reasons().get(0)).startsWith("G05").contains("77.7");
    }

    @Test
    void g05_numbersFromMetricsAndEvidenceAreAllowedIncludingFormatVariants() throws Exception {
        GuardDecision decision = guard.check(with("analysis",
                "Thiết lập mất 6.20 s, kết nối 95.3 giây, MOS 2,9, mất gói 9.8 %. Lúc 10:00:41 (EV002), có 2 bên."), rule);

        assertThat(decision.isRejected()).as(decision.reasons().toString()).isFalse();
    }

    @Test
    void g05_faithfulRoundingOfAComputedMetricIsAcceptedButNothingElse() throws Exception {
        Report precise = new Report("CALL-1", "SUCCESS", true, "NETWORK_PACKET_LOSS", java.util.List.of(), "MEDIUM", "s",
                TestReports.successWithPacketLoss().evidence(),
                java.util.List.of(new com.tutran.callassistant.domain.report.MetricRow("MOS thấp nhất (caller)", "4.40337", "End Call"),
                        new com.tutran.callassistant.domain.report.MetricRow("Packet loss cao nhất", "11.3208 %", "End Call")),
                java.util.List.of(), java.util.List.of());

        assertThat(guard.check(with("analysis", "MOS thấp nhất 4.4 (EV002) và mất gói khoảng 11.32% hay 11%."), precise)
                .isRejected()).isFalse();
        // lệch quá nửa đơn vị ở chữ số cuối -> không phải làm tròn của giá trị nào
        assertThat(guard.check(with("analysis", "MOS thấp nhất 4.5 (EV002)."), precise).isRejected()).isTrue();
        assertThat(guard.check(with("analysis", "Mất gói 11.4%."), precise).isRejected()).isTrue();
        // làm tròn không được mượn từ giá trị nguồn không có thêm chữ số (không phải làm tròn)
        assertThat(guard.check(with("analysis", "MOS 4.3 (EV002)."), precise).isRejected()).isTrue();
        // số bịa hoàn toàn vẫn bị bắt
        assertThat(guard.check(with("analysis", "Packet loss 77.7% (EV002)."), precise).isRejected()).isTrue();
    }

    @Test
    void g05_unitlessSingleDigitCountsAreTolerated_butSingleDigitWithUnitIsNot() throws Exception {
        assertThat(guard.check(with("summary", "Cả 2 bên đều kết nối, gặp 1 vấn đề chính."), rule).isRejected())
                .isFalse();
        assertThat(guard.check(with("summary", "Mất 7 giây để thiết lập."), rule).isRejected()).isTrue();
    }

    @Test
    void g05_evidenceIdDigitsAreNotTreatedAsMetrics() throws Exception {
        assertThat(guard.check(with("analysis", "Theo EV001 và EV003, cuộc gọi kết thúc bình thường."), rule)
                .isRejected()).isFalse();
    }

    @Test
    void oversizedGarbageResponseIsRejectedNotCrashing() throws Exception {
        String huge = "{\"verdict\":\"SUCCESS\",\"summary\":\"" + "x".repeat(2_000_000) + "\"}";

        assertThat(guard.check(huge, rule).isRejected()).isTrue();
    }

    @Test
    void decisionForRejectedResponseCarriesNoAnalysis() {
        GuardDecision decision = guard.check("nope", rule);

        assertThat(decision.analysis()).isNull();
        assertThat(decision.outcome()).isEqualTo(GuardDecision.Outcome.REJECT);
    }
}
