package com.tutran.callassistant.analysis.timeline;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.testsupport.Events;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClockSkewAnchorTest {

    private final ClockSkewAnchor anchor = new ClockSkewAnchor();

    private CanonicalEvent deviceCmd(Leg leg, String cmd, String timestamp, long seq) {
        return Events.endCall(leg, "SIGNALING_CMD", timestamp, seq, Map.of("cmd", cmd));
    }

    @Test
    void shiftsDeviceEventsWhenDeviceClockIsFarBehindServer() {
        // đồng hồ thiết bị chậm 60 giây so với server (cộng 100ms trễ mạng)
        List<CanonicalEvent> input = List.of(
                Events.signaling("INIT_CALL", "2026-01-01T00:01:00.100Z", 1),
                Events.signaling("INVITE", "2026-01-01T00:01:01.100Z", 2),
                deviceCmd(Leg.CALLER, "INIT_CALL", "2026-01-01T00:00:00.000Z", 3),
                deviceCmd(Leg.CALLER, "INVITE", "2026-01-01T00:00:01.000Z", 4),
                Events.endCall(Leg.CALLER, "PERIODIC_STATS", "2026-01-01T00:00:10.000Z", 5, Map.of()));

        List<CanonicalEvent> result = anchor.anchor(input);

        CanonicalEvent stats = result.get(4);
        assertThat(stats.timestamp()).isEqualTo(Instant.parse("2026-01-01T00:01:10.100Z"));
        assertThat(stats.attribute(ClockSkewAnchor.CORRECTION_ATTRIBUTE)).isEqualTo("60100");
        assertThat(result.stream().filter(e -> e.source() == EventSource.SIGNALING))
                .allSatisfy(e -> assertThat(e.attributes()).isEmpty());
    }

    @Test
    void leavesTimestampsAloneWhenDifferenceIsOnlyNetworkLatency() {
        List<CanonicalEvent> input = List.of(
                Events.signaling("INIT_CALL", "2026-01-01T00:00:00.300Z", 1),
                deviceCmd(Leg.CALLER, "INIT_CALL", "2026-01-01T00:00:00.000Z", 2));

        assertThat(anchor.anchor(input)).isEqualTo(input);
    }

    @Test
    void correctsEachLegIndependently() {
        List<CanonicalEvent> input = List.of(
                Events.signaling("INIT_CALL", "2026-01-01T00:00:30.000Z", 1),
                Events.signaling("INVITE", "2026-01-01T00:00:31.000Z", 2),
                deviceCmd(Leg.CALLER, "INIT_CALL", "2026-01-01T00:00:30.000Z", 3),
                deviceCmd(Leg.CALLEE, "INVITE", "2026-01-01T00:00:21.000Z", 4));

        List<CanonicalEvent> result = anchor.anchor(input);

        assertThat(result.get(2).timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:30.000Z"));
        assertThat(result.get(3).timestamp()).isEqualTo(Instant.parse("2026-01-01T00:00:31.000Z"));
    }

    @Test
    void doesNothingWithoutSharedCommands() {
        List<CanonicalEvent> input = List.of(
                Events.signaling("BYE", "2026-01-01T00:10:00Z", 1),
                deviceCmd(Leg.CALLER, "INIT_CALL", "2026-01-01T00:00:00Z", 2));

        assertThat(anchor.anchor(input)).isEqualTo(input);
    }
}
