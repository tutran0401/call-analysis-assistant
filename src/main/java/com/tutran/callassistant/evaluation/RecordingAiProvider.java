package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.port.out.AiProvider;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Bọc một {@link AiProvider} để ghi lại <b>đúng nội dung đã gửi sang AI</b> của lần gọi gần nhất - cách
 * duy nhất đo Security Leakage (input) trung thực, vì đo ở cửa ra chứ không đo ở chỗ sanitizer tự báo.
 * Nội dung chỉ nằm trong bộ nhớ của lần chạy, không bao giờ được ghi ra file kết quả.
 */
public final class RecordingAiProvider implements AiProvider {

    private final AiProvider delegate;
    private final AtomicReference<String> lastRequest = new AtomicReference<>();

    public RecordingAiProvider(AiProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public AiResult analyze(AiPrompt prompt) {
        lastRequest.set(prompt.systemPrompt() + "\n" + prompt.userContent());
        return delegate.analyze(prompt);
    }

    public void reset() {
        lastRequest.set(null);
    }

    /** @return nội dung gửi AI ở lần gọi gần nhất kể từ {@link #reset()}, hoặc {@code null} nếu chưa gọi AI */
    public String lastRequest() {
        return lastRequest.get();
    }
}
