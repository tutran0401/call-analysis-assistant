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
 * Cuộc gọi chưa từng đạt trạng thái đã xác nhận (không có {@code OK_ACK_OK}) thì kết luận FAIL.
 *
 * <p>Issue category được chọn theo evidence: có lỗi ICE trong log WebRTC thì quy cho
 * {@link IssueCategory#ICE_FAILURE}; không có thì quy về
 * {@link IssueCategory#SIGNALING_FAILURE} - cuộc gọi đã dừng trước khi tầng media kịp vào việc,
 * nên vấn đề nằm ở tầng signaling.
 */
@Component
@Order(30)
public class FailedToConnectRule implements VerdictRule {

    private final IceFailureDetector iceFailureDetector;

    public FailedToConnectRule(IceFailureDetector iceFailureDetector) {
        this.iceFailureDetector = iceFailureDetector;
    }

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        if (context.callConfirmed().isPresent()) {
            return Optional.empty();
        }
        context.recordCallSetupStarted();

        Optional<CanonicalEvent> iceFailure = iceFailureDetector.findFailure(context.timeline());
        if (iceFailure.isPresent()) {
            context.recordEvidence(iceFailure.get(), "WebRTC log reports an ICE connection failure");
            return Optional.of(context.fail(IssueCategory.ICE_FAILURE,
                    "Call failed to establish: ICE connectivity failed in the WebRTC log."));
        }
        return Optional.of(context.fail(IssueCategory.SIGNALING_FAILURE,
                "Call failed to establish: no OK_ACK_OK (confirmed) event was observed at the "
                        + "signaling layer, and no ICE connection failure evidence was found, so the call is "
                        + "attributed to a signaling-layer failure."));
    }
}
