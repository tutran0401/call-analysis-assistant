package com.tutran.callassistant.ai;

import com.tutran.callassistant.application.port.out.AiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chọn {@link AiProvider} theo {@code call-analysis.ai.provider}: {@code none} (mặc định - không AI,
 * pipeline chạy degraded bằng rule), {@code fake} (giả lập xác định, cho demo offline / evaluation khi
 * chưa có key). Provider thật (OpenAI) được cắm vào đây.
 */
@Configuration
public class AiConfiguration {

    @Bean
    public AiProvider aiProvider(@Value("${call-analysis.ai.provider:none}") String provider) {
        return switch (provider.trim().toLowerCase()) {
            case "fake" -> new FakeAiProvider();
            case "none" -> new NoAiProvider("Chưa cấu hình AI provider (call-analysis.ai.provider=none)");
            default -> throw new IllegalStateException("Unknown call-analysis.ai.provider: " + provider);
        };
    }
}
