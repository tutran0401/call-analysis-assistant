package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Tìm evidence cho thấy ICE thật sự không kết nối được.
 *
 * <p>Chỉ tin vào đúng từ vựng trạng thái có sẵn của engine ({@code ICE_CONNECTION_STATE_CHANGE}).
 * Cách so khớp từ khoá tự do "turn"/"ice" cộng "error"/"fail" đã được thử và bỏ đi: nó báo nhầm cả
 * những phản hồi giao thức TURN bình thường, tự phục hồi được theo từng candidate (thử thách
 * credential dài hạn code=401, lỗi permission code=400) thành lỗi thật - quan sát thấy hàng chục lần
 * ngay trên một cuộc gọi SUCCESS hoàn toàn sạch. Vì vậy một bộ phát hiện TURN riêng, đáng tin cậy là
 * việc của sprint sau, và lỗi tầng TURN hiện gộp vào ICE_FAILURE/SIGNALING_FAILURE.
 *
 * <p><b>Chỉ {@code failed} mới là lỗi, {@code disconnected} thì không.</b> Bản trước nhận cả
 * "disconnected", nhưng đối chiếu data mẫu cho thấy 3 file kết thúc bằng
 * {@code connected => disconnected} và <i>cả 3 đều thuộc cuộc gọi SUCCESS</i> - đó là teardown bình
 * thường lúc cúp máy, không phải lỗi. Toàn bộ data mẫu chỉ có đúng một cuộc gọi chứa
 * {@code => failed}. Nhận "disconnected" là lỗi sẽ biến mọi cuộc gọi kết thúc bình thường thành
 * ICE_FAILURE.
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

    /** Tìm lỗi ICE của riêng một bên - dùng khi cần biết bên nào mất media. */
    public Optional<CanonicalEvent> findFailure(CallTimeline timeline, Leg leg) {
        return timeline.forSourceAndLeg(EventSource.WEBRTC, leg).stream()
                .filter(e -> ICE_STATE_CHANGE.equals(e.eventType()))
                .filter(e -> indicatesFailure(e.attribute(MESSAGE)))
                .findFirst();
    }

    private boolean indicatesFailure(String message) {
        if (message == null) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains("failed");
    }
}
