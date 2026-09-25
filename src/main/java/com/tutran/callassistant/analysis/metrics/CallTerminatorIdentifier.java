package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.analysis.SignalingCommands;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Xác định bên nào chủ động kết thúc cuộc gọi, bằng cách đối chiếu {@code appUserId} của
 * lệnh BYE với {@code appUserId} trong dòng CALL_SUMMARY của end-call log từng bên.
 *
 * <p>Signaling log không nói trực tiếp BYE đến từ leg nào, nên cần bắc cầu qua danh tính
 * lấy từ client log; thiếu một trong hai đầu thì trả N/A kèm lý do, chứ không đoán.
 */
@Component
public class CallTerminatorIdentifier {

    private static final String APP_USER_ID = "appUserId";
    private static final String CALL_SUMMARY = "CALL_SUMMARY";

    public MetricResult<Leg> identify(CallTimeline timeline) {
        Optional<CanonicalEvent> bye = timeline.earliestSignaling(SignalingCommands.BYE);
        if (bye.isEmpty()) {
            return MetricResult.notAvailable("", MetricSources.SIGNALING, "no BYE command observed");
        }
        String byeAppUserId = bye.get().attribute(APP_USER_ID);
        if (byeAppUserId == null) {
            return MetricResult.notAvailable("", MetricSources.SIGNALING,
                    "BYE event has no appUserId to identify the sender");
        }
        for (Leg leg : com.tutran.callassistant.domain.metrics.CallMetrics.QUALITY_LEGS) {
            if (byeAppUserId.equals(identityOf(timeline, leg))) {
                return MetricResult.of(leg, "", MetricSources.SIGNALING);
            }
        }
        return MetricResult.notAvailable("", MetricSources.SIGNALING,
                "BYE sender's appUserId did not match caller or callee identity from end-call logs");
    }

    private String identityOf(CallTimeline timeline, Leg leg) {
        return timeline.forSourceAndLeg(EventSource.END_CALL, leg).stream()
                .filter(e -> CALL_SUMMARY.equals(e.eventType()))
                .map(e -> e.attribute(APP_USER_ID))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
