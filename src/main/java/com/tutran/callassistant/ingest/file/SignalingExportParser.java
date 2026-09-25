package com.tutran.callassistant.ingest.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import org.springframework.stereotype.Component;

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
 * Parse định dạng export Elasticsearch mà mentor cung cấp cho signaling log
 * ({@code signaling.json}: {@code {callId, events: [...] }}).
 *
 * <p>Dùng cho cả script import ES local (T1) lẫn bất kỳ đâu cần view chuẩn hoá của signaling một
 * cuộc gọi mà không cần query ES thật (test, chạy offline - xem {@link LocalFileSignalingSource}).
 * Mọi sự kiện đều gắn {@link Leg#SERVER}: signaling log là góc nhìn phía server, không gắn với
 * một bên cụ thể nào.
 *
 * <p>Cố tình <b>không</b> implement {@link ClientLogParser}: khi quét thư mục cuộc gọi, file này
 * phải bị bỏ qua vì signaling đã đi vào hệ thống qua đường riêng - parse lại sẽ đếm lặp mọi sự
 * kiện signaling.
 */
@Component
public class SignalingExportParser implements LogParser {

    private static final String TIMESTAMP_FIELD = "@timestamp";
    private static final String DEFAULT_EVENT_TYPE = "SIGNALING_EVENT";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LogFileType type() {
        return LogFileType.SIGNALING_EXPORT;
    }

    @Override
    public boolean recognizes(String firstMeaningfulLine) {
        return firstMeaningfulLine.startsWith("{");
    }

    @Override
    public NormalizedEvents parse(LogFile file) {
        Path path = file.path();
        JsonNode root;
        try {
            root = objectMapper.readTree(path.toFile());
        } catch (IOException e) {
            return NormalizedEvents.unreadable("Cannot read/parse " + path + ": " + e.getMessage());
        }

        String callId = resolveCallId(file, root);
        JsonNode eventsNode = root.path("events");
        if (!eventsNode.isArray()) {
            return NormalizedEvents.unreadable("No 'events' array found in " + path);
        }

        List<CanonicalEvent> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int index = 0;
        for (JsonNode eventNode : eventsNode) {
            index++;
            try {
                events.add(toEvent(eventNode, callId, path, index));
            } catch (RuntimeException e) {
                warnings.add("Skipped malformed signaling event #" + index + " in " + path
                        + " - " + e.getMessage());
            }
        }

        if (root.path("truncated").asBoolean(false)) {
            warnings.add("Signaling export for " + callId + " is truncated (returned "
                    + root.path("returned").asInt(-1) + " of " + root.path("total_matching").asInt(-1) + ")");
        }
        return new NormalizedEvents(events, warnings);
    }

    /** Call-ID từ bên ngoài (tên thư mục) là nguồn tin cậy nhất; không có thì đọc từ chính file. */
    private String resolveCallId(LogFile file, JsonNode root) {
        if (file.callId() != null) {
            return file.callId();
        }
        Path parent = file.path().getParent();
        String fallback = parent != null ? parent.getFileName().toString() : "UNKNOWN";
        return root.path("callId").asText(fallback);
    }

    private CanonicalEvent toEvent(JsonNode eventNode, String callId, Path path, int sequenceNumber) {
        String rawTimestamp = eventNode.path(TIMESTAMP_FIELD).asText(null);
        if (rawTimestamp == null) {
            throw new IllegalArgumentException("missing " + TIMESTAMP_FIELD);
        }
        Instant timestamp;
        try {
            timestamp = Instant.parse(rawTimestamp);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("unparseable " + TIMESTAMP_FIELD + " '" + rawTimestamp + "'");
        }

        String cmd = eventNode.path("cmd").asText(null);
        return new CanonicalEvent(
                callId,
                Leg.SERVER,
                EventSource.SIGNALING,
                path.toString(),
                timestamp,
                rawTimestamp,
                TimestampConfidence.EXACT,
                sequenceNumber,
                cmd != null ? cmd : DEFAULT_EVENT_TYPE,
                flattenAttributes(eventNode),
                eventNode.toString()
        );
    }

    private Map<String, String> flattenAttributes(JsonNode eventNode) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = eventNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (TIMESTAMP_FIELD.equals(entry.getKey())) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (!value.isNull()) {
                attributes.put(entry.getKey(), value.isTextual() ? value.asText() : value.toString());
            }
        }
        return attributes;
    }
}
