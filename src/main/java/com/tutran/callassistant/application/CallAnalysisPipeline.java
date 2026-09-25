package com.tutran.callassistant.application;

import com.tutran.callassistant.analysis.metrics.CallMetricsCalculator;
import com.tutran.callassistant.analysis.timeline.TimelineBuilder;
import com.tutran.callassistant.analysis.verdict.VerdictEngine;
import com.tutran.callassistant.application.port.in.AnalyzeCallUseCase;
import com.tutran.callassistant.application.port.out.ClientLogSource;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import com.tutran.callassistant.report.ReportAssembler;
import com.tutran.callassistant.report.ReportGuard;
import org.springframework.stereotype.Service;

/**
 * <b>Toàn bộ hệ thống, đọc một lượt là hiểu.</b> Mỗi dòng của {@link #analyze} là một
 * thành phần trong kiến trúc mục tiêu ở PROJECT_SPEC.md mục 3.1, theo đúng thứ tự:
 *
 * <pre>
 *   nguồn dữ liệu  -&gt;  Log Normalizer  -&gt;  Timeline Builder  -&gt;  Metrics Calculator
 *                  -&gt;  Evidence Engine + Rule Verdict  -&gt;  Report  -&gt;  Guardrails
 * </pre>
 *
 * <p>Lớp này chỉ làm đúng một việc: <i>ghép thứ tự các bước</i>. Nó không biết log có định
 * dạng gì (việc của adapter {@code ingest}), không biết rule nào quyết định verdict (việc
 * của {@code analysis.verdict}), và không biết report được in ra bằng gì (việc của
 * {@link com.tutran.callassistant.application.port.out.ReportPresenter}). Vì vậy các chỗ
 * Sprint 2 phải cắm thêm vào đều nhìn thấy rõ: Sanitizer + AI Analysis Engine nằm giữa
 * {@code metrics} và {@code assembler}, còn Guardrails chính là {@link ReportGuard} đã có
 * sẵn ở cuối.
 */
@Service
public class CallAnalysisPipeline implements AnalyzeCallUseCase {

    private final SignalingSourceResolver signalingSources;
    private final ClientLogSource clientLogSource;
    private final TimelineBuilder timelineBuilder;
    private final CallMetricsCalculator metricsCalculator;
    private final VerdictEngine verdictEngine;
    private final ReportAssembler reportAssembler;
    private final ReportGuard reportGuard;

    public CallAnalysisPipeline(SignalingSourceResolver signalingSources,
                                ClientLogSource clientLogSource,
                                TimelineBuilder timelineBuilder,
                                CallMetricsCalculator metricsCalculator,
                                VerdictEngine verdictEngine,
                                ReportAssembler reportAssembler,
                                ReportGuard reportGuard) {
        this.signalingSources = signalingSources;
        this.clientLogSource = clientLogSource;
        this.timelineBuilder = timelineBuilder;
        this.metricsCalculator = metricsCalculator;
        this.verdictEngine = verdictEngine;
        this.reportAssembler = reportAssembler;
        this.reportGuard = reportGuard;
    }

    @Override
    public AnalysisOutcome analyze(CallReference call, SignalingSourcePreference signalingSource) {
        NormalizedEvents signaling = signalingSources.resolve(signalingSource).fetch(call);
        NormalizedEvents clientLogs = clientLogSource.load(call);
        NormalizedEvents ingested = signaling.combine(clientLogs);

        CallTimeline timeline = timelineBuilder.build(call.callId(), ingested.events());
        CallMetrics metrics = metricsCalculator.calculate(timeline);
        RuleVerdictResult verdict = verdictEngine.evaluate(timeline, metrics);
        Report report = reportAssembler.assemble(call.callId(), verdict, metrics);

        NormalizedEvents checked = ingested.withExtraWarnings(reportGuard.inspect(report));
        return new AnalysisOutcome(report, checked.warnings());
    }
}
