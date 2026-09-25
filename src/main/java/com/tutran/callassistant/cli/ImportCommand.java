package com.tutran.callassistant.cli;

import com.tutran.callassistant.application.ImportSummary;
import com.tutran.callassistant.application.port.in.ImportSignalingUseCase;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/** Index toàn bộ {@code signaling.json} dưới các thư mục gốc vào Elasticsearch (T1). */
@Component
@Order(10)
public class ImportCommand implements CliCommand {

    private final ImportSignalingUseCase importSignaling;
    private final ConsoleWriter console;

    public ImportCommand(ImportSignalingUseCase importSignaling, ConsoleWriter console) {
        this.importSignaling = importSignaling;
        this.console = console;
    }

    @Override
    public String name() {
        return "import";
    }

    @Override
    public String usage() {
        return "import <dataRoot> [<dataRoot> ...]";
    }

    @Override
    public void execute(List<String> arguments) {
        if (arguments.isEmpty()) {
            console.line("Usage: " + usage());
            return;
        }
        List<Path> dataRoots = arguments.stream().map(Path::of).toList();

        // In tiến độ ngay trong lúc chạy thay vì chờ tới khi xong: import cả tập data mẫu mất một
        // lúc, người chạy cần thấy nó đang tiến triển.
        ImportSummary summary = importSignaling.importFrom(dataRoots,
                (callId, eventCount) -> console.line("Imported " + eventCount + " events for " + callId));

        summary.warnings().forEach(warning -> console.error(warning));
        console.line("Done: " + summary.totalCalls() + " calls, " + summary.totalEvents()
                + " events indexed into " + summary.indexName() + ".");
    }
}
