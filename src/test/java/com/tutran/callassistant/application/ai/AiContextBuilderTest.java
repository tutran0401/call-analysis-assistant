package com.tutran.callassistant.application.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.tutran.callassistant.analysis.verdict.IssueCategoryRegistry;
import com.tutran.callassistant.application.request.CallFocus;
import com.tutran.callassistant.application.request.Intent;
import com.tutran.callassistant.application.request.ParsedRequest;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.testsupport.Events;
import com.tutran.callassistant.testsupport.TestReports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiContextBuilderTest {

    private final AiContextBuilder builder = new AiContextBuilder(IssueCategoryRegistry.withDefaults());

    private CallTimeline timelineOf(int invites) {
        List<CanonicalEvent> events = new ArrayList<>();
        for (int i = 0; i < invites; i++) {
            events.add(Events.signaling("INVITE", "2026-09-18T03:07:2" + (i % 10) + "Z", i));
        }
        events.add(Events.signaling("OK_ACK_OK", "2026-09-18T03:08:00Z", invites));
        return new CallTimeline(Events.CALL_ID, events);
    }

    private JsonNode build(CallTimeline timeline) {
        return builder.build(new ParsedRequest(Intent.ANALYZE_WITH_FOCUS, Set.of(CallFocus.AUDIO_QUALITY), null),
                timeline, TestReports.successWithPacketLoss());
    }

    @Test
    void contextCarriesIntentFocusBaselineEvidenceMetricsAndTaxonomy() {
        JsonNode ctx = build(timelineOf(1));

        assertThat(ctx.get("intent").asText()).isEqualTo("ANALYZE_WITH_FOCUS");
        assertThat(ctx.get("focus")).hasSize(1);
        assertThat(ctx.at("/ruleBaseline/verdict").asText()).isEqualTo("SUCCESS");
        assertThat(ctx.get("evidence")).hasSize(3);
        assertThat(ctx.at("/evidence/1/id").asText()).isEqualTo("EV002");
        assertThat(ctx.get("metrics")).hasSize(5);
        assertThat(ctx.get("dataLimitations")).hasSize(1);
        assertThat(ctx.get("taxonomy").size()).isGreaterThanOrEqualTo(6);
        assertThat(ctx.at("/taxonomy/0/definition").asText()).isNotBlank();
    }

    @Test
    void timelineHasNoTimestampsAndCollapsesRepeatedEvents() {
        JsonNode timeline = build(timelineOf(15)).get("timeline");

        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("leg", "source", "event");
        assertThat(timeline.get(0).get("event").asText()).isEqualTo("INVITE");
    }

    @Test
    void timelineIsCapped() {
        List<CanonicalEvent> events = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            events.add(Events.endCall(Leg.CALLER, "TYPE" + i, "2026-09-18T03:07:21Z", i, java.util.Map.of()));
        }

        JsonNode timeline = build(new CallTimeline(Events.CALL_ID, events)).get("timeline");

        assertThat(timeline).hasSize(AiContextBuilder.MAX_TIMELINE_ENTRIES);
    }

    @Test
    void semiModeCarriesTheSameFactsAsOneNarrativeAndNoStructuredArrays() {
        AiContextBuilder semi = new AiContextBuilder(IssueCategoryRegistry.withDefaults(), "semi");

        JsonNode ctx = semi.build(new ParsedRequest(Intent.ANALYZE_CALL, Set.of(), null), timelineOf(2),
                TestReports.successWithPacketLoss());

        assertThat(semi.mode()).isEqualTo(AiContextBuilder.Mode.SEMI);
        assertThat(ctx.has("evidence")).isFalse();
        assertThat(ctx.has("metrics")).isFalse();
        assertThat(ctx.has("taxonomy")).isFalse();
        assertThat(ctx.at("/ruleBaseline/verdict").asText()).isEqualTo("SUCCESS");
        String narrative = ctx.get("narrative").asText();
        assertThat(narrative).contains("[EV002][callee_endcall.log 10:00:41.000] Packet loss phía callee = 9.8%")
                .contains("MOS (callee): 2.9 (End Call)").contains("Thiếu callee_webrtc.log")
                .contains("NETWORK_PACKET_LOSS:").contains("INVITE");
    }

    @Test
    void unknownContextModeFailsFast() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new AiContextBuilder(IssueCategoryRegistry.withDefaults(), "weird"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new AiContextBuilder(IssueCategoryRegistry.withDefaults()).mode())
                .isEqualTo(AiContextBuilder.Mode.STRUCTURED);
    }

    @Test
    void rawLinesAndAttributesNeverReachTheContext() {
        CanonicalEvent secretEvent = Events.endCall(Leg.CALLER, "SIGNALING_CMD", "2026-09-18T03:07:21Z", 1,
                java.util.Map.of("sdpOffer", "v=0 SECRET-SDP", "ip", "113.161.45.67"));

        String json = build(new CallTimeline(Events.CALL_ID, List.of(secretEvent))).toString();

        assertThat(json).doesNotContain("SECRET-SDP").doesNotContain("113.161.45.67").doesNotContain("sdpOffer");
    }
}
