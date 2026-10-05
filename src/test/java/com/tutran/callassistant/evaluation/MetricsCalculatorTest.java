package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.domain.report.MetricRow;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.report.TextReportRenderer;
import com.tutran.callassistant.testsupport.TestReports;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Số kỳ vọng được tính tay từ bộ record bên dưới (xem chú thích từng assert). */
class MetricsCalculatorTest {

    private static final Report RULE_REPORT = TestReports.successWithPacketLoss();
    private static final String GOOD_TEXT = new TextReportRenderer().render(RULE_REPORT);

    private final MetricsCalculator calculator = new MetricsCalculator();

    private static EvaluationCase testCase(String id, String verdict, Boolean quality, String category,
                                           String intent, List<String> sensitive) {
        return new EvaluationCase(id, intent == null ? "CALL-" + id : null, List.of(), List.of("q1", "q2", "q3"),
                intent, verdict, quality, category, List.of(), "dev", null, null, sensitive);
    }

    private static RunRecord record(String caseId, int question, int run, String verdict, String category,
                                    long latency) {
        return new RunRecord(caseId, question, "q" + question, run, "ANALYZE_CALL", verdict, category,
                "PACKET".equals(category), "MEDIUM", false, null, latency, true, null, true, 0, 0, 0, 0,
                List.of(), RULE_REPORT, GOOD_TEXT);
    }

    private static RunRecord with(RunRecord r, java.util.function.UnaryOperator<RunRecordBuilder> change) {
        return change.apply(new RunRecordBuilder(r)).build();
    }

    /** Builder tối thiểu để test sửa từng field của record bất biến. */
    private static final class RunRecordBuilder {
        RunRecord r;
        String intent;
        boolean degraded;
        String fallbackReason;
        boolean pipelineOk;
        boolean aiCalled;
        int inputProbeHits;
        int outputResidual;
        List<String> warnings;
        Report report;
        String renderedText;

        RunRecordBuilder(RunRecord r) {
            this.r = r;
            intent = r.intent();
            degraded = r.degraded();
            fallbackReason = r.fallbackReason();
            pipelineOk = r.pipelineOk();
            aiCalled = r.aiCalled();
            inputProbeHits = r.inputProbeHits();
            outputResidual = r.outputResidual();
            warnings = r.warnings();
            report = r.report();
            renderedText = r.renderedText();
        }

        RunRecord build() {
            return new RunRecord(r.caseId(), r.questionIndex(), r.question(), r.run(), intent, r.verdict(),
                    r.issueCategory(), r.qualityFlag(), r.confidenceLevel(), degraded, fallbackReason,
                    r.latencyMillis(), pipelineOk, pipelineOk ? null : "boom", aiCalled, inputProbeHits, 0, 0,
                    outputResidual, warnings, report, renderedText);
        }
    }

