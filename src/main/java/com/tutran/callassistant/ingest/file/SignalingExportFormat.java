package com.tutran.callassistant.ingest.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Hai dạng export signaling mentor đã cung cấp, đưa về một dạng chung trước khi parse/import.
 *
 * <ul>
 *   <li>Dạng cũ (Sprint 1-2): {@code {callId, events: [{cmd, service, level, ...}]}}.</li>
 *   <li>Dạng mới ({@code new_dataset}, export thẳng document ES): {@code {call_id, logs: [{grpcMethod,
 *       service: {name}, kubernetes: {container_name}, log: {level}, message, ...}]}}.</li>
 * </ul>
 *
 * <p>Sự kiện dạng mới được đổi tên field về dạng cũ ({@code grpcMethod -> cmd}, {@code service.name ->
 * service}, {@code log.level -> level}) để rule, timeline và mapping ES không phải biết có hai dạng.
 * Field {@code message} (text tự do của server) được giữ nguyên - phép đếm "No sessions found" vốn tìm
 * trên mọi nguồn có field này.
 */
public final class SignalingExportFormat {

    private SignalingExportFormat() {
    }

    /** Mảng sự kiện: {@code events} (dạng cũ) hoặc {@code logs} (dạng mới); không có thì MissingNode. */
    public static JsonNode events(JsonNode root) {
        JsonNode events = root.path("events");
        if (events.isArray()) {
            return events;
        }
        JsonNode logs = root.path("logs");
        return logs.isArray() ? logs : MissingNode.getInstance();
    }

    /** Call-ID ghi trong file ({@code callId} hoặc {@code call_id}), không có thì {@code fallback}. */
    public static String callId(JsonNode root, String fallback) {
        String callId = root.path("callId").asText(null);
        return callId != null ? callId : root.path("call_id").asText(fallback);
    }

    /** Bản sao sự kiện với tên field theo dạng cũ; sự kiện dạng cũ đi qua không đổi. */
    public static JsonNode normalize(JsonNode event) {
        if (!event.isObject()) {
            return event;
        }
        ObjectNode copy = ((ObjectNode) event).deepCopy();
        JsonNode grpcMethod = copy.remove("grpcMethod");
        if (!copy.hasNonNull("cmd") && grpcMethod != null && !grpcMethod.isNull()) {
            copy.set("cmd", grpcMethod);
        }
        JsonNode kubernetes = copy.remove("kubernetes");
        JsonNode service = copy.get("service");
        if (service != null && service.isObject()) {
            String name = service.path("name").asText(null);
            if (name == null && kubernetes != null) {
                name = kubernetes.path("container_name").asText(null);
            }
            if (name != null) {
                copy.put("service", name);
            } else {
                copy.remove("service");
            }
        }
        JsonNode log = copy.remove("log");
        if (!copy.hasNonNull("level") && log != null && log.hasNonNull("level")) {
            copy.set("level", log.get("level"));
        }
        return copy;
    }
}
