package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.application.port.out.CallDirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Liệt kê thư mục cuộc gọi trên đĩa: mỗi thư mục con trực tiếp của thư mục gốc data mẫu là một
 * cuộc gọi, tên thư mục chính là Call-ID.
 *
 * <p>Thư mục gốc không đọc được (sai đường dẫn) chỉ sinh log cảnh báo và trả về danh sách rỗng,
 * để một đường dẫn sai trong số nhiều đường dẫn không làm hỏng cả lượt chạy.
 */
@Component
public class FileSystemCallDirectoryScanner implements CallDirectoryScanner {

    private static final Logger log = LoggerFactory.getLogger(FileSystemCallDirectoryScanner.class);

    @Override
    public List<Path> callDirectoriesUnder(Path dataRoot) {
        List<Path> directories = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataRoot)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    directories.add(path);
                }
            }
        } catch (IOException e) {
            log.warn("Could not list {}: {}", dataRoot, e.getMessage());
        }
        return directories;
    }
}
