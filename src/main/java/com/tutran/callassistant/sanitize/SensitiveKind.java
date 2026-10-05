package com.tutran.callassistant.sanitize;

import static com.tutran.callassistant.sanitize.DataClassification.INTERNAL;
import static com.tutran.callassistant.sanitize.DataClassification.SECRET;
import static com.tutran.callassistant.sanitize.DataClassification.SENSITIVE;
import static com.tutran.callassistant.sanitize.SanitizeAction.DROP;
import static com.tutran.callassistant.sanitize.SanitizeAction.MASK;
import static com.tutran.callassistant.sanitize.SanitizeAction.MINIMIZE;
import static com.tutran.callassistant.sanitize.SanitizeAction.PSEUDONYMIZE;

/**
 * Các loại dữ liệu nhạy cảm sanitizer nhận ra, mỗi loại kèm phân loại + chính sách lấy từ
 * {@code docs/sensitive-data-inventory.md}. Đây là bảng chính sách duy nhất: đổi chính sách một
 * loại dữ liệu là sửa đúng một dòng ở đây.
 *
 * <p>{@code replacement} là placeholder cho MASK/DROP/MINIMIZE, hoặc tiền tố cho PSEUDONYMIZE.
 */
public enum SensitiveKind {
    AUTHORIZATION(SECRET, DROP, "[REDACTED]"),
    JWT(SECRET, DROP, "[REDACTED]"),
    API_KEY(SECRET, DROP, "[REDACTED]"),
    ICE_CREDENTIAL(SECRET, DROP, "[REDACTED]"),
    DTLS_FINGERPRINT(SECRET, DROP, "[REDACTED]"),
    SDP(SENSITIVE, MINIMIZE, "[SDP_REDACTED]"),
    TURN_SERVER(INTERNAL, MINIMIZE, "turn:[TURN_SERVER_REDACTED]"),
    PHONE(SENSITIVE, MASK, "[PHONE_REDACTED]"),
    EMAIL(SENSITIVE, MASK, "[EMAIL_REDACTED]"),
    IP_ADDRESS(SENSITIVE, MASK, "[IP_REDACTED]"),
    USER_ID(SENSITIVE, PSEUDONYMIZE, "USER"),
    DEVICE_ID(SENSITIVE, PSEUDONYMIZE, "DEVICE"),
    SESSION_ID(SENSITIVE, PSEUDONYMIZE, "SESSION"),
    TURN_SESSION(SENSITIVE, PSEUDONYMIZE, "TURN");

    private final DataClassification classification;
    private final SanitizeAction action;
    private final String replacement;

    SensitiveKind(DataClassification classification, SanitizeAction action, String replacement) {
        this.classification = classification;
        this.action = action;
        this.replacement = replacement;
    }

    public DataClassification classification() {
        return classification;
    }

    public SanitizeAction action() {
        return action;
    }

    public String replacement() {
        return replacement;
    }
}
