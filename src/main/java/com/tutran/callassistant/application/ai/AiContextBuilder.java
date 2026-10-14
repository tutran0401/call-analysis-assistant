package com.tutran.callassistant.application.ai;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tutran.callassistant.analysis.verdict.IssueCategoryRegistry;
import com.tutran.callassistant.application.request.ParsedRequest;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.IssueCategoryDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Dựng "minimum necessary context" cho AI (PROJECT_SPEC.md mục 3.3): chỉ timeline rút gọn, evidence,
 * chỉ số, giới hạn dữ liệu và taxonomy - <b>không bao giờ</b> {@code rawLine}/{@code attributes} của
 * event (nơi chứa SDP, IP, ID). Câu hỏi gốc của người dùng cũng không được chuyển đi; AI chỉ nhận
 * intent + focus đã phân loại, nên văn bản tự do của người dùng không là đường rò rỉ.
 *
 * <p>Timeline cố tình không mang mốc thời gian: mọi con số AI được phép nhắc đã nằm trong metrics và
 * evidence, giúp Guardrails G05 kiểm "số liệu ngoài bộ chỉ số" một cách đơn giản và chặt.
 *
 * <p>Hai cách dựng (T11, {@code call-analysis.ai.context-mode}): {@code structured} (mặc định) - evidence, chỉ số,
 * timeline, taxonomy là các mảng JSON; {@code semi} - cùng nội dung nhưng viết thành một đoạn văn bản trong field
 * {@code narrative}. {@code ruleBaseline}, intent và focus luôn ở dạng có cấu trúc. Dùng để đo cách nào cho
 * accuracy / consistency / token / latency tốt hơn.
 */
@Component
public class AiContextBuilder {

    static final int MAX_TIMELINE_ENTRIES = 80;

    /** Cách đưa evidence / chỉ số cho AI. */
    public enum Mode { STRUCTURED, SEMI }

    private final IssueCategoryRegistry taxonomy;
    private final Mode mode;
    private final boolean includeRuleBaseline;

    public AiContextBuilder(IssueCategoryRegistry taxonomy) {
        this(taxonomy, "structured");
    }

    public AiContextBuilder(IssueCategoryRegistry taxonomy, String mode) {
        this(taxonomy, mode, true);
    }

    /**
     * @param includeRuleBaseline {@code false} để đo AI độc lập với rule (Sprint 3 T1): AI không thấy kết luận
     *                            của rule, chỉ thấy evidence / chỉ số / timeline. Guardrails vẫn đối chiếu với
     *                            rule như cũ, nên chính sách G04 lần đầu được thử thật.
     */
    @Autowired
    public AiContextBuilder(IssueCategoryRegistry taxonomy,
                            @Value("${call-analysis.ai.context-mode:structured}") String mode,
                            @Value("${call-analysis.ai.include-rule-baseline:true}") boolean includeRuleBaseline) {
        this.taxonomy = taxonomy;
        this.mode = parse(mode);
        this.includeRuleBaseline = includeRuleBaseline;
    }

    public boolean includesRuleBaseline() {
        return includeRuleBaseline;
    }

    private static Mode parse(String value) {
        try {
            return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("call-analysis.ai.context-mode must be structured or semi: " + value);
        }
    }

    public Mode mode() {
        return mode;
    }

    /** @param ruleReport report rule-based đã assemble - nguồn duy nhất của verdict baseline, evidence và chỉ số */
    public ObjectNode build(ParsedRequest request, CallTimeline timeline, Report ruleReport) {
        Objects.requireNonNull(ruleReport, "ruleReport");
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode root = f.objectNode();
        root.put("intent", request.intent().name());
        ArrayNode focus = root.putArray("focus");
        request.focus().stream().map(Enum::name).sorted().forEach(focus::add);

        if (includeRuleBaseline) {
            ObjectNode baseline = root.putObject("ruleBaseline");
            baseline.put("verdict", ruleReport.verdict());
            baseline.put("qualityFlag", ruleReport.qualityFlag());
            baseline.put("issueCategory", ruleReport.issueCategory());
            baseline.put("confidenceLevel", ruleReport.confidenceLevel());
            baseline.put("summary", ruleReport.summary());
        }

        if (mode == Mode.SEMI) {
            root.put("narrative", narrative(timeline, ruleReport));
            return root;
        }

        ArrayNode evidence = root.putArray("evidence");
        ruleReport.evidence().forEach(e -> evidence.addObject()
                .put("id", e.id()).put("source", e.source()).put("time", e.timestamp())
                .put("description", e.description()));

        ArrayNode metrics = root.putArray("metrics");
        ruleReport.metrics().forEach(m -> metrics.addObject()
                .put("name", m.name()).put("value", m.value()).put("source", m.source()));

        ArrayNode limitations = root.putArray("dataLimitations");
        ruleReport.dataLimitations().forEach(limitations::add);

        addTimeline(root.putArray("timeline"), timeline);
        addTaxonomy(root.putArray("taxonomy"));
        return root;
    }

