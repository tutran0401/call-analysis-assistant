package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.sanitize.Pseudonymizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CallTerminatorIdentifierTest {
    @Test
    void matchesPseudonymizedServerIdentityToRawClientIdentity() {
        Pseudonymizer pseudonymizer = new Pseudonymizer("stable-test-salt");
        String sender = pseudonymizer.pseudonym("USER", "caller-raw-id");
        CallTimeline timeline = new CallTimeline("CALL-1", List.of(
                event(EventSource.SIGNALING, Leg.SERVER, "BYE", sender, 1),
                event(EventSource.END_CALL, Leg.CALLER, "CALL_SUMMARY", "caller-raw-id", 2)));

        assertThat(new CallTerminatorIdentifier(pseudonymizer).identify(timeline).value()).isEqualTo(Leg.CALLER);
    }

    private static CanonicalEvent event(EventSource source, Leg leg, String type, String user, int second) {
        return new CanonicalEvent("CALL-1", leg, source, "test", Instant.parse("2026-10-20T00:00:0" + second + "Z"),
                "test", TimestampConfidence.EXACT, second, type, Map.of("appUserId", user), "raw");
    }
}
