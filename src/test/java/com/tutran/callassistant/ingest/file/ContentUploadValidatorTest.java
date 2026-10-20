package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.application.validation.UploadedFile;
import com.tutran.callassistant.application.validation.ValidatedUpload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** F01-F04 của PROJECT_SPEC.md mục 6.4, bằng nội dung log tổng hợp (data mẫu không nằm trong git). */
class ContentUploadValidatorTest {

    private static final String CALL_A = "AAAAAAAA-1111-2222-3333-444444444444";
    private static final String CALL_B = "BBBBBBBB-1111-2222-3333-444444444444";

    private final ContentUploadValidator validator =
            new ContentUploadValidator(ContentBasedLogFileTypeDetector.withDefaults(), 1024 * 1024);

    private static UploadedFile endCall(String name, String callId) {
        String text = "#H1\t#ts\t#tag\tappUserId\tcallId\trole\n"
                + "1\t1000\tinfo\tU1\t" + callId + "\tcaller\n";
        return new UploadedFile(name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static UploadedFile webRtc(String name) {
        return new UploadedFile(name,
                "[000:000][259] (RTCLogging.mm:34): hello\n".getBytes(StandardCharsets.UTF_8));
    }

    private static UploadedFile signaling(String callId) {
        return new UploadedFile("signaling.json",
                ("{\n  \"callId\": \"" + callId + "\",\n  \"events\": []\n}").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void completeUploadHasNoLimitationsAndTakesCallIdFromContent() {
        try (ValidatedUpload result = validator.validate(List.of(
                endCall("caller_endcall.log", CALL_A), webRtc("caller_webrtc.log"),
                webRtc("callee_webrtc.log")), null)) {

            assertThat(result.callId()).isEqualTo(CALL_A);
            assertThat(result.accepted()).hasSize(3);
            assertThat(result.rejected()).isEmpty();
            assertThat(result.limitations()).isEmpty();
        }
    }

    @Test
    void f01_missingWebRtcOfOneSideIsReported() {
        try (ValidatedUpload result = validator.validate(List.of(
                endCall("caller_endcall.log", CALL_A), webRtc("caller_webrtc.log")), null)) {

            assertThat(result.limitations()).containsExactly("Thiếu callee_webrtc.log");
        }
    }

    @Test
    void f02_fileNameThatContradictsContentIsAnalysedByContent() {
        try (ValidatedUpload result = validator.validate(List.of(
                endCall("caller_webrtc.log", CALL_A), webRtc("callee_webrtc.log")), null)) {

            assertThat(result.accepted()).hasSize(2);
            assertThat(result.limitations())
                    .anyMatch(l -> l.contains("caller_webrtc.log") && l.contains("end-call log"));
        }
    }

    @Test
    void f03_fileOfAnotherCallIsRejected() {
        try (ValidatedUpload result = validator.validate(List.of(
                endCall("caller_endcall.log", CALL_A), endCall("callee_endcall.log", CALL_B),
                endCall("extra_endcall.log", CALL_A)), null)) {

            assertThat(result.callId()).isEqualTo(CALL_A);
            assertThat(result.rejected()).singleElement()
                    .satisfies(r -> assertThat(r.name()).isEqualTo("callee_endcall.log"));
            assertThat(result.limitations()).anyMatch(l -> l.contains("callee_endcall.log") && l.contains(CALL_B));
            assertThat(result.directory().toFile().list()).hasSize(2);
        }
    }

    @Test
    void mixedCallUploadQuarantinesUnverifiableWebRtcFiles() {
        try (ValidatedUpload result = validator.validate(List.of(
                endCall("caller_endcall.log", CALL_A),
                endCall("callee_endcall.log", CALL_B),
                webRtc("callee_webrtc.log")), CALL_A)) {
            assertThat(result.accepted()).containsExactly("caller_endcall.log");
            assertThat(result.rejected()).extracting(r -> r.name())
                    .containsExactlyInAnyOrder("callee_endcall.log", "callee_webrtc.log");
            assertThat(result.limitations()).anyMatch(l -> l.contains("không xác minh được Call-ID"));
        }
    }

    @Test
    void callIdWrittenInTheQuestionOverridesMajority() {
        try (ValidatedUpload result = validator.validate(List.of(
                endCall("a_endcall.log", CALL_A), endCall("b_endcall.log", CALL_A)), CALL_B)) {

            assertThat(result.callId()).isEqualTo(CALL_B);
            assertThat(result.accepted()).isEmpty();
            assertThat(result.limitations()).contains("Không có file log hợp lệ nào để phân tích");
        }
    }

    @Test
    void f04_emptyBinaryOversizedAndUnrecognisedFilesAreRejectedWithoutThrowing() {
        byte[] binary = new byte[100];
        Arrays.fill(binary, (byte) 1);
        binary[10] = 0;
        byte[] huge = new byte[2 * 1024 * 1024];
        Arrays.fill(huge, (byte) 'a');

        try (ValidatedUpload result = validator.validate(List.of(
                new UploadedFile("empty.log", new byte[0]),
                new UploadedFile("bin.log", binary),
                new UploadedFile("huge.log", huge),
                new UploadedFile("notes.txt", "xin chao\n".getBytes(StandardCharsets.UTF_8)),
                new UploadedFile("bad_utf8.log", new byte[]{(byte) 0xC3, (byte) 0x28, 'x'}),
                endCall("caller_endcall.log", CALL_A)), null)) {

            assertThat(result.accepted()).hasSize(1);
            assertThat(result.rejected()).extracting(r -> r.name())
                    .containsExactlyInAnyOrder("empty.log", "bin.log", "huge.log", "notes.txt", "bad_utf8.log");
            assertThat(result.callId()).isEqualTo(CALL_A);
        }
    }

    @Test
    void signalingExportIsIgnoredButStillCountsForCallId() {
        try (ValidatedUpload result = validator.validate(List.of(
                signaling(CALL_A), webRtc("caller_webrtc.log"), webRtc("callee_webrtc.log")), null)) {

            assertThat(result.callId()).isEqualTo(CALL_A);
            assertThat(result.rejected()).singleElement()
                    .satisfies(r -> assertThat(r.reason()).contains("Elasticsearch"));
        }
    }

    @Test
    void noCallIdAnywhereYieldsUnknownCallId() {
        try (ValidatedUpload result = validator.validate(List.of(webRtc("caller_webrtc.log")), null)) {

            assertThat(result.hasCallId()).isFalse();
            assertThat(result.limitations()).contains("Không xác định được Call-ID từ file đính kèm hoặc câu hỏi");
        }
    }

    @Test
    void closeRemovesTheWorkspace() {
        ValidatedUpload result = validator.validate(List.of(endCall("caller_endcall.log", CALL_A)), null);
        assertThat(result.directory()).exists();
        result.close();
        assertThat(result.directory()).doesNotExist();
    }
}
