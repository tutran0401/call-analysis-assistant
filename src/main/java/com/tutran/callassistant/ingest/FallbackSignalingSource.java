package com.tutran.callassistant.ingest;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.domain.event.NormalizedEvents;

import java.util.List;

/**
 * Thử nguồn signaling chính trước, không có dữ liệu thì dùng nguồn dự phòng (Decorator).
 *
 * <p>Sprint 1 dùng nó để ghép "query Elasticsearch" với "đọc file export local": demo vẫn chạy
 * được ngay cả khi người dùng chưa chạy {@code import} hoặc Elasticsearch chưa bật, và luôn nói rõ
 * trong report là đã phải dùng đường dự phòng.
 *
 * <p>Cả hai nguồn cùng implement {@link SignalingEventSource}, và bản thân lớp này cũng vậy - nên
 * pipeline không biết (và không cần biết) rằng có tới hai nguồn đang tham gia. Đây là chỗ thay thế
 * cho nhánh {@code if/else} chọn nguồn từng nằm trong CLI cũ.
 */
public class FallbackSignalingSource implements SignalingEventSource {

    private final SignalingEventSource primary;
    private final SignalingEventSource fallback;
    private final String fallbackReason;

    public FallbackSignalingSource(SignalingEventSource primary, SignalingEventSource fallback,
                                   String fallbackReason) {
        this.primary = primary;
        this.fallback = fallback;
        this.fallbackReason = fallbackReason;
    }

    @Override
    public NormalizedEvents fetch(CallReference call) {
        NormalizedEvents fromPrimary = primary.fetch(call);
        if (!fromPrimary.isEmpty()) {
            return fromPrimary;
        }
        // Giữ nguyên thứ tự kể lại câu chuyện: nguồn chính báo gì, vì sao phải chuyển sang dự
        // phòng, rồi nguồn dự phòng báo gì.
        return NormalizedEvents.empty()
                .withExtraWarnings(fromPrimary.warnings())
                .withExtraWarnings(List.of(fallbackReason.formatted(call.callId())))
                .combine(fallback.fetch(call));
    }
}
