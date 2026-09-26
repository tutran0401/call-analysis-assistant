package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.VerdictContext;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Signaling ghi rõ cuộc gọi bị chấm dứt ({@code CANCEL} hoặc {@code FAIL_HARD}) mà chưa từng đạt
 * {@code OK_ACK_OK} thì kết luận FAIL - <b>không cần client log</b>.
 *
 * <p>Đây là bằng chứng dứt khoát: trong toàn bộ data mẫu, hai lệnh này xuất hiện ở 8 cuộc gọi và
 * <i>chưa bao giờ</i> đi cùng {@code OK_ACK_OK}. Biết cuộc gọi đã bị huỷ/bị từ chối là đã đủ để nói
 * nó không thiết lập được; không cần hỏi thêm phía client có nghe được hay không.
 *
 * <p><b>Vì sao phải đứng trước {@link MissingClientLogsRule}:</b> trước khi có rule này, hai cuộc gọi
 * thật trong tập ground truth {@code fail/} ({@code 7B56D7AD} với {@code CANCEL} ×15 và
 * {@code E9D6C112} với {@code CANCEL} ×10) bị trả UNKNOWN chỉ vì thiếu end-call log, dù signaling đã
 * chứng minh dứt khoát. Thiếu file client không làm một bằng chứng đã đủ mạnh yếu đi.
 *
 * <p><b>Issue category:</b> {@code FAIL_HARD} là server từ chối nên đúng là
 * {@link IssueCategory#SIGNALING_FAILURE}. {@code CANCEL} là hành vi người dùng - mục 4.2 của spec
 * chưa có category nào cho việc đó, nên tạm dùng cùng category và nói rõ bản chất trong phần tóm tắt;
 * đây là câu đang chờ mentor xác nhận (xem {@code docs/accuracy-evaluation.md} mục 7).
 */
@Component
@Order(15)
public class ExplicitTerminationRule implements VerdictRule {

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        if (context.callConfirmed().isPresent()) {
            return Optional.empty();
        }
        Optional<CanonicalEvent> termination = context.explicitTermination();
        if (termination.isEmpty()) {
            return Optional.empty();
        }

        context.recordCallSetupStarted();
        context.recordExplicitTermination(termination.get());
        return Optional.of(context.fail(IssueCategory.SIGNALING_FAILURE,
                "Call failed to establish: signaling shows an explicit " + termination.get().eventType()
                        + " before the call was ever confirmed (no OK_ACK_OK observed)."));
    }
}
