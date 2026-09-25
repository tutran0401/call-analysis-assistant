package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.verdict.Evidence;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evidence Engine (T7): gán ID tuần tự, ổn định ({@code EV001}, {@code EV002}, ...) cho các
 * sự kiện chuẩn hoá khi rule chọn ra chúng, để mỗi kết luận trong report đều trích dẫn được
 * chính xác nó đến từ dòng log nào.
 *
 * <p>Có trạng thái (bộ đếm ID) nên mỗi lượt phân tích tạo một instance mới - không phải
 * singleton dùng chung, tránh ID nhảy lung tung giữa các cuộc gọi.
 */
public final class EvidenceEngine {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final List<Evidence> evidence = new ArrayList<>();

    public Evidence add(CanonicalEvent event, String description) {
        String id = "EV%03d".formatted(evidence.size() + 1);
        String timestampDisplay = event.timestamp() != null
                ? TIMESTAMP_FORMAT.format(event.timestamp())
                : event.rawTimestamp() + " (relative offset, unanchored)";
        Evidence created = new Evidence(id, sourceLabel(event), timestampDisplay, description, event);
        evidence.add(created);
        return created;
    }

    public List<Evidence> all() {
        return List.copyOf(evidence);
    }

    private String sourceLabel(CanonicalEvent event) {
        return switch (event.source()) {
            case SIGNALING -> "signaling";
            case END_CALL -> event.leg().name().toLowerCase(Locale.ROOT) + "_endcall.log";
            case WEBRTC -> event.leg().name().toLowerCase(Locale.ROOT) + "_webrtc.log";
        };
    }
}
