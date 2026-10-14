package com.tutran.callassistant.sanitize.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sprint 3 T5 + S08: log JSON có request_id, đọc được bằng máy, và không chứa giá trị nhạy cảm dạng gốc. */
class SanitizingJsonLayoutTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";

    private final SanitizingJsonLayout layout = new SanitizingJsonLayout();

    private LoggingEvent event(String message, Throwable error) {
        LoggerContext context = new LoggerContext();
        LoggingEvent event = new LoggingEvent("x", context.getLogger("test.Logger"), Level.INFO, message, error, null);
        event.setMDCPropertyMap(Map.of(SanitizingJsonLayout.REQUEST_ID_KEY, "req-1234abcd"));
        return event;
    }

    @Test
    void writesOneJsonObjectPerLineWithTheRequestId() throws Exception {
        layout.start();

        String line = layout.doLayout(event("ai_call model=gpt latency_ms=12", null));

        assertThat(line).endsWith(System.lineSeparator()).doesNotContain("\n{");
        JsonNode json = new ObjectMapper().readTree(line);
        assertThat(json.get("request_id").asText()).isEqualTo("req-1234abcd");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("message").asText()).isEqualTo("ai_call model=gpt latency_ms=12");
        assertThat(json.has("error")).isFalse();
    }

    @Test
    void messageAndStackTraceAreSanitized() throws Exception {
        layout.start();

        String line = layout.doLayout(event("token=" + JWT + " phone=0987654321",
                new IllegalStateException("Authorization: Bearer " + JWT)));

        assertThat(line).doesNotContain(JWT).doesNotContain("0987654321");
        JsonNode json = new ObjectMapper().readTree(line);
        assertThat(json.get("error").asText()).contains("IllegalStateException");
    }
}
