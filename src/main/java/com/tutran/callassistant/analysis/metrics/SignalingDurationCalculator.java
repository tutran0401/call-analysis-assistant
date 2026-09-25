package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.analysis.SignalingCommands;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Các chỉ số thời lượng suy ra từ khoảng cách giữa hai lệnh signaling (mục 4.3): thời gian
 * thiết lập, thời gian với tới callee, thời gian đổ chuông, thời lượng kết nối.
 *
 * <p>Cả bốn chỉ số dùng chung đúng một phép tính "khoảng cách giữa lệnh A và lệnh A-sau-đó",
 * nên chỉ viết một lần ở {@link #between}; thiếu một trong hai đầu mốc thì trả N/A kèm lý do
 * chỉ rõ thiếu lệnh nào, chứ không trả 0.
 */
@Component
public class SignalingDurationCalculator {

    public MetricResult<Duration> setupTime(CallTimeline timeline) {
        return between(timeline, SignalingCommands.INIT_CALL, SignalingCommands.OK_ACK_OK, "Setup time");
    }

    public MetricResult<Duration> timeToReachCallee(CallTimeline timeline) {
        return between(timeline, SignalingCommands.INVITE, SignalingCommands.TRYING, "time to reach callee");
    }

    public MetricResult<Duration> ringingTime(CallTimeline timeline) {
        return between(timeline, SignalingCommands.RINGING, SignalingCommands.OK, "ringing time");
    }

    public MetricResult<Duration> connectedDuration(CallTimeline timeline) {
        return between(timeline, SignalingCommands.OK_ACK_OK, SignalingCommands.BYE, "connected duration");
    }

    private MetricResult<Duration> between(CallTimeline timeline, String startCmd, String endCmd, String label) {
        Optional<CanonicalEvent> start = timeline.earliestSignaling(startCmd);
        if (start.isEmpty()) {
            return MetricResult.notAvailable("s", MetricSources.SIGNALING, "no " + startCmd + " event found");
        }
        Optional<CanonicalEvent> end = timeline.earliestSignaling(endCmd, start.get().timestamp());
        if (end.isEmpty()) {
            return MetricResult.notAvailable("s", MetricSources.SIGNALING,
                    "no " + endCmd + " event found after " + startCmd + " for " + label);
        }
        return MetricResult.of(Duration.between(start.get().timestamp(), end.get().timestamp()),
                "s", MetricSources.SIGNALING);
    }
}