    /** Bỏ sự kiện lặp liên tiếp (cùng leg/nguồn/loại) rồi cắt cho đủ nhỏ. */
    private void addTimeline(ArrayNode out, CallTimeline timeline) {
        String previous = null;
        int count = 0;
        for (CanonicalEvent event : timeline.events()) {
            String key = event.leg() + "|" + event.source() + "|" + event.eventType();
            if (key.equals(previous)) {
                continue;
            }
            previous = key;
            if (count++ >= MAX_TIMELINE_ENTRIES) {
                break;
            }
            out.addObject().put("leg", event.leg().name()).put("source", event.source().name())
                    .put("event", event.eventType());
        }
    }

    /**
     * Thứ tự cố định theo enum (Sprint 3 T1): {@code taxonomy.all()} là {@code Map.of}, thứ tự lặp đổi giữa các lần
     * khởi động JVM - tức là cùng một cuộc gọi có thể nhận prompt khác nhau, làm giảm Consistency.
     */
    private List<IssueCategoryDefinition> orderedTaxonomy() {
        return Arrays.stream(IssueCategory.values()).map(taxonomy::definitionOf).filter(Objects::nonNull).toList();
    }

    private void addTaxonomy(ArrayNode out) {
        for (IssueCategoryDefinition definition : orderedTaxonomy()) {
            ObjectNode node = out.addObject();
            node.put("category", definition.category().name());
            node.put("definition", definition.definition());
            ArrayNode symptoms = node.putArray("symptoms");
            definition.symptoms().forEach(symptoms::add);
            // Nguồn evidence bắt buộc (không chứa ngưỡng số, nên không gợi AI nhắc số liệu ngoài bộ chỉ số - G05).
            ArrayNode required = node.putArray("requiredEvidence");
            definition.requiredEvidence().forEach(required::add);
            if (definition.knownAmbiguity() != null) {
                node.put("knownAmbiguity", definition.knownAmbiguity());
            }
        }
    }

    /** Cùng dữ kiện với dạng có cấu trúc, trình bày như một đoạn báo cáo văn bản. */
    private String narrative(CallTimeline timeline, Report report) {
        StringBuilder text = new StringBuilder();
        line(text, "Evidence:");
        if (report.evidence().isEmpty()) {
            line(text, "(không có)");
        }
        report.evidence().forEach(e -> line(text, "[" + e.id() + "][" + e.source() + " " + e.timestamp() + "] "
                + e.description()));

        line(text, "");
        line(text, "Chỉ số:");
        report.metrics().forEach(m -> line(text, "- " + m.name() + ": " + m.value() + " (" + m.source() + ")"));

        line(text, "");
        line(text, "Giới hạn dữ liệu:");
        if (report.dataLimitations().isEmpty()) {
            line(text, "(không có)");
        }
        report.dataLimitations().forEach(l -> line(text, "- " + l));

        line(text, "");
        line(text, "Diễn biến:");
        ArrayNode events = JsonNodeFactory.instance.arrayNode();
        addTimeline(events, timeline);
        events.forEach(e -> line(text, e.get("leg").asText() + "/" + e.get("source").asText() + ": "
                + e.get("event").asText()));

        line(text, "");
        line(text, "Các nhóm vấn đề:");
        orderedTaxonomy().forEach(d -> line(text, "- " + d.category().name() + ": " + d.definition()
                + " Evidence cần có: " + String.join("; ", d.requiredEvidence()) + "."));
        return text.toString();
    }

    private static void line(StringBuilder text, String value) {
        text.append(value).append('\n');
    }
}
