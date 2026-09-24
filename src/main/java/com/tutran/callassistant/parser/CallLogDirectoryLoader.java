package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.Leg;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scans a call's directory and normalizes every recognizable log file in it, using
 * {@link FileTypeDetector} to identify each file by content - so a misnamed file (the
 * sample dataset itself has one: {@code calleer_webrtc.log}) is still parsed correctly.
 * The end-call log's leg comes from its own CALL_SUMMARY row when present (see
 * {@link EndCallLogParser}); WebRTC engine logs carry no such signal in their content at
 * all, so their leg is inferred from the filename as a last resort.
 */
public final class CallLogDirectoryLoader {

    private final EndCallLogParser endCallLogParser = new EndCallLogParser();
    private final WebRtcLogParser webRtcLogParser = new WebRtcLogParser();

    /** Parses every end-call and WebRTC log in {@code dir}. Signaling files are skipped - see {@code includeSignaling}. */
    public ParseResult loadClientLogs(Path dir, String callId) {
        List<Path> files = listFiles(dir);
        List<com.tutran.callassistant.domain.CanonicalEvent> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Path file : files) {
            LogFileType type = FileTypeDetector.detect(file);
            ParseResult result = switch (type) {
                case END_CALL -> endCallLogParser.parse(file, callId);
                case WEBRTC -> webRtcLogParser.parse(file, callId, inferLegFromFilename(file));
                case SIGNALING_EXPORT -> ParseResult.empty();
                case UNKNOWN -> new ParseResult(List.of(),
                        List.of("Unrecognized file type, skipped: " + file));
            };
            events.addAll(result.events());
            warnings.addAll(result.warnings());
        }
        return new ParseResult(events, warnings);
    }

    private List<Path> listFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        } catch (IOException e) {
            // an unreadable directory yields no client-log events; the caller sees this
            // reflected as missing data (e.g. RuleVerdictEngine's "missing end-call log").
        }
        return files;
    }

    private Leg inferLegFromFilename(Path file) {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.contains("callee")) {
            return Leg.CALLEE;
        }
        if (lower.contains("caller")) {
            return Leg.CALLER;
        }
        return Leg.UNKNOWN;
    }
}
