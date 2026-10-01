package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.LegQualityMetrics;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chỉ số chất lượng của một bên: MOS, packet loss, RTT, jitter - lấy giá trị <b>tệ nhất quan sát
 * được trong cả cuộc gọi</b> từ periodic stats của end-call log, cộng thêm bản tóm tắt các sự kiện
 * ICE/TURN đáng chú ý trong log WebRTC.
 *
 * <p>Xem {@link #worstOf} để biết vì sao phải lấy giá trị tệ nhất chứ không phải dòng cuối cùng.
 *
 * <p>Thiếu nguồn nào thì chỉ số của nguồn đó là N/A tường minh kèm lý do; một field có mặt
 * nhưng không phải số cũng được báo lại chứ không bị âm thầm bỏ qua.
 */
@Component
public class LegQualityCalculator {

    private static final String PERIODIC_STATS = "PERIODIC_STATS";
    private static final String ENGINE_LOG = "ENGINE_LOG";

    private static final String FIELD_MOS = "audio.audioMos";
    private static final String FIELD_PACKET_LOSS = "audio.packetLostPercent";
    private static final String FIELD_RTT = "transport.currentRttMs";
    private static final String FIELD_JITTER = "audio.jitter";
    private static final String FIELD_BYTES_RECEIVED = "audio.bytesReceived";

    public LegQualityMetrics calculate(CallTimeline timeline, Leg leg) {
        List<CanonicalEvent> periodicStats = timeline.forSourceAndLeg(EventSource.END_CALL, leg).stream()
                .filter(e -> PERIODIC_STATS.equals(e.eventType()))
                .toList();

        if (periodicStats.isEmpty()) {
            // Phân biệt hai tình huống rất khác nhau mà trước đây gộp làm một câu: file không có, với
            // file có nhưng cuộc gọi chưa vào tầng media. Gộp lại khiến report nói "thiếu end-call log"
            // ngay ở cuộc gọi mà chính report đang trích dẫn end-call log đó làm evidence.
            String reason = timeline.hasAnyEvent(EventSource.END_CALL, leg)
                    ? "end-call log của " + leg + " có nhưng không chứa dòng periodic stats nào "
                            + "(cuộc gọi chưa vào tầng media)"
                    : "không có end-call log của " + leg;
            return new LegQualityMetrics(
                    MetricResult.notAvailable("", MetricSources.END_CALL, reason),
                    MetricResult.notAvailable("%", MetricSources.END_CALL, reason),
                    MetricResult.notAvailable("ms", MetricSources.END_CALL, reason),
                    MetricResult.notAvailable("ms", MetricSources.END_CALL, reason),
                    webrtcKeyEvents(timeline, leg));
        }

        List<CanonicalEvent> withMedia = periodicStats.stream().filter(this::hasReceivedMedia).toList();
        if (withMedia.isEmpty()) {
            String reason = leg + " chưa nhận được media nào (cả " + periodicStats.size()
                    + " dòng periodic stats đều có " + FIELD_BYTES_RECEIVED + " = 0), giá trị 0 chỉ là giữ chỗ";
            return new LegQualityMetrics(
                    MetricResult.notAvailable("", MetricSources.END_CALL, reason),
                    MetricResult.notAvailable("%", MetricSources.END_CALL, reason),
                    MetricResult.notAvailable("ms", MetricSources.END_CALL, reason),
                    MetricResult.notAvailable("ms", MetricSources.END_CALL, reason),
                    webrtcKeyEvents(timeline, leg));
        }

        return new LegQualityMetrics(
                worstOf(withMedia, FIELD_MOS, "", Worst.LOWEST),
                worstOf(withMedia, FIELD_PACKET_LOSS, "%", Worst.HIGHEST),
                worstOf(withMedia, FIELD_RTT, "ms", Worst.HIGHEST),
                worstOf(withMedia, FIELD_JITTER, "ms", Worst.HIGHEST),
                webrtcKeyEvents(timeline, leg));
    }

    /**
     * Dòng stats có phản ánh media thật không. Client vẫn ghi periodic stats khi media chưa lên
     * (ví dụ ICE thất bại): khi đó mọi field đều bằng 0, kể cả bytesReceived. Nếu đưa các dòng này
     * vào {@link #worstOf}, "MOS thấp nhất" sẽ luôn là 0 và báo cáo thành "packet loss 0%, RTT 0ms" -
     * đúng lỗi "mặc định về 0" mà mục 4.3 cấm. Schema nào không khai báo bytesReceived thì không có
     * căn cứ để loại, nên vẫn giữ dòng đó.
     */
    private boolean hasReceivedMedia(CanonicalEvent row) {
        String raw = row.attribute(FIELD_BYTES_RECEIVED);
        if (raw == null || raw.isBlank()) {
            return true;
        }
        try {
            return Double.parseDouble(raw) > 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** Chiều nào của chỉ số là "xấu": MOS càng thấp càng tệ, còn mất gói/RTT/jitter càng cao càng tệ. */
    private enum Worst { LOWEST, HIGHEST }

    /**
     * Lấy giá trị <b>tệ nhất quan sát được trong cả cuộc gọi</b>, chứ không phải giá trị ở dòng
     * periodic stats cuối cùng.
     *
     * <p>Dòng cuối là mẫu <i>ít đại diện nhất</i>: lúc đó stream thường đã tắt nên mọi chỉ số về 0.
     * Trên data mẫu, cuộc gọi {@code 271D1FAF} có dòng cuối báo packet loss 0.000% trong khi giữa
     * cuộc có tới 18 mẫu vượt ngưỡng, đỉnh 11.32% (caller) và 7.55% (callee) - tức cách lấy dòng cuối
     * đã giấu mất một cuộc gọi chất lượng kém thật sự.
     */
    private MetricResult<Double> worstOf(List<CanonicalEvent> samples, String attribute, String unit, Worst worst) {
        List<Double> values = new ArrayList<>();
        String lastNonNumeric = null;
        boolean mosPlaceholderSeen = false;
        for (CanonicalEvent sample : samples) {
            String raw = sample.attribute(attribute);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                double value = Double.parseDouble(raw);
                // MOS hợp lệ nằm trong thang 1-5; 0 nghĩa là client chưa tính được, không phải chất
                // lượng tệ nhất - để lọt vào đây thì "MOS thấp nhất" sẽ luôn là 0.
                if (FIELD_MOS.equals(attribute) && value <= 0) {
                    mosPlaceholderSeen = true;
                    continue;
                }
                values.add(value);
            } catch (NumberFormatException e) {
                lastNonNumeric = raw;
            }
        }
        if (values.isEmpty()) {
            String reason = lastNonNumeric != null
                    ? "field " + attribute + " có giá trị không phải số: " + lastNonNumeric
                    : mosPlaceholderSeen
                    ? attribute + " luôn = 0, nằm ngoài thang 1-5 - client chưa tính được MOS"
                    : "log này không có field " + attribute;
            return MetricResult.notAvailable(unit, MetricSources.END_CALL, reason);
        }
        double picked = worst == Worst.LOWEST
                ? values.stream().mapToDouble(Double::doubleValue).min().orElseThrow()
                : values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        return MetricResult.of(picked, unit, MetricSources.END_CALL);
    }

    /** Đếm các sự kiện WebRTC đã được phân loại (bỏ {@code ENGINE_LOG} là phần log nền chung). */
    private MetricResult<String> webrtcKeyEvents(CallTimeline timeline, Leg leg) {
        if (!timeline.hasAnyEvent(EventSource.WEBRTC, leg)) {
            return MetricResult.notAvailable("", MetricSources.WEBRTC,
                    "không có webrtc log của " + leg);
        }
        Map<String, Long> counts = timeline.forSourceAndLeg(EventSource.WEBRTC, leg).stream()
                .filter(e -> !ENGINE_LOG.equals(e.eventType()))
                .collect(Collectors.groupingBy(CanonicalEvent::eventType, Collectors.counting()));
        if (counts.isEmpty()) {
            return MetricResult.of("không có sự kiện đáng chú ý", "", MetricSources.WEBRTC);
        }
        String summary = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return MetricResult.of(summary, "", MetricSources.WEBRTC);
    }
}
