package com.tutran.callassistant.ai.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.ai.AiProviderException.Kind;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.port.out.AiProvider;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

/**
 * Adapter OpenAI Chat Completions (mặc định {@code gpt-4o-mini}) với Structured Outputs
 * ({@code response_format.json_schema.strict = true}) để ép phản hồi đúng {@code ai-output-schema-v1.json}.
 *
 * <p>{@code temperature = 0} và {@code seed} cố định để cùng input cho output ổn định nhất có thể (đo ở
 * metric Consistency). Một lần gọi, không retry (retry có kiểm soát là Sprint 3 T8). API key chỉ nằm
 * trong header của {@link RestClient}; không bao giờ đưa vào message của exception hay log.
 */
public class OpenAiProvider implements AiProvider {

    static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final int SEED = 20260;

    private final RestClient client;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    /** @param client đã gắn base URL và header {@code Authorization} */
    public OpenAiProvider(RestClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public AiResult analyze(AiPrompt prompt) {
        long started = System.nanoTime();
        String body = callApi(requestBody(prompt));
        long latency = (System.nanoTime() - started) / 1_000_000;
        return parse(body, latency);
    }

    private String requestBody(AiPrompt prompt) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0);
        root.put("seed", SEED);
        var messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt.systemPrompt());
        messages.addObject().put("role", "user").put("content", prompt.userContent());
        ObjectNode format = root.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode jsonSchema = format.putObject("json_schema");
        jsonSchema.put("name", "call_analysis");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", prompt.outputSchema());
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new AiProviderException(Kind.INVALID_RESPONSE, "Không dựng được request OpenAI", e);
        }
    }

    private String callApi(String requestBody) {
        try {
            return client.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException e) {
            throw new AiProviderException(statusKind(e.getStatusCode()),
                    "OpenAI trả về HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof SocketTimeoutException;
            throw new AiProviderException(timeout ? Kind.TIMEOUT : Kind.UNAVAILABLE,
                    timeout ? "OpenAI quá thời gian phản hồi" : "Không kết nối được tới OpenAI");
        } catch (RuntimeException e) {
            throw new AiProviderException(Kind.UNAVAILABLE, "Lỗi khi gọi OpenAI: " + e.getClass().getSimpleName());
        }
    }

    private static Kind statusKind(HttpStatusCode status) {
        int code = status.value();
        return code == 408 || code == 504 ? Kind.TIMEOUT : Kind.UNAVAILABLE;
    }

    private AiResult parse(String body, long latency) {
        JsonNode root;
        try {
            root = mapper.readTree(body == null ? "" : body);
        } catch (JsonProcessingException e) {
            throw new AiProviderException(Kind.INVALID_RESPONSE, "Phản hồi OpenAI không phải JSON");
        }
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        if (message.isMissingNode()) {
            throw new AiProviderException(Kind.INVALID_RESPONSE, "Phản hồi OpenAI thiếu choices[0].message");
        }
        if (!message.path("refusal").isMissingNode() && !message.path("refusal").isNull()) {
            throw new AiProviderException(Kind.INVALID_RESPONSE, "OpenAI từ chối trả lời");
        }
        if ("length".equals(choice.path("finish_reason").asText())) {
            throw new AiProviderException(Kind.INVALID_RESPONSE, "Phản hồi OpenAI bị cắt cụt (hết token)");
        }
        String content = message.path("content").asText("");
        if (content.isBlank()) {
            throw new AiProviderException(Kind.INVALID_RESPONSE, "Phản hồi OpenAI không có nội dung");
        }
        JsonNode usage = root.path("usage");
        Integer prompt = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
        Integer completion = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null;
        return new AiResult(content, model, latency, prompt, completion);
    }
}
