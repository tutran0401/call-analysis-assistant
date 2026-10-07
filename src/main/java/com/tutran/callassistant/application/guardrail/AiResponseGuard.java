package com.tutran.callassistant.application.guardrail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.tutran.callassistant.application.ai.AiPromptFactory;
import com.tutran.callassistant.domain.report.EvidenceItem;
import com.tutran.callassistant.domain.report.MetricRow;
import com.tutran.callassistant.domain.report.Report;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
 * <p><b>Về G05:</b> số nguyên một chữ số đứng một mình ("2 bên", "1 lần") không bị coi là số liệu vì AI
 * dùng chúng như đếm thông thường; mọi số thập phân, số từ hai chữ số trở lên, hay số kèm đơn vị
 * ({@code %}, {@code ms}, {@code s}, giây, gói, lần) đều phải xuất hiện trong dữ liệu đầu vào.
 * <b>Làm tròn trung thực được chấp nhận</b>: {@code 4.4} là hợp lệ khi chỉ số thật là {@code 4.40337}, vì đó
 * không phải số liệu mới. Số đã cho được coi là bản làm tròn của một giá trị có thật khi nó ít chữ số thập phân
 * hơn giá trị đó và lệch không quá nửa đơn vị ở chữ số cuối cùng của nó. Đo thực tế với gpt-4o-mini: nếu bắt
 * khớp chuỗi tuyệt đối thì khoảng 40% report hợp lệ bị loại chỉ vì AI viết MOS 4.4 thay vì 4.40337.
 */
@Component
public class AiResponseGuard {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern EVIDENCE_ID = Pattern.compile("\\bEV\\d{3}\\b");
    private static final Pattern UNIT_AFTER = Pattern.compile("^\\s*(?:%|ms\\b|s\\b|giây|gói|lần|kbps|phút)",
            Pattern.CASE_INSENSITIVE);

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
        return analysis.evidenceIds().stream().filter(id -> !known.contains(id)).distinct().toList();
    }

    private List<String> unsupportedNumbers(AiAnalysis analysis, Report rule) {
        Set<String> allowed = allowedNumbers(rule);
        List<BigDecimal> sourceValues = allowed.stream().map(BigDecimal::new).toList();
        List<String> unsupported = new ArrayList<>();
        List<String> texts = new ArrayList<>(List.of(analysis.summary(), analysis.analysis()));
        texts.addAll(analysis.suggestions());
        for (String text : texts) {
            String cleaned = EVIDENCE_ID.matcher(text).replaceAll(" ");
            Matcher matcher = NUMBER.matcher(cleaned);
            while (matcher.find()) {
                String token = matcher.group();
                boolean decimal = token.contains(".") || token.contains(",");
                boolean unit = UNIT_AFTER.matcher(cleaned.substring(matcher.end())).find();
                if ((decimal || token.length() >= 2 || unit) && !allowed.contains(normalise(token))
                        && !isFaithfulRounding(token, sourceValues)) {
                    unsupported.add(token);
                }
            }
        }
        return unsupported.stream().distinct().toList();
    }

    /**
     * {@code token} là bản làm tròn của một giá trị nguồn: ít chữ số thập phân hơn nguồn và lệch không quá nửa
     * đơn vị ở chữ số cuối của chính nó (ví dụ 4.4 ~ 4.40337; 11 ~ 11.3208; nhưng 4.5 !~ 4.40337).
     */
    private static boolean isFaithfulRounding(String token, List<BigDecimal> sourceValues) {
        BigDecimal rounded = new BigDecimal(token.replace(',', '.'));
        int decimals = Math.max(0, rounded.scale());
        BigDecimal halfUnit = BigDecimal.valueOf(5, decimals + 1);
        for (BigDecimal source : sourceValues) {
            if (source.stripTrailingZeros().scale() > decimals
                    && source.subtract(rounded).abs().compareTo(halfUnit) <= 0) {
                return true;
            }
        }
        return false;
    }

    /** Mọi số xuất hiện trong dữ liệu code đã tính: chỉ số, evidence, giới hạn dữ liệu và tóm tắt của rule. */
    private Set<String> allowedNumbers(Report rule) {
        Set<String> allowed = new LinkedHashSet<>();
        List<String> sources = new ArrayList<>();
        sources.add(rule.summary());
        for (MetricRow metric : rule.metrics()) {
            sources.add(metric.name());
            sources.add(metric.value());
        }
        for (EvidenceItem evidence : rule.evidence()) {
            sources.add(evidence.description());
            sources.add(evidence.timestamp());
            sources.add(evidence.source());
        }
        sources.addAll(rule.dataLimitations());
        sources.addAll(rule.alternativeCauses());
        for (String source : sources) {
            Matcher matcher = NUMBER.matcher(EVIDENCE_ID.matcher(source == null ? "" : source).replaceAll(" "));
            while (matcher.find()) {
                allowed.add(normalise(matcher.group()));
                // "06.320" (mili-giây) cho phép cả "06": AI hay viết lại mốc thời gian tới đơn vị giây.
                allowed.add(normalise(matcher.group().split("[.,]")[0]));
            }
        }
        return allowed;
    }

    private static String normalise(String number) {
        return new BigDecimal(number.replace(',', '.')).stripTrailingZeros().toPlainString();
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
