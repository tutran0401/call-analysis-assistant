package com.tutran.callassistant.application.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.ai.TimeLimitedAiCaller;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.sanitize.InputSanitizer;
import com.tutran.callassistant.sanitize.SanitizationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

/**
 * Request Parser có AI (PROJECT_SPEC.md mục 3.2: "Hiểu câu hỏi của người dùng - AI"), thiết kế để
 * <b>không làm mất tính xác định</b> của phần còn lại:
 *
 * <ul>
 *   <li>Rule ({@link RuleRequestParser}) luôn chạy trước. Nếu rule xác định được là trong phạm vi
 *       ({@code ANALYZE_*}), kết quả đó được giữ nguyên và <b>không gọi AI</b> - cùng câu hỏi luôn cho cùng
 *       intent/focus, không tốn thêm một lượt gọi.</li>
 *   <li>AI chỉ được hỏi khi rule trả {@code OUT_OF_SCOPE} cho một câu hỏi không rỗng: đó là chỗ từ khoá yếu nhất
 *       (câu diễn đạt lạ). AI chỉ có thể <i>cứu</i> câu hỏi đó thành trong phạm vi; nó không bao giờ biến một
 *       câu trong phạm vi thành bị từ chối. Sai lầm xấu nhất của AI là trả một report thừa thay vì một lời từ chối.</li>
 *   <li>Call-ID luôn do regex (code) trích, không bao giờ do AI.</li>
 *   <li>Mọi lỗi AI (timeout, chưa cấu hình, JSON hỏng, enum lạ) → dùng kết quả của rule.</li>
 * </ul>
 *
 * <p>Câu hỏi được qua Input Sanitizer trước khi gửi: người dùng có thể dán nhầm token vào câu hỏi.
 */
@Component
@Primary
public class AiRequestParser implements RequestParser {

    private static final String SYSTEM_PROMPT_RESOURCE = "/ai/prompts/intent-system.vi.txt";
    private static final String SCHEMA_RESOURCE = "/ai/intent-schema-v1.json";

    private static final Logger log = LoggerFactory.getLogger(AiRequestParser.class);

    private final RuleRequestParser rules;
    private final AiProvider ai;
    private final InputSanitizer sanitizer;
    private final TimeLimitedAiCaller caller;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String systemPrompt;
    private final JsonNode schema;

    public AiRequestParser(RuleRequestParser rules, AiProvider ai, InputSanitizer sanitizer,
                           TimeLimitedAiCaller caller) {
        this.rules = rules;
        this.ai = ai;
        this.sanitizer = sanitizer;
        this.caller = caller;
        try (InputStream prompt = open(SYSTEM_PROMPT_RESOURCE); InputStream schemaStream = open(SCHEMA_RESOURCE)) {
            this.systemPrompt = new String(prompt.readAllBytes(), StandardCharsets.UTF_8).strip();
            this.schema = mapper.readTree(schemaStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load intent prompt resources", e);
        }
    }

    /** Cùng định dạng dòng {@code ai_call} của phân tích, thêm {@code purpose=intent}; không ghi câu hỏi. */
    private void logCall(String model, long startedNanos, String status, Integer tokens, String fallbackReason) {
        log.info("ai_call purpose=intent model={} latency_ms={} result_status={} token_usage={} fallback_reason={}",
                model, (System.nanoTime() - startedNanos) / 1_000_000, status, tokens, fallbackReason);
    }

    @Override
    public ParsedRequest parse(String message, boolean hasAttachments) {
        ParsedRequest byRules = rules.parse(message, hasAttachments);
        if (!byRules.isOutOfScope() || message == null || message.isBlank()) {
            return byRules;
        }
        long started = System.nanoTime();
        try {
            String question = sanitizer.sanitizeText(message, new SanitizationSummary());
            AiResult result = caller.call(ai, new AiPrompt(systemPrompt, question, schema));
            ParsedRequest parsed = fromAi(mapper.readTree(result.rawJson()), byRules);
            logCall(result.model(), started, "ACCEPTED", result.totalTokens(), null);
            return parsed;
        } catch (IOException | RuntimeException e) {
            logCall(ai.modelName(), started, "FALLBACK", null, e instanceof AiProviderException known
                    ? "AI_" + known.kind().name() : e.getClass().getSimpleName());
            return byRules;
        }
    }

    @Override
    public RequestParser withAiProvider(AiProvider other) {
        return new AiRequestParser(rules, other, sanitizer, caller);
    }

    /** Chỉ chấp nhận kết quả AI nếu hợp lệ hoàn toàn; ngược lại giữ kết quả của rule. */
    private ParsedRequest fromAi(JsonNode node, ParsedRequest byRules) {
        Intent intent = Intent.valueOf(node.path("intent").asText());
        if (intent == Intent.OUT_OF_SCOPE) {
            return byRules;
        }
        Set<CallFocus> focus = EnumSet.noneOf(CallFocus.class);
        node.path("focus").forEach(item -> focus.add(CallFocus.valueOf(item.asText())));
        if (intent == Intent.ANALYZE_WITH_FOCUS && focus.isEmpty()) {
            intent = Intent.ANALYZE_CALL;
        }
        if (intent == Intent.ANALYZE_CALL) {
            focus.clear();
        }
        return new ParsedRequest(intent, focus, byRules.callIdInText());
    }

    private InputStream open(String resource) {
        InputStream in = getClass().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Resource not found: " + resource);
        }
        return in;
    }
}