    @Test
    void computesEveryMetricFromHandCountedRecords() {
        List<EvaluationCase> cases = List.of(
                testCase("c1", "SUCCESS", false, "NONE", null, List.of()),
                testCase("c2", "FAIL", false, "TURN_FAILURE", null, List.of()),
                testCase("c3", "SUCCESS", true, "PACKET", null, List.of("0987654321", "jwt")),
                testCase("oos", null, null, null, "OUT_OF_SCOPE", List.of()));

        Report alteredMetrics = new Report(RULE_REPORT.callId(), "FAIL", false, "ICE_FAILURE", List.of(), "MEDIUM", "s",
                RULE_REPORT.evidence(), List.of(new MetricRow("MOS (callee)", "9.9", "End Call")), List.of(), List.of());

        List<RunRecord> records = List.of(
                with(record("c1", 0, 0, "SUCCESS", "NONE", 100), b -> { b.inputProbeHits = 1; return b; }),
                record("c1", 0, 1, "SUCCESS", "NONE", 200),
                with(record("c1", 1, 0, "UNKNOWN", "UNKNOWN", 300), b -> {
                    b.degraded = true;
                    b.fallbackReason = "GUARDRAIL_REJECTED";
                    b.warnings = List.of("G05: số liệu không có trong bộ chỉ số");
                    b.renderedText = "bad layout";
                    return b;
                }),
                with(record("c2", 0, 0, "FAIL", "TURN_FAILURE", 400), b -> { b.aiCalled = false; return b; }),
                with(record("c2", 0, 1, "FAIL", "ICE_FAILURE", 500), b -> {
                    b.degraded = true;
                    b.fallbackReason = "AI_TIMEOUT";
                    b.report = alteredMetrics;
                    return b;
                }),
                with(record("c2", 2, 0, "FAIL", "TURN_FAILURE", 600), b -> { b.pipelineOk = false; return b; }),
                with(record("c3", 0, 0, "SUCCESS", "PACKET", 700), b -> { b.outputResidual = 2; return b; }),
                with(record("oos", 0, 0, "UNKNOWN", "UNKNOWN", 800), b -> { b.intent = "OUT_OF_SCOPE"; return b; }),
                with(record("oos", 1, 0, "UNKNOWN", "UNKNOWN", 900), b -> b));

        Map<String, RuleBaseline> rules = Map.of(
                "c1", new RuleBaseline("c1", "SUCCESS", "NONE", false, RULE_REPORT.metrics()),
                "c2", new RuleBaseline("c2", "FAIL", "TURN_FAILURE", false, RULE_REPORT.metrics()),
                "c3", new RuleBaseline("c3", "UNKNOWN", "UNKNOWN", false, RULE_REPORT.metrics()));

        EvaluationMetrics m = calculator.compute(cases, records, rules, 3);

        // c1 đúng 2/3 (q1 sai), c2 đúng 2/2 (record lỗi pipeline bị loại), c3 đúng 1/1
        assertThat(m.verdictAccuracy()).isEqualTo(new Ratio(5, 6));
        assertThat(m.ruleVerdictAccuracy()).isEqualTo(new Ratio(2, 3));
        // chỉ c2 (FAIL) và c3 (SUCCESS có cờ): c2 đúng 1/2, c3 1/1
        assertThat(m.issueCategoryAccuracy()).isEqualTo(new Ratio(2, 3));
        assertThat(m.qualityFlagAccuracy()).isEqualTo(new Ratio(6, 6));
        // 8 record chạy xong; 7 đúng (1 record OOS bị phân loại nhầm là trong phạm vi)
        assertThat(m.intentAccuracy()).isEqualTo(new Ratio(7, 8));
        // chỉ c3 nhất quán
        assertThat(m.consistencyCases()).isEqualTo(new Ratio(1, 3));
        // c1: 2/3, c2: 1/2, c3: 1/1
        assertThat(m.consistencyRuns()).isEqualTo(new Ratio(4, 6));
        // 6 record có rule + report, trong đó 1 record bị đổi bảng chỉ số
        assertThat(m.metricCorrectness()).isEqualTo(new Ratio(5, 6));
        // 6 record analysed có text; 1 sai bố cục
        assertThat(m.templateCompliance()).isEqualTo(new Ratio(5, 6));
        // tới Guardrails: c1 r0, c1 r1, c1 q1 (G05), c3 -> 1/4 (c2 không gọi AI / timeout không tính)
        assertThat(m.unsupportedClaims()).isEqualTo(new Ratio(1, 4));
        assertThat(m.pipelineSuccess()).isEqualTo(new Ratio(8, 9));
        assertThat(m.degradedRate()).isEqualTo(new Ratio(2, 7));
        assertThat(m.inputLeakage()).isEqualTo(1);
        assertThat(m.outputLeakage()).isEqualTo(2);
        assertThat(m.logLeakage()).isEqualTo(3);
        assertThat(m.sensitiveValuesInjected()).isEqualTo(2);
        // nearest-rank trên 100..900 ms
        assertThat(m.latencyP50Millis()).isEqualTo(500);
        assertThat(m.latencyP95Millis()).isEqualTo(900);
    }

    @Test
    void metricsWithoutLabelsAreUnmeasuredNotZero() {
        List<EvaluationCase> cases = List.of(testCase("c1", null, null, null, null, List.of()));

        EvaluationMetrics m = calculator.compute(cases, List.of(record("c1", 0, 0, "SUCCESS", "NONE", 10)),
                Map.of(), 0);

        assertThat(m.verdictAccuracy().measured()).isFalse();
        assertThat(m.verdictAccuracy().percent()).isNull();
        assertThat(m.issueCategoryAccuracy().measured()).isFalse();
        assertThat(m.metricCorrectness().measured()).isFalse();
        assertThat(m.pipelineSuccess()).isEqualTo(new Ratio(1, 1));
    }

    @Test
    void emptyRunYieldsNoLatencyInsteadOfZero() {
        EvaluationMetrics m = calculator.compute(List.of(), List.of(), Map.of(), 0);

        assertThat(m.latencyP50Millis()).isNull();
        assertThat(m.latencyP95Millis()).isNull();
    }

    @Test
    void templateCheckRequiresAllSectionsInOrder() {
        assertThat(MetricsCalculator.followsTemplate(GOOD_TEXT)).isTrue();
        assertThat(MetricsCalculator.followsTemplate(GOOD_TEXT.replace("## Đề xuất", "## Gợi ý"))).isFalse();
        String swapped = GOOD_TEXT.replace("## Chỉ số cuộc gọi", "@@A@@").replace("## Đề xuất", "## Chỉ số cuộc gọi")
                .replace("@@A@@", "## Đề xuất");
        assertThat(MetricsCalculator.followsTemplate(swapped)).isFalse();
    }
}
