package com.tutran.callassistant.sanitize;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.tutran.callassistant.domain.report.EvidenceItem;
import com.tutran.callassistant.domain.report.MetricRow;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.sanitize.logging.SanitizingMessageConverter;
import com.tutran.callassistant.sanitize.logging.SanitizingThrowableConverter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** S07-S08 (output) của PROJECT_SPEC.md mục 6.4. */
class OutputSanitizerTest {

    private final OutputSanitizer sanitizer =
            new OutputSanitizer(new SensitiveDataSanitizer(new Pseudonymizer("test-salt")));

    private Report reportWith(String summary, String evidence, String suggestion) {
        return new Report("CALL-1", "FAIL", false, "ICE_FAILURE", List.of("alt 0987654321"), "MEDIUM", summary,
                List.of(new EvidenceItem("EV001", "callee_webrtc.log", "10:00:00", evidence)),
                List.of(new MetricRow("MOS", "2.9", "End Call")),
                List.of(suggestion), List.of("limit user_id=555"));
    }

    @Test
    void s07_aiResponseRepeatingSensitiveValuesIsCleaned() {
        Report dirty = reportWith("Callee ở 113.161.45.67 gọi từ 0987654321",
                "ICE failed tới turn:14.238.152.70:3478", "Liên hệ nguyen@example.com, device_id=ABC123");

        Report clean = sanitizer.sanitize(dirty);

        String all = clean.summary() + clean.evidence().get(0).description() + clean.suggestions()
                + clean.alternativeCauses() + clean.dataLimitations();
        assertThat(all).doesNotContain("113.161.45.67").doesNotContain("0987654321")
                .doesNotContain("14.238.152.70").doesNotContain("nguyen@example.com")
                .doesNotContain("ABC123").doesNotContain("=555");
        assertThat(sanitizer.countResidual(all)).isZero();
    }

    @Test
    void reportStructureAndNonSensitiveValuesSurviveUnchanged() {
        Report original = reportWith("Cuộc gọi thất bại do ICE", "ICE checking => failed", "Kiểm tra mạng");

        Report clean = sanitizer.sanitize(original);

        assertThat(clean.callId()).isEqualTo("CALL-1");
        assertThat(clean.verdict()).isEqualTo("FAIL");
        assertThat(clean.evidence().get(0).id()).isEqualTo("EV001");
        assertThat(clean.metrics()).containsExactly(new MetricRow("MOS", "2.9", "End Call"));
        assertThat(clean.summary()).isEqualTo(original.summary());
    }

    @Test
    void s08_logMessageIsSanitizedByTheLogbackConverter() {
        Logger logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("test");
        LoggingEvent event = new LoggingEvent("fqcn", logger, Level.ERROR,
                "AI call failed: Authorization: Bearer abcdefgh12345678 for user_id=777 from 10.1.2.3", null, null);

        String line = new SanitizingMessageConverter().convert(event);

        assertThat(line).doesNotContain("abcdefgh12345678").doesNotContain("=777").doesNotContain("10.1.2.3");
        assertThat(line).contains("Authorization: [REDACTED]").contains("[IP_REDACTED]");
    }

    @Test
    void s08_exceptionMessageInStackTraceIsSanitized() {
        Logger logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("test");
        LoggingEvent event = new LoggingEvent("fqcn", logger, Level.ERROR, "boom",
                new IllegalStateException("401 from provider, api_key=sk-proj-abcdefghijklmnop1234"), null);

        String trace = new SanitizingThrowableConverter().convert(event);

        assertThat(trace).doesNotContain("sk-proj-abcdefghijklmnop1234").contains("IllegalStateException");
    }
}
