package com.tutran.callassistant.application;

import com.tutran.callassistant.application.guardrail.AiAnalysis;
import com.tutran.callassistant.application.guardrail.GuardDecision;
import com.tutran.callassistant.domain.report.EvidenceItem;
import com.tutran.callassistant.domain.report.Report;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ghép report cuối cùng từ report rule-based và phân tích AI đã qua Guardrails.
 *
 * <p>Nguyên tắc phân vai (mục 3.2): AI chỉ được thay <b>lời văn</b> - summary, analysis, suggestions.
 * Verdict, issue category, cờ chất lượng, evidence, chỉ số, độ tin cậy luôn là của code; riêng khi AI
 * mâu thuẫn rule thì verdict thành {@code UNKNOWN} và độ tin cậy hạ xuống {@code LOW}.
 */
@Component
public class AnalysisReportComposer {

    static final String DEGRADED_NOTE =
            "Phân tích AI không khả dụng; báo cáo này hoàn toàn dựa trên rule-based.";

    /** Fallback: report rule-based, đánh dấu degraded. */
    public Report degraded(Report rule) {
        List<String> limitations = new ArrayList<>(rule.dataLimitations());
        addIfAbsent(limitations, DEGRADED_NOTE);
        return new Report(rule.callId(), rule.verdict(), rule.qualityFlag(), rule.issueCategory(),
                rule.alternativeCauses(), rule.confidenceLevel(), rule.summary(), rule.evidence(),
                rule.metrics(), rule.suggestions(), limitations, "", true);
    }

    /** @param decision phải khác REJECT */
    public Report fromAi(Report rule, GuardDecision decision) {
        if (decision.isRejected() || decision.analysis() == null) {
            throw new IllegalArgumentException("Cannot compose a report from a rejected AI response");
        }
        AiAnalysis ai = decision.analysis();
        boolean conflict = decision.verdictOverride() != null;

        List<String> limitations = new ArrayList<>(rule.dataLimitations());
        decision.flags().forEach(flag -> addIfAbsent(limitations, flag));

        String verdict = conflict ? decision.verdictOverride() : rule.verdict();
        String category = conflict ? "UNKNOWN" : rule.issueCategory();
        boolean qualityFlag = !conflict && rule.qualityFlag();
        String confidence = decision.flags().isEmpty() ? rule.confidenceLevel() : "LOW";

        // Khi mâu thuẫn, lời văn AI đang bảo vệ đúng cái verdict bị bác nên không dùng; dùng câu cố định.
        String summary = conflict
                ? "Kết luận rule-based (" + rule.verdict() + ") và AI (" + ai.verdict()
                + ") mâu thuẫn nên cuộc gọi được đánh dấu UNKNOWN, cần kiểm tra thủ công."
                : (ai.summary().isBlank() ? rule.summary() : ai.summary());
        String analysis = conflict ? "" : ai.analysis();

        List<String> suggestions = conflict ? rule.suggestions() : nonBlank(ai.suggestions());
        if (suggestions.isEmpty()) {
            suggestions = rule.suggestions();
        }

        return new Report(rule.callId(), verdict, qualityFlag, category, rule.alternativeCauses(), confidence,
                summary, citedFirst(rule.evidence(), ai.evidenceIds()), rule.metrics(), suggestions, limitations,
                analysis, false);
    }

    private static List<EvidenceItem> citedFirst(List<EvidenceItem> evidence, List<String> citedIds) {
        Set<String> cited = new LinkedHashSet<>(citedIds);
        List<EvidenceItem> ordered = new ArrayList<>();
        cited.forEach(id -> evidence.stream().filter(e -> e.id().equals(id)).findFirst().ifPresent(ordered::add));
        evidence.stream().filter(e -> !cited.contains(e.id())).forEach(ordered::add);
        return ordered;
    }

    private static List<String> nonBlank(List<String> values) {
        return values.stream().filter(v -> !v.isBlank()).toList();
    }

    private static void addIfAbsent(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }
}
