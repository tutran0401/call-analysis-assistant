package com.tutran.callassistant.application;

/**
 * Lấy signaling log của cuộc gọi từ đâu. Kiến trúc mục tiêu (mục 3.1) luôn query
 * Elasticsearch; {@link #LOCAL_FILE} là đường dành cho demo/debug nhanh khi chưa chạy
 * {@code import}, tương ứng cờ {@code --from-file} của CLI.
 */
public enum SignalingSourcePreference {
    /** Query Elasticsearch, tự fallback sang {@code signaling.json} nếu index chưa có dữ liệu. */
    INDEXED,
    /** Đọc thẳng {@code signaling.json} trong thư mục cuộc gọi, không cần Elasticsearch. */
    LOCAL_FILE
}
