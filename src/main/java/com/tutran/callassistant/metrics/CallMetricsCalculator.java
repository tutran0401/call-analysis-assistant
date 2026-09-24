package com.tutran.callassistant.metrics;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.timeline.CallTimeline;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Computes the Sprint 1 Core call metrics (PROJECT_SPEC.md §4.3) from a built
 * {@link CallTimeline}. Every metric is either a value or an explicit N/A with a reason -
 * never a silent 0 for missing data.
 *
 * <p>The signaling command vocabulary observed in the sample data
 * ({@code INIT_CALL, INVITE, TRYING, RINGING, ACK_RINGING, OK, OK_ACK_OK, ICE, ACK_ICE,
 * PAIR_PING, PAIR_PONG_PAIR_PING, BYE, ACK_BYE, BYE_ACK_BYE, LOG_STATS}) does not include a
 * literal "OK_ACK" command as named in the spec's metric definitions; {@code OK_ACK_OK} is
 * treated as its equivalent (the concatenated ack-of-OK event). This mapping assumption is
 * called out here so it can be corrected once the mentor confirms the exact signaling
 * command semantics.
 */
public final class CallMetricsCalculator {

    private static final String SIGNALING = "Signaling";
    private static final String END_CALL = "End Call log";
    private static final String WEBRTC = "WebRTC log";

    public CallMetrics calculate(CallTimeline timeline) {
        List<CanonicalEvent> all = timeline.events();
        List<CanonicalEvent> signaling = timeline.forSource(EventSource.SIGNALING);

        MetricResult<Duration> setupTime = durationBetween(signaling, "INIT_CALL", "OK_ACK_OK",
                "Setup time");
        MetricResult<Duration> timeToReachCallee = durationBetween(signaling, "INVITE", "TRYING",
                "time to reach callee");
        MetricResult<Duration> ringingTime = durationBetween(signaling, "RINGING", "OK",
                "ringing time");
        MetricResult<Duration> connectedDuration = durationBetween(signaling, "OK_ACK_OK", "BYE",
                "connected duration");

        MetricResult<Integer> inviteRetransmitCount = retransmitCount(signaling, "INVITE");
        MetricResult<Integer> byeRetransmitCount = retransmitCount(signaling, "BYE");
        MetricResult<Integer> noSessionsFoundCount = countNoSessionsFound(all);
        MetricResult<Leg> terminator = terminatorLeg(timeline, signaling);

        Map<Leg, LegQualityMetrics> qualityByLeg = new EnumMap<>(Leg.class);
        qualityByLeg.put(Leg.CALLER, legQuality(timeline, Leg.CALLER));
        qualityByLeg.put(Leg.CALLEE, legQuality(timeline, Leg.CALLEE));

        return new CallMetrics(
                timeline.callId(),
                setupTime,
                timeToReachCallee,
                inviteRetransmitCount,
                noSessionsFoundCount,
                ringingTime,
                connectedDuration,
                terminator,
                byeRetransmitCount,
                qualityByLeg
        );
    }

    private MetricResult<Duration> durationBetween(List<CanonicalEvent> signalingEvents,
                                                     String startCmd, String endCmd, String label) {
        Optional<CanonicalEvent> start = earliest(signalingEvents, startCmd, null);
        if (start.isEmpty()) {
            return MetricResult.notAvailable("s", SIGNALING, "no " + startCmd + " event found");
        }
        Optional<CanonicalEvent> end = earliest(signalingEvents, endCmd, start.get().timestamp());
        if (end.isEmpty()) {
            return MetricResult.notAvailable("s", SIGNALING,
                    "no " + endCmd + " event found after " + startCmd + " for " + label);
        }
        Duration duration = Duration.between(start.get().timestamp(), end.get().timestamp());
        return MetricResult.of(duration, "s", SIGNALING);
    }

    private Optional<CanonicalEvent> earliest(List<CanonicalEvent> events, String cmd, Instant after) {
        return events.stream()
                .filter(e -> cmd.equals(e.eventType()))
                .filter(e -> e.timestamp() != null)
                .filter(e -> after == null || !e.timestamp().isBefore(after))
                .min(Comparator.comparing(CanonicalEvent::timestamp));
    }

    private MetricResult<Integer> retransmitCount(List<CanonicalEvent> signalingEvents, String cmd) {
        Set<String> requestIds = signalingEvents.stream()
                .filter(e -> cmd.equals(e.eventType()))
                .map(e -> e.attribute("requestId"))
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        if (requestIds.isEmpty()) {
            return MetricResult.notAvailable("retransmits", SIGNALING, "no " + cmd + " event found");
        }
        return MetricResult.of(requestIds.size() - 1, "retransmits", SIGNALING);
    }

