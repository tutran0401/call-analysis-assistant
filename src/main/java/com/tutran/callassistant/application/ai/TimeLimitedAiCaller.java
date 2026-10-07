package com.tutran.callassistant.application.ai;

import com.tutran.callassistant.application.port.out.AiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bọc mọi lời gọi AI bằng một hạn chót cứng, độc lập với timeout của từng provider: dù provider nào
 * treo, request của người dùng vẫn kết thúc đúng hạn và đi tiếp bằng fallback. Mọi lỗi - kể cả
 * exception lạ - đều được quy về {@link AiProviderException}.
 */
@Component
public class TimeLimitedAiCaller {

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ai-call");
        thread.setDaemon(true);
        return thread;
    });
    private final long timeoutMillis;

    public TimeLimitedAiCaller(@Value("${call-analysis.ai.timeout-ms:30000}") long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public AiResult call(AiProvider provider, AiPrompt prompt) {
        CompletableFuture<AiResult> future = CompletableFuture.supplyAsync(() -> provider.analyze(prompt), executor);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AiProviderException(AiProviderException.Kind.TIMEOUT,
                    "AI không phản hồi trong " + timeoutMillis + " ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE, "Bị ngắt khi chờ AI", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AiProviderException known) {
                throw known;
            }
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE,
                    "Lỗi không dự kiến khi gọi AI: " + e.getCause().getClass().getSimpleName(), e.getCause());
        }
    }
}
