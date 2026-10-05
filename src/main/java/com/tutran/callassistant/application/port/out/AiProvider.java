package com.tutran.callassistant.application.port.out;

import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.ai.AiResult;

/**
 * AI Provider Abstraction (PROJECT_SPEC.md mục 6.6). Đổi provider (OpenAI, local, giả lập) là đổi một
 * bean, không đụng pipeline.
 */
public interface AiProvider {

    /** Tên model/provider để ghi log và report AI-vs-rule. */
    String modelName();

    /**
     * Gọi AI một lần, không retry.
     *
     * @throws AiProviderException mọi lỗi (timeout, không dùng được, trả lời hỏng) - không ném loại exception nào khác
     */
    AiResult analyze(AiPrompt prompt) throws AiProviderException;
}
