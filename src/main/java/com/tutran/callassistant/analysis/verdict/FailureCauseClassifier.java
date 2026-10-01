package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Chọn issue category cho một cuộc gọi <b>không thiết lập được</b>, dựa trên evidence mạnh nhất tìm
 * thấy trong log client.
 *
 * <p>Tồn tại vì hai rule khác nhau đều cần đúng câu hỏi này - {@code ExplicitTerminationRule} (cuộc
 * gọi bị CANCEL/FAIL_HARD) và {@code FailedToConnectRule} (không thấy OK_ACK_OK). Trước đây mỗi rule
 * tự quyết định, nên rule đầu luôn trả {@code SIGNALING_FAILURE} và không bao giờ nhìn tới log client.
 *
 * <p><b>Thứ tự ưu tiên - từ nguyên nhân gốc tới triệu chứng:</b>
 *
 * <ol>
 *   <li>{@link IssueCategory#TURN_FAILURE} - client không đi hết được vòng request/response với TURN
 *       server. Đây là nguyên nhân <i>gốc</i>: không có candidate relay thì app hết đường, và cái
 *       {@code CANCEL} nhìn thấy ở signaling chỉ là hệ quả.</li>
 *   <li>{@link IssueCategory#ICE_FAILURE} - TURN đi được nhưng ICE vẫn không kết nối.</li>
 *   <li>{@link IssueCategory#SIGNALING_FAILURE} - không có evidence tầng media nào; quy về tầng
 *       signaling (trả về {@link Optional#empty()}, để rule gọi tự đặt fallback này).</li>
 * </ol>
 */
@Component
public class FailureCauseClassifier {

    private final TurnFailureDetector turnFailureDetector;
    private final IceFailureDetector iceFailureDetector;

    public FailureCauseClassifier(TurnFailureDetector turnFailureDetector,
                                  IceFailureDetector iceFailureDetector) {
        this.turnFailureDetector = turnFailureDetector;
        this.iceFailureDetector = iceFailureDetector;
    }

    /** Nguyên nhân đã xác định được từ log client, kèm dòng log gốc để trích dẫn. */
    public record Cause(IssueCategory category, CanonicalEvent evidence, String description) {
    }

    public Optional<Cause> classify(VerdictContext context) {
        Optional<TurnFailureDetector.TurnFailure> turn =
                turnFailureDetector.findFailure(context.timeline());
        if (turn.isPresent()) {
            return Optional.of(new Cause(IssueCategory.TURN_FAILURE, turn.get().evidence(),
                    "Lỗi TURN phía " + turn.get().evidence().leg() + ": " + turn.get().describe()));
        }

        Optional<CanonicalEvent> ice = iceFailureDetector.findFailure(context.timeline());
        return ice.map(event -> new Cause(IssueCategory.ICE_FAILURE, event,
                "Log WebRTC báo ICE của " + event.leg() + " không kết nối được"));
    }
}
