package com.tutran.callassistant.application;

import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;

import java.util.List;

/**
 * Kết quả phân tích rule-based <b>kèm các bước trung gian</b>: AI Analysis Engine cần timeline để dựng
 * context, còn CLI chỉ cần report nên dùng {@link AnalysisOutcome}.
 */
public record CallAnalysisDetail(CallTimeline timeline,
                                 CallMetrics metrics,
                                 RuleVerdictResult verdict,
                                 Report report,
                                 List<String> processingWarnings) {

    public CallAnalysisDetail {
        processingWarnings = List.copyOf(processingWarnings);
    }

    public AnalysisOutcome toOutcome() {
        return new AnalysisOutcome(report, processingWarnings);
    }
}
