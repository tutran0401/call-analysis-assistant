package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SignalingExportParserTest {

    private final SignalingExportParser parser = new SignalingExportParser();

    @Test
    void parsesRealSampleSignalingExport() {
        Path file = SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS).resolve("signaling.json");

        NormalizedEvents result = parser.parse(LogFile.of(file));

        assertThat(result.events()).hasSize(200);
        assertThat(result.warnings()).anyMatch(w -> w.contains("truncated"));
        assertThat(result.events()).allSatisfy(e -> {
            assertThat(e.leg()).isEqualTo(Leg.SERVER);
            assertThat(e.source()).isEqualTo(EventSource.SIGNALING);
            assertThat(e.callId()).isEqualTo(SampleCalls.SUCCESS_FULL_LOGS);
            assertThat(e.timestampConfidence()).isEqualTo(TimestampConfidence.EXACT);
        });

        CanonicalEvent first = result.events().get(0);
        assertThat(first.eventType()).isEqualTo("INIT_CALL");
        assertThat(first.timestamp()).isEqualTo(Instant.parse("2026-09-18T03:07:21.453318103Z"));
        assertThat(first.attribute("isp")).isEqualTo("MOBIFONE");
    }

    @Test
    void prefersTheCallIdGivenByTheCallerOverTheOneInsideTheFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("signaling.json");
        Files.writeString(file, """
                {
                  "callId": "STALE-ID-INSIDE-FILE",
                  "events": [ {"@timestamp": "2026-01-01T00:00:00Z", "cmd": "INIT_CALL"} ]
                }
                """);

        NormalizedEvents result = parser.parse(LogFile.of(file, "CALL-FROM-DIRECTORY"));

        assertThat(result.events()).singleElement()
                .satisfies(e -> assertThat(e.callId()).isEqualTo("CALL-FROM-DIRECTORY"));
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

        NormalizedEvents result = parser.parse(LogFile.of(file));

        assertThat(result.events()).hasSize(1);
        assertThat(result.warnings()).anyMatch(w -> w.contains("malformed signaling event"));
    }

    @Test
    void parsesNewLogsExportFormatIntoTheSameShapeAsTheOldOne(@TempDir Path tempDir) throws IOException {
        // Dạng export của new_dataset: logs[] + grpcMethod + field lồng (service.name, log.level, kubernetes).
        Path file = tempDir.resolve("signaling.json");
        Files.writeString(file, """
                {
                  "call_id": "06BCA48C-6861-8A9F-19E3-0923EAD71440",
                  "logs": [
                    {
                      "callId": "06BCA48C-6861-8A9F-19E3-0923EAD71440",
                      "kubernetes": {"container_name": "S46TQW3OJDW", "pod_name": "S46TQW3OJDW-5999cb47b8-cpgrp"},
                      "grpcMethod": "INIT_CALL",
                      "log": {"level": "INFO"},
                      "isp": "VIETTEL",
                      "message": "[IN][NATs][INIT_CALL][CALLER] Current dialog state: No dialog",
                      "@timestamp": "2026-10-13T07:50:17.803396571Z",
                      "service": {"name": "S46TQW3OJDW"},
                      "appUserId": "UHKMFKVGDIG"
                    }
                  ]
                }
                """);

        NormalizedEvents result = parser.parse(LogFile.of(file));

        assertThat(result.events()).singleElement().satisfies(e -> {
            assertThat(e.callId()).isEqualTo("06BCA48C-6861-8A9F-19E3-0923EAD71440");
            assertThat(e.eventType()).isEqualTo("INIT_CALL");
            assertThat(e.attribute("service")).isEqualTo("S46TQW3OJDW");
            assertThat(e.attribute("level")).isEqualTo("INFO");
            assertThat(e.attribute("appUserId")).isEqualTo("UHKMFKVGDIG");
            assertThat(e.attribute("grpcMethod")).isNull();
            assertThat(e.attribute("kubernetes")).isNull();
        });
    }

    @Test
    void oldFormatEventsPassThroughNormalizationUnchanged() {
        Path file = SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS).resolve("signaling.json");

        NormalizedEvents result = parser.parse(LogFile.of(file));

        assertThat(result.events().get(0).attribute("service")).isEqualTo("S46TQW3OJDW");
        assertThat(result.events().get(0).attribute("level")).isEqualTo("INFO");
    }

    @Test
    void missingEventsArrayProducesWarningNotCrash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("signaling.json");
        Files.writeString(file, "{\"callId\": \"CALL-1\"}");

        NormalizedEvents result = parser.parse(LogFile.of(file));

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
    }

    @Test
    void invalidJsonProducesWarningNotCrash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("signaling.json");
        Files.writeString(file, "{ this is not valid json");

        NormalizedEvents result = parser.parse(LogFile.of(file));

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
    }

    @Test
    void isDeliberatelyNotAClientLogParserSoDirectoryScansSkipIt() {
        // Signaling đi vào hệ thống qua SignalingEventSource; nếu parser này cũng là ClientLogParser
        // thì mọi sự kiện signaling sẽ bị đếm hai lần khi quét thư mục cuộc gọi.
        assertThat(parser).isNotInstanceOf(ClientLogParser.class);
        assertThat(parser.type()).isEqualTo(LogFileType.SIGNALING_EXPORT);
    }
}
