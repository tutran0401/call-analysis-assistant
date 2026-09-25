package com.tutran.callassistant.ingest.file;

import java.nio.file.Path;

/** Nhận diện một file thuộc loại nguồn log nào, dựa trên nội dung. */
public interface LogFileTypeDetector {

    LogFileType detect(Path file);
}
