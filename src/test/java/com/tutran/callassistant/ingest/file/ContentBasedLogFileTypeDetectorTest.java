package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContentBasedLogFileTypeDetectorTest {

    private final LogFileTypeDetector detector = ContentBasedLogFileTypeDetector.withDefaults();

    @Test
    void detectsRealSignalingExport() {
        Path file = SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS).resolve("signaling.json");

        assertThat(detector.detect(file)).isEqualTo(LogFileType.SIGNALING_EXPORT);
    }

    @Test
    void detectsRealEndCallLog() {
        Path file = SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS).resolve("caller_endcall.log");

        assertThat(detector.detect(file)).isEqualTo(LogFileType.END_CALL);
    }

    @Test
    void detectsRealWebRtcLogInIosFormat() {
        Path file = SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS).resolve("caller_webrtc.log");

        assertThat(detector.detect(file)).isEqualTo(LogFileType.WEBRTC);
    }

    @Test
    void detectsRealWebRtcLogInNativeFormat() {
        Path file = SampleCalls.fail(SampleCalls.FAIL_CANCELLED_BEFORE_INVITE).resolve("caller_webrtc.log");

        assertThat(detector.detect(file)).isEqualTo(LogFileType.WEBRTC);
    }

    @Test
    void detectsWebRtcLogRegardlessOfMisleadingFilename(@TempDir Path tempDir) throws IOException {
        Path misnamed = tempDir.resolve("this_looks_like_endcall.log");
        Files.writeString(misnamed, "[000:000][1] (a.cc:1): hello\n");

        assertThat(detector.detect(misnamed)).isEqualTo(LogFileType.WEBRTC);
    }

    @Test
    void skipsLeadingBlankLinesBeforeDeciding(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("leading_blanks.log");
        Files.writeString(file, "\n\n   \n[000:000][1] (a.cc:1): hello\n");

        assertThat(detector.detect(file)).isEqualTo(LogFileType.WEBRTC);
    }

    @Test
    void unknownContentReturnsUnknownInsteadOfThrowing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("random.txt");
        Files.writeString(file, "just some random text\nwith multiple lines\n");

        assertThat(detector.detect(file)).isEqualTo(LogFileType.UNKNOWN);
    }

    @Test
    void missingFileReturnsUnknownInsteadOfThrowing() {
        assertThat(detector.detect(Path.of("does", "not", "exist.log"))).isEqualTo(LogFileType.UNKNOWN);
    }
}
