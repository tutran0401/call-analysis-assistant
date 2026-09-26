package com.tutran.callassistant.report;

import com.networknt.schema.ValidationMessage;
import com.tutran.callassistant.analysis.metrics.CallMetricsCalculator;
import com.tutran.callassistant.analysis.verdict.RuleVerdictEngine;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.report.MetricRow;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedReportAssemblerTest {

    private final ReportAssembler assembler = RuleBasedReportAssembler.withDefaults();
    private final ReportSchemaGuard schemaGuard = new ReportSchemaGuard();

    private Report reportFor(Path callDirectory, String callId) {
        CallTimeline timeline = SampleCalls.timelineWithEndCallLogs(callDirectory, callId);
        CallMetrics metrics = CallMetricsCalculator.withDefaults().calculate(timeline);
        RuleVerdictResult verdict = RuleVerdictEngine.withDefaults().evaluate(timeline, metrics);
        return assembler.assemble(callId, verdict, metrics);
    }

    @Test
    void buildsAValidReportForARealSuccessfulCall() {
        String callId = SampleCalls.SUCCESS_FULL_LOGS;
        Report report = reportFor(SampleCalls.success(callId), callId);

        assertThat(report.callId()).isEqualTo(callId);
        assertThat(report.verdict()).isEqualTo("SUCCESS");
        assertThat(report.qualityFlag()).isFalse();
        assertThat(report.issueCategory()).isEqualTo("NONE");
        assertThat(report.metrics()).isNotEmpty();
        assertThat(report.evidence()).isNotEmpty();

        Set<ValidationMessage> violations = schemaGuard.validate(report);
        assertThat(violations).as("schema violations: %s", violations).isEmpty();
    }

    @Test
    void schemaGuardReportsNoProblemsForAValidReport() {
        String callId = SampleCalls.SUCCESS_FULL_LOGS;
        Report report = reportFor(SampleCalls.success(callId), callId);

        assertThat(schemaGuard.inspect(report)).isEmpty();
    }

    @Test
    void reportsNoneAsTheCategoryForACleanSuccessfulCall() {
        // Cuộc gọi sạch vẫn phải nêu category, nhưng là NONE chứ không phải UNKNOWN: NONE là kết luận
        // khẳng định "đã kiểm tra, không có vấn đề", còn UNKNOWN sẽ bị đọc thành "có vấn đề nhưng
        // không biết là gì". Đề xuất thì vẫn để trống - không bịa việc cho cuộc gọi bình thường.
        String callId = SampleCalls.SUCCESS_FULL_LOGS;
        Report report = reportFor(SampleCalls.success(callId), callId);

        assertThat(report.issueCategory()).isEqualTo("NONE");
        assertThat(report.suggestions()).isEmpty();
    }

    @Test
    void everyCallReportsAnIssueCategoryWhateverTheVerdict() {
        // Mục "Vấn đề chất lượng / nguyên nhân khả dĩ" không bao giờ được để trống, ở cả 3 loại verdict.
        String ok = SampleCalls.SUCCESS_FULL_LOGS;
        String bad = SampleCalls.FAIL_REJECTED_AFTER_INVITE;

        assertThat(reportFor(SampleCalls.success(ok), ok).issueCategory()).isNotNull();
        assertThat(reportFor(SampleCalls.fail(bad), bad).issueCategory()).isNotNull();
    }

    @Test
    void includesCategoryAndSuggestionForARealFailedCall() {
        String callId = SampleCalls.FAIL_REJECTED_AFTER_INVITE;
        Report report = reportFor(SampleCalls.fail(callId), callId);

        assertThat(report.verdict()).isEqualTo("FAIL");
        assertThat(report.issueCategory()).isEqualTo("SIGNALING_FAILURE");
        assertThat(report.suggestions()).isNotEmpty();
        assertThat(schemaGuard.inspect(report)).isEmpty();
    }

    @Test
    void everyMetricNameAppearsAtMostOnce() {
        String callId = SampleCalls.SUCCESS_FULL_LOGS;
        Report report = reportFor(SampleCalls.success(callId), callId);

        List<String> names = report.metrics().stream().map(MetricRow::name).toList();
        assertThat(names).doesNotHaveDuplicates();
    }
}
