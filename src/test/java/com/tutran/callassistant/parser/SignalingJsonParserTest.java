package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SignalingJsonParserTest {

    private final SignalingJsonParser parser = new SignalingJsonParser();

    @Test
    void parsesRealSampleSignalingExport() {
        Path file = Path.of("sample-data", "success", "DE7DD314-F432-45CB-BCB4-AE9103CC0919", "signaling.json");

        ParseResult result = parser.parse(file, null);

        assertThat(result.events()).hasSize(200);
        assertThat(result.warnings()).anyMatch(w -> w.contains("truncated"));
        assertThat(result.events()).allSatisfy(e -> {
            assertThat(e.leg()).isEqualTo(Leg.SERVER);
            assertThat(e.source()).isEqualTo(EventSource.SIGNALING);
            assertThat(e.callId()).isEqualTo("DE7DD314-F432-45CB-BCB4-AE9103CC0919");
            assertThat(e.timestampConfidence()).isEqualTo(TimestampConfidence.EXACT);
        });

        CanonicalEvent first = result.events().get(0);
        assertThat(first.eventType()).isEqualTo("INIT_CALL");
        assertThat(first.timestamp()).isEqualTo(Instant.parse("2026-09-18T03:07:21.453318103Z"));
        assertThat(first.attribute("isp")).isEqualTo("MOBIFONE");
    }

    @Test
    void skipsEventsMissingTimestampAndReportsWarning(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("signaling.json");
        Files.writeString(file, """
                {
                  "callId": "CALL-1",
                  "events": [
                    {"@timestamp": "2026-01-01T00:00:00Z", "cmd": "INIT_CALL"},
                    {"cmd": "INVITE"}
                  ]
                }
                """);

        ParseResult result = parser.parse(file, null);

        assertThat(result.events()).hasSize(1);
        assertThat(result.warnings()).anyMatch(w -> w.contains("malformed signaling event"));
    }

    @Test
    void missingEventsArrayProducesWarningNotCrash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("signaling.json");
        Files.writeString(file, "{\"callId\": \"CALL-1\"}");

        ParseResult result = parser.parse(file, null);

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
    }

    @Test
    void invalidJsonProducesWarningNotCrash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("signaling.json");
        Files.writeString(file, "{ this is not valid json");

        ParseResult result = parser.parse(file, null);

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
    }
}
