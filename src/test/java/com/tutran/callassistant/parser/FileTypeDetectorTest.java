package com.tutran.callassistant.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeDetectorTest {

    @Test
    void detectsRealSignalingExport() {
        assertThat(FileTypeDetector.detect(Path.of("sample-data", "success", "DE7DD314-F432-45CB-BCB4-AE9103CC0919", "signaling.json")))
                .isEqualTo(LogFileType.SIGNALING_EXPORT);
    }

    @Test
    void detectsRealEndCallLog() {
        assertThat(FileTypeDetector.detect(Path.of("sample-data", "success", "DE7DD314-F432-45CB-BCB4-AE9103CC0919", "caller_endcall.log")))
                .isEqualTo(LogFileType.END_CALL);
    }

    @Test
    void detectsRealWebRtcLogFormat1() {
        assertThat(FileTypeDetector.detect(Path.of("sample-data", "success", "DE7DD314-F432-45CB-BCB4-AE9103CC0919", "caller_webrtc.log")))
                .isEqualTo(LogFileType.WEBRTC);
    }

    @Test
    void detectsRealWebRtcLogFormat2() {
        assertThat(FileTypeDetector.detect(Path.of("sample-data", "fail", "703100CF-5742-467E-9E0E-34E45F60FF58", "caller_webrtc.log")))
                .isEqualTo(LogFileType.WEBRTC);
    }

    @Test
    void detectsWebRtcLogRegardlessOfMisleadingFilename(@TempDir Path tempDir) throws IOException {
        Path misnamed = tempDir.resolve("this_looks_like_endcall.log");
        Files.writeString(misnamed, "[000:000][1] (a.cc:1): hello\n");

        assertThat(FileTypeDetector.detect(misnamed)).isEqualTo(LogFileType.WEBRTC);
    }

    @Test
    void unknownContentReturnsUnknownInsteadOfThrowing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("random.txt");
        Files.writeString(file, "just some random text\nwith multiple lines\n");

        assertThat(FileTypeDetector.detect(file)).isEqualTo(LogFileType.UNKNOWN);
    }

    @Test
    void missingFileReturnsUnknownInsteadOfThrowing() {
        assertThat(FileTypeDetector.detect(Path.of("does", "not", "exist.log"))).isEqualTo(LogFileType.UNKNOWN);
    }
}
