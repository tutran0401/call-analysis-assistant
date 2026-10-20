package com.tutran.callassistant.cli;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tutran.callassistant.ai.NoAiProvider;
import com.tutran.callassistant.application.ChatAnalysisService;
import com.tutran.callassistant.application.ChatRequest;
import com.tutran.callassistant.application.ChatResponse;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.application.port.out.AnalysisResultArchive;
import com.tutran.callassistant.application.port.out.SignalingEventArchive;
import com.tutran.callassistant.application.validation.UploadedFile;
import com.tutran.callassistant.evaluation.BatchStatistics;
import com.tutran.callassistant.ingest.es.ElasticsearchResultArchive;
import com.tutran.callassistant.ingest.file.CallDirectoryIndex;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Phân tích hàng loạt mọi Call-ID đang có trong Elasticsearch local (Sprint 3 T10) và thống kê phân bố verdict /
 * issue category / UNKNOWN / fallback.
 *
 * <p>ES chỉ chứa signaling; log client (end-call, WebRTC) được lấy từ thư mục cuộc gọi tương ứng dưới
 * {@code --data-root} - thiếu thì Call-ID đó bị bỏ qua và được đếm riêng. Mỗi cuộc gọi đi qua đúng đường của Web UI
 * ({@link ChatAnalysisService}), nên kết quả giống hệt khi người dùng tự đính kèm file. Kết quả được ghi vào index
 * {@code call-analysis-results} với {@code channel=batch} để xem trên Kibana.
 *
 * <p>Mặc định chỉ chạy rule (không tốn chi phí AI); {@code --with-ai} dùng AI provider đang cấu hình.
 */
@Component
@Order(40)
public class BatchCommand implements CliCommand {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String QUESTION = "Phân tích cuộc gọi này";

    private final ChatAnalysisService service;
    private final SignalingEventArchive signaling;
    private final AnalysisResultArchive results;
    private final AiProvider aiProvider;
    private final ConsoleWriter console;

    public BatchCommand(ChatAnalysisService service, SignalingEventArchive signaling,
                        ElasticsearchClient elasticsearch, AiProvider aiProvider, ConsoleWriter console) {
        this.service = service;
        this.signaling = signaling;
        // Batch luôn ghi kết quả vào ES (mục đích của nó là xem phân bố trên Kibana), kể cả khi CLI mặc định tắt.
        this.results = new ElasticsearchResultArchive(elasticsearch);
        this.aiProvider = aiProvider;
        this.console = console;
    }

    @Override
    public String name() {
        return "batch";
    }

    @Override
    public String usage() {
        return "batch --data-root <dir> [--with-ai] [--limit 500] [--out <dir>]";
    }

    @Override
    public void execute(List<String> arguments) {
        Map<String, String> options = Options.parse(arguments);
        if (!options.containsKey("data-root")) {
            console.line("Usage: " + usage());
            return;
        }
        CallDirectoryIndex index = new CallDirectoryIndex(Path.of(options.get("data-root")));
        int limit = Options.intOf(options, "limit", 500);
        Path outDir = Path.of(options.getOrDefault("out", "reports/batch-" + LocalDateTime.now().format(STAMP)));
        boolean withAi = options.containsKey("with-ai");
        AiProvider provider = withAi ? aiProvider : NoAiProvider.ruleOnly("rule-only (batch)");
        ChatAnalysisService batch = service.withAiProvider(provider).withResultArchive(results, "batch");

        List<String> callIds;
        try {
            callIds = signaling.callIds(limit);
        } catch (IOException | RuntimeException e) {
            console.line("Không đọc được danh sách Call-ID từ " + signaling.name() + " (" + e.getClass().getSimpleName()
                    + "). Elasticsearch đã chạy và đã import chưa?");
            return;
        }
        console.line("Tìm thấy " + callIds.size() + " Call-ID trong " + signaling.name()
                + (withAi ? " - phân tích có AI (" + provider.modelName() + ")" : " - chỉ rule (thêm --with-ai để dùng AI)"));

        BatchStatistics stats = new BatchStatistics();
        for (String callId : callIds) {
            Optional<List<UploadedFile>> files = clientLogs(index, callId);
            if (files.isEmpty()) {
                stats.addSkipped();
                continue;
            }
            long started = System.nanoTime();
            try {
                ChatResponse response = batch.handle(new ChatRequest(QUESTION, files.get(), callId));
                stats.add(response, (System.nanoTime() - started) / 1_000_000);
                console.line("  " + callId + " -> " + response.report().verdict() + " / "
                        + response.report().issueCategory());
            } catch (RuntimeException e) {
                stats.addError();
                console.line("  " + callId + " -> lỗi " + e.getClass().getSimpleName());
            }
        }
        String summary = stats.toMarkdown(withAi ? "Batch analysis (có AI)"
                : "Batch analysis (chỉ rule - mọi lượt đều degraded theo thiết kế, tỉ lệ fallback 100% là đương nhiên)",
                provider.modelName());
        console.line(summary);
        try {
            Files.createDirectories(outDir);
            Files.writeString(outDir.resolve("summary.md"), summary, StandardCharsets.UTF_8);
            console.line("Đã ghi " + outDir.resolve("summary.md").toAbsolutePath());
        } catch (IOException e) {
            console.line("Không ghi được " + outDir + ": " + e.getMessage());
        }
    }

    private static Optional<List<UploadedFile>> clientLogs(CallDirectoryIndex index, String callId) {
        Optional<Path> dir = index.find(callId);
        if (dir.isEmpty()) {
            return Optional.empty();
        }
        List<UploadedFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir.get())) {
            for (Path path : stream.filter(p -> p.getFileName().toString().matches(".*_(endcall|webrtc)\\.log")).toList()) {
                files.add(new UploadedFile(path.getFileName().toString(), Files.readAllBytes(path)));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return files.isEmpty() ? Optional.empty() : Optional.of(files);
    }
}
