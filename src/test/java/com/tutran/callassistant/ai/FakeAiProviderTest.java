package com.tutran.callassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.tutran.callassistant.analysis.verdict.IssueCategoryRegistry;
import com.tutran.callassistant.application.ai.AiContextBuilder;
import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiPromptFactory;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.request.Intent;
import com.tutran.callassistant.application.request.ParsedRequest;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.testsupport.TestReports;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAiProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiPromptFactory prompts = new AiPromptFactory();

    private AiPrompt prompt() {
        JsonNode context = new AiContextBuilder(IssueCategoryRegistry.withDefaults()).build(
                new ParsedRequest(Intent.ANALYZE_CALL, Set.of(), null),
                new CallTimeline("CALL-1", List.of()), TestReports.successWithPacketLoss());
        return prompts.create(context.toString());
    }

    @Test
    void promptFactoryLoadsVietnameseSystemPromptAndSchema() {
        AiPrompt prompt = prompt();

        assertThat(prompt.systemPrompt()).contains("evidenceIds").contains("tiếng Việt");
        assertThat(prompt.outputSchema().get("required")).hasSize(8);
    }

    @Test
    void agreeModeMirrorsRuleBaselineAndMatchesTheOutputSchema() throws Exception {
        String raw = new FakeAiProvider().analyze(prompt()).rawJson();
        JsonNode out = mapper.readTree(raw);
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(prompts.outputSchema());

        assertThat(schema.validate(out)).isEmpty();
        assertThat(out.get("verdict").asText()).isEqualTo("SUCCESS");
        assertThat(out.get("evidenceIds")).hasSize(3);
    }

    @Test
    void faultModesProduceTheirSpecificDefect() throws Exception {
        assertThat(new FakeAiProvider(FakeAiProvider.Mode.UNKNOWN_EVIDENCE).analyze(prompt()).rawJson())
                .contains("EV999");
        assertThat(new FakeAiProvider(FakeAiProvider.Mode.BAD_ENUM).analyze(prompt()).rawJson()).contains("MAYBE");
        assertThat(mapper.readTree(new FakeAiProvider(FakeAiProvider.Mode.CONFLICT).analyze(prompt()).rawJson())
                .get("verdict").asText()).isEqualTo("FAIL");
        assertThat(new FakeAiProvider(FakeAiProvider.Mode.INVENTED_NUMBER).analyze(prompt()).rawJson())
                .contains("77.7");
        assertThat(new FakeAiProvider(FakeAiProvider.Mode.MISSING_FIELD).analyze(prompt()).rawJson())
                .doesNotContain("\"analysis\"");
        assertThat(new FakeAiProvider(FakeAiProvider.Mode.BAD_FORMAT).analyze(prompt()).rawJson()).doesNotContain("{");
    }

    @Test
    void timeoutAndUnavailableModesThrowClassifiedExceptions() {
        assertThatThrownBy(() -> new FakeAiProvider(FakeAiProvider.Mode.TIMEOUT).analyze(prompt()))
                .isInstanceOf(AiProviderException.class)
                .extracting(e -> ((AiProviderException) e).kind()).isEqualTo(AiProviderException.Kind.TIMEOUT);
        assertThatThrownBy(() -> new NoAiProvider("no key").analyze(prompt()))
                .isInstanceOf(AiProviderException.class)
                .extracting(e -> ((AiProviderException) e).kind()).isEqualTo(AiProviderException.Kind.NOT_CONFIGURED);
        assertThatThrownBy(() -> NoAiProvider.ruleOnly("batch").analyze(prompt()))
                .isInstanceOf(AiProviderException.class)
                .extracting(e -> ((AiProviderException) e).kind()).isEqualTo(AiProviderException.Kind.RULE_ONLY);
    }
}
