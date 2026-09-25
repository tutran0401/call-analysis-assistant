package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.metrics.LegQualityMetrics;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.report.MetricRow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chuyển {@link CallMetrics} thành các dòng của bảng "Chỉ số cuộc gọi" trong report: tên tiếng Việt,
 * giá trị đã định dạng, và nguồn.
 *
 * <p>Tách khỏi {@link RuleBasedReportAssembler} vì đây là hai trách nhiệm khác nhau: assembler quyết
 * định report gồm những phần nào, catalog này quyết định bảng chỉ số trông như thế nào. Thêm/đổi tên
 * một dòng chỉ số không còn phải mở class dựng report.
 */
@Component
public class MetricRowCatalog {

    public List<MetricRow> rowsFor(CallMetrics metrics) {
        List<MetricRow> rows = new ArrayList<>();
        rows.add(duration("Thời gian thiết lập", metrics.setupTime()));
        rows.add(duration("Thời gian với tới callee (INVITE→TRYING)", metrics.timeToReachCallee()));
        rows.add(plain("Số lần gửi lại INVITE", metrics.inviteRetransmitCount()));
        rows.add(plain("Số lần \"No sessions found\"", metrics.noSessionsFoundCount()));
        rows.add(duration("Thời gian đổ chuông", metrics.ringingTime()));
        rows.add(duration("Thời lượng kết nối", metrics.connectedDuration()));
        rows.add(leg("Bên kết thúc", metrics.terminator()));
        rows.add(plain("Số lần gửi lại BYE", metrics.byeRetransmitCount()));
        rows.addAll(qualityRows(metrics));
        return rows;
    }

    private List<MetricRow> qualityRows(CallMetrics metrics) {
        List<MetricRow> rows = new ArrayList<>();
        for (Leg leg : CallMetrics.QUALITY_LEGS) {
            LegQualityMetrics quality = metrics.qualityOf(leg);
            if (quality == null) {
                continue;
            }
            String label = leg.name().toLowerCase(Locale.ROOT);
            rows.add(plain("MOS (" + label + ")", quality.mos()));
            rows.add(plain("Packet loss (" + label + ")", quality.packetLossPercent()));
            rows.add(plain("RTT (" + label + ")", quality.rttMs()));
            rows.add(plain("Jitter (" + label + ")", quality.jitterMs()));
            rows.add(plain("Sự kiện ICE/TURN (" + label + ")", quality.webrtcKeyEvents()));
        }
        return rows;
    }

    /** Thời lượng luôn hiển thị theo giây với 3 chữ số thập phân, để so sánh giữa các cuộc gọi dễ hơn. */
    private MetricRow duration(String name, MetricResult<Duration> metric) {
        String value = metric.isAvailable()
                ? String.format(Locale.ROOT, "%.3f s", metric.value().toMillis() / 1000.0)
                : notAvailable(metric);
        return new MetricRow(name, value, metric.source());
    }

    private MetricRow leg(String name, MetricResult<Leg> metric) {
        String value = metric.isAvailable() ? metric.value().name() : notAvailable(metric);
        return new MetricRow(name, value, metric.source());
    }

    private MetricRow plain(String name, MetricResult<?> metric) {
        return new MetricRow(name, metric.display(), metric.source());
    }

    private String notAvailable(MetricResult<?> metric) {
        return "N/A (" + metric.naReason() + ")";
    }
}
