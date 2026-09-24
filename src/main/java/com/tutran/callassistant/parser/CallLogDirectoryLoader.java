package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.Leg;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Quét thư mục của một cuộc gọi và chuẩn hoá mọi file log nhận diện được, dùng
 * {@link FileTypeDetector} để xác định loại file theo nội dung - nhờ vậy một file đặt
 * sai tên (bản thân data mẫu có 1 file như thế: {@code calleer_webrtc.log}) vẫn được
 * parse đúng. Leg của end-call log lấy từ chính dòng CALL_SUMMARY của nó nếu có (xem
 * {@link EndCallLogParser}); log engine WebRTC thì hoàn toàn không có tín hiệu này trong
 * nội dung, nên leg của nó phải suy ra từ tên file như phương án cuối cùng.
 */
public final class CallLogDirectoryLoader {

    private final EndCallLogParser endCallLogParser = new EndCallLogParser();
    private final WebRtcLogParser webRtcLogParser = new WebRtcLogParser();

    /** Parse mọi end-call log và WebRTC log trong {@code dir}. File signaling bị bỏ qua (xem query ES ở nơi khác). */
    public ParseResult loadClientLogs(Path dir, String callId) {
        List<Path> files = listFiles(dir);
        List<com.tutran.callassistant.domain.CanonicalEvent> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Path file : files) {
            LogFileType type = FileTypeDetector.detect(file);
            ParseResult result = switch (type) {
                case END_CALL -> endCallLogParser.parse(file, callId);
                case WEBRTC -> webRtcLogParser.parse(file, callId, inferLegFromFilename(file));
                case SIGNALING_EXPORT -> ParseResult.empty();
                case UNKNOWN -> new ParseResult(List.of(),
                        List.of("Unrecognized file type, skipped: " + file));
            };
            events.addAll(result.events());
            warnings.addAll(result.warnings());
        }
        return new ParseResult(events, warnings);
    }

    private List<Path> listFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        } catch (IOException e) {
            // thư mục không đọc được thì trả về danh sách rỗng; nơi gọi sẽ thấy điều này
            // phản ánh thành thiếu dữ liệu (ví dụ RuleVerdictEngine báo "missing end-call log").
        }
        return files;
    }

    private Leg inferLegFromFilename(Path file) {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.contains("callee")) {
            return Leg.CALLEE;
        }
        if (lower.contains("caller")) {
            return Leg.CALLER;
        }
        return Leg.UNKNOWN;
    }
}
