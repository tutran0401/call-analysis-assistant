package com.tutran.callassistant.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.ai.AiResult;
import com.tutran.callassistant.application.port.out.AiProvider;

/**
 * AI giả lập, xác định (không gọi mạng) - dùng cho test, demo offline và để chạy Evaluation Runner khi
 * chưa có API key. Đọc {@code ruleBaseline} và {@code evidence} trong context rồi trả lời theo
 * {@link Mode}: {@code AGREE} là AI "đồng ý với rule"; các mode còn lại cố tình tạo đúng một loại lỗi
 * để test Guardrails (G01-G05) và Fallback.
 */
public class FakeAiProvider implements AiProvider {

    public enum Mode {
        AGREE,
        /** G02: không phải JSON. */
        BAD_FORMAT,
        /** G02: thiếu field bắt buộc. */
        MISSING_FIELD,
        /** G01: trích evidence id không tồn tại. */
        UNKNOWN_EVIDENCE,
        /** G03: verdict ngoài taxonomy. */
        BAD_ENUM,
        /** G04: verdict ngược với rule. */
        CONFLICT,
        /** G05: nhắc một con số không có trong bộ chỉ số. */
        INVENTED_NUMBER,
        /** S07: phản hồi chứa lại số điện thoại và IP. */
        LEAKY,
        TIMEOUT,
        UNAVAILABLE
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Mode mode;

    public FakeAiProvider() {
        this(Mode.AGREE);
    }

    public FakeAiProvider(Mode mode) {
        this.mode = mode;
    }

    @Override
    public String modelName() {
        return "fake-" + mode.name().toLowerCase();
    }

    @Override
    public AiResult analyze(AiPrompt prompt) {
        switch (mode) {
            case TIMEOUT -> throw new AiProviderException(AiProviderException.Kind.TIMEOUT, "fake timeout");
            case UNAVAILABLE -> throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "fake unavailable");
            default -> { }
        }
        JsonNode context = readContext(prompt.userContent());
        return new AiResult(respond(context), modelName(), 1, 0, 0);
    }

    private String respond(JsonNode context) {
        if (mode == Mode.BAD_FORMAT) {
            return "Đây không phải JSON";
        }
        JsonNode baseline = context.path("ruleBaseline");
        ObjectNode out = mapper.createObjectNode();
        String verdict = baseline.path("verdict").asText("UNKNOWN");
        out.put("verdict", switch (mode) {
            case BAD_ENUM -> "MAYBE";
            case CONFLICT -> verdict.equals("SUCCESS") ? "FAIL" : "SUCCESS";
            default -> verdict;
        });
        out.put("qualityFlag", baseline.path("qualityFlag").asBoolean(false));
        out.put("issueCategory", baseline.path("issueCategory").asText("UNKNOWN"));
        out.put("confidenceLevel", baseline.path("confidenceLevel").asText("LOW"));
        out.put("summary", "Kết luận theo evidence và rule baseline: " + verdict + ".");

        ArrayNode ids = out.putArray("evidenceIds");
        context.path("evidence").forEach(e -> ids.add(e.path("id").asText()));
        if (mode == Mode.UNKNOWN_EVIDENCE) {
            ids.add("EV999");
        }

        out.put("analysis", analysisText(baseline));
        out.putArray("suggestions").add("Đối chiếu lại các evidence đã nêu trong report.");
        if (mode == Mode.MISSING_FIELD) {
            out.remove("analysis");
        }
        try {
            return mapper.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE, "fake serialisation failed", e);
        }
    }

    private String analysisText(JsonNode baseline) {
        String text = "Theo rule baseline, kết luận là " + baseline.path("verdict").asText() + ".";
        return switch (mode) {
            case INVENTED_NUMBER -> text + " Packet loss ước tính 77.7%.";
            case LEAKY -> text + " Liên hệ 0987654321 hoặc kiểm tra IP 113.161.45.67.";
            default -> text;
        };
    }

    private JsonNode readContext(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE, "fake cannot read context", e);
        }
    }
}
