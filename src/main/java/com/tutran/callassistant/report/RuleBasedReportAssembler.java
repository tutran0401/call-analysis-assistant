package com.tutran.callassistant.report;

import com.tutran.callassistant.analysis.verdict.IssueCategoryRegistry;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.report.EvidenceItem;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.domain.verdict.Evidence;
import com.tutran.callassistant.domain.verdict.IssueCategoryDefinition;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import com.tutran.callassistant.report.confidence.ConfidenceAssessment;
import com.tutran.callassistant.report.confidence.ConfidencePolicy;
import com.tutran.callassistant.report.confidence.EvidenceBasedConfidencePolicy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dựng report từ output của rule engine. Sprint 1 chưa có AI: verdict theo rule trực tiếp điền mọi
 * field mà mẫu report (mục 4.5) cần.
 *
 * <p>Ba việc phụ trợ được giao ra ngoài - độ tin cậy ({@link ConfidencePolicy}), bảng chỉ số
 * ({@link MetricRowCatalog}), đề xuất ({@link SuggestionCatalog}) - nên bản thân class này chỉ còn
 * đúng phần "report gồm những gì", đọc gần như là chính cái mẫu report.
 */
@Component
public class RuleBasedReportAssembler implements ReportAssembler {

    private final ConfidencePolicy confidencePolicy;
    private final MetricRowCatalog metricRowCatalog;
    private final SuggestionCatalog suggestionCatalog;
    private final IssueCategoryRegistry issueCategories;
    private final VerdictExplainer explainer;

    public RuleBasedReportAssembler(ConfidencePolicy confidencePolicy,
                                    MetricRowCatalog metricRowCatalog,
                                    SuggestionCatalog suggestionCatalog,
                                    IssueCategoryRegistry issueCategories,
                                    VerdictExplainer explainer) {
        this.confidencePolicy = confidencePolicy;
        this.metricRowCatalog = metricRowCatalog;
        this.suggestionCatalog = suggestionCatalog;
        this.issueCategories = issueCategories;
        this.explainer = explainer;
    }

    /** Bộ mặc định, dùng cho test và chỗ nào không có Spring container. */
    public static RuleBasedReportAssembler withDefaults() {
        return new RuleBasedReportAssembler(new EvidenceBasedConfidencePolicy(),
                new MetricRowCatalog(), new SuggestionCatalog(), IssueCategoryRegistry.withDefaults(),
                new VerdictExplainer());
    }

    @Override
    public Report assemble(String callId, RuleVerdictResult verdict, CallMetrics metrics) {
        // Issue category LUÔN được nêu, kể cả cuộc gọi sạch (khi đó là NONE) - để người đọc phân
        // biệt được "đã kiểm tra, không có vấn đề" với "chưa kiểm tra". Riêng đề xuất thì vẫn chỉ
        // kèm khi thật sự có vấn đề, tránh bịa việc cho một cuộc gọi hoàn toàn bình thường.
        boolean reportIssue = verdict.hasIssueToReport();
        ConfidenceAssessment confidence = confidencePolicy.assess(verdict);
        List<String> suggestions = new ArrayList<>(reportIssue
                ? suggestionCatalog.suggestionsFor(verdict.issueCategory()) : List.of());
        suggestionCatalog.forDataLimitations(verdict.dataLimitations()).stream()
                .filter(s -> !suggestions.contains(s)).forEach(suggestions::add);
        return new Report(
                callId,
                verdict.verdict().name(),
                verdict.qualityFlag(),
                verdict.issueCategory().name(),
                alternativeCausesFor(verdict),
                confidence.level().name(),
                verdict.summary(),
                verdict.evidence().stream().map(this::toEvidenceItem).toList(),
                metricRowCatalog.rowsFor(metrics),
                suggestions,
                verdict.dataLimitations(),
                "",
                false,
                confidence.reasons(),
                explainer.explain(verdict, suggestions)
        );
    }

    /**
     * Dòng "Khả dĩ khác" của mẫu report (mục 4.5): kết luận này còn có thể nhầm với cái gì.
     *
     * <p>Lấy thẳng từ phần "điểm mơ hồ đã biết" của taxonomy (T5) - nhờ vậy taxonomy không còn là tài
     * liệu chết mà thật sự xuất hiện trong report, và hai nơi không thể nói khác nhau.
     */
    private List<String> alternativeCausesFor(RuleVerdictResult verdict) {
        IssueCategoryDefinition definition = issueCategories.definitionOf(verdict.issueCategory());
        if (definition == null || definition.knownAmbiguity() == null
                || definition.knownAmbiguity().isBlank()) {
            return List.of();
        }
        return List.of(definition.knownAmbiguity());
    }

    private EvidenceItem toEvidenceItem(Evidence evidence) {
        return new EvidenceItem(evidence.id(), evidence.sourceLabel(), evidence.timestampDisplay(),
                evidence.description());
    }
}
