package com.tutran.callassistant.application.port.out;

import java.util.Optional;

/**
 * Phân loại lỗi khi lấy signaling từ Elasticsearch (Sprint 3 T8), để log, report và UI nói đúng chuyện gì đã xảy ra
 * thay vì một câu "lỗi" chung chung.
 *
 * <ul>
 *   <li>{@code ES_UNAVAILABLE}: không kết nối được (ES chưa chạy, sai host/port) - đáng thử lại một lần.</li>
 *   <li>{@code ES_TIMEOUT}: kết nối được nhưng quá {@code socket-timeout-ms} - không thử lại, tránh
 *       nhân đôi thời gian chờ của người dùng.</li>
 *   <li>{@code ES_INDEX_MISSING}: ES chạy nhưng chưa import signaling.</li>
 *   <li>{@code ES_ERROR}: lỗi khác do ES trả về hoặc lỗi không dự kiến.</li>
 *   <li>{@code ES_TRUNCATED}: đọc được, nhưng cuộc gọi có nhiều sự kiện hơn trần đọc một lần nên chỉ phân tích phần
 *       đầu. Khác các mã trên: signaling không rỗng, chỉ thiếu phần cuối.</li>
 * </ul>
 */
public enum SignalingFetchError {
    ES_UNAVAILABLE, ES_TIMEOUT, ES_INDEX_MISSING, ES_ERROR, ES_TRUNCATED;

    private static final String CODE_PREFIX = "ES_";

    /** Mã lỗi đứng đầu một cảnh báo xử lý ("ES_TIMEOUT: ..."), nếu có. */
    public static Optional<SignalingFetchError> fromWarning(String warning) {
        if (warning == null || !warning.startsWith(CODE_PREFIX)) {
            return Optional.empty();
        }
        int colon = warning.indexOf(':');
        String code = colon < 0 ? warning : warning.substring(0, colon);
        for (SignalingFetchError error : values()) {
            if (error.name().equals(code)) {
                return Optional.of(error);
            }
        }
        return Optional.empty();
    }

    /** Có lấy được signaling nhưng không đủ (khác các mã còn lại: không lấy được gì). */
    public boolean partial() {
        return this == ES_TRUNCATED;
    }

    public boolean retryable() {
        return this == ES_UNAVAILABLE;
    }

    /** Câu cho mục "Giới hạn dữ liệu" của report. */
    public String limitation() {
        return switch (this) {
            case ES_UNAVAILABLE -> "Không kết nối được Elasticsearch local nên không lấy được signaling; "
                    + "không xác định được cuộc gọi đã thiết lập và kết thúc ra sao.";
            case ES_TIMEOUT -> "Elasticsearch phản hồi quá chậm (timeout) nên không lấy được signaling; "
                    + "không xác định được cuộc gọi đã thiết lập và kết thúc ra sao.";
            case ES_INDEX_MISSING -> "Elasticsearch chưa có index signaling (chưa chạy import) nên không lấy được "
                    + "signaling.";
            case ES_ERROR -> "Truy vấn signaling từ Elasticsearch bị lỗi nên không lấy được signaling.";
            case ES_TRUNCATED -> "Cuộc gọi có nhiều sự kiện signaling hơn trần đọc một lần từ Elasticsearch nên chỉ "
                    + "phân tích phần đầu (theo thời gian); diễn biến cuối cuộc gọi có thể bị thiếu.";
        };
    }
}
