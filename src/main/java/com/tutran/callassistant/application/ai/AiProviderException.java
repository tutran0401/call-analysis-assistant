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
        INVALID_RESPONSE,
        /** Chưa cấu hình AI (provider none / thiếu API key) - người dùng nhận report rule-based. */
        NOT_CONFIGURED,
        /**
         * Lượt chạy CHỈ rule theo thiết kế (batch không --with-ai, rule baseline của evaluate / security-eval):
         * không phải sự cố, nên không được tính chung với fallback thật trên dashboard (peer evaluation issue 5).
         */
        RULE_ONLY
    }

    private final Kind kind;
    private final boolean retryable;

    public AiProviderException(Kind kind, String message) {
        this(kind, message, false);
    }

    /** @param retryable lỗi mang tính tạm thời (đáng thử lại): 429, 5xx, mất kết nối */
    public AiProviderException(Kind kind, String message, boolean retryable) {
        super(message);
        this.kind = kind;
        this.retryable = retryable;
    }

    public AiProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.retryable = false;
    }

    public Kind kind() {
        return kind;
    }

    /** {@code true} khi thử lại có thể thành công (lỗi tạm thời); {@code false} với lỗi cấu hình / phản hồi hỏng. */
    public boolean isRetryable() {
        return retryable;
    }
}
