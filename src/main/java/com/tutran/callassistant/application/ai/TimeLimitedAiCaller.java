package com.tutran.callassistant.application.ai;

import com.tutran.callassistant.application.port.out.AiProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bọc mọi lời gọi AI bằng một hạn chót cứng, độc lập với timeout của từng provider: dù provider nào
 * treo, request của người dùng vẫn kết thúc đúng hạn và đi tiếp bằng fallback. Mọi lỗi - kể cả
 * exception lạ - đều được quy về {@link AiProviderException}.
 */
@Component
public class TimeLimitedAiCaller {

    /**
     * Số lời gọi AI chạy đồng thời tối đa. Pool không giới hạn (như Sprint 2) để một provider treo kéo theo
     * số thread tăng mãi; vượt mức này thì request mới fallback ngay về rule thay vì xếp hàng chờ.
     */
    static final int MAX_CONCURRENT_CALLS = 16;

    private final ExecutorService executor = new ThreadPoolExecutor(0, MAX_CONCURRENT_CALLS, 60, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "ai-call");
                thread.setDaemon(true);
                return thread;
            });
    private final long timeoutMillis;

    public TimeLimitedAiCaller(@Value("${call-analysis.ai.timeout-ms:30000}") long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public AiResult call(AiProvider provider, AiPrompt prompt) {
        CompletableFuture<AiResult> future;
        try {
            future = CompletableFuture.supplyAsync(() -> provider.analyze(prompt), executor);
        } catch (RejectedExecutionException e) {
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE,
                    "Đang có quá nhiều lời gọi AI đồng thời", e);
        }
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
