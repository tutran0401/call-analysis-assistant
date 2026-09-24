package com.tutran.callassistant.cli;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.es.EsSignalingEventFetcher;
import com.tutran.callassistant.es.SignalingIndexer;
import com.tutran.callassistant.evidence.RuleVerdictEngine;
import com.tutran.callassistant.evidence.RuleVerdictResult;
import com.tutran.callassistant.metrics.CallMetrics;
import com.tutran.callassistant.metrics.CallMetricsCalculator;
import com.tutran.callassistant.parser.CallLogDirectoryLoader;
import com.tutran.callassistant.parser.ParseResult;
import com.tutran.callassistant.parser.SignalingJsonParser;
import com.tutran.callassistant.report.Report;
import com.tutran.callassistant.report.ReportBuilder;
import com.tutran.callassistant.report.ReportRenderer;
import com.tutran.callassistant.timeline.CallTimeline;
import com.tutran.callassistant.timeline.TimelineBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 1 demo entrypoint (no web UI yet - §5.3 "Demo tối thiểu 5 cuộc gọi bằng CLI").
 *
 * <pre>
 *   import &lt;dataRoot&gt; [&lt;dataRoot&gt; ...]   index every signaling.json under these roots into ES
 *   analyze &lt;callDir&gt; [--from-file]      run the full pipeline for one call, print its report
 *   demo &lt;dataRoot&gt; [&lt;dataRoot&gt; ...]     analyze every call folder found under these roots
 * </pre>
 */
@Component
public class CallAnalysisCli implements CommandLineRunner {

    private final SignalingIndexer signalingIndexer;
    private final EsSignalingEventFetcher esSignalingEventFetcher;
    private final CallLogDirectoryLoader directoryLoader = new CallLogDirectoryLoader();
    private final TimelineBuilder timelineBuilder = new TimelineBuilder();
    private final CallMetricsCalculator metricsCalculator = new CallMetricsCalculator();
    private final RuleVerdictEngine ruleVerdictEngine = new RuleVerdictEngine();
    private final ReportBuilder reportBuilder = new ReportBuilder();
    private final ReportRenderer reportRenderer = new ReportRenderer();

    @Value("${elasticsearch.signaling-index:call-signaling-events}")
    private String signalingIndexName;

    public CallAnalysisCli(SignalingIndexer signalingIndexer, EsSignalingEventFetcher esSignalingEventFetcher) {
        this.signalingIndexer = signalingIndexer;
        this.esSignalingEventFetcher = esSignalingEventFetcher;
    }

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            printUsage(System.out);
            return;
        }
        try {
            switch (args[0]) {
                case "import" -> runImport(List.of(args).subList(1, args.length));
                case "analyze" -> runAnalyze(List.of(args).subList(1, args.length));
                case "demo" -> runDemo(List.of(args).subList(1, args.length));
                default -> printUsage(System.out);
            }
        } catch (Exception e) {
            System.err.println("Command failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }

    private void printUsage(PrintStream out) {
        out.println("Usage:");
        out.println("  import <dataRoot> [<dataRoot> ...]");
        out.println("  analyze <callDir> [--from-file]");
        out.println("  demo <dataRoot> [<dataRoot> ...]");
    }

    private void runImport(List<String> args) throws IOException {
        if (args.isEmpty()) {
            System.out.println("Usage: import <dataRoot> [<dataRoot> ...]");
            return;
        }
        signalingIndexer.ensureIndex(signalingIndexName);
        int totalCalls = 0;
        int totalEvents = 0;
        for (String rootArg : args) {
            Path root = Path.of(rootArg);
            for (Path callDir : listCallDirectories(root)) {
                Path signalingFile = callDir.resolve("signaling.json");
                if (!Files.exists(signalingFile)) {
                    continue;
                }
                int indexed = signalingIndexer.importFile(signalingIndexName, signalingFile);
                totalCalls++;
                totalEvents += indexed;
                System.out.println("Imported " + indexed + " events for " + callDir.getFileName());
            }
        }
        System.out.println("Done: " + totalCalls + " calls, " + totalEvents + " events indexed into '"
                + signalingIndexName + "'.");
    }

    private void runDemo(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("Usage: demo <dataRoot> [<dataRoot> ...]");
            return;
        }
        for (String rootArg : args) {
            Path root = Path.of(rootArg);
            for (Path callDir : listCallDirectories(root)) {
                System.out.println("=".repeat(80));
                System.out.println("Call folder: " + callDir);
                analyzeAndPrint(callDir, false);
            }
        }
    }

    private void runAnalyze(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("Usage: analyze <callDir> [--from-file]");
            return;
        }
        Path callDir = Path.of(args.get(0));
        boolean fromFile = args.contains("--from-file");
        analyzeAndPrint(callDir, fromFile);
    }

    private void analyzeAndPrint(Path callDir, boolean fromFile) {
        String callId = callDir.getFileName().toString();
        List<String> warnings = new ArrayList<>();

        List<CanonicalEvent> signalingEvents = fetchSignaling(callDir, callId, fromFile, warnings);
        ParseResult clientLogs = directoryLoader.loadClientLogs(callDir, callId);
        warnings.addAll(clientLogs.warnings());

        List<CanonicalEvent> all = new ArrayList<>(signalingEvents);
        all.addAll(clientLogs.events());

        CallTimeline timeline = timelineBuilder.build(callId, all);
        CallMetrics metrics = metricsCalculator.calculate(timeline);
        RuleVerdictResult ruleResult = ruleVerdictEngine.evaluate(timeline, metrics);
        Report report = reportBuilder.build(callId, ruleResult, metrics);

        System.out.println(reportRenderer.render(report));
        if (!warnings.isEmpty()) {
            System.out.println("## Ghi chú xử lý (parser warnings, không thuộc report chính thức)");
            warnings.forEach(w -> System.out.println("- " + w));
        }
    }

    private List<CanonicalEvent> fetchSignaling(Path callDir, String callId, boolean fromFile, List<String> warnings) {
        if (!fromFile) {
            ParseResult esResult = esSignalingEventFetcher.fetchByCallId(callId);
            if (!esResult.events().isEmpty()) {
                return esResult.events();
            }
            warnings.addAll(esResult.warnings());
            warnings.add("No signaling events found in Elasticsearch for " + callId
                    + "; falling back to the local signaling.json (run 'import' first for the ES-backed path).");
        }
        Path signalingFile = callDir.resolve("signaling.json");
        if (!Files.exists(signalingFile)) {
            warnings.add("No signaling.json found in " + callDir);
            return List.of();
        }
        ParseResult fileResult = new SignalingJsonParser().parse(signalingFile, callId);
        warnings.addAll(fileResult.warnings());
        return fileResult.events();
    }

    private List<Path> listCallDirectories(Path root) {
        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    dirs.add(path);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not list " + root + ": " + e.getMessage());
        }
        return dirs;
    }
}
