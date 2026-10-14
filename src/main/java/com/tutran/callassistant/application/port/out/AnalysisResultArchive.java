package com.tutran.callassistant.application.port.out;

/**
 * Nơi lưu kết quả phân tích để thống kê sau (Sprint 3 T9-T11: Kibana view / dashboard). Chỉ nhận
 * {@link AnalysisResultRecord} - một bản tóm tắt đã sanitize, không bao giờ là report hay log gốc.
 *
 * <p>Ghi kết quả là việc phụ: implementation không được ném exception làm hỏng request của người dùng.
 */
public interface AnalysisResultArchive {

    void record(AnalysisResultRecord result);

    /** Không lưu gì - mặc định khi chạy CLI hoặc chưa bật Elasticsearch. */
    AnalysisResultArchive NONE = result -> { };
}
