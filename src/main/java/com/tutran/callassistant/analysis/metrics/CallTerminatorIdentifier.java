package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.analysis.SignalingCommands;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Xác định bên nào chủ động kết thúc cuộc gọi (ai gửi BYE đầu tiên), bằng cách đối chiếu
 * {@code appUserId} của lệnh BYE với danh tính của caller và callee.
 *
 * <p>Danh tính lấy <b>từ chính signaling</b> trước (mục 4.3 ghi nguồn của chỉ số này là
 * Signaling): caller là người gửi {@code INIT_CALL}, callee là người gửi lệnh phản hồi đầu tiên
 * ({@code TRYING}/{@code RINGING}/{@code OK}). Chỉ khi signaling không có đủ danh tính mới bắc cầu
 * qua dòng CALL_SUMMARY của end-call log từng bên. Không khớp được thì trả N/A kèm lý do, không đoán.
 */
@Component
public class CallTerminatorIdentifier {

    private static final String APP_USER_ID = "appUserId";
    private static final String CALL_SUMMARY = "CALL_SUMMARY";
    /** Các lệnh chỉ callee gửi - phản hồi cho INVITE của caller. */
    private static final List<String> CALLEE_RESPONSES =
            List.of(SignalingCommands.TRYING, SignalingCommands.RINGING, SignalingCommands.OK);

    public MetricResult<Leg> identify(CallTimeline timeline) {
        Optional<CanonicalEvent> bye = timeline.earliestSignaling(SignalingCommands.BYE);
        if (bye.isEmpty()) {
            return MetricResult.notAvailable("", MetricSources.SIGNALING, "không có lệnh BYE nào trong signaling");
        }
        String byeAppUserId = bye.get().attribute(APP_USER_ID);
        if (byeAppUserId == null) {
            return MetricResult.notAvailable("", MetricSources.SIGNALING,
                    "lệnh BYE không có appUserId để xác định bên gửi");
        }
        for (Leg leg : CallMetrics.QUALITY_LEGS) {
            if (byeAppUserId.equals(identityOf(timeline, leg))) {
                return MetricResult.of(leg, "", MetricSources.SIGNALING);
            }
        }
        return MetricResult.notAvailable("", MetricSources.SIGNALING,
                "appUserId của bên gửi BYE không khớp caller (INIT_CALL) hay callee (TRYING/RINGING/OK)");
    }

    private String identityOf(CallTimeline timeline, Leg leg) {
        return signalingIdentityOf(timeline, leg).orElseGet(() -> endCallIdentityOf(timeline, leg));
    }

    private Optional<String> signalingIdentityOf(CallTimeline timeline, Leg leg) {
        List<String> commands = leg == Leg.CALLER ? List.of(SignalingCommands.INIT_CALL) : CALLEE_RESPONSES;
        return commands.stream()
                .map(timeline::earliestSignaling)
                .flatMap(Optional::stream)
                .map(e -> e.attribute(APP_USER_ID))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private String endCallIdentityOf(CallTimeline timeline, Leg leg) {
        return timeline.forSourceAndLeg(EventSource.END_CALL, leg).stream()
                .filter(e -> CALL_SUMMARY.equals(e.eventType()))
                .map(e -> e.attribute(APP_USER_ID))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
