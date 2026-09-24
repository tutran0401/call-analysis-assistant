package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WebRtcLogParserTest {

    private final WebRtcLogParser parser = new WebRtcLogParser();

    @Test
    void parsesRealFormat1SampleFile() {
        Path file = Path.of("sample-data", "success", "DE7DD314-F432-45CB-BCB4-AE9103CC0919", "caller_webrtc.log");

        ParseResult result = parser.parse(file, "DE7DD314-F432-45CB-BCB4-AE9103CC0919", Leg.CALLER);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.events()).hasSize(2473);
        assertThat(result.events()).allSatisfy(e -> {
            assertThat(e.timestampConfidence()).isEqualTo(TimestampConfidence.UNKNOWN);
            assertThat(e.timestamp()).isNull();
            assertThat(e.leg()).isEqualTo(Leg.CALLER);
        });
    }

    @Test
    void parsesRealFormat2SampleFile() {
        Path file = Path.of("sample-data", "fail", "703100CF-5742-467E-9E0E-34E45F60FF58", "caller_webrtc.log");

        ParseResult result = parser.parse(file, "703100CF-5742-467E-9E0E-34E45F60FF58", Leg.CALLER);

        assertThat(result.warnings()).isEmpty();
        assertThat(result.events()).hasSize(321);
        CanonicalEvent first = result.events().get(0);
        assertThat(first.attribute("sourceFile")).isEqualTo("peer_connection_factory.cc");
        assertThat(first.attribute("sourceLine")).isEqualTo("398");
        assertThat(first.attribute("message")).contains("Using default network controller factory");
        assertThat(first.rawTimestamp()).isEqualTo("0ms");
    }

    @Test
    void joinsMultiLineContinuationsIntoOneEvent(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("multiline.log");
        Files.writeString(file, String.join("\n",
                "[000:000][259] (RTCLogging.mm:34): Audio route changed:",
                "inputs = (",
                ");",
                "outputs = (",
                "    \"<AVAudioSessionPortDescription>\"",
                ")>",
                "[000:035][259] (RTCLogging.mm:34): Next event"
        ) + "\n");

        ParseResult result = parser.parse(file, "CALL-1", Leg.CALLEE);

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).attribute("message")).contains("Audio route changed:").contains("outputs = (");
        assertThat(result.events().get(1).attribute("message")).isEqualTo("Next event");
    }

    @Test
    void classifiesEngineCallbacksByTheirControlledVocabularyName(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("classify.log");
        Files.writeString(file, String.join("\n",
                "[000:000][1] (a.cc:1): onIceConnectionChange: FAILED",
                "[000:001][1] (a.cc:2): onConnectionChange: CONNECTED with action: 1",
                "[000:002][1] (a.cc:3): onIceCandidate: seq:1 with candidate: host",
                "[000:003][1] (a.cc:4): something unrelated happened"
        ) + "\n");

        ParseResult result = parser.parse(file, "CALL-1", Leg.CALLER);

        assertThat(result.events().get(0).eventType()).isEqualTo("ICE_CONNECTION_STATE_CHANGE");
        assertThat(result.events().get(1).eventType()).isEqualTo("PEER_CONNECTION_STATE_CHANGE");
        assertThat(result.events().get(2).eventType()).isEqualTo("ICE_CANDIDATE");
        assertThat(result.events().get(3).eventType()).isEqualTo("ENGINE_LOG");
    }

    @Test
    void doesNotMisclassifyRoutineTurnProtocolNoiseAsAFailure(@TempDir Path tempDir) throws IOException {
        // Phát hiện được nhờ test end-to-end thật trên một cuộc gọi SUCCESS sạch: so khớp
        // từ khóa tự do "turn"/"ice" + "error"/"fail" báo nhầm hàng chục phản hồi giao
        // thức TURN bình thường, tự phục hồi được theo từng candidate (thử thách
        // credential dài hạn code=401, và lỗi permission code=400 trên các candidate pair
        // đơn giản bị bỏ) thành lỗi thật. Giờ chỉ tin vào đúng callback trạng thái kết
        // thúc ICE_CONNECTION_STATE_CHANGE của engine cho tín hiệu lỗi.
        Path file = tempDir.resolve("turn_noise.log");
        Files.writeString(file, String.join("\n",
                "[000:101][1] (turn_port.cc:1687): TurnPort(...): Received TURN probe error "
                        + "response, id=6e344f7a, code=401, rtt=35081 us",
                "[000:102][1] (turn_port.cc:2069): TurnPort(...): Received TURN create permission "
                        + "error response, id=abc123, code=400, rtt=1000 us"
        ) + "\n");

        ParseResult result = parser.parse(file, "CALL-1", Leg.CALLER);

        assertThat(result.events()).allSatisfy(e -> assertThat(e.eventType()).isEqualTo("ENGINE_LOG"));
    }

    @Test
    void emptyOrUnrecognizableFileProducesWarningNotCrash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("garbage.log");
        Files.writeString(file, "this is not a webrtc log line at all\n");

        ParseResult result = parser.parse(file, "CALL-1", Leg.CALLER);

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
    }
}
