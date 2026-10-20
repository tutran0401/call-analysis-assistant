package com.tutran.callassistant.application.guardrail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.tutran.callassistant.application.ai.AiPromptFactory;
import com.tutran.callassistant.domain.report.Report;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guardrails (PROJECT_SPEC.md mục 6.1 T6) - tầng code đứng giữa AI và report. AI chỉ <i>đề xuất</i>;
 * mọi thứ phải đi qua đây, theo thứ tự:
 * <ol>
 *   <li><b>G02</b> không phải JSON / sai schema / thiếu field → REJECT.</li>
 *   <li><b>G03</b> verdict, issueCategory, confidenceLevel ngoài taxonomy → REJECT.</li>
 *   <li><b>G01</b> {@code evidenceIds} không tồn tại trong evidence của rule (hoặc kết luận không dẫn evidence nào) → REJECT.</li>
 *   <li><b>G05</b> số liệu trong summary/analysis/suggestions không có trong bộ chỉ số/evidence đã tính → REJECT.</li>
 *   <li><b>G04</b> verdict của AI mâu thuẫn rule → verdict {@code UNKNOWN} + cờ cần kiểm tra
 *       ({@code ACCEPT_WITH_FLAGS}); lệch category/qualityFlag nhưng cùng verdict → giữ giá trị của rule + cờ.</li>
 * </ol>
 * REJECT nghĩa là pipeline fallback về rule-based; AI không bao giờ làm hỏng pipeline.
 *
 * <p>G05: AI uses metric reference tokens; code binds labels, values and units.
 */
@Component
public class AiResponseGuard {

    private static final Pattern EVIDENCE_ID = Pattern.compile("\\bEV[0-9]+\\b");

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchema schema;

    public AiResponseGuard(AiPromptFactory prompts) {
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(prompts.outputSchema());
    }

    public GuardDecision check(String rawAiJson, Report ruleReport) {
        JsonNode node;
        try {
            node = mapper.readTree(rawAiJson == null ? "" : rawAiJson);
        } catch (JsonProcessingException e) {
            return GuardDecision.reject(List.of("G02: phản hồi AI không phải JSON hợp lệ"));
        }
        if (node == null || !node.isObject()) {
            return GuardDecision.reject(List.of("G02: phản hồi AI không phải JSON object"));
        }

        List<String> schemaProblems = schemaProblems(node);
        if (!schemaProblems.isEmpty()) {
            return GuardDecision.reject(schemaProblems);
        }

        AiAnalysis analysis = toAnalysis(node);

        List<String> unknownEvidence = unknownEvidence(analysis, ruleReport);
        if (!unknownEvidence.isEmpty()) {
            return GuardDecision.reject(List.of("G01: evidenceId không tồn tại: " + String.join(", ", unknownEvidence)));
        }
        if (!analysis.verdict().equals("UNKNOWN") && analysis.evidenceIds().isEmpty()) {
            return GuardDecision.reject(List.of("G01: kết luận " + analysis.verdict() + " không dẫn evidence nào"));
        }

        List<String> unsupported = unsupportedNumbers(analysis, ruleReport);
        if (!unsupported.isEmpty()) {
            return GuardDecision.reject(List.of("G05: số liệu không có trong bộ chỉ số đã tính: "
                    + String.join(", ", unsupported)));
        }

        return compareWithRule(analysis, ruleReport);
    }

    /** G02 (thiếu field / sai kiểu / field lạ) và G03 (giá trị enum ngoài taxonomy), tách bạch để báo đúng loại lỗi. */
    private List<String> schemaProblems(JsonNode node) {
        List<String> problems = new ArrayList<>();
        for (ValidationMessage message : schema.validate(node)) {
            String type = message.getType();
            if ("enum".equals(type)) {
                problems.add("G03: giá trị ngoài taxonomy: " + message.getMessage());
            } else {
                problems.add("G02: sai schema: " + message.getMessage());
            }
        }
        return problems;
    }

