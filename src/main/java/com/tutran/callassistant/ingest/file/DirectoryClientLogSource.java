package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.port.out.ClientLogSource;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quét thư mục của một cuộc gọi và chuẩn hoá mọi client log nhận diện được.
 *
 * <p>Loại file được xác định theo <b>nội dung</b> ({@link LogFileTypeDetector}) chứ không theo tên
 * file - nhờ vậy một file đặt sai tên (bản thân data mẫu có đúng một file như thế:
 * {@code calleer_webrtc.log}) vẫn được parse đúng. Sau khi biết loại, việc chọn parser chỉ là tra
 * map: trước đây chỗ này là một câu {@code switch} liệt kê từng loại, nên thêm một nguồn log là
 * phải sửa cả ở đây.
 */
@Component
public class DirectoryClientLogSource implements ClientLogSource {

    /**
     * Signaling export nằm cùng thư mục nhưng cố tình không parse ở đây: signaling đi vào hệ thống
     * qua {@link com.tutran.callassistant.application.port.out.SignalingEventSource}. Bỏ qua lặng
     * lẽ (không cảnh báo) vì đây là trạng thái bình thường, đúng như mong đợi.
     */
    private static final Set<LogFileType> HANDLED_ELSEWHERE = EnumSet.of(LogFileType.SIGNALING_EXPORT);

    private final LogFileTypeDetector typeDetector;
    private final Map<LogFileType, ClientLogParser> parsersByType;

    public DirectoryClientLogSource(LogFileTypeDetector typeDetector, List<ClientLogParser> parsers) {
        this.typeDetector = typeDetector;
        this.parsersByType = parsers.stream()
                .collect(Collectors.toUnmodifiableMap(LogParser::type, Function.identity()));
    }

    /** Bộ parser mặc định, dùng cho test và chỗ nào không có Spring container. */
    public static DirectoryClientLogSource withDefaults() {
        return new DirectoryClientLogSource(ContentBasedLogFileTypeDetector.withDefaults(),
                List.of(new EndCallLogParser(new EndCallSchemaClassifier()), new WebRtcLogParser()));
    }

    @Override
    public NormalizedEvents load(CallReference call) {
        List<NormalizedEvents> parsed = new ArrayList<>();
        for (Path file : listRegularFiles(call.directory())) {
            parsed.add(parseOne(file, call));
        }
        return NormalizedEvents.merge(parsed);
    }

    private NormalizedEvents parseOne(Path file, CallReference call) {
        LogFileType type = typeDetector.detect(file);
        if (HANDLED_ELSEWHERE.contains(type)) {
            return NormalizedEvents.empty();
        }
        Optional<ClientLogParser> parser = Optional.ofNullable(parsersByType.get(type));
        if (parser.isEmpty()) {
            return NormalizedEvents.unreadable("Unrecognized file type, skipped: " + file);
        }
        return parser.get().parse(LogFile.inDirectory(file, call.callId()));
    }

    private List<Path> listRegularFiles(Path directory) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        } catch (IOException e) {
            // Thư mục không đọc được thì trả về danh sách rỗng; chỗ gọi sẽ thấy điều đó phản ánh
            // thành thiếu dữ liệu (ví dụ MissingEndCallLogDetector báo "Missing caller_endcall.log").
        }
        return files;
    }
}
