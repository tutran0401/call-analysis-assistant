package com.tutran.callassistant.evidence;

import com.tutran.callassistant.domain.CanonicalEvent;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gán ID evidence tuần tự, ổn định ({@code EV001}, {@code EV002}, ...) cho các sự kiện
 * chuẩn hoá khi {@link com.tutran.callassistant.evidence.RuleVerdictEngine} chọn ra chúng,
 * để mỗi kết luận trong report đều trích dẫn được chính xác nó đến từ dòng log nào.
 */
public final class EvidenceEngine {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final List<Evidence> evidence = new ArrayList<>();

    public Evidence add(CanonicalEvent event, String description) {
        String id = "EV%03d".formatted(evidence.size() + 1);
        String sourceLabel = sourceLabel(event);
        String timestampDisplay = event.timestamp() != null
                ? TIMESTAMP_FORMAT.format(event.timestamp())
                : event.rawTimestamp() + " (relative offset, unanchored)";
        Evidence created = new Evidence(id, sourceLabel, timestampDisplay, description, event);
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
