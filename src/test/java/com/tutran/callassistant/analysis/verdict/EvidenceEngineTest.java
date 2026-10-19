package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import com.tutran.callassistant.domain.verdict.Evidence;
import com.tutran.callassistant.testsupport.Events;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Mục 8.2: evidence trace được về dòng log gốc (peer evaluation issue 4). */
class EvidenceEngineTest {

    @Test
    void anchoredWebRtcEvidenceShowsTheFileLineAndTheMarkThatIsActuallyInTheFile() {
        // Dòng 902 của callee_webrtc.log (2D9057AA): "[020:195] ... IceConnectionState checking => failed".
        CanonicalEvent raw = Events.unanchoredWebRtc(Leg.CALLEE, 20_195, 902);
        CanonicalEvent anchored = raw.withTimestamp(Instant.parse("2026-09-17T23:43:22.031Z"), TimestampConfidence.ANCHORED);

        Evidence evidence = new EvidenceEngine().add(anchored, "ICE failed");

        assertThat(evidence.sourceLabel()).isEqualTo("callee_webrtc.log:902");
        assertThat(evidence.timestampDisplay()).isEqualTo("[020:195] ≈ 2026-09-17 23:43:22.031");
        assertThat(evidence.formatted()).startsWith("[EV001][callee_webrtc.log:902 [020:195] ≈ ");
    }

    @Test
    void unanchoredWebRtcEvidenceKeepsTheFileMark() {
        Evidence evidence = new EvidenceEngine().add(Events.unanchoredWebRtc(Leg.CALLER, 5_007, 12), "x");

        assertThat(evidence.timestampDisplay()).isEqualTo("[005:007] (relative offset, unanchored)");
        assertThat(evidence.sourceLabel()).isEqualTo("caller_webrtc.log:12");
    }

    @Test
    void endCallEvidenceHasALineNumberAndSignalingHasNone() {
        EvidenceEngine engine = new EvidenceEngine();

        Evidence endCall = engine.add(Events.endCall(Leg.CALLER, "CALL_SUMMARY", "2026-09-17T23:43:00Z", 37, Map.of()), "x");
        Evidence signaling = engine.add(Events.signaling("BYE", "2026-09-17T23:44:00Z", 5), "y");

        assertThat(endCall.sourceLabel()).isEqualTo("caller_endcall.log:37");
        assertThat(endCall.timestampDisplay()).isEqualTo("2026-09-17 23:43:00.000");
        assertThat(signaling.sourceLabel()).isEqualTo("signaling");
        assertThat(signaling.id()).isEqualTo("EV002");
    }
}
