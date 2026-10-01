package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.FailureCauseClassifier;
import com.tutran.callassistant.analysis.verdict.VerdictContext;
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

    private final FailureCauseClassifier causeClassifier;

    public FailedToConnectRule(FailureCauseClassifier causeClassifier) {
        this.causeClassifier = causeClassifier;
    }

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        if (context.callConfirmed().isPresent()) {
            return Optional.empty();
        }
        context.recordCallSetupStarted();
        // Mục 4.2: client state timeline là nguồn evidence chính cho SIGNALING_FAILURE/ICE_FAILURE.
        context.recordClientStateTimeline();

        Optional<FailureCauseClassifier.Cause> cause = causeClassifier.classify(context);
        if (cause.isPresent()) {
            context.recordEvidence(cause.get().evidence(), cause.get().description());
            return Optional.of(context.fail(cause.get().category(),
                    "Cuộc gọi không thiết lập được: " + cause.get().description() + "."));
        }
        return Optional.of(context.fail(IssueCategory.SIGNALING_FAILURE,
                "Cuộc gọi không thiết lập được: không quan sát được sự kiện OK_ACK_OK (đã xác nhận) ở "
                        + "tầng signaling, và cũng không có evidence nào về lỗi ICE, nên quy về lỗi tầng "
                        + "signaling."));
    }
}
