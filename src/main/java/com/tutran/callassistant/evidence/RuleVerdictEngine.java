package com.tutran.callassistant.evidence;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.metrics.CallMetrics;
import com.tutran.callassistant.metrics.LegQualityMetrics;
import com.tutran.callassistant.metrics.MetricResult;
import com.tutran.callassistant.taxonomy.IssueCategory;
import com.tutran.callassistant.taxonomy.IssueCategoryRegistry;
import com.tutran.callassistant.taxonomy.Verdict;
import com.tutran.callassistant.timeline.CallTimeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Baseline rule xác định (deterministic) cho verdict + issue category (PROJECT_SPEC.md
 * mục 4.1/4.2), chỉ dựng từ timeline và chỉ số mà code đã tính với độ chắc chắn 100% -
 * không có AI tham gia (mục 3.2: đây là tín hiệu rule mà verdict của AI sẽ được đối chiếu
 * vào ở Sprint 2).
 *
 * <p>Thứ tự quyết định: (1) không đủ dữ liệu -&gt; UNKNOWN; (2) cuộc gọi chưa từng đạt
 * trạng thái đã xác nhận -&gt; FAIL, issue category chọn theo evidence lỗi tầng WebRTC nếu
 * có, nếu không thì fallback về SIGNALING_FAILURE khi cuộc gọi chưa đi đủ xa; (3) cuộc gọi
 * đã xác nhận nhưng chưa quan sát được BYE -&gt; UNKNOWN (nhiều khả năng do dữ liệu bị cắt/
 * thiếu chứ không phải cuộc gọi thật đang diễn ra); (4) cuộc gọi đã xác nhận và kết thúc
 * bằng BYE -&gt; SUCCESS, gắn cờ chất lượng kém nếu chỉ số của một trong hai bên vượt
 * ngưỡng ở {@link IssueCategoryRegistry}.
 */
public final class RuleVerdictEngine {

    public RuleVerdictResult evaluate(CallTimeline timeline, CallMetrics metrics) {
        EvidenceEngine evidenceEngine = new EvidenceEngine();
        List<String> dataLimitations = new ArrayList<>();

        List<CanonicalEvent> signalingEvents = timeline.forSource(EventSource.SIGNALING);
        boolean hasCallerEndCall = !timeline.forLeg(Leg.CALLER).stream()
                .filter(e -> e.source() == EventSource.END_CALL).toList().isEmpty();
        boolean hasCalleeEndCall = !timeline.forLeg(Leg.CALLEE).stream()
                .filter(e -> e.source() == EventSource.END_CALL).toList().isEmpty();
        if (!hasCallerEndCall) {
            dataLimitations.add("Missing caller_endcall.log");
        }
        if (!hasCalleeEndCall) {
            dataLimitations.add("Missing callee_endcall.log");
        }

        if (signalingEvents.isEmpty()) {
            dataLimitations.add("No signaling data available for this call");
            return new RuleVerdictResult(Verdict.UNKNOWN, false, IssueCategory.UNKNOWN,
                    "Not enough evidence to conclude: no signaling data was found for this call.",
                    evidenceEngine.all(), dataLimitations);
        }
        if (!hasCallerEndCall && !hasCalleeEndCall) {
            return new RuleVerdictResult(Verdict.UNKNOWN, false, IssueCategory.UNKNOWN,
                    "Not enough evidence to conclude: end-call logs are missing for both legs.",
                    evidenceEngine.all(), dataLimitations);
        }

        Optional<CanonicalEvent> initCall = earliest(signalingEvents, "INIT_CALL");
        initCall.ifPresent(e -> evidenceEngine.add(e, "INIT_CALL: call setup started"));

        Optional<CanonicalEvent> confirmed = earliest(signalingEvents, "OK_ACK_OK");
        Optional<CanonicalEvent> bye = earliest(signalingEvents, "BYE");

        if (confirmed.isEmpty()) {
            return evaluateFail(timeline, evidenceEngine, dataLimitations);
        }
        evidenceEngine.add(confirmed.get(), "OK_ACK_OK: call confirmed, both legs connected");

        if (bye.isEmpty()) {
            dataLimitations.add("Call appears connected (OK_ACK_OK observed) but no BYE was found; "
                    + "signaling data may be truncated or incomplete");
            return new RuleVerdictResult(Verdict.UNKNOWN, false, IssueCategory.UNKNOWN,
                    "Not enough evidence to conclude: the call connected but its end could not be observed.",
                    evidenceEngine.all(), dataLimitations);
        }
        String terminatorText = metrics.terminator().isAvailable()
                ? metrics.terminator().value().name()
                : "unknown side";
        evidenceEngine.add(bye.get(), "BYE from " + terminatorText);

        return evaluateSuccess(timeline, metrics, evidenceEngine, dataLimitations);
    }

