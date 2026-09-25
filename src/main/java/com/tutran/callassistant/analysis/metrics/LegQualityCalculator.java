package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.LegQualityMetrics;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chỉ số chất lượng của một bên: MOS, packet loss, RTT, jitter (lấy từ dòng periodic stats
 * cuối cùng của end-call log, tức là ảnh chụp gần nhất với lúc cuộc gọi kết thúc), cộng
 * thêm bản tóm tắt các sự kiện ICE/TURN đáng chú ý trong log WebRTC.
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

    public LegQualityMetrics calculate(CallTimeline timeline, Leg leg) {
        List<CanonicalEvent> periodicStats = timeline.forSourceAndLeg(EventSource.END_CALL, leg).stream()
                .filter(e -> PERIODIC_STATS.equals(e.eventType()))
                .toList();

        if (periodicStats.isEmpty()) {
            String reason = "no periodic stats rows found for " + leg
                    + " (end-call log missing or leg not present)";
            return new LegQualityMetrics(
                    MetricResult.notAvailable("", MetricSources.END_CALL, reason),
                    MetricResult.notAvailable("%", MetricSources.END_CALL, reason),
                    MetricResult.notAvailable("ms", MetricSources.END_CALL, reason),
                    MetricResult.notAvailable("ms", MetricSources.END_CALL, reason),
                    webrtcKeyEvents(timeline, leg));
        }

        CanonicalEvent last = periodicStats.get(periodicStats.size() - 1);
        return new LegQualityMetrics(
                doubleMetric(last, FIELD_MOS, ""),
                doubleMetric(last, FIELD_PACKET_LOSS, "%"),
                doubleMetric(last, FIELD_RTT, "ms"),
                doubleMetric(last, FIELD_JITTER, "ms"),
                webrtcKeyEvents(timeline, leg));
    }

    private MetricResult<Double> doubleMetric(CanonicalEvent event, String attribute, String unit) {
        String raw = event.attribute(attribute);
        if (raw == null || raw.isBlank()) {
            return MetricResult.notAvailable(unit, MetricSources.END_CALL,
                    "field '" + attribute + "' not present in this log");
        }
        try {
            return MetricResult.of(Double.parseDouble(raw), unit, MetricSources.END_CALL);
        } catch (NumberFormatException e) {
            return MetricResult.notAvailable(unit, MetricSources.END_CALL,
                    "field '" + attribute + "' had a non-numeric value: " + raw);
        }
    }

    /** Đếm các sự kiện WebRTC đã được phân loại (bỏ {@code ENGINE_LOG} là phần log nền chung). */
    private MetricResult<String> webrtcKeyEvents(CallTimeline timeline, Leg leg) {
        if (!timeline.hasAnyEvent(EventSource.WEBRTC, leg)) {
            return MetricResult.notAvailable("", MetricSources.WEBRTC, "no webrtc log found for " + leg);
        }
        Map<String, Long> counts = timeline.forSourceAndLeg(EventSource.WEBRTC, leg).stream()
                .filter(e -> !ENGINE_LOG.equals(e.eventType()))
                .collect(Collectors.groupingBy(CanonicalEvent::eventType, Collectors.counting()));
        if (counts.isEmpty()) {
            return MetricResult.of("none observed", "", MetricSources.WEBRTC);
        }
        String summary = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return MetricResult.of(summary, "", MetricSources.WEBRTC);
    }
}
