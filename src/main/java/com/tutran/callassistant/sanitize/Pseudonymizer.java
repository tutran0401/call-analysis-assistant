package com.tutran.callassistant.sanitize;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Sinh định danh giả ổn định: cùng giá trị gốc + cùng salt → cùng kết quả ({@code USER_a81f2c}), nên AI
 * và report vẫn thấy được "cùng một người" mà không biết người đó là ai. HMAC-SHA256 chứ không phải hash
 * trần, vì ID ngắn/đoán được sẽ bị dò ngược nếu không có khoá.
 *
 * <p>Salt cấu hình qua {@code call-analysis.sanitizer.pseudonym-salt}; để trống thì mỗi lần khởi động một
 * salt ngẫu nhiên. Evaluation Runner nên đặt salt cố định để input gửi AI giữa các lần chạy giống hệt
 * nhau (nếu không, pseudonym khác nhau làm sai lệch đo consistency).
 */
@Component
public class Pseudonymizer {

    private static final int HEX_LENGTH = 6;

    private final byte[] salt;

    public Pseudonymizer(@Value("${call-analysis.sanitizer.pseudonym-salt:}") String configuredSalt) {
        this.salt = configuredSalt == null || configuredSalt.isBlank() ? randomSalt()
                : configuredSalt.getBytes(StandardCharsets.UTF_8);
    }

    public String pseudonym(String prefix, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(prefix).append('_');
            for (int i = 0; hex.length() < prefix.length() + 1 + HEX_LENGTH; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.substring(0, prefix.length() + 1 + HEX_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static byte[] randomSalt() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
