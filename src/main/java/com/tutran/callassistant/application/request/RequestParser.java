package com.tutran.callassistant.application.request;

import com.tutran.callassistant.application.port.out.AiProvider;

/** Request Parser (PROJECT_SPEC.md mục 6.1 T4): phân loại intent, trích focus và Call-ID. */
public interface RequestParser {

    /**
     * @param message        câu hỏi của người dùng (có thể rỗng nếu chỉ đính kèm file)
     * @param hasAttachments có file đính kèm hay không; câu hỏi rỗng + có file được hiểu là "phân tích cuộc gọi này"
     */
    ParsedRequest parse(String message, boolean hasAttachments);

    /**
     * Bản sao dùng AI provider khác (Evaluation Runner bọc provider để ghi lại mọi thứ gửi sang AI, kể cả
     * câu hỏi gửi đi phân loại). Parser không dùng AI thì không cần đổi gì.
     */
    default RequestParser withAiProvider(AiProvider provider) {
        return this;
    }
}
