package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.TimestampConfidence;
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
 * <p>Mục 8.2 đòi "mọi evidence trace được về dòng log gốc", nên evidence từ log client mang
 * <b>số dòng</b> trong file ({@code callee_webrtc.log:902}). Với WebRTC log, file chỉ có mốc tương đối
 * {@code [giây:mili]}; mốc tuyệt đối là giá trị <i>suy ra</i> khi neo vào signaling - tìm trong file sẽ
 * không thấy - nên hiển thị mốc gốc trước, mốc suy ra sau dấu {@code ≈} (peer evaluation issue 4).
 *
 * <p>Có trạng thái (bộ đếm ID) nên mỗi lượt phân tích tạo một instance mới - không phải
 * singleton dùng chung, tránh ID nhảy lung tung giữa các cuộc gọi.
 */
public final class EvidenceEngine {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final String ELAPSED_SUFFIX = "ms";

    private final List<Evidence> evidence = new ArrayList<>();

    public Evidence add(CanonicalEvent event, String description) {
        String id = "EV%03d".formatted(evidence.size() + 1);
        Evidence created = new Evidence(id, sourceLabel(event), timestampDisplay(event), description, event);
        evidence.add(created);
        return created;
    }

    public List<Evidence> all() {
        return List.copyOf(evidence);
    }

    /** Tên file, kèm số dòng với log client (signaling đến từ ES nên không có dòng file để trỏ về). */
    static String sourceLabel(CanonicalEvent event) {
        String leg = event.leg().name().toLowerCase(Locale.ROOT);
        return switch (event.source()) {
            case SIGNALING -> "signaling";
            case END_CALL -> leg + "_endcall.log" + line(event);
            case WEBRTC -> leg + "_webrtc.log" + line(event);
        };
    }

    static String timestampDisplay(CanonicalEvent event) {
        String fileMark = event.source() == EventSource.WEBRTC ? webRtcMark(event.rawTimestamp()) : null;
        if (event.timestamp() == null) {
            return (fileMark != null ? fileMark : event.rawTimestamp()) + " (relative offset, unanchored)";
        }
        String absolute = TIMESTAMP_FORMAT.format(event.timestamp());
        if (fileMark != null && event.timestampConfidence() == TimestampConfidence.ANCHORED) {
            return fileMark + " ≈ " + absolute;
        }
        return absolute;
    }

    private static String line(CanonicalEvent event) {
        // Parser log client đánh sequenceNumber đúng bằng số dòng (bắt đầu từ 1) của dòng mở đầu sự kiện.
        return event.sequenceNumber() > 0 ? ":" + event.sequenceNumber() : "";
    }

    /** {@code 20195ms} -> {@code [020:195]}, đúng dạng mốc đầu dòng của WebRTC log để tìm lại được trong file. */
    private static String webRtcMark(String rawTimestamp) {
        if (rawTimestamp == null || !rawTimestamp.endsWith(ELAPSED_SUFFIX)) {
            return null;
        }
        try {
            long millis = Long.parseLong(rawTimestamp.substring(0, rawTimestamp.length() - ELAPSED_SUFFIX.length()));
            return "[%03d:%03d]".formatted(millis / 1000, millis % 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
