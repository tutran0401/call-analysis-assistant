package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses native WebRTC engine logs (iOS {@code RTCLogging.mm}-style and
 * Android/native {@code peer_connection_factory.cc}-style). These logs carry only a
 * relative {@code [seconds:millis]} offset since process start - there is no absolute
 * clock in this source at all, so every event here is emitted with
 * {@link TimestampConfidence#UNKNOWN}; {@code TimelineBuilder} is responsible for
 * anchoring them to an absolute time using another source.
 *
 * <p>Lines that don't match either header pattern are treated as a continuation of the
 * previous event's message (WebRTC engine logs routinely wrap a single log statement
 * across multiple physical lines, e.g. multi-line audio route dumps) rather than being
 * reported as malformed - only a genuinely unparseable file (no recognizable lines at
 * all) produces a warning.
 */
public final class WebRtcLogParser {

    private static final Pattern FORMAT_1 =
            Pattern.compile("^\\[(\\d+):(\\d+)]\\[(\\d+)]\\s+\\(([^)]+)\\):\\s*(.*)$");
    private static final Pattern FORMAT_2 =
            Pattern.compile("^([\\w.]+\\.(?:cc|mm)):\\s+\\[(\\d+):(\\d+)]\\[(\\d+)]\\s+\\(line\\s+(\\d+)\\):\\s*(.*)$");

    public ParseResult parse(Path file, String callId, Leg leg) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ParseResult(List.of(), List.of("Cannot read file " + file + ": " + e.getMessage()));
        }

        List<PendingEvent> pendingEvents = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        PendingEvent current = null;

        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            if (line.isBlank()) {
                continue;
            }
            Matcher m1 = FORMAT_1.matcher(line);
            Matcher m2 = FORMAT_2.matcher(line);
            if (m1.matches()) {
                current = new PendingEvent(lineNo,
                        Long.parseLong(m1.group(1)) * 1000L + Long.parseLong(m1.group(2)),
                        Map.of("threadId", m1.group(3), "location", m1.group(4)),
                        m1.group(5), line);
                pendingEvents.add(current);
            } else if (m2.matches()) {
                current = new PendingEvent(lineNo,
                        Long.parseLong(m2.group(2)) * 1000L + Long.parseLong(m2.group(3)),
                        Map.of("sourceFile", m2.group(1), "threadId", m2.group(4), "sourceLine", m2.group(5)),
                        m2.group(6), line);
                pendingEvents.add(current);
            } else if (current != null) {
                current.appendContinuation(line);
            } else {
                warnings.add("Unrecognized leading line at " + file + ":" + lineNo + " (no event to attach it to)");
            }
        }

        if (pendingEvents.isEmpty()) {
            warnings.add("No recognizable WebRTC log lines found in " + file);
        }

        List<CanonicalEvent> events = new ArrayList<>(pendingEvents.size());
        for (PendingEvent p : pendingEvents) {
            String message = p.message.toString();
            Map<String, String> attrs = new LinkedHashMap<>(p.attributes);
            attrs.put("message", message);
            events.add(new CanonicalEvent(
                    callId,
                    leg,
                    EventSource.WEBRTC,
                    file.toString(),
                    null,
                    p.elapsedMillis + "ms",
                    TimestampConfidence.UNKNOWN,
                    p.lineNo,
                    classify(message),
                    attrs,
                    p.rawLine.toString()
            ));
        }

        return new ParseResult(events, warnings);
    }

    private static String classify(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("iceconnectionchange")) {
            return "ICE_CONNECTION_STATE_CHANGE";
        }
        if (lower.contains("onconnectionchange")) {
            return "PEER_CONNECTION_STATE_CHANGE";
        }
        if (lower.contains("turn") && (lower.contains("error") || lower.contains("fail"))) {
            return "TURN_ERROR";
        }
        if (lower.contains("ice") && (lower.contains("error") || lower.contains("fail"))) {
            return "ICE_ERROR";
        }
        if (lower.contains("icecandidate") || lower.contains("addice")) {
            return "ICE_CANDIDATE";
        }
        return "ENGINE_LOG";
    }

    private static final class PendingEvent {
        final int lineNo;
        final long elapsedMillis;
        final Map<String, String> attributes;
        final StringBuilder message;
        final StringBuilder rawLine;

        PendingEvent(int lineNo, long elapsedMillis, Map<String, String> attributes,
                     String firstMessage, String firstRawLine) {
            this.lineNo = lineNo;
            this.elapsedMillis = elapsedMillis;
            this.attributes = attributes;
            this.message = new StringBuilder(firstMessage);
            this.rawLine = new StringBuilder(firstRawLine);
        }

        void appendContinuation(String line) {
            rawLine.append('\n').append(line);
            message.append('\n').append(line);
        }
    }
}
