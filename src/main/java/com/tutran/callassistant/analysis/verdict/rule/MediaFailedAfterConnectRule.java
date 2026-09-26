package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.IceFailureDetector;
import com.tutran.callassistant.analysis.verdict.VerdictContext;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Signaling nói cuộc gọi đã kết nối và kết thúc bình thường, nhưng log WebRTC cho thấy ICE của một bên
 * đã {@code failed} - tức bên đó không bao giờ có media. Kết luận FAIL / {@link IssueCategory#ICE_FAILURE}.
 *
 * <p>Đây là loại lỗi mà <b>chỉ signaling không bao giờ thấy được</b>: các lệnh vẫn đi qua đủ
 * {@code INIT_CALL → INVITE → RINGING → OK → OK_ACK_OK → BYE}, nhìn hệt một cuộc gọi hoàn hảo. Cuộc
 * gọi thật {@code 2D9057AA} trong tập ground truth {@code fail/} chính là ca đó: ICE của callee
 * {@code checking => failed}, MOS = 0.000 trên cả 25 dòng periodic stats, nhưng trước khi có rule này
 * hệ thống kết luận SUCCESS - câu trả lời sai duy nhất trên toàn bộ tập có nhãn.
 *
 * <p>Đứng <i>sau</i> {@link ConnectedWithoutByeRule} và <i>trước</i>
 * {@link ConnectedSuccessfullyRule}: tới được đây nghĩa là đã có cả {@code OK_ACK_OK} lẫn
 * {@code BYE}, nên câu hỏi duy nhất còn lại là media có thật sự hoạt động hay không.
 *
 * <p>Chỉ trạng thái {@code failed} được tính là lỗi - xem {@link IceFailureDetector} để biết vì sao
 * {@code disconnected} thì không.
 */
@Component
@Order(45)
public class MediaFailedAfterConnectRule implements VerdictRule {

    private final IceFailureDetector iceFailureDetector;

    public MediaFailedAfterConnectRule(IceFailureDetector iceFailureDetector) {
        this.iceFailureDetector = iceFailureDetector;
    }

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        Optional<CanonicalEvent> iceFailure = iceFailureDetector.findFailure(context.timeline());
        if (iceFailure.isEmpty()) {
            return Optional.empty();
        }

        context.recordCallSetupStarted();
        context.recordCallConfirmed(context.callConfirmed().orElseThrow());
        context.recordEvidence(iceFailure.get(),
                "WebRTC log reports ICE connectivity failed for " + iceFailure.get().leg()
                        + " - that leg never had media");
        context.recordCallEnded(context.callEnded().orElseThrow());

        return Optional.of(context.fail(IssueCategory.ICE_FAILURE,
                "Call was confirmed at the signaling layer but ICE connectivity failed for "
                        + iceFailure.get().leg() + ", so that side never had media - the call did not "
                        + "actually work despite a normal-looking signaling flow."));
    }
}
