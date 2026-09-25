package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EndCallLogParserTest {

    private static final String CALL_ID = SampleCalls.SUCCESS_FULL_LOGS;
    private static final Path SAMPLE_FILE =
            SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS).resolve("caller_endcall.log");

    private final EndCallLogParser parser = new EndCallLogParser(new EndCallSchemaClassifier());

    @Test
    void parsesRealSampleFileWithoutWarnings() {
        NormalizedEvents result = parser.parse(LogFile.of(SAMPLE_FILE, CALL_ID));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.events()).hasSize(599);
    }

    @Test
    void infersLegFromCallSummaryRoleField() {
        NormalizedEvents result = parser.parse(LogFile.of(SAMPLE_FILE, CALL_ID));

        assertThat(result.events()).isNotEmpty();
        assertThat(result.events()).allSatisfy(e -> assertThat(e.leg()).isEqualTo(Leg.CALLER));
    }

    @Test
    void mapsCallSummaryRowFieldsByHeaderSchema() {
        NormalizedEvents result = parser.parse(LogFile.of(SAMPLE_FILE, CALL_ID));

        CanonicalEvent callSummary = result.events().stream()
                .filter(e -> "CALL_SUMMARY".equals(e.eventType()))
                .findFirst()
                .orElseThrow();

        assertThat(callSummary.attribute("appUserId")).isEqualTo("UTGMJUPAY7W");
        assertThat(callSummary.attribute("callId")).isEqualTo(CALL_ID);
        assertThat(callSummary.attribute("platform")).isEqualTo("ios");
        assertThat(callSummary.attribute("role")).isEqualTo("caller");
        assertThat(callSummary.attribute("status")).isEqualTo("INIT");
        assertThat(callSummary.timestamp().toEpochMilli()).isEqualTo(1789700841389L);
    }

    @Test
    void doesNotCrashOnMalformedRowsAndReportsThemAsWarnings(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("malformed_endcall.log");
        Files.writeString(file, String.join("\n",
                "#H1\t#ts\t#tag\tappUserId\tcallId\tcallMode\tcallType\tcallUserId\tduration\tend\tfromTag\tpartnerAppUserId\tpartnerCallUserId\tplatform\trole\tsessionId\tstatus\ttoTag\tversion",
                "1\t1789700841389\tinfo\tUSER1\tCALL-1\tDIRECT\taudio\tCU1\t0\t0\tFT\tPU1\tPC1\tios\tcaller\tS1\tINIT\tT1\t0.3.24",
                "not-a-number\tbroken-row-should-be-skipped",
                "1\t1789700841999\tinfo\tUSER1\tCALL-1\tDIRECT\taudio\tCU1\t5\t0\tFT\tPU1\tPC1\tios\tcaller\tS1\tTERMINATED\tT1\t0.3.24"
        ) + "\n");

        NormalizedEvents result = parser.parse(LogFile.of(file, "CALL-1"));

        assertThat(result.events()).hasSize(2);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("malformed row");
    }

    @Test
    void unknownSchemaIdFallsBackToGenericFieldNamesInsteadOfCrashing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("unknown_schema.log");
        Files.writeString(file, "42\t1789700841389\tinfo\tsome-value\tanother-value\n");

        NormalizedEvents result = parser.parse(LogFile.of(file, "CALL-1"));

        assertThat(result.events()).hasSize(1);
        CanonicalEvent event = result.events().get(0);
        assertThat(event.eventType()).isEqualTo("UNKNOWN_SCHEMA_42");
        assertThat(event.attribute("field_0")).isEqualTo("some-value");
        assertThat(event.attribute("field_1")).isEqualTo("another-value");
    }

    @Test
    void fallsBackToFilenameWhenNoCallSummaryRoleIsPresent(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("callee_endcall.log");
        Files.writeString(file, String.join("\n",
                "#H5\t#ts\t#tag\tmsg\toriginator\tsignal",
                "5\t1789700841389\t\t\tlocal\tuser_accept_call"
        ) + "\n");

        NormalizedEvents result = parser.parse(LogFile.inDirectory(file, "CALL-1"));

        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).leg()).isEqualTo(Leg.CALLEE);
    }

    @Test
    void recognizesOnlyTheEndCallHeaderFormat() {
        assertThat(parser.type()).isEqualTo(LogFileType.END_CALL);
        assertThat(parser.recognizes("#H1\t#ts\t#tag\trole\tcallMode")).isTrue();
        assertThat(parser.recognizes("[000:000][1] (a.cc:1): hello")).isFalse();
        assertThat(parser.recognizes("{\"callId\": \"X\"}")).isFalse();
    }
}
