package com.tutran.callassistant.ingest.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Tra thư mục của một cuộc gọi theo Call-ID trong một cây dữ liệu mẫu, theo quy ước "thư mục tên đúng
 * bằng Call-ID" của data mentor ({@code <root>/success/<callId>/}, {@code <root>/fail/<callId>/}, ...).
 * Quét tối đa {@value #MAX_DEPTH} tầng, một lần, khi cần lần đầu; so khớp không phân biệt hoa thường.
 */
public final class CallDirectoryIndex {

    static final int MAX_DEPTH = 3;

    private final Path root;
    private Map<String, Path> byCallId;

    public CallDirectoryIndex(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public synchronized Optional<Path> find(String callId) {
        if (callId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(index().get(callId.toLowerCase(Locale.ROOT)));
    }

    private Map<String, Path> index() {
        if (byCallId == null) {
            byCallId = scan();
        }
        return byCallId;
    }

    private Map<String, Path> scan() {
        Map<String, Path> found = new HashMap<>();
        if (!Files.isDirectory(root)) {
            return found;
        }
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
            walk.filter(Files::isDirectory)
                    .filter(dir -> !dir.equals(root))
                    .forEach(dir -> found.putIfAbsent(dir.getFileName().toString().toLowerCase(Locale.ROOT), dir));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot scan data root " + root, e);
        }
        return found;
    }
}