    private RuleVerdictResult evaluateFail(CallTimeline timeline, EvidenceEngine evidenceEngine,
                                            List<String> dataLimitations) {
        List<CanonicalEvent> webrtcEvents = timeline.forSource(EventSource.WEBRTC);

        // Ở đây chỉ tin vào đúng callback trạng thái kết thúc ICE có sẵn của engine - cách
        // so khớp từ khóa tự do "turn"/"ice" + "error"/"fail" đã được thử và bỏ đi (xem
        // Javadoc của classify() trong WebRtcLogParser): nó báo nhầm cả nhiễu giao thức
        // TURN bình thường, tự phục hồi được, thành lỗi ngay cả trên cuộc gọi SUCCESS
        // sạch, nên một bộ phát hiện TURN_FAILURE riêng, đáng tin cậy sẽ để lại sau
        // Sprint 1 (xem ghi chú điểm mơ hồ đã biết của ICE_FAILURE/TURN_FAILURE trong
        // IssueCategoryRegistry).
        Optional<CanonicalEvent> iceFailure = webrtcEvents.stream()
                .filter(e -> "ICE_CONNECTION_STATE_CHANGE".equals(e.eventType())
                        && matchesFailureKeyword(e.attribute("message")))
                .findFirst();

        IssueCategory category;
        String summary;
        if (iceFailure.isPresent()) {
            category = IssueCategory.ICE_FAILURE;
            evidenceEngine.add(iceFailure.get(), "WebRTC log reports an ICE connection failure");
            summary = "Call failed to establish: ICE connectivity failed in the WebRTC log.";
        } else {
            category = IssueCategory.SIGNALING_FAILURE;
            summary = "Call failed to establish: no OK_ACK_OK (confirmed) event was observed at the "
                    + "signaling layer, and no ICE connection failure evidence was found, so the call is "
                    + "attributed to a signaling-layer failure.";
        }

        return new RuleVerdictResult(Verdict.FAIL, false, category, summary, evidenceEngine.all(), dataLimitations);
    }

    private RuleVerdictResult evaluateSuccess(CallTimeline timeline, CallMetrics metrics,
                                               EvidenceEngine evidenceEngine, List<String> dataLimitations) {
        QualityAssessment quality = assessQuality(metrics, evidenceEngine);
        String summary = quality.flagged
                ? "Call established and ended normally, but quality degraded (" + quality.category + ")."
                : "Call established and ended normally with no quality issues detected.";
        return new RuleVerdictResult(Verdict.SUCCESS, quality.flagged, quality.category, summary,
                evidenceEngine.all(), dataLimitations);
    }

    private QualityAssessment assessQuality(CallMetrics metrics, EvidenceEngine evidenceEngine) {
        for (Leg leg : List.of(Leg.CALLER, Leg.CALLEE)) {
            LegQualityMetrics quality = metrics.qualityByLeg().get(leg);
            if (quality == null) {
                continue;
            }
            if (breaches(quality.packetLossPercent(), IssueCategoryRegistry.PACKET_LOSS_WARN_PERCENT)) {
                return new QualityAssessment(true, IssueCategory.NETWORK_PACKET_LOSS,
                        leg + " packet loss " + quality.packetLossPercent().value() + "% exceeds "
                                + IssueCategoryRegistry.PACKET_LOSS_WARN_PERCENT + "%");
            }
        }
        for (Leg leg : List.of(Leg.CALLER, Leg.CALLEE)) {
            LegQualityMetrics quality = metrics.qualityByLeg().get(leg);
            if (quality == null) {
                continue;
            }
            if (breaches(quality.jitterMs(), IssueCategoryRegistry.JITTER_WARN_MS)
                    || breaches(quality.rttMs(), IssueCategoryRegistry.RTT_WARN_MS)) {
                return new QualityAssessment(true, IssueCategory.NETWORK_DELAY_JITTER,
                        leg + " jitter/RTT exceeds warning thresholds");
            }
        }
        for (Leg leg : List.of(Leg.CALLER, Leg.CALLEE)) {
            LegQualityMetrics quality = metrics.qualityByLeg().get(leg);
            if (quality != null && breachesLowerBound(quality.mos(), IssueCategoryRegistry.MOS_WARN)) {
                return new QualityAssessment(true, IssueCategory.UNKNOWN,
                        leg + " MOS " + quality.mos().value() + " is below " + IssueCategoryRegistry.MOS_WARN
                                + " but no specific network metric explains it");
            }
        }
        return new QualityAssessment(false, IssueCategory.UNKNOWN, null);
    }

    private boolean breaches(MetricResult<Double> metric, double warnThreshold) {
        return metric.isAvailable() && metric.value() > warnThreshold;
    }

    private boolean breachesLowerBound(MetricResult<Double> metric, double warnThreshold) {
        return metric.isAvailable() && metric.value() < warnThreshold;
    }

    private boolean matchesFailureKeyword(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("failed") || lower.contains("disconnected");
    }

    private Optional<CanonicalEvent> earliest(List<CanonicalEvent> events, String eventType) {
        return events.stream()
                .filter(e -> eventType.equals(e.eventType()))
                .filter(e -> e.timestamp() != null)
                .min((a, b) -> a.timestamp().compareTo(b.timestamp()));
    }

    private record QualityAssessment(boolean flagged, IssueCategory category, String reason) {
    }
}
