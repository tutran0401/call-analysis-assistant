package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Phát hiện cuộc gọi <b>đã có media rồi bị ngắt giữa chừng</b> - vế "bị ngắt bất thường" trong định nghĩa
 * {@code FAIL} của spec (mục 4.1), thứ mà signaling không thấy được: BYE vẫn tới server, luồng lệnh nhìn
 * như một cuộc gọi bình thường.
 *
 * <p>Một bên được coi là bị rớt khi end-call log của <b>chính bên đó</b>, trước lúc bên đó kết thúc cuộc gọi,
 * có đủ cả hai dấu hiệu:
 * <ol>
 *   <li><b>mất kết nối</b>: client báo đối phương mất mạng ({@code signal = network_lost} /
 *       {@code PARTNER_LOST_NETWORK}) hoặc ICE chuyển sang {@code DISCONNECTED};</li>
 *   <li><b>mất tiếng tới tận lúc kết thúc</b>: sau khi media đã chạy, các dòng periodic stats cuối cùng
 *       liên tiếp có {@code audio.bytesReceivedPts = 0} (không nhận được byte audio nào trong chu kỳ) trong
 *       ít nhất {@link #MIN_SILENT_SECONDS} giây, và dấu hiệu mất kết nối rơi vào khoảng im lặng đó.</li>
 * </ol>
 *
 * <p>Vì sao cần cả hai: {@code DISCONNECTED} đơn lẻ vẫn xuất hiện lúc cúp máy bình thường (xem
 * {@link IceFailureDetector}); im lặng đơn lẻ có thể chỉ là đối phương tắt mic. Trên toàn bộ 30 cuộc gọi mẫu,
 * chỉ {@code 70C388AD} và {@code FE4EF5E7} (new_dataset) thoả cả hai; không cuộc gọi SUCCESS nào mentor đã
 * gán nhãn thoả. Mọi mốc so sánh đều lấy từ cùng một file nên không bị lệch đồng hồ giữa các thiết bị.
 */
@Component
public class MediaDropDetector {

    /** Số giây im lặng tối thiểu ở cuối cuộc gọi; các khoảng 1-4 s giữa cuộc rồi có tiếng lại không tính. */
    static final long MIN_SILENT_SECONDS = 3;
    /** Dấu hiệu mất kết nối có thể được ghi ngay trước dòng stats im lặng đầu tiên (stats ghi mỗi ~1 s). */
    private static final Duration SILENCE_START_TOLERANCE = Duration.ofSeconds(2);

    private static final String PERIODIC_STATS = "PERIODIC_STATS";
    private static final String SIGNAL = "SIGNAL";
    private static final String LOG_MESSAGE = "LOG_MESSAGE";
    private static final String FIELD_SIGNAL = "signal";
    private static final String FIELD_MSG = "msg";
    private static final String FIELD_BYTES_RECEIVED = "audio.bytesReceived";
    private static final String FIELD_BYTES_RECEIVED_IN_PERIOD = "audio.bytesReceivedPts";

    public record MediaDrop(Leg leg, CanonicalEvent connectionLost, CanonicalEvent lastMediaSample,
                            long silentSeconds) {
    }

    public Optional<MediaDrop> find(CallTimeline timeline) {
        for (Leg leg : CallMetrics.QUALITY_LEGS) {
            Optional<MediaDrop> drop = find(timeline, leg);
            if (drop.isPresent()) {
                return drop;
            }
        }
        return Optional.empty();
    }

    Optional<MediaDrop> find(CallTimeline timeline, Leg leg) {
        List<CanonicalEvent> events = timeline.forSourceAndLeg(EventSource.END_CALL, leg);
        Instant end = events.stream()
                .filter(this::isLocalEnd)
                .map(CanonicalEvent::timestamp)
                .findFirst()
                .orElse(null);
        if (end == null) {
            return Optional.empty();
        }

        List<CanonicalEvent> stats = events.stream()
                .filter(e -> PERIODIC_STATS.equals(e.eventType()))
                .filter(e -> !e.timestamp().isAfter(end))
                .toList();
        Optional<CanonicalEvent> lastMedia = lastSampleWithMedia(stats);
        if (lastMedia.isEmpty()) {
            return Optional.empty();
        }
        List<CanonicalEvent> silent = new ArrayList<>(stats.subList(stats.indexOf(lastMedia.get()) + 1, stats.size()));
        if (silent.isEmpty() || !silent.stream().allMatch(this::receivedNothingInPeriod)) {
            return Optional.empty();
        }
        long silentSeconds = Duration.between(lastMedia.get().timestamp(), end).toSeconds();
        if (silentSeconds < MIN_SILENT_SECONDS) {
            return Optional.empty();
        }

        Instant silenceStart = silent.get(0).timestamp().minus(SILENCE_START_TOLERANCE);
        return events.stream()
                .filter(this::indicatesConnectionLost)
                .filter(e -> !e.timestamp().isBefore(silenceStart) && e.timestamp().isBefore(end))
                .findFirst()
                .map(lost -> new MediaDrop(leg, lost, lastMedia.get(), silentSeconds));
    }

    /** Dòng stats cuối cùng còn nhận được audio; không có thì bên này chưa từng có media (đã có rule khác lo). */
    private Optional<CanonicalEvent> lastSampleWithMedia(List<CanonicalEvent> stats) {
        for (int i = stats.size() - 1; i >= 0; i--) {
            CanonicalEvent row = stats.get(i);
            if (positive(row.attribute(FIELD_BYTES_RECEIVED_IN_PERIOD)) && positive(row.attribute(FIELD_BYTES_RECEIVED))) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    /** Chỉ coi là im lặng khi field có mặt và bằng 0 - thiếu field thì không có căn cứ. */
    private boolean receivedNothingInPeriod(CanonicalEvent row) {
        String raw = row.attribute(FIELD_BYTES_RECEIVED_IN_PERIOD);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            return Double.parseDouble(raw) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean positive(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            return Double.parseDouble(raw) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Mốc chính bên này kết thúc cuộc gọi: bấm cúp máy, hoặc dòng "endCall with ..." của client. */
    private boolean isLocalEnd(CanonicalEvent e) {
        if (SIGNAL.equals(e.eventType())) {
            return "user_end_call".equals(e.attribute(FIELD_SIGNAL));
        }
        return LOG_MESSAGE.equals(e.eventType()) && startsWith(e.attribute(FIELD_MSG), "endCall with");
    }

    private boolean indicatesConnectionLost(CanonicalEvent e) {
        if (SIGNAL.equals(e.eventType())) {
            return "network_lost".equals(e.attribute(FIELD_SIGNAL));
        }
        if (!LOG_MESSAGE.equals(e.eventType()) || e.attribute(FIELD_MSG) == null) {
            return false;
        }
        String msg = e.attribute(FIELD_MSG).toUpperCase(Locale.ROOT);
        return msg.contains("PARTNER_LOST_NETWORK") || msg.contains("ONICECONNECTIONCHANGE: DISCONNECTED");
    }

    private boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }
}
