package com.tutran.callassistant.application.ai;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tutran.callassistant.analysis.verdict.IssueCategoryRegistry;
import com.tutran.callassistant.application.request.ParsedRequest;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.domain.verdict.IssueCategoryDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public AiContextBuilder(IssueCategoryRegistry taxonomy) {
        this(taxonomy, "structured");
    }

    @Autowired
    public AiContextBuilder(IssueCategoryRegistry taxonomy,
                            @Value("${call-analysis.ai.context-mode:structured}") String mode) {
        this.taxonomy = taxonomy;
        this.mode = parse(mode);
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

        ObjectNode baseline = root.putObject("ruleBaseline");
        baseline.put("verdict", ruleReport.verdict());
        baseline.put("qualityFlag", ruleReport.qualityFlag());
        baseline.put("issueCategory", ruleReport.issueCategory());
        baseline.put("confidenceLevel", ruleReport.confidenceLevel());
        baseline.put("summary", ruleReport.summary());

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

    private void addTaxonomy(ArrayNode out) {
        for (IssueCategoryDefinition definition : taxonomy.all().values()) {
            ObjectNode node = out.addObject();
            node.put("category", definition.category().name());
            node.put("definition", definition.definition());
            ArrayNode symptoms = node.putArray("symptoms");
            definition.symptoms().forEach(symptoms::add);
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
        taxonomy.all().values().forEach(d -> line(text, "- " + d.category().name() + ": " + d.definition()));
        return text.toString();
    }

    private static void line(StringBuilder text, String value) {
        text.append(value).append('\n');
    }
}
