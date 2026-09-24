package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.evidence.RuleVerdictEngine;
import com.tutran.callassistant.evidence.RuleVerdictResult;
import com.tutran.callassistant.metrics.CallMetrics;
import com.tutran.callassistant.metrics.CallMetricsCalculator;
import com.tutran.callassistant.parser.EndCallLogParser;
import com.tutran.callassistant.parser.SignalingJsonParser;
import com.tutran.callassistant.timeline.CallTimeline;
import com.tutran.callassistant.timeline.TimelineBuilder;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReportBuilderTest {

    private final ReportBuilder reportBuilder = new ReportBuilder();
    private final ReportSchemaValidator validator = new ReportSchemaValidator();

    private Report buildReportFor(Path callDir, String callId) {
        List<CanonicalEvent> all = new ArrayList<>();
        all.addAll(new SignalingJsonParser().parse(callDir.resolve("signaling.json"), callId).events());
        all.addAll(new EndCallLogParser().parse(callDir.resolve("caller_endcall.log"), callId).events());
        all.addAll(new EndCallLogParser().parse(callDir.resolve("callee_endcall.log"), callId).events());
        CallTimeline timeline = new TimelineBuilder().build(callId, all);
        CallMetrics metrics = new CallMetricsCalculator().calculate(timeline);
        RuleVerdictResult ruleResult = new RuleVerdictEngine().evaluate(timeline, metrics);
        return reportBuilder.build(callId, ruleResult, metrics);
    }

    @Test
    void buildsAValidReportForARealSuccessfulCall() {
        String callId = "DE7DD314-F432-45CB-BCB4-AE9103CC0919";
        Report report = buildReportFor(Path.of("sample-data", "success", callId), callId);

        assertThat(report.callId()).isEqualTo(callId);
        assertThat(report.verdict()).isEqualTo("SUCCESS");
        assertThat(report.qualityFlag()).isFalse();
        assertThat(report.issueCategory()).isNull();
        assertThat(report.metrics()).isNotEmpty();
        assertThat(report.evidence()).isNotEmpty();

        Set<ValidationMessage> violations = validator.validate(report);
        assertThat(violations).as("schema violations: %s", violations).isEmpty();
    }

    @Test
    void everyMetricNameAppearsAtMostOnce() {
        String callId = "DE7DD314-F432-45CB-BCB4-AE9103CC0919";
        Report report = buildReportFor(Path.of("sample-data", "success", callId), callId);

        List<String> names = report.metrics().stream().map(MetricRow::name).toList();
        assertThat(names).doesNotHaveDuplicates();
    }
}
