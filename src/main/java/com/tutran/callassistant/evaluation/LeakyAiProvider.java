package com.tutran.callassistant.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.port.out.AiProvider;

import java.io.IOException;
import java.util.List;

/**
 * AI giả <b>cố tình rò rỉ</b> (S07): lấy câu trả lời hợp lệ của provider bên trong rồi chèn mọi giá trị nhạy cảm
 * đã biết vào summary, analysis và suggestions - đúng tình huống model "nhớ" hoặc bịa ra một định danh.
 *
 * <p>Chỉ dùng trong {@code security-eval} để chứng minh Output Sanitizer + Guardrails chặn được, không bao giờ
 * dùng trong ứng dụng thật.
 */
public final class LeakyAiProvider implements AiProvider {

    private final AiProvider delegate;
    private final List<String> leakedValues;
    private final ObjectMapper mapper = new ObjectMapper();

    public LeakyAiProvider(AiProvider delegate, List<String> leakedValues) {
        this.delegate = delegate;
        this.leakedValues = List.copyOf(leakedValues);
    }

    @Override
    public AiResult analyze(AiPrompt prompt) {
        AiResult honest = delegate.analyze(prompt);
        try {
            JsonNode parsed = mapper.readTree(honest.rawJson());
            if (!(parsed instanceof ObjectNode out)) {
                return honest;
            }
            String leak = String.join(" ", leakedValues);
            out.put("summary", out.path("summary").asText("") + " Liên hệ: " + leak);
            out.put("analysis", out.path("analysis").asText("") + " Thông tin người dùng: " + leak);
            ArrayNode suggestions = out.withArray("suggestions");
            suggestions.add("Gọi lại cho khách hàng theo " + leak);
            return new AiResult(mapper.writeValueAsString(out), modelName(), honest.latencyMillis(),
                    honest.promptTokens(), honest.completionTokens());
        } catch (IOException e) {
            throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE, "leaky fake failed", e);
        }
    }

    @Override
    public String modelName() {
        return "leaky(" + delegate.modelName() + ")";
    }
}
