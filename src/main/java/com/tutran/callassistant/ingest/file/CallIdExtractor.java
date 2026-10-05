package com.tutran.callassistant.ingest.file;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc Call-ID từ <b>nội dung</b> file (không tin tên file / tên thư mục).
 *
 * <p>End-call log có cột {@code callId} ở dòng dữ liệu của schema header đầu tiên khai báo nó;
 * signaling export có field {@code callId} ở đầu file. WebRTC log không chứa Call-ID nên không thể
 * kiểm chứng - trả {@code Optional.empty()} chứ không đoán.
 */
final class CallIdExtractor {

    private static final Pattern SIGNALING_CALL_ID = Pattern.compile("\"callId\"\\s*:\\s*\"([^\"]+)\"");
    private static final String CALL_ID_FIELD = "callId";
    private static final int SIGNALING_SCAN_CHARS = 4096;

    private CallIdExtractor() {
    }

    static Optional<String> extract(LogFileType type, String text) {
        return switch (type) {
            case END_CALL -> fromEndCall(text);
            case SIGNALING_EXPORT -> fromSignaling(text);
            default -> Optional.empty();
        };
    }

    private static Optional<String> fromSignaling(String text) {
        Matcher matcher = SIGNALING_CALL_ID.matcher(text.substring(0, Math.min(text.length(), SIGNALING_SCAN_CHARS)));
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }

    private static Optional<String> fromEndCall(String text) {
        String[] lines = text.split("\\R");
        for (String line : lines) {
            if (!line.startsWith("#H")) {
                continue;
            }
            String[] header = line.split("\t", -1);
            int column = indexOf(header, CALL_ID_FIELD);
            if (column < 0) {
                continue;
            }
            String tag = header[0].substring(2);
            return firstDataValue(lines, tag, column);
        }
        return Optional.empty();
    }

    private static Optional<String> firstDataValue(String[] lines, String tag, int column) {
        String prefix = tag + "\t";
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                String[] cells = line.split("\t", -1);
                if (column < cells.length && !cells[column].isBlank()) {
                    return Optional.of(cells[column].trim());
                }
            }
        }
        return Optional.empty();
    }

    private static int indexOf(String[] cells, String name) {
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
