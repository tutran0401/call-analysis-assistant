package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.Leg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndCallLogParserTest {

    private static final Path SAMPLE_FILE =
            Path.of("success", "DE7DD314-F432-45CB-BCB4-AE9103CC0919", "caller_endcall.log");

    private final EndCallLogParser parser = new EndCallLogParser();

    @Test
    void parsesRealSampleFileWithoutWarnings() {
        ParseResult result = parser.parse(SAMPLE_FILE, "DE7DD314-F432-45CB-BCB4-AE9103CC0919");

        assertThat(result.warnings()).isEmpty();
        assertThat(result.events()).hasSize(599);
    }

    @Test
    void infersLegFromCallSummaryRoleField() {
        ParseResult result = parser.parse(SAMPLE_FILE, "DE7DD314-F432-45CB-BCB4-AE9103CC0919");

        assertThat(result.events()).isNotEmpty();
        assertThat(result.events()).allSatisfy(e -> assertThat(e.leg()).isEqualTo(Leg.CALLER));
    }

    @Test
    void mapsCallSummaryRowFieldsByHeaderSchema() {
        ParseResult result = parser.parse(SAMPLE_FILE, "DE7DD314-F432-45CB-BCB4-AE9103CC0919");

        CanonicalEvent callSummary = result.events().stream()
                .filter(e -> "CALL_SUMMARY".equals(e.eventType()))
                .findFirst()
                .orElseThrow();

        assertThat(callSummary.attribute("appUserId")).isEqualTo("UTGMJUPAY7W");
        assertThat(callSummary.attribute("callId")).isEqualTo("DE7DD314-F432-45CB-BCB4-AE9103CC0919");
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

        ParseResult result = parser.parse(file, "CALL-1");

        assertThat(result.events()).hasSize(2);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("malformed row");
    }

    @Test
    void unknownSchemaIdFallsBackToGenericFieldNamesInsteadOfCrashing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("unknown_schema.log");
        Files.writeString(file, "42\t1789700841389\tinfo\tsome-value\tanother-value\n");

        ParseResult result = parser.parse(file, "CALL-1");

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

        ParseResult result = parser.parse(file, "CALL-1");

        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).leg()).isEqualTo(Leg.CALLEE);
    }
}
