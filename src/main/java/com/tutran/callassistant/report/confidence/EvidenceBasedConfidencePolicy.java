package com.tutran.callassistant.report.confidence;

import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import com.tutran.callassistant.domain.report.ConfidenceLevel;
import com.tutran.callassistant.domain.verdict.Evidence;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import com.tutran.callassistant.domain.verdict.Verdict;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chính sách độ tin cậy của Sprint 3 (T3), đúng bảng ở mục 7.2:
 *
 * <pre>
 *   Strong evidence + AI khớp rule + đủ file    -&gt; HIGH
 *   Partial evidence hoặc thiếu một phần file   -&gt; MEDIUM
 *   Weak / ambiguous evidence hoặc AI lệch rule -&gt; LOW
 * </pre>
 *
 * <p>Tín hiệu LOW thắng MEDIUM, MEDIUM thắng HIGH. Mọi tín hiệu kích hoạt đều được ghi thành một lý do, kể
 * cả khi mức đã bị một tín hiệu mạnh hơn quyết định, để người đọc thấy đủ bức tranh.
 *
 * <p><b>Nguồn chính</b> của mỗi kết luận lấy theo cột "Nguồn evidence chính" ở mục 4.2; SUCCESS sạch dựa
 * vào signaling (OK_ACK + BYE).
 */
@Component
public class EvidenceBasedConfidencePolicy implements ConfidencePolicy {

    private static final Map<IssueCategory, Set<EventSource>> PRIMARY_SOURCES = Map.of(
            IssueCategory.NETWORK_PACKET_LOSS, EnumSet.of(EventSource.END_CALL),
            IssueCategory.NETWORK_DELAY_JITTER, EnumSet.of(EventSource.END_CALL),
            IssueCategory.ICE_FAILURE, EnumSet.of(EventSource.WEBRTC, EventSource.END_CALL),
            IssueCategory.TURN_FAILURE, EnumSet.of(EventSource.WEBRTC),
            IssueCategory.SIGNALING_FAILURE, EnumSet.of(EventSource.SIGNALING, EventSource.END_CALL),
            IssueCategory.NONE, EnumSet.of(EventSource.SIGNALING));

    @Override
    public ConfidenceAssessment assess(RuleVerdictResult verdict) {
        List<String> low = new ArrayList<>();
        List<String> medium = new ArrayList<>();

        if (verdict.verdict() == Verdict.UNKNOWN) {
            low.add("Không đủ evidence để kết luận (verdict UNKNOWN).");
        }
        switch (strengthOf(verdict)) {
            case WEAK -> low.add(verdict.evidence().isEmpty()
                    ? "Không có evidence nào đỡ cho kết luận."
                    : "Có vấn đề nhưng chưa quy được về issue category nào.");
            case PARTIAL -> medium.add("Evidence chưa đủ mạnh: thiếu nguồn chính (" + primarySourceText(verdict)
                    + ") hoặc chỉ có một dòng evidence.");
            case STRONG -> {
            }
        }
        if (!verdict.dataLimitations().isEmpty()) {
            medium.add("Thiếu một phần dữ liệu: " + String.join("; ", verdict.dataLimitations()) + ".");
        }
        if (verdict.evidence().stream().map(Evidence::sourceEvent).filter(Objects::nonNull)
                .anyMatch(e -> e.timestampConfidence() == TimestampConfidence.UNKNOWN)) {
            medium.add("Có evidence không neo được vào thời gian tuyệt đối; thứ tự so với signaling chỉ là ước lượng.");
        }

        List<String> reasons = new ArrayList<>(low);
        reasons.addAll(medium);
        if (!low.isEmpty()) {
            return new ConfidenceAssessment(ConfidenceLevel.LOW, reasons);
        }
        if (!medium.isEmpty()) {
            return new ConfidenceAssessment(ConfidenceLevel.MEDIUM, reasons);
        }
        reasons.add("Evidence mạnh từ nguồn chính (" + primarySourceText(verdict) + ") và đủ file log.");
        return new ConfidenceAssessment(ConfidenceLevel.HIGH, reasons);
    }

    @Override
    public ConfidenceAssessment withAi(ConfidenceAssessment ruleBased, AiAgreement agreement) {
        List<String> reasons = new ArrayList<>(ruleBased.reasons());
        return switch (agreement) {
            case NOT_COMPARED -> ruleBased;
            case AGREES -> {
                reasons.add("AI đưa ra cùng kết luận với rule.");
                yield new ConfidenceAssessment(ruleBased.level(), reasons);
            }
            case DISAGREES -> {
                reasons.add(0, "AI và rule không cùng kết luận - cần kiểm tra thủ công.");
                yield new ConfidenceAssessment(ConfidenceLevel.LOW, reasons);
            }
            case UNAVAILABLE -> {
                // Mục 7.2: HIGH đòi hỏi "AI khớp rule"; không có AI thì không thể khẳng định điều đó.
                reasons.add("Không có phân tích AI để đối chiếu (report degraded, chỉ dựa trên rule).");
                yield new ConfidenceAssessment(ruleBased.level() == ConfidenceLevel.HIGH
                        ? ConfidenceLevel.MEDIUM : ruleBased.level(), reasons);
            }
        };
    }

    EvidenceStrength strengthOf(RuleVerdictResult verdict) {
        List<Evidence> evidence = verdict.evidence();
        if (evidence.isEmpty()
                || (verdict.hasIssueToReport() && verdict.issueCategory() == IssueCategory.UNKNOWN)) {
            return EvidenceStrength.WEAK;
        }
        Set<EventSource> primary = PRIMARY_SOURCES.getOrDefault(verdict.issueCategory(), Set.of());
        boolean hasPrimary = evidence.stream().map(Evidence::sourceEvent).filter(Objects::nonNull)
                .anyMatch(e -> primary.contains(e.source()));
        return hasPrimary && evidence.size() >= 2 ? EvidenceStrength.STRONG : EvidenceStrength.PARTIAL;
    }

    private static String primarySourceText(RuleVerdictResult verdict) {
        Set<EventSource> primary = PRIMARY_SOURCES.getOrDefault(verdict.issueCategory(), Set.of());
        return primary.isEmpty() ? "không xác định"
                : primary.stream().map(EventSource::name).sorted().collect(Collectors.joining(" hoặc "));
    }
}
