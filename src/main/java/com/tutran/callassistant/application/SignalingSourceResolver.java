package com.tutran.callassistant.application;

import com.tutran.callassistant.application.port.out.SignalingEventSource;

import java.util.EnumMap;
import java.util.Map;

/**
 * Chọn {@link SignalingEventSource} ứng với lựa chọn của người dùng (Strategy + Factory).
 *
 * <p>Nhờ có lớp này mà pipeline không còn nhánh {@code if (fromFile) ... else ...}: nó chỉ
 * hỏi "nguồn nào cho preference này" rồi gọi một interface duy nhất. Thêm một nguồn
 * signaling thứ ba (ví dụ query trực tiếp hệ thống thật) chỉ là thêm một entry vào map khi
 * wiring, không phải sửa pipeline.
 */
public final class SignalingSourceResolver {

    private final Map<SignalingSourcePreference, SignalingEventSource> sources;

    public SignalingSourceResolver(Map<SignalingSourcePreference, SignalingEventSource> sources) {
        // Khởi tạo theo lớp enum rồi mới putAll: constructor EnumMap(Map) ném exception khi map rỗng
        // vì không suy ra được kiểu khoá - mà "chưa wire nguồn nào" là trạng thái phải báo lỗi rõ ràng
        // ở resolve(), không phải nổ ngay lúc dựng.
        this.sources = new EnumMap<>(SignalingSourcePreference.class);
        this.sources.putAll(sources);
    }

    public SignalingEventSource resolve(SignalingSourcePreference preference) {
        SignalingEventSource source = sources.get(preference);
        if (source == null) {
            throw new IllegalStateException("No signaling source wired for preference " + preference);
        }
        return source;
    }
}
