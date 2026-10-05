package com.tutran.callassistant.ai;

import com.tutran.callassistant.ai.openai.OpenAiProvider;
import com.tutran.callassistant.application.port.out.AiProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationTest {

    private final AiConfiguration config = new AiConfiguration();

    private AiProvider choose(String provider, String key) {
        return config.aiProvider(provider, key, "gpt-4o-mini", "https://api.openai.com", 1000);
    }

    @Test
    void defaultsToNoAiAndFakeIsSelectable() {
        assertThat(choose("none", "")).isInstanceOf(NoAiProvider.class);
        assertThat(choose("FAKE ", "")).isInstanceOf(FakeAiProvider.class);
    }

    @Test
    void openAiWithoutKeyDegradesToNoAiInsteadOfFailingStartup() {
        AiProvider provider = choose("openai", "  ");

        assertThat(provider).isInstanceOf(NoAiProvider.class);
        assertThat(provider.modelName()).isEqualTo("none");
    }

    @Test
    void openAiWithKeyUsesTheConfiguredModel() {
        AiProvider provider = choose("openai", "sk-test");

        assertThat(provider).isInstanceOf(OpenAiProvider.class);
        assertThat(provider.modelName()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void unknownProviderFailsFast() {
        assertThatThrownBy(() -> choose("gemini", "")).isInstanceOf(IllegalStateException.class);
    }
}
