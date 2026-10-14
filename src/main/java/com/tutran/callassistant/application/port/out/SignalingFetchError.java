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
 * </ul>
 */
public enum SignalingFetchError {
    ES_UNAVAILABLE, ES_TIMEOUT, ES_INDEX_MISSING, ES_ERROR;

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

    public boolean retryable() {
        return this == ES_UNAVAILABLE;
    }

    /** Câu cho mục "Giới hạn dữ liệu" của report. */
    public String limitation() {
        return switch (this) {
            case ES_UNAVAILABLE -> "Không kết nối được Elasticsearch local nên không lấy được signaling; "
                    + "kết luận chỉ dựa trên log client.";
            case ES_TIMEOUT -> "Elasticsearch phản hồi quá chậm (timeout) nên không lấy được signaling; "
                    + "kết luận chỉ dựa trên log client.";
            case ES_INDEX_MISSING -> "Elasticsearch chưa có index signaling (chưa chạy import) nên không lấy được "
                    + "signaling.";
            case ES_ERROR -> "Truy vấn signaling từ Elasticsearch bị lỗi nên không lấy được signaling.";
        };
    }
}
