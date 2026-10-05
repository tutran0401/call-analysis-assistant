package com.tutran.callassistant.application.request;

/** Request Parser (PROJECT_SPEC.md mục 6.1 T4): phân loại intent, trích focus và Call-ID. */
public interface RequestParser {

    /**
     * @param message        câu hỏi của người dùng (có thể rỗng nếu chỉ đính kèm file)
     * @param hasAttachments có file đính kèm hay không; câu hỏi rỗng + có file được hiểu là "phân tích cuộc gọi này"
     */
    ParsedRequest parse(String message, boolean hasAttachments);
}
