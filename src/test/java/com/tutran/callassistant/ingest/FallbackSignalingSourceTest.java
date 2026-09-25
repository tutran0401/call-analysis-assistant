package com.tutran.callassistant.ingest;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.testsupport.Events;
import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackSignalingSourceTest {

    private static final String REASON = "nothing indexed for %s; using the local file";
    private static final CallReference CALL =
            CallReference.ofDirectory(SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS));

    private static SignalingEventSource returning(NormalizedEvents events) {
        return call -> events;
    }

    @Test
    void usesThePrimarySourceWhenItHasData() {
        NormalizedEvents indexed = NormalizedEvents.of(
                List.of(Events.signaling("INIT_CALL", "2026-01-01T00:00:00Z", 1)));
        SignalingEventSource neverCalled = call -> {
            throw new AssertionError("fallback must not be consulted when the primary has data");
        };

        NormalizedEvents result = new FallbackSignalingSource(returning(indexed), neverCalled, REASON).fetch(CALL);

        assertThat(result.events()).hasSize(1);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void fallsBackAndExplainsWhyWhenThePrimaryIsEmpty() {
        NormalizedEvents fromFallback = NormalizedEvents.of(
                List.of(Events.signaling("INIT_CALL", "2026-01-01T00:00:00Z", 1)));

        NormalizedEvents result = new FallbackSignalingSource(
                returning(NormalizedEvents.empty()), returning(fromFallback), REASON).fetch(CALL);

        assertThat(result.events()).hasSize(1);
        assertThat(result.warnings()).containsExactly(
                "nothing indexed for " + SampleCalls.SUCCESS_FULL_LOGS + "; using the local file");
    }

    @Test
    void keepsTheWarningsOfBothSourcesInTheOrderTheyHappened() {
        NormalizedEvents primaryFailure = NormalizedEvents.unreadable("elasticsearch is down");
        NormalizedEvents fallbackFailure = NormalizedEvents.unreadable("no signaling.json either");

        NormalizedEvents result = new FallbackSignalingSource(
                returning(primaryFailure), returning(fallbackFailure), REASON).fetch(CALL);

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).containsExactly(
                "elasticsearch is down",
                "nothing indexed for " + SampleCalls.SUCCESS_FULL_LOGS + "; using the local file",
                "no signaling.json either");
    }

    @Test
    void readsRealSignalingFromDiskThroughTheFallbackPath() {
        SignalingEventSource emptyIndex = returning(NormalizedEvents.empty());
        SignalingEventSource onDisk = com.tutran.callassistant.ingest.file.LocalFileSignalingSource.withDefaults();

        NormalizedEvents result = new FallbackSignalingSource(emptyIndex, onDisk, REASON).fetch(CALL);

        assertThat(result.events()).hasSize(200);
        assertThat(result.warnings()).anyMatch(w -> w.contains("using the local file"));
    }

    @Test
    void reportsMissingFileWhenNeitherSourceHasAnything() {
        SignalingEventSource emptyIndex = returning(NormalizedEvents.empty());
        SignalingEventSource onDisk = com.tutran.callassistant.ingest.file.LocalFileSignalingSource.withDefaults();
        CallReference missing = CallReference.ofDirectory(Path.of("sample-data", "success", "NO-SUCH-CALL"));

        NormalizedEvents result = new FallbackSignalingSource(emptyIndex, onDisk, REASON).fetch(missing);

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("No signaling.json found"));
    }
}
