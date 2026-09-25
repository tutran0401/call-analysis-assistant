package com.tutran.callassistant.ingest.file;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Nhận diện loại file bằng cách hỏi lần lượt từng {@link LogParser} xem nó có nhận ra nội dung
 * không (Chain of Responsibility).
 *
 * <p>Bộ nhận diện này không biết gì về định dạng cụ thể nào - toàn bộ hiểu biết về định dạng nằm
 * trong chính parser của định dạng đó. Đây là điểm khác quan trọng so với bản trước: khi đó các
 * biểu thức nhận diện nằm ở đây, còn biểu thức để parse nằm trong parser, nên thêm một định dạng
 * phải sửa hai chỗ và hai chỗ đó có thể lệch nhau.
 *
 * <p>Xem tối đa {@value #LINES_TO_INSPECT} dòng có nội dung đầu file: một số file log thật mở đầu
 * bằng dòng trống hoặc dòng banner. Các pattern của ba định dạng hiện có là rời nhau
 * (JSON mở ngoặc nhọn / header {@code #HN} có tab / mốc thời gian {@code [giây:ms]}), nên thứ tự
 * hỏi parser không ảnh hưởng kết quả.
 */
@Component
public class ContentBasedLogFileTypeDetector implements LogFileTypeDetector {

    private static final int LINES_TO_INSPECT = 5;

    private final List<LogParser> parsers;

    public ContentBasedLogFileTypeDetector(List<LogParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    /** Bộ parser mặc định, dùng cho test và chỗ nào không có Spring container. */
    public static ContentBasedLogFileTypeDetector withDefaults() {
        return new ContentBasedLogFileTypeDetector(List.of(
                new SignalingExportParser(),
                new EndCallLogParser(new EndCallSchemaClassifier()),
                new WebRtcLogParser()));
    }

    @Override
    public LogFileType detect(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int linesChecked = 0;
            while ((line = reader.readLine()) != null && linesChecked < LINES_TO_INSPECT) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                linesChecked++;
                for (LogParser parser : parsers) {
                    if (parser.recognizes(trimmed)) {
                        return parser.type();
                    }
                }
            }
            return LogFileType.UNKNOWN;
        } catch (IOException e) {
            // File không đọc được (không tồn tại, không phải UTF-8, không có quyền) thì coi là
            // không nhận diện được - chỗ gọi sẽ báo lại thành cảnh báo, không bao giờ ném ra ngoài.
            return LogFileType.UNKNOWN;
        }
    }
}
