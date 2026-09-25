package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Tìm evidence cho thấy ICE thật sự không kết nối được.
 *
 * <p>Chỉ tin vào đúng callback trạng thái kết thúc có sẵn của engine
 * ({@code ICE_CONNECTION_STATE_CHANGE} với message FAILED/DISCONNECTED). Cách so khớp từ khoá
 * tự do "turn"/"ice" cộng "error"/"fail" đã được thử và bỏ đi: nó báo nhầm cả những phản hồi
 * giao thức TURN bình thường, tự phục hồi được theo từng candidate (thử thách credential dài
 * hạn code=401, lỗi permission code=400) thành lỗi thật - quan sát thấy hàng chục lần ngay
 * trên một cuộc gọi SUCCESS hoàn toàn sạch. Vì vậy một bộ phát hiện TURN riêng, đáng tin cậy
 * là việc của sprint sau, và lỗi tầng TURN hiện gộp vào ICE_FAILURE/SIGNALING_FAILURE.
 */
@Component
public class IceFailureDetector {

    private static final String ICE_STATE_CHANGE = "ICE_CONNECTION_STATE_CHANGE";
    private static final String MESSAGE = "message";

    public Optional<CanonicalEvent> findFailure(CallTimeline timeline) {
        return timeline.forSource(EventSource.WEBRTC).stream()
                .filter(e -> ICE_STATE_CHANGE.equals(e.eventType()))
                .filter(e -> indicatesFailure(e.attribute(MESSAGE)))
                .findFirst();
    }

    private boolean indicatesFailure(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("failed") || lower.contains("disconnected");
    }
}
