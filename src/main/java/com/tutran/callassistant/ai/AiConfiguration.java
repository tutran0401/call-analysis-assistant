package com.tutran.callassistant.ai;

import com.tutran.callassistant.ai.openai.OpenAiProvider;
import com.tutran.callassistant.application.port.out.AiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Chọn {@link AiProvider} theo {@code call-analysis.ai.provider}:
 * <ul>
 *   <li>{@code none} (mặc định) - không AI, pipeline chạy degraded bằng rule;</li>
 *   <li>{@code fake} - giả lập xác định, cho demo offline / evaluation khi chưa có key;</li>
 *   <li>{@code openai} - OpenAI Chat Completions; thiếu {@code OPENAI_API_KEY} thì tự rơi về {@code none}
 *       (hệ thống vẫn chạy, báo rõ lý do) thay vì lỗi khi khởi động.</li>
 * </ul>
 */
@Configuration
public class AiConfiguration {

    @Bean
    public AiProvider aiProvider(
            @Value("${call-analysis.ai.provider:none}") String provider,
            @Value("${call-analysis.ai.openai.api-key:}") String openAiKey,
            @Value("${call-analysis.ai.openai.model:gpt-4o-mini}") String openAiModel,
            @Value("${call-analysis.ai.openai.base-url:https://api.openai.com}") String openAiBaseUrl,
            @Value("${call-analysis.ai.openai.timeout-ms:12000}") long openAiTimeoutMillis,
            @Value("${call-analysis.ai.openai.max-attempts:2}") int openAiMaxAttempts) {
        return switch (provider.trim().toLowerCase()) {
            case "fake" -> new FakeAiProvider();
            case "none" -> new NoAiProvider("Chưa cấu hình AI provider (call-analysis.ai.provider=none)");
            case "openai" -> openAiKey.isBlank()
                    ? new NoAiProvider("Chưa đặt OPENAI_API_KEY nên không dùng được OpenAI")
                    : new OpenAiProvider(openAiClient(openAiBaseUrl, openAiKey, openAiTimeoutMillis), openAiModel,
                            openAiMaxAttempts);
            default -> throw new IllegalStateException("Unknown call-analysis.ai.provider: " + provider);
        };
    }

    private static RestClient openAiClient(String baseUrl, String apiKey, long timeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMillis, 5000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }
}
