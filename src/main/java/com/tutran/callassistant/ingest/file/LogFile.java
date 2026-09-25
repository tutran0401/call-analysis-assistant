package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.domain.event.Leg;

import java.nio.file.Path;

/**
 * Một file log cần chuẩn hoá, kèm những gì đã biết về nó từ bên ngoài nội dung file.
 *
 * <p>Tồn tại để mọi {@link LogParser} dùng chung đúng một chữ ký {@code parse(LogFile)} - trước
 * đây ba parser có ba chữ ký khác nhau ({@code (file, callId)}, {@code (file, callId, leg)},
 * {@code (file, callIdOverride)}) nên không thể gọi chúng qua một interface chung, và chỗ gọi
 * buộc phải là một câu {@code switch} biết trước từng loại parser.
 *
 * @param path     đường dẫn file
 * @param callId   Call-ID đã biết từ tên thư mục, hoặc {@code null} nếu để parser tự đọc từ nội dung
 * @param legHint  leg suy ra từ tên file - chỉ là <i>gợi ý</i>: parser nào tìm được tín hiệu
 *                 đáng tin hơn trong nội dung thì phải ưu tiên nội dung
 */
public record LogFile(Path path, String callId, Leg legHint) {

    public static LogFile of(Path path) {
        return new LogFile(path, null, Leg.UNKNOWN);
    }

    public static LogFile of(Path path, String callId) {
        return new LogFile(path, callId, Leg.UNKNOWN);
    }

    public static LogFile of(Path path, String callId, Leg legHint) {
        return new LogFile(path, callId, legHint);
    }

    /** Suy ra {@code legHint} từ tên file, dùng khi quét cả một thư mục cuộc gọi. */
    public static LogFile inDirectory(Path path, String callId) {
        return new LogFile(path, callId, LegNaming.fromText(path.getFileName().toString()));
    }

    public String fileName() {
        return path.getFileName().toString();
    }
}
