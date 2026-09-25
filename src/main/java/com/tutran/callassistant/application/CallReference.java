package com.tutran.callassistant.application;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Trỏ tới đúng một cuộc gọi cần phân tích: Call-ID (khoá tra cứu signaling) và thư mục
 * chứa client log của nó. Tồn tại để không phải truyền cặp {@code (Path, String)} lẫn lộn
 * qua từng tầng - và để quy ước "tên thư mục chính là Call-ID" chỉ nằm ở đúng một chỗ.
 */
public record CallReference(String callId, Path directory) {

    public CallReference {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(directory, "directory");
    }

    /** Data mẫu của mentor đặt mỗi cuộc gọi trong một thư mục tên đúng bằng Call-ID của nó. */
    public static CallReference ofDirectory(Path callDirectory) {
        return new CallReference(callDirectory.getFileName().toString(), callDirectory);
    }

    public Path resolve(String fileName) {
        return directory.resolve(fileName);
    }
}
