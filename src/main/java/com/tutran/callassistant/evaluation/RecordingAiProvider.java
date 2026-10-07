package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.port.out.AiProvider;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bọc một {@link AiProvider} để ghi lại <b>đúng nội dung đã gửi sang AI</b> trong lượt xử lý một request - cách
 * duy nhất đo Security Leakage (input) trung thực, vì đo ở cửa ra chứ không đo ở chỗ sanitizer tự báo.
 * Nội dung chỉ nằm trong bộ nhớ của lần chạy, không bao giờ được ghi ra file kết quả.
 */
public final class RecordingAiProvider implements AiProvider {

    private static final String SEPARATOR = "\n----\n";

    private final AiProvider delegate;
    private final CopyOnWriteArrayList<String> requests = new CopyOnWriteArrayList<>();

    public RecordingAiProvider(AiProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public AiResult analyze(AiPrompt prompt) {
        requests.add(prompt.systemPrompt() + "\n" + prompt.userContent());
        return delegate.analyze(prompt);
    }

    public void reset() {
        requests.clear();
    }

    /**
     * @return nội dung của <b>mọi</b> lần gọi AI kể từ {@link #reset()} (phân loại câu hỏi lẫn phân tích),
     *         hoặc {@code null} nếu chưa gọi AI lần nào
     */
    public String lastRequest() {
        return requests.isEmpty() ? null : String.join(SEPARATOR, requests);
    }
}
