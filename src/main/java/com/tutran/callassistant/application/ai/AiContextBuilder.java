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
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Dựng "minimum necessary context" cho AI (PROJECT_SPEC.md mục 3.3): chỉ timeline rút gọn, evidence,
 * chỉ số, giới hạn dữ liệu và taxonomy - <b>không bao giờ</b> {@code rawLine}/{@code attributes} của
 * event (nơi chứa SDP, IP, ID). Câu hỏi gốc của người dùng cũng không được chuyển đi; AI chỉ nhận
 * intent + focus đã phân loại, nên văn bản tự do của người dùng không là đường rò rỉ.
 *
 * <p>Timeline cố tình không mang mốc thời gian: mọi con số AI được phép nhắc đã nằm trong metrics và
 * evidence, giúp Guardrails G05 kiểm "số liệu ngoài bộ chỉ số" một cách đơn giản và chặt.
 */
@Component
public class AiContextBuilder {

    static final int MAX_TIMELINE_ENTRIES = 80;

    private final IssueCategoryRegistry taxonomy;

    public AiContextBuilder(IssueCategoryRegistry taxonomy) {
        this.taxonomy = taxonomy;
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
}
