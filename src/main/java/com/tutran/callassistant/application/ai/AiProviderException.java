package com.tutran.callassistant.application.ai;

/**
 * Lỗi khi gọi AI, đã phân loại để fallback ghi đúng {@code fallback_reason} (mục 6.2) và để Sprint 3 T8
 * quyết định lỗi nào đáng retry.
 */
public class AiProviderException extends RuntimeException {

    public enum Kind {
        /** Quá thời gian chờ. */
        TIMEOUT,
        /** Provider không dùng được: chưa cấu hình, hết hạn mức, 5xx, không kết nối được. */
        UNAVAILABLE,
        /** Provider trả lời nhưng nội dung không dùng được (không phải JSON, bị từ chối, bị cắt cụt). */
        INVALID_RESPONSE
    }

    private final Kind kind;

    public AiProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
