package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryClientLogSourceTest {

    private final DirectoryClientLogSource source = DirectoryClientLogSource.withDefaults();

    @Test
    void loadsAllRecognizedFilesIncludingOneWithAMisleadingFilename() {
        // success/EE129C8F.../ có cả "callee_webrtc.log" lẫn 1 file đặt sai tên bên cạnh
        // "calleer_webrtc.log" - cả hai đều phải nhận diện đúng là nội dung WEBRTC của bên CALLEE,
        // và signaling.json phải bị bỏ qua (xử lý qua SignalingEventSource thay vào đó).
        CallReference call = CallReference.ofDirectory(SampleCalls.success(SampleCalls.SUCCESS_MISNAMED_FILE));

        NormalizedEvents result = source.load(call);

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
    void neverEmitsSignalingEventsSoTheyCannotBeCountedTwice() {
        CallReference call = CallReference.ofDirectory(SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS));

        NormalizedEvents result = source.load(call);

        assertThat(result.events()).noneMatch(e -> e.source() == EventSource.SIGNALING);
    }

    @Test
    void skipsSignalingJsonSilentlyAndReportsUnrecognizedFilesAsWarnings(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("signaling.json"), "{\"callId\":\"X\",\"events\":[]}");
        Files.writeString(tempDir.resolve("readme.txt"), "not a log file");

        NormalizedEvents result = source.load(CallReference.ofDirectory(tempDir));

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("readme.txt"));
        assertThat(result.warnings()).noneMatch(w -> w.contains("signaling.json"));
    }

    @Test
    void unreadableDirectoryYieldsNothingInsteadOfThrowing() {
        CallReference missing = CallReference.ofDirectory(Path.of("does", "not", "exist"));

        NormalizedEvents result = source.load(missing);

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void allCallSummaryRowsFromTheCallerFileAreAttributedToTheCallerLeg() {
        // kiểm tra cho chắc rằng loader này không tự đoán lại leg mà EndCallLogParser đã suy ra từ
        // nội dung (xem EndCallLogParserTest để có test chính thức). End-call log thật ghi
        // CALL_SUMMARY 2 lần mỗi bên (lúc bắt đầu và lúc kết thúc) - cả 2 dòng đều phải mang đúng
        // cùng một leg.
        CallReference call = CallReference.ofDirectory(SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS));

        NormalizedEvents result = source.load(call);

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