    private AiAnalysis toAnalysis(JsonNode node) {
        return new AiAnalysis(
                node.get("verdict").asText(),
                node.get("qualityFlag").asBoolean(),
                node.get("issueCategory").asText(),
                node.get("confidenceLevel").asText(),
                node.get("summary").asText(),
                strings(node.get("evidenceIds")),
                node.get("analysis").asText(),
                strings(node.get("suggestions")));
    }

    private List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asText()));
        return values;
    }

    private List<String> unknownEvidence(AiAnalysis analysis, Report rule) {
        Set<String> known = new LinkedHashSet<>();
        rule.evidence().forEach(e -> known.add(e.id()));
        Set<String> cited = new LinkedHashSet<>(analysis.evidenceIds());
        List<String> texts = new ArrayList<>(List.of(analysis.summary(), analysis.analysis()));
        texts.addAll(analysis.suggestions());
        for (String text : texts) {
            Matcher matcher = EVIDENCE_ID.matcher(text);
            while (matcher.find()) cited.add(matcher.group());
        }
        return cited.stream().filter(id -> !known.contains(id)).toList();
    }

    private List<String> unsupportedNumbers(AiAnalysis analysis, Report rule) {
        List<String> texts = new ArrayList<>(List.of(analysis.summary(), analysis.analysis()));
        texts.addAll(analysis.suggestions());
        return texts.stream().flatMap(text -> MetricReferences.inspect(text, rule).stream()).distinct().toList();
    }

    /**
     * Kiểm tra độc lập <b>report đã giao cho người dùng</b> (Unsupported Claim Rate, mục 6.5): trả về mọi số liệu và
     * evidence ID trong summary / analysis / suggestions mà không có trong chính chỉ số, evidence, giới hạn dữ liệu
     * của report đó. Không đi qua đường của AI nên là phép kiểm lại thật sự, không phải Guardrails tự chấm.
     */
    public static List<String> unsupportedClaimsIn(Report delivered) {
        List<String> texts = new ArrayList<>(List.of(delivered.summary(), delivered.analysis()));
        texts.addAll(delivered.suggestions());
        List<String> claims = new ArrayList<>(texts.stream()
                .flatMap(text -> MetricReferences.inspectDelivered(text, delivered).stream()).toList());
        Set<String> knownIds = new LinkedHashSet<>();
        delivered.evidence().forEach(e -> knownIds.add(e.id()));
        for (String text : texts) {
            Matcher matcher = EVIDENCE_ID.matcher(text);
            while (matcher.find()) {
                if (!knownIds.contains(matcher.group())) {
                    claims.add(matcher.group());
                }
            }
        }
        return claims.stream().distinct().toList();
    }

    /** G04. */
    private GuardDecision compareWithRule(AiAnalysis ai, Report rule) {
        List<String> flags = new ArrayList<>();
        String verdictOverride = null;
        if (!ai.verdict().equals(rule.verdict())) {
            verdictOverride = "UNKNOWN";
            flags.add("G04: verdict AI (" + ai.verdict() + ") mâu thuẫn rule (" + rule.verdict()
                    + "); đặt UNKNOWN, cần kiểm tra thủ công");
        } else {
            if (!ai.issueCategory().equals(rule.issueCategory())) {
                flags.add("G04: AI đề xuất nhóm vấn đề " + ai.issueCategory() + " khác rule ("
                        + rule.issueCategory() + "); giữ kết quả của rule");
            }
            if (ai.qualityFlag() != rule.qualityFlag()) {
                flags.add("G04: AI " + (ai.qualityFlag() ? "gắn" : "không gắn")
                        + " cờ chất lượng kém, khác rule; giữ kết quả của rule");
            }
        }
        GuardDecision.Outcome outcome = flags.isEmpty() ? GuardDecision.Outcome.ACCEPT
                : GuardDecision.Outcome.ACCEPT_WITH_FLAGS;
        return new GuardDecision(outcome, ai, List.of(), flags, verdictOverride);
    }
}
