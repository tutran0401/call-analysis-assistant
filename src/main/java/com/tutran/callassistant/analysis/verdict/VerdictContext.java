package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.analysis.SignalingCommands;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import com.tutran.callassistant.domain.verdict.Verdict;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tất cả những gì một {@link com.tutran.callassistant.analysis.verdict.rule.VerdictRule} cần,
 * gói vào một tham số duy nhất: dữ liệu đã tính (timeline, metrics), chỗ ghi evidence, chỗ ghi
 * giới hạn dữ liệu, và các hàm dựng kết luận.
 *
 * <p>Nhờ có lớp này mà mỗi rule chỉ còn vài dòng và đọc gần như văn xuôi: nó hỏi context vài
 * câu hỏi về cuộc gọi, ghi lại evidence mà kết luận của nó dựa vào, rồi trả về kết luận. Mỗi
 * lượt phân tích dùng một context mới vì nó có trạng thái (bộ đếm ID evidence).
 *
 * <p>Các mốc tiến trình cuộc gọi ({@code INIT_CALL}, {@code OK_ACK_OK}, {@code BYE}) được ghi
 * evidence qua các method {@code record*} ở đây thay vì để từng rule tự làm - vừa tránh lặp,
 * vừa đảm bảo cùng một mốc luôn được mô tả y như nhau trong report.
 */
public final class VerdictContext {

    private final CallTimeline timeline;
    private final CallMetrics metrics;
    private final EvidenceEngine evidenceEngine = new EvidenceEngine();
    private final List<String> dataLimitations = new ArrayList<>();

    public VerdictContext(CallTimeline timeline, CallMetrics metrics) {
        this.timeline = timeline;
        this.metrics = metrics;
    }

    public CallTimeline timeline() {
        return timeline;
    }

    public CallMetrics metrics() {
        return metrics;
    }

    // --- câu hỏi về dữ liệu của cuộc gọi -------------------------------------------------

    public boolean hasSignalingData() {
        return !timeline.forSource(EventSource.SIGNALING).isEmpty();
    }

    public boolean hasEndCallLog(Leg leg) {
        return timeline.hasAnyEvent(EventSource.END_CALL, leg);
    }

    /**
     * Bên này có <i>bất kỳ</i> log phía client nào không - end-call log hoặc WebRTC log.
     *
     * <p>Cả hai đều là log của thiết bị người dùng và đều nói được về trạng thái phía client, nên
     * cả hai đều tính là bằng chứng. Trước đây chỉ end-call log được tính, khiến 6/20 cuộc gọi
     * trong data mẫu bị trả UNKNOWN dù có WebRTC log ghi rõ ICE đã lên được hay chưa.
     */
    public boolean hasClientEvidence(Leg leg) {
        return timeline.hasAnyEvent(EventSource.END_CALL, leg)
                || timeline.hasAnyEvent(EventSource.WEBRTC, leg);
    }

    /**
     * Sự kiện signaling cho biết cuộc gọi bị chấm dứt tường minh ({@code FAIL_HARD} hoặc
     * {@code CANCEL}), nếu có. Ưu tiên {@code FAIL_HARD} vì nó chỉ rõ lỗi phía server.
     */
    public Optional<CanonicalEvent> explicitTermination() {
        for (String cmd : SignalingCommands.EXPLICIT_TERMINATIONS) {
            Optional<CanonicalEvent> event = timeline.earliestSignaling(cmd);
            if (event.isPresent()) {
                return event;
            }
        }
        return Optional.empty();
    }

    /** Cuộc gọi đã đạt trạng thái đã xác nhận (CONFIRMED) chưa - mốc phân định SUCCESS/FAIL. */
    public Optional<CanonicalEvent> callConfirmed() {
        return timeline.earliestSignaling(SignalingCommands.OK_ACK_OK);
    }

    public Optional<CanonicalEvent> callEnded() {
        return timeline.earliestSignaling(SignalingCommands.BYE);
    }

    // --- ghi evidence và giới hạn dữ liệu ------------------------------------------------

    public void recordEvidence(CanonicalEvent event, String description) {
        evidenceEngine.add(event, description);
    }

    public void noteDataLimitation(String limitation) {
        dataLimitations.add(limitation);
    }

    /** Ghi mốc "bắt đầu thiết lập cuộc gọi", nếu quan sát được. */
    public void recordCallSetupStarted() {
        timeline.earliestSignaling(SignalingCommands.INIT_CALL)
                .ifPresent(e -> recordEvidence(e, "INIT_CALL: call setup started"));
    }

    public void recordCallConfirmed(CanonicalEvent confirmed) {
        recordEvidence(confirmed, "OK_ACK_OK: call confirmed, both legs connected");
    }

    public void recordCallEnded(CanonicalEvent bye) {
        String terminator = metrics.terminator().isAvailable()
                ? metrics.terminator().value().name()
                : "unknown side";
        recordEvidence(bye, "BYE from " + terminator);
    }

    /** Ghi mốc cuộc gọi bị chấm dứt tường minh, nêu rõ lệnh nào đã chấm dứt nó. */
    public void recordExplicitTermination(CanonicalEvent termination) {
        recordEvidence(termination, termination.eventType()
                + ": call terminated before it was ever confirmed");
    }

    // --- dựng kết luận -------------------------------------------------------------------

    public RuleVerdictResult unknown(String summary) {
        return new RuleVerdictResult(Verdict.UNKNOWN, false, IssueCategory.UNKNOWN, summary,
                evidenceEngine.all(), dataLimitations);
    }

    public RuleVerdictResult fail(IssueCategory category, String summary) {
        return new RuleVerdictResult(Verdict.FAIL, false, category, summary,
                evidenceEngine.all(), dataLimitations);
    }

    public RuleVerdictResult success(boolean qualityFlag, IssueCategory category, String summary) {
        return new RuleVerdictResult(Verdict.SUCCESS, qualityFlag, category, summary,
                evidenceEngine.all(), dataLimitations);
    }
}
