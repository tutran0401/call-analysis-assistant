package com.tutran.callassistant.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Nhận diện một file thuộc loại nào trong 3 nguồn log gốc, bằng cách xem nội dung
 * thay vì tin vào tên file - bản thân data mẫu đã có 1 file đặt sai tên
 * ("calleer_webrtc.log"), và spec yêu cầu rõ phải nhận diện theo nội dung để không bao
 * giờ bị phân loại sai một cách âm thầm chỉ vì tên/đuôi file sai.
 */
public final class FileTypeDetector {

    private static final Pattern END_CALL_HEADER = Pattern.compile("^#H\\d+\\t");
    private static final Pattern WEBRTC_FORMAT_1 = Pattern.compile("^\\[\\d+:\\d+]\\[\\d+]\\s+\\(.*");
    private static final Pattern WEBRTC_FORMAT_2 = Pattern.compile("^[\\w.]+\\.(cc|mm):\\s+\\[\\d+:\\d+]\\[\\d+]\\s+\\(.*");

    private FileTypeDetector() {
    }

    public static LogFileType detect(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int linesChecked = 0;
            while ((line = reader.readLine()) != null && linesChecked < 5) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                linesChecked++;
                LogFileType detected = detectFromLine(trimmed);
                if (detected != LogFileType.UNKNOWN) {
                    return detected;
                }
            }
            return LogFileType.UNKNOWN;
        } catch (IOException e) {
            return LogFileType.UNKNOWN;
        }
    }

    private static LogFileType detectFromLine(String firstLine) {
        if (firstLine.startsWith("{")) {
            return LogFileType.SIGNALING_EXPORT;
        }
        if (END_CALL_HEADER.matcher(firstLine).find()) {
            return LogFileType.END_CALL;
        }
        if (WEBRTC_FORMAT_1.matcher(firstLine).matches() || WEBRTC_FORMAT_2.matcher(firstLine).matches()) {
            return LogFileType.WEBRTC;
        }
        return LogFileType.UNKNOWN;
    }
}
