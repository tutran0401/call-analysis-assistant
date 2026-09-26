package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WebRtcLogParserTest {

    private final WebRtcLogParser parser = new WebRtcLogParser();

    @Test
    void parsesRealIosFormatSampleFile() {
        Path file = SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS).resolve("caller_webrtc.log");

        NormalizedEvents result = parser.parse(
                LogFile.of(file, SampleCalls.SUCCESS_FULL_LOGS, Leg.CALLER));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.events()).hasSize(2473);
        assertThat(result.events()).allSatisfy(e -> {
            assertThat(e.timestampConfidence()).isEqualTo(TimestampConfidence.UNKNOWN);
            assertThat(e.timestamp()).isNull();
            assertThat(e.leg()).isEqualTo(Leg.CALLER);
        });
    }

    @Test
    void parsesRealNativeFormatSampleFile() {
        Path file = SampleCalls.fail(SampleCalls.FAIL_CANCELLED_BEFORE_INVITE).resolve("caller_webrtc.log");

        NormalizedEvents result = parser.parse(
                LogFile.of(file, SampleCalls.FAIL_CANCELLED_BEFORE_INVITE, Leg.CALLER));

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

        NormalizedEvents result = parser.parse(LogFile.of(file, "CALL-1", Leg.CALLEE));

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).attribute("message"))
                .contains("Audio route changed:").contains("outputs = (");
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

        NormalizedEvents result = parser.parse(LogFile.of(file, "CALL-1", Leg.CALLER));

        assertThat(result.events().get(0).eventType()).isEqualTo("ICE_CONNECTION_STATE_CHANGE");
        assertThat(result.events().get(1).eventType()).isEqualTo("PEER_CONNECTION_STATE_CHANGE");
        assertThat(result.events().get(2).eventType()).isEqualTo("ICE_CANDIDATE");
        assertThat(result.events().get(3).eventType()).isEqualTo("ENGINE_LOG");
    }

    @Test
    void doesNotMisclassifyRoutineTurnProtocolNoiseAsAFailure(@TempDir Path tempDir) throws IOException {
        // Phát hiện được nhờ test end-to-end thật trên một cuộc gọi SUCCESS sạch: so khớp từ khóa tự
        // do "turn"/"ice" + "error"/"fail" báo nhầm hàng chục phản hồi giao thức TURN bình thường, tự
        // phục hồi được theo từng candidate (thử thách credential dài hạn code=401, và lỗi permission
        // code=400 trên các candidate pair đơn giản bị bỏ) thành lỗi thật. Giờ chỉ tin vào đúng
        // callback trạng thái kết thúc ICE_CONNECTION_STATE_CHANGE của engine cho tín hiệu lỗi.
        Path file = tempDir.resolve("turn_noise.log");
        Files.writeString(file, String.join("\n",
                "[000:101][1] (turn_port.cc:1687): TurnPort(...): Received TURN probe error "
                        + "response, id=6e344f7a, code=401, rtt=35081 us",
                "[000:102][1] (turn_port.cc:2069): TurnPort(...): Received TURN create permission "
                        + "error response, id=abc123, code=400, rtt=1000 us"
        ) + "\n");

        NormalizedEvents result = parser.parse(LogFile.of(file, "CALL-1", Leg.CALLER));

        assertThat(result.events()).allSatisfy(e -> assertThat(e.eventType()).isEqualTo("ENGINE_LOG"));
    }

    @Test
    void emptyOrUnrecognizableFileProducesWarningNotCrash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("garbage.log");
        Files.writeString(file, "this is not a webrtc log line at all\n");

        NormalizedEvents result = parser.parse(LogFile.of(file, "CALL-1", Leg.CALLER));

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
    }

    @Test
    void classifiesNativeEngineIceStateLinesWhichUseAnEntirelyDifferentWording(@TempDir Path tempDir)
            throws IOException {
        // Bản iOS gọi callback theo tên (onIceConnectionChange); bản native peer_connection.cc lại ghi
        // thành câu kể "Changing IceConnectionState X => Y", kèm một dòng song song có chữ
        // "standardized". Trước đây chỉ dạng đầu được nhận ra, nên 16/28 file webrtc của data mẫu CÓ
        // ghi trạng thái ICE mà hệ thống thấy 0 sự kiện.
        Path file = tempDir.resolve("native_ice.log");
        Files.writeString(file, String.join("\n",
                "peer_connection.cc: [004:592][8431] (line 2032): Changing IceConnectionState new => checking",
                "peer_connection.cc: [005:191][8431] (line 2054): Changing standardized IceConnectionState new => checking",
                "peer_connection.cc: [020:195][8431] (line 2032): Changing IceConnectionState checking => failed"
        ) + "\n");

        NormalizedEvents result = parser.parse(LogFile.of(file, "CALL-1", Leg.CALLEE));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.events()).hasSize(3);
        assertThat(result.events()).allSatisfy(e ->
                assertThat(e.eventType()).isEqualTo("ICE_CONNECTION_STATE_CHANGE"));
    }

    @Test
    void recognizesBothEngineLogFormats() {
        assertThat(parser.type()).isEqualTo(LogFileType.WEBRTC);
        assertThat(parser.recognizes("[000:000][259] (RTCLogging.mm:34): hello")).isTrue();
        assertThat(parser.recognizes("peer_connection_factory.cc: [000:000][1] (line 398): hello")).isTrue();
        assertThat(parser.recognizes("#H1\t#ts\t#tag\trole")).isFalse();
    }
}
