package com.tutran.callassistant.application.ai;

/**
 * Phản hồi thô của AI (chưa tin được - phải qua Guardrails) cùng số đo để ghi log (mục 6.2: model,
 * latency, token_usage). Cố tình không có nội dung request, vì không được log raw AI request/response.
 *
 * @param rawJson          chuỗi JSON AI trả về
 * @param promptTokens     {@code null} nếu provider không báo
 * @param completionTokens {@code null} nếu provider không báo
 */
public record AiResult(String rawJson, String model, long latencyMillis,
                       Integer promptTokens, Integer completionTokens) {

    public Integer totalTokens() {
        return promptTokens == null || completionTokens == null ? null : promptTokens + completionTokens;
    }
}
