package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Phát hiện lỗi TURN phía client, và nói rõ <b>hỏng theo kiểu nào</b>.
 *
 * <p><b>Vì sao không thể chỉ đếm lỗi.</b> Bản đầu tiên so khớp từ khoá tự do ("turn" + "error"/"fail")
 * và bị bỏ đi vì báo nhầm hàng loạt: trong data mẫu, <i>mọi</i> cuộc gọi SUCCESS đều có sẵn 20 dòng
 * {@code TURN probe error response} và 4 dòng {@code Received TURN allocate error response} - đó là
 * thử thách credential dài hạn và nhiễu dò đường theo từng candidate, hoàn toàn bình thường. Đếm lỗi
 * sẽ gắn TURN_FAILURE cho cả 7 cuộc gọi tốt.
 *
 * <p><b>Tín hiệu thật nằm ở chỗ khác:</b> không phải "có lỗi hay không", mà là
 * <i>client có đi hết được vòng request/response với TURN server hay không</i>:
 *
 * <table>
 *   <caption>Ba hình thái quan sát được trên data mẫu</caption>
 *   <tr><th>Socket lỗi</th><th>Request gửi đi</th><th>Phản hồi nhận về</th><th>Kết luận</th></tr>
 *   <tr><td>có</td><td>0</td><td>0</td><td>{@link Kind#SOCKET_NOT_CREATED}</td></tr>
 *   <tr><td>không</td><td>&gt; 0</td><td>0</td><td>{@link Kind#NO_RESPONSE}</td></tr>
 *   <tr><td>không</td><td>&gt; 0</td><td>&gt; 0</td><td>TURN hoạt động - <b>không</b> phải lỗi</td></tr>
 * </table>
 *
 * <p>Tiêu chí này cho 0 false positive trên cả 13 file webrtc của tập {@code success/}.
 */
@Component
public class TurnFailureDetector {

    private static final String SOCKET_ERROR = "TURN_SOCKET_ERROR";
    private static final String ALLOCATE_REQUEST = "TURN_ALLOCATE_REQUEST";
    private static final String ALLOCATE_RESPONSE = "TURN_ALLOCATE_RESPONSE";

    /** Kiểu hỏng của TURN - quyết định cả lời giải thích lẫn đề xuất đưa ra cho người đọc. */
    public enum Kind {
        /** Không tạo nổi socket tới TURN server: chưa gửi đi được request nào. */
        SOCKET_NOT_CREATED,
        /** Đã gửi request allocate nhưng không nhận được phản hồi nào. */
        NO_RESPONSE
    }

    /** Một lỗi TURN đã xác định, kèm dòng log gốc để trích dẫn làm evidence. */
    public record TurnFailure(Kind kind, CanonicalEvent evidence, int requestsSent) {

        /** Mô tả tiếng Việt dùng cho phần tóm tắt và evidence của report. */
        public String describe() {
            return switch (kind) {
                case SOCKET_NOT_CREATED ->
                        "không tạo được socket tới TURN server nên chưa gửi đi được request nào";
                case NO_RESPONSE ->
                        "đã gửi " + requestsSent + " request tới TURN server nhưng không nhận được "
                                + "phản hồi nào";
            };
        }
    }

    public Optional<TurnFailure> findFailure(CallTimeline timeline) {
        List<CanonicalEvent> webrtc = timeline.forSource(EventSource.WEBRTC);

        Optional<CanonicalEvent> socketError = firstOfType(webrtc, SOCKET_ERROR);
        if (socketError.isPresent()) {
            return Optional.of(new TurnFailure(Kind.SOCKET_NOT_CREATED, socketError.get(), 0));
        }

        List<CanonicalEvent> requests = ofType(webrtc, ALLOCATE_REQUEST);
        boolean anyResponse = !ofType(webrtc, ALLOCATE_RESPONSE).isEmpty();
        if (!requests.isEmpty() && !anyResponse) {
            return Optional.of(new TurnFailure(Kind.NO_RESPONSE, requests.get(0), requests.size()));
        }
        return Optional.empty();
    }

    private List<CanonicalEvent> ofType(List<CanonicalEvent> events, String eventType) {
        return events.stream().filter(e -> eventType.equals(e.eventType())).toList();
    }

    private Optional<CanonicalEvent> firstOfType(List<CanonicalEvent> events, String eventType) {
        return ofType(events, eventType).stream().findFirst();
    }
}
