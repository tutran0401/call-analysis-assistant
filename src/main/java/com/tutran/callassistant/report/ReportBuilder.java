package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.evidence.Evidence;
import com.tutran.callassistant.evidence.RuleVerdictResult;
import com.tutran.callassistant.metrics.CallMetrics;
import com.tutran.callassistant.metrics.LegQualityMetrics;
import com.tutran.callassistant.metrics.MetricResult;
import com.tutran.callassistant.taxonomy.IssueCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dựng một {@link Report} từ output của rule engine. Sprint 1 chưa có AI: verdict theo
 * rule trực tiếp điền mọi field mà mẫu report (mục 4.5) cần.
 */
public final class ReportBuilder {

    private static final Map<IssueCategory, List<String>> BASELINE_SUGGESTIONS = Map.of(
            IssueCategory.NETWORK_PACKET_LOSS, List.of(
                    "Kiểm tra chất lượng mạng của bên bị ảnh hưởng trong thời điểm cuộc gọi."),
            IssueCategory.NETWORK_DELAY_JITTER, List.of(
                    "Kiểm tra độ trễ/jitter mạng của bên bị ảnh hưởng; bổ sung RTT/jitter nếu log hiện thiếu."),
            IssueCategory.ICE_FAILURE, List.of(
                    "Bổ sung đầy đủ webrtc.log của cả hai bên để kiểm tra chi tiết quá trình ICE."),
            IssueCategory.TURN_FAILURE, List.of(
                    "Kiểm tra khả năng kết nối tới TURN server từ phía client bị lỗi."),
            IssueCategory.SIGNALING_FAILURE, List.of(
                    "Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân."),
            IssueCategory.UNKNOWN, List.of(
                    "Bổ sung thêm log/dữ liệu liên quan để có đủ căn cứ kết luận nguyên nhân cụ thể.")
    );

    public Report build(String callId, RuleVerdictResult result, CallMetrics metrics) {
        ConfidenceLevel confidence = ConfidenceLevel.derive(result);

        List<EvidenceItem> evidenceItems = result.evidence().stream()
                .map(this::toEvidenceItem)
                .toList();

        List<MetricRow> metricRows = buildMetricRows(metrics);

        List<String> suggestions = new ArrayList<>();
        if (result.verdict() != com.tutran.callassistant.taxonomy.Verdict.SUCCESS || result.qualityFlag()) {
            suggestions.addAll(BASELINE_SUGGESTIONS.getOrDefault(result.issueCategory(), List.of()));
        }

        String issueCategory = (result.verdict() != com.tutran.callassistant.taxonomy.Verdict.SUCCESS || result.qualityFlag())
                ? result.issueCategory().name()
                : null;

        return new Report(
                callId,
                result.verdict().name(),
                result.qualityFlag(),
                issueCategory,
                confidence.name(),
                result.summary(),
                evidenceItems,
                metricRows,
                suggestions,
                result.dataLimitations()
        );
    }

    private EvidenceItem toEvidenceItem(Evidence evidence) {
        return new EvidenceItem(evidence.id(), evidence.sourceLabel(), evidence.timestampDisplay(),
                evidence.description());
    }

    private List<MetricRow> buildMetricRows(CallMetrics metrics) {
        List<MetricRow> rows = new ArrayList<>();
        rows.add(durationRow("Thời gian thiết lập", metrics.setupTime()));
        rows.add(durationRow("Thời gian với tới callee (INVITE→TRYING)", metrics.timeToReachCallee()));
        rows.add(genericRow("Số lần gửi lại INVITE", metrics.inviteRetransmitCount()));
        rows.add(genericRow("Số lần \"No sessions found\"", metrics.noSessionsFoundCount()));
        rows.add(durationRow("Thời gian đổ chuông", metrics.ringingTime()));
        rows.add(durationRow("Thời lượng kết nối", metrics.connectedDuration()));
        rows.add(legRow("Bên kết thúc", metrics.terminator()));
        rows.add(genericRow("Số lần gửi lại BYE", metrics.byeRetransmitCount()));

        for (Leg leg : List.of(Leg.CALLER, Leg.CALLEE)) {
            LegQualityMetrics quality = metrics.qualityByLeg().get(leg);
            if (quality == null) {
                continue;
            }
            String legLabel = leg.name().toLowerCase(Locale.ROOT);
            rows.add(genericRow("MOS (" + legLabel + ")", quality.mos()));
            rows.add(genericRow("Packet loss (" + legLabel + ")", quality.packetLossPercent()));
            rows.add(genericRow("RTT (" + legLabel + ")", quality.rttMs()));
            rows.add(genericRow("Jitter (" + legLabel + ")", quality.jitterMs()));
            rows.add(genericRow("Sự kiện ICE/TURN (" + legLabel + ")", quality.webrtcKeyEvents()));
        }
        return rows;
    }

    private MetricRow durationRow(String name, MetricResult<java.time.Duration> metric) {
        String value = metric.isAvailable()
                ? String.format(Locale.ROOT, "%.3f s", metric.value().toMillis() / 1000.0)
                : "N/A (" + metric.naReason() + ")";
        return new MetricRow(name, value, metric.source());
    }

    private MetricRow legRow(String name, MetricResult<Leg> metric) {
        String value = metric.isAvailable() ? metric.value().name() : "N/A (" + metric.naReason() + ")";
        return new MetricRow(name, value, metric.source());
    }

    private MetricRow genericRow(String name, MetricResult<?> metric) {
        return new MetricRow(name, metric.display(), metric.source());
    }
}
