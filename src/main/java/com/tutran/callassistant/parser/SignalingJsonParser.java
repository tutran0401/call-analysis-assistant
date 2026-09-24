package com.tutran.callassistant.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the mentor-provided Elasticsearch export format for signaling logs
 * ({@code signaling.json}: {@code {callId, events: [...] }}). Used both by the local ES
 * import script (T1) and, directly, wherever a canonical view of a call's signaling is
 * needed without a live ES query (tests, offline runs). Every event is
 * {@link Leg#SERVER} - the signaling log is a server-side view not tied to one leg.
 */
public final class SignalingJsonParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParseResult parse(Path file, String callIdOverride) {
        JsonNode root;
        try {
            root = objectMapper.readTree(file.toFile());
        } catch (IOException e) {
            return new ParseResult(List.of(), List.of("Cannot read/parse " + file + ": " + e.getMessage()));
        }

        String callId = callIdOverride != null ? callIdOverride
                : root.path("callId").asText(file.getParent() != null ? file.getParent().getFileName().toString() : "UNKNOWN");

        JsonNode eventsNode = root.path("events");
        if (!eventsNode.isArray()) {
            return new ParseResult(List.of(), List.of("No 'events' array found in " + file));
        }

        List<CanonicalEvent> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int index = 0;
        for (JsonNode eventNode : eventsNode) {
            index++;
            try {
                events.add(toEvent(eventNode, callId, file, index));
            } catch (RuntimeException e) {
                warnings.add("Skipped malformed signaling event #" + index + " in " + file + " - " + e.getMessage());
            }
        }

        if (root.path("truncated").asBoolean(false)) {
            warnings.add("Signaling export for " + callId + " is truncated (returned "
                    + root.path("returned").asInt(-1) + " of " + root.path("total_matching").asInt(-1) + ")");
        }

        return new ParseResult(events, warnings);
    }

    private CanonicalEvent toEvent(JsonNode eventNode, String callId, Path file, int sequenceNumber) {
        String rawTimestamp = eventNode.path("@timestamp").asText(null);
        if (rawTimestamp == null) {
            throw new IllegalArgumentException("missing @timestamp");
        }
        Instant timestamp;
        try {
            timestamp = Instant.parse(rawTimestamp);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("unparseable @timestamp '" + rawTimestamp + "'");
        }

        String cmd = eventNode.path("cmd").asText(null);
        String eventType = cmd != null ? cmd : "SIGNALING_EVENT";

        Map<String, String> attributes = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = eventNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if ("@timestamp".equals(entry.getKey())) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (!value.isNull()) {
                attributes.put(entry.getKey(), value.isTextual() ? value.asText() : value.toString());
            }
        }

        return new CanonicalEvent(
                callId,
                Leg.SERVER,
                EventSource.SIGNALING,
                file.toString(),
                timestamp,
                rawTimestamp,
                TimestampConfidence.EXACT,
                sequenceNumber,
                eventType,
                attributes,
                eventNode.toString()
        );
    }
}