    private MetricResult<Integer> countNoSessionsFound(List<CanonicalEvent> allEvents) {
        boolean anyFreeTextField = allEvents.stream()
                .anyMatch(e -> e.attribute("msg") != null || e.attribute("message") != null);
        if (!anyFreeTextField) {
            return MetricResult.notAvailable("occurrences", SIGNALING + "/" + END_CALL,
                    "no free-text log message field present in the provided logs to search");
        }
        long count = allEvents.stream()
                .flatMap(e -> java.util.stream.Stream.of(e.attribute("msg"), e.attribute("message")))
                .filter(text -> text != null && text.toLowerCase().contains("no session"))
                .count();
        return MetricResult.of((int) count, "occurrences", SIGNALING + "/" + END_CALL);
    }

    private MetricResult<Leg> terminatorLeg(CallTimeline timeline, List<CanonicalEvent> signalingEvents) {
        Optional<CanonicalEvent> bye = earliest(signalingEvents, "BYE", null);
        if (bye.isEmpty()) {
            return MetricResult.notAvailable("", SIGNALING, "no BYE command observed");
        }
        String byeAppUserId = bye.get().attribute("appUserId");
        if (byeAppUserId == null) {
            return MetricResult.notAvailable("", SIGNALING, "BYE event has no appUserId to identify the sender");
        }
        String callerAppUserId = callSummaryAttribute(timeline, Leg.CALLER, "appUserId");
        String calleeAppUserId = callSummaryAttribute(timeline, Leg.CALLEE, "appUserId");
        if (byeAppUserId.equals(callerAppUserId)) {
            return MetricResult.of(Leg.CALLER, "", SIGNALING);
        }
        if (byeAppUserId.equals(calleeAppUserId)) {
            return MetricResult.of(Leg.CALLEE, "", SIGNALING);
        }
        return MetricResult.notAvailable("", SIGNALING,
                "BYE sender's appUserId did not match caller or callee identity from end-call logs");
    }

    private String callSummaryAttribute(CallTimeline timeline, Leg leg, String attribute) {
        return timeline.forLeg(leg).stream()
                .filter(e -> e.source() == EventSource.END_CALL && "CALL_SUMMARY".equals(e.eventType()))
                .map(e -> e.attribute(attribute))
                .filter(v -> v != null)
                .findFirst()
                .orElse(null);
    }

    private LegQualityMetrics legQuality(CallTimeline timeline, Leg leg) {
        List<CanonicalEvent> periodicStats = timeline.forLeg(leg).stream()
                .filter(e -> e.source() == EventSource.END_CALL && "PERIODIC_STATS".equals(e.eventType()))
                .toList();

        MetricResult<Double> mos;
        MetricResult<Double> packetLoss;
        MetricResult<Double> rtt;
        MetricResult<Double> jitter;
        if (periodicStats.isEmpty()) {
            String reason = "no periodic stats rows found for " + leg + " (end-call log missing or leg not present)";
            mos = MetricResult.notAvailable("", END_CALL, reason);
            packetLoss = MetricResult.notAvailable("%", END_CALL, reason);
            rtt = MetricResult.notAvailable("ms", END_CALL, reason);
            jitter = MetricResult.notAvailable("ms", END_CALL, reason);
        } else {
            CanonicalEvent last = periodicStats.get(periodicStats.size() - 1);
            mos = doubleMetric(last, "audio.audioMos", "", END_CALL);
            packetLoss = doubleMetric(last, "audio.packetLostPercent", "%", END_CALL);
            rtt = doubleMetric(last, "transport.currentRttMs", "ms", END_CALL);
            jitter = doubleMetric(last, "audio.jitter", "ms", END_CALL);
        }

        MetricResult<String> webrtcKeyEvents = webrtcKeyEvents(timeline, leg);

        return new LegQualityMetrics(mos, packetLoss, rtt, jitter, webrtcKeyEvents);
    }

    private MetricResult<Double> doubleMetric(CanonicalEvent event, String attribute, String unit, String source) {
        String raw = event.attribute(attribute);
        if (raw == null || raw.isBlank()) {
            return MetricResult.notAvailable(unit, source, "field '" + attribute + "' not present in this log");
        }
        try {
            return MetricResult.of(Double.parseDouble(raw), unit, source);
        } catch (NumberFormatException e) {
            return MetricResult.notAvailable(unit, source, "field '" + attribute + "' had a non-numeric value: " + raw);
        }
    }

    private MetricResult<String> webrtcKeyEvents(CallTimeline timeline, Leg leg) {
        List<CanonicalEvent> webrtcEvents = timeline.forLeg(leg).stream()
                .filter(e -> e.source() == EventSource.WEBRTC && !"ENGINE_LOG".equals(e.eventType()))
                .toList();
        if (timeline.forLeg(leg).stream().noneMatch(e -> e.source() == EventSource.WEBRTC)) {
            return MetricResult.notAvailable("", WEBRTC, "no webrtc log found for " + leg);
        }
        Map<String, Long> counts = webrtcEvents.stream()
                .collect(java.util.stream.Collectors.groupingBy(CanonicalEvent::eventType,
                        java.util.stream.Collectors.counting()));
        if (counts.isEmpty()) {
            return MetricResult.of("none observed", "", WEBRTC);
        }
        String summary = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
        return MetricResult.of(summary, "", WEBRTC);
    }
}
