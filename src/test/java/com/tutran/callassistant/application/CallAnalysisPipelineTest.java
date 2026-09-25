package com.tutran.callassistant.application;

import com.tutran.callassistant.analysis.metrics.CallMetricsCalculator;
import com.tutran.callassistant.analysis.verdict.RuleVerdictEngine;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.ingest.file.DirectoryClientLogSource;
import com.tutran.callassistant.ingest.file.LocalFileSignalingSource;
import com.tutran.callassistant.report.ReportSchemaGuard;
import com.tutran.callassistant.report.RuleBasedReportAssembler;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy cả pipeline trên cuộc gọi thật, không có Elasticsearch và không mock tầng phân tích.
 *
 * <p>Đây là test chứng minh kiến trúc đứng được: nguồn signaling được thay bằng implementation đọc
 * file (chỉ cần một dòng khi wiring, không sửa pipeline), còn lại là code thật từ parser tới report -
 * gồm cả webrtc.log mà các test đơn vị phía trên cố tình không nạp.
 */
class CallAnalysisPipelineTest {

    private final CallAnalysisPipeline pipeline = pipelineReadingSignalingFromFile();

    private static CallAnalysisPipeline pipelineReadingSignalingFromFile() {
        Map<SignalingSourcePreference, SignalingEventSource> sources =
                new EnumMap<>(SignalingSourcePreference.class);
        sources.put(SignalingSourcePreference.LOCAL_FILE, LocalFileSignalingSource.withDefaults());
        return new CallAnalysisPipeline(
                new SignalingSourceResolver(sources),
                DirectoryClientLogSource.withDefaults(),
                SampleCalls.timelineBuilder(),
                CallMetricsCalculator.withDefaults(),
                RuleVerdictEngine.withDefaults(),
                RuleBasedReportAssembler.withDefaults(),
                new ReportSchemaGuard());
    }

    private AnalysisOutcome analyze(java.nio.file.Path callDirectory) {
        return pipeline.analyze(CallReference.ofDirectory(callDirectory), SignalingSourcePreference.LOCAL_FILE);
    }

    @Test
    void producesASchemaValidReportForARealSuccessfulCallEndToEnd() {
        AnalysisOutcome outcome = analyze(SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS));

        assertThat(outcome.report().callId()).isEqualTo(SampleCalls.SUCCESS_FULL_LOGS);
        assertThat(outcome.report().verdict()).isEqualTo("SUCCESS");
        assertThat(outcome.report().confidenceLevel()).isEqualTo("HIGH");
        assertThat(outcome.report().evidence()).isNotEmpty();
        assertThat(outcome.report().metrics()).isNotEmpty();
        // Guard nằm trong pipeline, nên report sai schema sẽ hiện thành ghi chú ngay ở đây.
        assertThat(outcome.processingWarnings()).noneMatch(w -> w.contains("schema violation"));
    }

    @Test
    void usesWebRtcLogsThatTheUnitTestsDeliberatelySkip() {
        AnalysisOutcome outcome = analyze(SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS));

        assertThat(outcome.report().metrics())
                .anySatisfy(row -> {
                    assertThat(row.name()).isEqualTo("Sự kiện ICE/TURN (caller)");
                    assertThat(row.value()).doesNotContain("N/A");
                });
    }

    @Test
    void surfacesParserWarningsSeparatelyFromTheReport() {
        // Signaling export mẫu bị cắt bớt; cảnh báo đó phải tới được người chạy nhưng không được lẫn
        // vào report chính thức theo mục 4.5.
        AnalysisOutcome outcome = analyze(SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS));

        assertThat(outcome.processingWarnings()).anyMatch(w -> w.contains("truncated"));
        assertThat(outcome.report().dataLimitations()).noneMatch(w -> w.contains("truncated"));
    }

    @Test
    void stillProducesAReportForACallWithNoClientLogsAtAll() {
        AnalysisOutcome outcome = analyze(SampleCalls.success(SampleCalls.SUCCESS_NO_CLIENT_LOGS));

        assertThat(outcome.report().verdict()).isEqualTo("UNKNOWN");
        assertThat(outcome.report().confidenceLevel()).isEqualTo("LOW");
        assertThat(outcome.report().dataLimitations())
                .contains("Missing caller_endcall.log", "Missing callee_endcall.log");
    }

    @Test
    void reportsUnknownInsteadOfCrashingWhenTheCallDirectoryDoesNotExist() {
        AnalysisOutcome outcome = analyze(java.nio.file.Path.of("sample-data", "success", "NO-SUCH-CALL"));

        assertThat(outcome.report().verdict()).isEqualTo("UNKNOWN");
        assertThat(outcome.processingWarnings()).isNotEmpty();
    }

    @Test
    void failsFastWhenAskedForASignalingSourceThatWasNeverWired() {
        SignalingSourceResolver resolver = new SignalingSourceResolver(Map.of());

        assertThat(NormalizedEvents.empty().isEmpty()).isTrue();
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> resolver.resolve(SignalingSourcePreference.INDEXED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INDEXED");
    }
}
