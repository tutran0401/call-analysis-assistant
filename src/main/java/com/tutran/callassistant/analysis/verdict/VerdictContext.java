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

    /** Tên event của dòng call summary trong end-call log - nơi chứa client state timeline. */
    private static final String CALL_SUMMARY_EVENT = "CALL_SUMMARY";
    private static final String CLIENT_STATUS_FIELD = "status";
    /** Trạng thái đích của client state timeline; chưa tới đây nghĩa là cuộc gọi chưa thật sự lên. */
    private static final String CONFIRMED_STATUS = "CONFIRMED";

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
                .ifPresent(e -> recordEvidence(e, "INIT_CALL: bắt đầu thiết lập cuộc gọi"));
    }

    public void recordCallConfirmed(CanonicalEvent confirmed) {
        recordEvidence(confirmed, "OK_ACK_OK: cuộc gọi đã được xác nhận, cả hai bên đã kết nối");
    }

    public void recordCallEnded(CanonicalEvent bye) {
        String terminator = metrics.terminator().isAvailable()
                ? metrics.terminator().value().name()
                : "không xác định được bên nào";
        recordEvidence(bye, "BYE từ " + terminator);
    }

    /** Ghi mốc cuộc gọi bị chấm dứt tường minh, nêu rõ lệnh nào đã chấm dứt nó. */
    public void recordExplicitTermination(CanonicalEvent termination) {
        recordEvidence(termination, termination.eventType()
                + ": cuộc gọi bị chấm dứt trước khi kịp được xác nhận");
    }

    /**
     * Ghi evidence từ <b>client state timeline</b> - trạng thái cuối cùng mà end-call log của mỗi bên
     * ghi nhận được.
     *
     * <p>Mục 4.2 của spec nêu "End Call state timeline" là nguồn evidence chính cho cả
     * {@code ICE_FAILURE} lẫn {@code SIGNALING_FAILURE}, vì nó là góc nhìn của chính thiết bị người
     * dùng: signaling chỉ cho biết lệnh đã đi qua, còn state timeline cho biết client <i>tự cho rằng</i>
     * cuộc gọi đã tới đâu. Trong data mẫu, trạng thái quan sát được gồm
     * {@code INIT}, {@code WAITING_INIT_CALL}, {@code INVITE_SENT}, {@code ANSWERED}, {@code CONFIRMED}.
     *
     * <p>Chỉ gọi ở các rule kết luận FAIL - với cuộc gọi thành công thì mốc {@code OK_ACK_OK} đã đủ và
     * thêm dòng này chỉ làm nhiễu report.
     */
    public void recordClientStateTimeline() {
        for (Leg leg : CallMetrics.QUALITY_LEGS) {
            lastClientState(leg).ifPresent(state -> recordEvidence(state,
                    "Client state timeline của " + leg + " dừng ở " + state.attribute(CLIENT_STATUS_FIELD)
                            + (CONFIRMED_STATUS.equals(state.attribute(CLIENT_STATUS_FIELD))
                            ? "" : " - chưa bao giờ đạt " + CONFIRMED_STATUS)));
        }
    }

    /** Dòng CALL_SUMMARY cuối cùng của một bên, tức trạng thái sau cùng mà client tự ghi nhận. */
    private Optional<CanonicalEvent> lastClientState(Leg leg) {
        List<CanonicalEvent> summaries = timeline.forSourceAndLeg(EventSource.END_CALL, leg).stream()
                .filter(e -> CALL_SUMMARY_EVENT.equals(e.eventType()))
                .filter(e -> e.attribute(CLIENT_STATUS_FIELD) != null)
                .toList();
        return summaries.isEmpty() ? Optional.empty() : Optional.of(summaries.get(summaries.size() - 1));
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
