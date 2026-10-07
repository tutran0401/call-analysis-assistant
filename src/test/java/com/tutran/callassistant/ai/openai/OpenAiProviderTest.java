package com.tutran.callassistant.ai.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutran.callassistant.application.ai.AiPrompt;
import com.tutran.callassistant.application.ai.AiProviderException;
import com.tutran.callassistant.application.ai.AiProviderException.Kind;
import com.tutran.callassistant.application.ai.AiPromptFactory;
import com.tutran.callassistant.application.ai.AiResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Không gọi mạng thật: toàn bộ HTTP đi qua {@link MockRestServiceServer}. */
class OpenAiProviderTest {

    private static final String URL = "https://api.openai.com" + OpenAiProvider.CHAT_COMPLETIONS_PATH;

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com")
            .defaultHeader("Authorization", "Bearer sk-test-secret-0123456789");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiProvider provider = new OpenAiProvider(builder.build(), "gpt-4o-mini");
    private final AiPrompt prompt = new AiPromptFactory().create("{\"intent\":\"ANALYZE_CALL\"}");

    private String envelope(String content, String finishReason) throws Exception {
        var root = mapper.createObjectNode();
        var choice = root.putArray("choices").addObject();
        choice.put("finish_reason", finishReason);
        choice.putObject("message").put("role", "assistant").put("content", content);
        root.putObject("usage").put("prompt_tokens", 812).put("completion_tokens", 143);
        return mapper.writeValueAsString(root);
    }

    @Test
    void sendsStrictJsonSchemaRequestAndParsesContentAndUsage() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test-secret-0123456789"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.temperature").value(0))
                .andExpect(jsonPath("$.seed").exists())
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("{\"intent\":\"ANALYZE_CALL\"}"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.required.length()").value(8))
                .andRespond(withSuccess(envelope("{\"verdict\":\"SUCCESS\"}", "stop"), MediaType.APPLICATION_JSON));

        AiResult result = provider.analyze(prompt);

        assertThat(result.rawJson()).isEqualTo("{\"verdict\":\"SUCCESS\"}");
        assertThat(result.model()).isEqualTo("gpt-4o-mini");
        assertThat(result.promptTokens()).isEqualTo(812);
        assertThat(result.completionTokens()).isEqualTo(143);
        assertThat(result.totalTokens()).isEqualTo(955);
        server.verify();
    }

    @Test
    void rateLimitAndServerErrorsAreUnavailableWithoutLeakingTheKey() {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.UNAUTHORIZED}) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withStatus(status).body("{\"error\":\"sk-test-secret-0123456789\"}"));

            assertThatThrownBy(() -> provider.analyze(prompt))
                    .isInstanceOfSatisfying(AiProviderException.class, e -> {
                        assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE);
                        assertThat(e.getMessage()).contains(String.valueOf(status.value()))
                                .doesNotContain("sk-test-secret");
                    });
        }
    }

    @Test
    void transientFailureIsRetriedOnceAndThenSucceeds() throws Exception {
        OpenAiProvider retrying = new OpenAiProvider(builder.build(), "gpt-4o-mini", 2);
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(URL)).andRespond(withSuccess(envelope("{\"verdict\":\"FAIL\"}", "stop"),
                MediaType.APPLICATION_JSON));

        AiResult result = retrying.analyze(prompt);

        assertThat(result.rawJson()).isEqualTo("{\"verdict\":\"FAIL\"}");
        server.verify();
    }

    @Test
    void retriesStopAfterMaxAttemptsAndKeepTheLastErrorKind() {
        OpenAiProvider retrying = new OpenAiProvider(builder.build(), "gpt-4o-mini", 3);
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> retrying.analyze(prompt))
                .isInstanceOfSatisfying(AiProviderException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE);
                    assertThat(e.getMessage()).contains("503");
                });
        server.verify();
    }

    @Test
    void permanentErrorsAreNeverRetried() throws Exception {
        OpenAiProvider retrying = new OpenAiProvider(builder.build(), "gpt-4o-mini", 3);
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> retrying.analyze(prompt)).isInstanceOf(AiProviderException.class);
        server.verify();

        server.reset();
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("not json at all", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> retrying.analyze(prompt)).isInstanceOfSatisfying(AiProviderException.class,
                e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_RESPONSE));
        server.verify();
    }

    @Test
    void rateLimitIsRetriedButBadRequestIsNot() {
        OpenAiProvider retrying = new OpenAiProvider(builder.build(), "gpt-4o-mini", 2);
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> retrying.analyze(prompt)).isInstanceOf(AiProviderException.class);
        server.verify();

        server.reset();
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> retrying.analyze(prompt)).isInstanceOf(AiProviderException.class);
        server.verify();
    }

    @Test
    void gatewayTimeoutAndSocketTimeoutAreTimeouts() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        assertThatThrownBy(() -> provider.analyze(prompt))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.kind()).isEqualTo(Kind.TIMEOUT));

        server.reset();
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
        assertThatThrownBy(() -> provider.analyze(prompt))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.kind()).isEqualTo(Kind.TIMEOUT));
    }

    @Test
    void connectionFailureIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new java.io.IOException("connection refused")));

        assertThatThrownBy(() -> provider.analyze(prompt))
                .isInstanceOfSatisfying(AiProviderException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
    }

    @Test
    void malformedOrIncompleteEnvelopesAreInvalidResponses() throws Exception {
        String[] bad = {
                "not json at all",
                "{}",
                "{\"choices\":[]}",
                envelope("", "stop"),
                envelope("{\"verdict\":\"SU", "length"),
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null,\"refusal\":\"Tôi không thể\"}}]}"
        };
        for (String body : bad) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> provider.analyze(prompt))
                    .as(body)
                    .isInstanceOfSatisfying(AiProviderException.class,
                            e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_RESPONSE));
        }
    }

    @Test
    void missingUsageIsToleratedAsUnknownTokens() throws Exception {
        server.expect(requestTo(URL)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}",
                        MediaType.APPLICATION_JSON));

        AiResult result = provider.analyze(prompt);

        assertThat(result.totalTokens()).isNull();
        assertThat(result.rawJson()).isEqualTo("{}");
    }
}
