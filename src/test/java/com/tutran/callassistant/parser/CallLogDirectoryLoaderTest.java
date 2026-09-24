package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallLogDirectoryLoaderTest {

    private final CallLogDirectoryLoader loader = new CallLogDirectoryLoader();

    @Test
    void loadsAllRecognizedFilesIncludingOneWithAMisleadingFilename() {
        // success/EE129C8F.../ có cả "callee_webrtc.log" lẫn 1 file đặt sai tên bên cạnh
        // "calleer_webrtc.log" - cả hai đều phải nhận diện đúng là nội dung WEBRTC của
        // bên CALLEE, và signaling.json phải bị bỏ qua (xử lý qua ES thay vào đó).
        Path dir = Path.of("sample-data", "success", "EE129C8F-EAD0-4302-AB68-920D32F8B8B7");

        ParseResult result = loader.loadClientLogs(dir, "EE129C8F-EAD0-4302-AB68-920D32F8B8B7");

        assertThat(result.warnings()).isEmpty();
        assertThat(result.events()).isNotEmpty();
        assertThat(result.events()).anyMatch(e -> e.source() == EventSource.END_CALL && e.leg() == Leg.CALLEE);
        assertThat(result.events()).anyMatch(e -> e.source() == EventSource.END_CALL && e.leg() == Leg.CALLER);

        long webrtcCalleeEvents = result.events().stream()
                .filter(e -> e.source() == EventSource.WEBRTC && e.leg() == Leg.CALLEE)
                .count();
        assertThat(webrtcCalleeEvents).isGreaterThan(0);
    }

    @Test
    void skipsSignalingJsonAndReportsUnrecognizedFilesAsWarnings(@org.junit.jupiter.api.io.TempDir Path tempDir)
            throws Exception {
        java.nio.file.Files.writeString(tempDir.resolve("signaling.json"), "{\"callId\":\"X\",\"events\":[]}");
        java.nio.file.Files.writeString(tempDir.resolve("readme.txt"), "not a log file");

        ParseResult result = loader.loadClientLogs(tempDir, "CALL-1");

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("readme.txt"));
    }

    @Test
    void allCallSummaryRowsFromTheCallerFileAreAttributedToTheCallerLeg() {
        // kiểm tra cho chắc rằng loader này không tự đoán lại leg mà EndCallLogParser đã
        // suy ra từ nội dung (xem EndCallLogParserTest để có test chính thức). End-call
        // log thật ghi CALL_SUMMARY 2 lần mỗi bên (lúc bắt đầu và lúc kết thúc) - cả 2
        // dòng đều phải mang đúng cùng một leg.
        Path dir = Path.of("sample-data", "success", "DE7DD314-F432-45CB-BCB4-AE9103CC0919");

        ParseResult result = loader.loadClientLogs(dir, "DE7DD314-F432-45CB-BCB4-AE9103CC0919");

        List<CanonicalEvent> callSummaries = result.events().stream()
                .filter(e -> e.source() == EventSource.END_CALL && "CALL_SUMMARY".equals(e.eventType()))
                .toList();
        assertThat(callSummaries).hasSize(4); // 2 dòng x 2 bên
        assertThat(callSummaries).filteredOn(e -> e.sourceFile().contains("caller_endcall"))
                .allSatisfy(e -> assertThat(e.leg()).isEqualTo(Leg.CALLER));
        assertThat(callSummaries).filteredOn(e -> e.sourceFile().contains("callee_endcall"))
                .allSatisfy(e -> assertThat(e.leg()).isEqualTo(Leg.CALLEE));
    }
}
