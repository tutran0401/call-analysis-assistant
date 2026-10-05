package com.tutran.callassistant.sanitize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

/**
 * Input Sanitizer (mục 6.2): chạy trên context đã dựng từ timeline/evidence/metrics, <b>ngay trước</b>
 * khi gửi AI. Sau khi xử lý, quét lại lần nữa để đếm giá trị còn sót - nếu khác 0 thì
 * {@code ChatAnalysisService} không được gửi context đó đi.
 */
@Component
public class InputSanitizer {

    private final SensitiveDataSanitizer engine;
    private final ObjectMapper mapper = new ObjectMapper();

    public InputSanitizer(SensitiveDataSanitizer engine) {
        this.engine = engine;
    }

    public SanitizedContext sanitize(JsonNode context) {
        SanitizationSummary summary = new SanitizationSummary();
        JsonNode safe = engine.sanitizeJson(context, summary);
        return new SanitizedContext(safe, summary, residual(safe));
    }

    /** Dùng cho các đoạn text rời (câu hỏi của người dùng, focus). */
    public String sanitizeText(String text, SanitizationSummary summary) {
        return engine.sanitizeText(text, summary);
    }

    private int residual(JsonNode safe) {
        try {
            return engine.countResidual(mapper.writeValueAsString(safe));
        } catch (JsonProcessingException e) {
            // Không serialize lại được thì không thể khẳng định là sạch → coi như còn sót.
            return Integer.MAX_VALUE;
        }
    }
}
