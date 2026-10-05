package com.tutran.callassistant.ingest.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Thư mục tạm chứa các file người dùng upload trong <b>một</b> request, để tái dùng nguyên
 * {@link DirectoryClientLogSource} (vốn đọc từ thư mục) mà không phải sửa parser nào.
 *
 * <p>Dùng với try-with-resources: thư mục bị xoá khi đóng, kể cả khi pipeline ném exception, nên log
 * của người dùng không nằm lại trên đĩa sau request. Tên file chỉ được lấy phần cuối (base name) để
 * một tên như {@code ../../x} không thể ghi ra ngoài thư mục tạm.
 */
public final class UploadWorkspace implements AutoCloseable {

    private final Path root;

    private UploadWorkspace(Path root) {
        this.root = root;
    }

    public static UploadWorkspace create() {
        try {
            return new UploadWorkspace(Files.createTempDirectory("call-upload-"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload workspace", e);
        }
    }

    public Path directory() {
        return root;
    }

    /**
     * Ghi một file upload vào workspace. Hai file trùng tên không ghi đè nhau (file sau được thêm
     * hậu tố số) vì người dùng có thể đính kèm hai file cùng tên từ hai thư mục khác nhau.
     */
    public Path store(String originalName, byte[] content) {
        Path target = uniqueTarget(safeName(originalName));
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store uploaded file", e);
        }
        return target;
    }

    static String safeName(String originalName) {
        String name = originalName == null ? "" : originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        return name.isEmpty() || name.equals("..") ? "upload.log" : name;
    }

    private Path uniqueTarget(String name) {
        Path candidate = root.resolve(name);
        for (int i = 1; Files.exists(candidate); i++) {
            candidate = root.resolve(i + "_" + name);
        }
        return candidate;
    }

    @Override
    public void close() {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            // Dọn rác thất bại không được làm hỏng response đã có kết quả.
        }
    }
}
