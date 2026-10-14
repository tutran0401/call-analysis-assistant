package com.tutran.callassistant.application.port.out;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Kho lưu signaling event có thể query theo Call-ID (Sprint 1: một index Elasticsearch
 * local). Tách thành port để use case import không phụ thuộc vào client Elasticsearch.
 */
public interface SignalingEventArchive {

    /** Tạo index kèm mapping nếu chưa có. Gọi lại nhiều lần là an toàn. */
    void ensureReady() throws IOException;

    /** Index một file export {@code signaling.json}. Trả về số sự kiện đã ghi. */
    int index(Path signalingExportFile) throws IOException;

    /** Các Call-ID đang có trong kho, tối đa {@code limit} (lệnh batch - Sprint 3 T10). */
    List<String> callIds(int limit) throws IOException;

    /** Tên kho đang dùng, chỉ để hiển thị lại cho người chạy. */
    String name();
}
