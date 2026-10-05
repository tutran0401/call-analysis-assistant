package com.tutran.callassistant.ai;

import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.port.out.AiProvider;

/**
 * Provider "không có AI": luôn báo không dùng được, nên pipeline đi đường fallback rule-based và đánh
 * dấu degraded. Là mặc định khi chưa cấu hình provider/API key - hệ thống vẫn chạy, không crash.
 */
public class NoAiProvider implements AiProvider {

    private final String reason;

    public NoAiProvider(String reason) {
        this.reason = reason;
    }

    @Override
    public String modelName() {
        return "none";
    }

    @Override
    public AiResult analyze(AiPrompt prompt) {
        throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, reason);
    }
}
