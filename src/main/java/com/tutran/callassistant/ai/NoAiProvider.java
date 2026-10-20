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
    private final AiProviderException.Kind kind;

    /** AI chưa được cấu hình ({@code NOT_CONFIGURED}). */
    public NoAiProvider(String reason) {
        this(reason, AiProviderException.Kind.NOT_CONFIGURED);
    }

    private NoAiProvider(String reason, AiProviderException.Kind kind) {
        this.reason = reason;
        this.kind = kind;
    }

    /** Chạy chỉ rule theo thiết kế ({@code RULE_ONLY}), không phải AI hỏng hay thiếu cấu hình. */
    public static NoAiProvider ruleOnly(String purpose) {
        return new NoAiProvider(purpose, AiProviderException.Kind.RULE_ONLY);
    }

    @Override
    public String modelName() {
        return "none";
    }

    @Override
    public AiResult analyze(AiPrompt prompt) {
        throw new AiProviderException(kind, reason);
    }
}
