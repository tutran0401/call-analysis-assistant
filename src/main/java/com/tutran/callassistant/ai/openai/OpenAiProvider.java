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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.io.InterruptedIOException;

/**
 * Adapter OpenAI Chat Completions (mặc định {@code gpt-4o-mini}) với Structured Outputs
 * ({@code response_format.json_schema.strict = true}) để ép phản hồi đúng {@code ai-output-schema-v1.json}.
 *
 * <p>{@code temperature = 0} và {@code seed} cố định để cùng input cho output ổn định nhất có thể (đo ở
 * metric Consistency). API key chỉ nằm trong header của {@link RestClient}; không bao giờ đưa vào message
 * của exception hay log.
 *
 * <p><b>Retry có kiểm soát:</b> chỉ thử lại khi lỗi mang tính tạm thời (timeout, mất kết nối, HTTP 408/429/5xx) và
 * tối đa {@code maxAttempts} lần, không chờ lâu. Lỗi chắc chắn không tự hết (401/403/400, phản hồi hỏng, bị từ
 * chối) không bao giờ retry. Đo thực tế: khoảng 1/10 lần gọi OpenAI bị treo quá 15 giây rồi lần sau chạy 4 giây
 * bình thường, nên một lần thử lại cứu được phần lớn các ca đó thay vì hiện banner degraded.
 */
public class OpenAiProvider implements AiProvider {

    static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final int SEED = 20260;

    private static final long RETRY_PAUSE_MILLIS = 300;

    private final RestClient client;
    private final String model;
    private final int maxAttempts;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Một lần gọi, không retry. */
    public OpenAiProvider(RestClient client, String model) {
        this(client, model, 1);
    }

    /** @param client đã gắn base URL và header {@code Authorization} */
    public OpenAiProvider(RestClient client, String model, int maxAttempts) {
        this.client = client;
        this.model = model;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public AiResult analyze(AiPrompt prompt) {
        long started = System.nanoTime();
        String request = requestBody(prompt);
        String body = callWithRetry(request);
        long latency = (System.nanoTime() - started) / 1_000_000;
        return parse(body, latency);
    }

    private String callWithRetry(String request) {
        AiProviderException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callApi(request);
            } catch (AiProviderException e) {
                last = e;
                if (!isTransient(e) || attempt == maxAttempts) {
                    break;
                }
                pause();
            }
        }
        throw last;
    }

    private static boolean isTransient(AiProviderException e) {
        return e.kind() == Kind.TIMEOUT || (e.kind() == Kind.UNAVAILABLE && e.isRetryable());
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_PAUSE_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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
            int code = e.getStatusCode().value();
            throw new AiProviderException(statusKind(e.getStatusCode()), "OpenAI trả về HTTP " + code,
                    code == 429 || code >= 500);
        } catch (ResourceAccessException e) {
            // SocketTimeoutException là một InterruptedIOException; JDK/Spring có thể bọc thêm một lớp nữa.
            boolean timeout = hasCause(e, InterruptedIOException.class);
            String cause = e.getCause() == null ? "?" : e.getCause().getClass().getSimpleName();
            throw new AiProviderException(timeout ? Kind.TIMEOUT : Kind.UNAVAILABLE,
                    (timeout ? "OpenAI quá thời gian phản hồi" : "Không kết nối được tới OpenAI") + " (" + cause + ")",
                    true);
        } catch (RestClientException e) {
            // Lỗi I/O xảy ra khi ĐỌC body (header đã về nhưng body treo / bị ngắt) được Spring bọc thành
            // RestClientException chứ không phải ResourceAccessException - phải phân loại như lỗi mạng,
            // nếu không sẽ không bao giờ được retry.
            boolean timeout = hasCause(e, InterruptedIOException.class);
            boolean io = timeout || hasCause(e, java.io.IOException.class);
            throw new AiProviderException(timeout ? Kind.TIMEOUT : Kind.UNAVAILABLE,
                    (timeout ? "OpenAI quá thời gian phản hồi khi đọc kết quả" : "Lỗi khi đọc phản hồi OpenAI")
                            + " (" + e.getClass().getSimpleName() + ")", io);
        } catch (RuntimeException e) {
            throw new AiProviderException(Kind.UNAVAILABLE, "Lỗi khi gọi OpenAI: " + e.getClass().getSimpleName());
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
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
