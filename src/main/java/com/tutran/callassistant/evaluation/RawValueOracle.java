package com.tutran.callassistant.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutran.callassistant.application.validation.UploadedFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bộ đo rò rỉ <b>độc lập với Sanitizer</b> (Mentor review Sprint 2): đọc thẳng <i>giá trị gốc</i> nhạy cảm từ file
 * dữ liệu của case, rồi tìm nguyên văn các giá trị đó trong AI request, response và log.
 *
 * <p>Phép đo cũ dùng chính bộ dò của Sanitizer cộng vài giá trị mồi, nên một giá trị Sanitizer không nhận ra thì
 * phép đo cũng không nhận ra. Ở đây hướng đi ngược lại: không nhìn văn bản đầu ra để đoán đâu là dữ liệu nhạy cảm,
 * mà lấy danh sách giá trị từ <b>cấu trúc</b> của dữ liệu đầu vào - cột theo schema {@code #Hn} của end-call log,
 * cây JSON của signaling export / ô JSON, dòng SDP, khối {@code Cand[...]} của WebRTC log - theo bảng field của
 * {@code docs/sensitive-data-inventory.md}. Lớp này không dò giá trị theo mẫu (regex) và không gọi gì trong gói
 * {@code sanitize}.
 *
 * <p>Không tính: Call-ID (người dùng tự đưa vào, report phải hiện nó), giá trị client đã tự che ({@code *},
 * {@code .x}), IP loopback / 0.0.0.0, giá trị ngắn hơn {@value #MIN_LENGTH} ký tự.
 */
public final class RawValueOracle {

    /** Field nhạy cảm theo inventory (mục 5.2 + phần bổ sung Sprint 1); so khớp phần cuối của tên field. */
    static final Set<String> SENSITIVE_FIELDS = Set.of(
            "appUserId", "callUserId", "partnerAppUserId", "partnerCallUserId", "userId", "deviceId",
            "sessionId", "csid", "requestId", "turnLoggingId", "turnSessionInfo",
            "ip", "address", "relatedAddress", "username", "credential", "password",
            "phone", "phoneNumber", "msisdn", "email", "token", "accessToken", "authorization", "jwt");
    /** Dòng SDP chứa bí mật (inventory: Secret - Drop). */
    private static final List<String> SDP_PREFIXES = List.of("a=ice-ufrag:", "a=ice-pwd:", "a=fingerprint:sha-256 ");
    private static final String CANDIDATE_BLOCK = "Cand[";
    static final int MIN_LENGTH = 4;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Mọi giá trị nhạy cảm gốc có trong các file của một case. */
    public Set<String> extract(List<UploadedFile> files, String callId) {
        Set<String> values = new LinkedHashSet<>();
        for (UploadedFile file : files) {
            String text = new String(file.content(), StandardCharsets.UTF_8);
            if (file.name().toLowerCase(Locale.ROOT).endsWith(".json")) {
                walkJson(text, values);
            } else {
                readLines(text, values);
            }
        }
        values.removeIf(v -> !worthChecking(v, callId));
        return values;
    }

    /** Giá trị nào trong {@code values} xuất hiện nguyên văn (đứng riêng, không dính chữ/số hai bên) trong {@code text}. */
    public static List<String> foundIn(String text, Collection<String> values) {
        List<String> found = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        for (String value : values) {
            if (containsStandalone(text, value)) {
                found.add(value);
            }
        }
        return found;
    }

    /** Mô tả một giá trị mà không lộ nó - để ghi vào báo cáo. */
    public static String describe(String value) {
        return value.substring(0, Math.min(2, value.length())) + "…(" + value.length() + " ký tự)";
    }

    // --- end-call log (TSV có schema #Hn) và WebRTC log (văn bản tự do) ---

    private void readLines(String text, Set<String> values) {
        Map<String, String[]> schemas = new HashMap<>();
        for (String line : text.split("\n")) {
            line = stripCarriageReturn(line);
            if (line.startsWith("#H")) {
                String[] columns = line.split("\t");
                schemas.put(columns[0].substring(2), columns);
                continue;
            }
            String[] cells = line.split("\t");
            String[] schema = cells.length > 1 ? schemas.get(cells[0]) : null;
            if (schema != null) {
                for (int i = 1; i < cells.length && i < schema.length; i++) {
                    if (isSensitiveField(schema[i])) {
                        values.add(cells[i].strip());
                    }
                    readCell(cells[i], values);
                }
            } else {
                readFreeText(line, values);
            }
        }
    }

    private void readCell(String cell, Set<String> values) {
        String trimmed = cell.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            walkJson(trimmed, values);
        }
        readFreeText(cell, values);
    }

    /** {@code key=value}, {@code "key":"value"}, SDP, khối Cand[...] và IPv4 đứng riêng. */
    private static void readFreeText(String text, Set<String> values) {
        String unescaped = text.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\\"", "\"");
        for (String field : SENSITIVE_FIELDS) {
            collectAfter(unescaped, field + "=", values, true);
            collectAfter(unescaped, "\"" + field + "\":\"", values, true);
            collectAfter(unescaped, "\"" + field + "\": \"", values, true);
        }
        for (String prefix : SDP_PREFIXES) {
            collectAfter(unescaped, prefix, values, false);
        }
        int at = unescaped.indexOf(CANDIDATE_BLOCK);
        while (at >= 0) {
            int end = unescaped.indexOf(']', at);
            String block = unescaped.substring(at + CANDIDATE_BLOCK.length(), end < 0 ? unescaped.length() : end);
            for (String part : block.split(":")) {
                if (isIpv4(part)) {
                    values.add(part);
                }
            }
            at = unescaped.indexOf(CANDIDATE_BLOCK, at + 1);
        }
        for (String token : splitOnNonIpChars(unescaped)) {
            if (isIpv4(token)) {
                values.add(token);
            }
        }
    }

    private static void collectAfter(String text, String marker, Set<String> values, boolean keyBoundary) {
        int from = 0;
        while (true) {
            int at = text.indexOf(marker, from);
            if (at < 0) {
                return;
            }
            from = at + marker.length();
            if (keyBoundary && at > 0 && Character.isLetterOrDigit(text.charAt(at - 1))) {
                continue;
            }
            int end = from;
            while (end < text.length() && !isValueEnd(text.charAt(end))) {
                end++;
            }
            values.add(text.substring(from, end));
        }
    }

    private static boolean isValueEnd(char c) {
        return Character.isWhitespace(c) || ",;\"'}{)[]&|<>\\".indexOf(c) >= 0;
    }

    // --- JSON (signaling export, ô JSON trong end-call log) ---

    private void walkJson(String text, Set<String> values) {
        JsonNode root;
        try {
            root = mapper.readTree(text);
        } catch (Exception e) {
            readFreeText(text, values);
            return;
        }
        walk(root, null, values);
    }

    private static void walk(JsonNode node, String key, Set<String> values) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> walk(e.getValue(), e.getKey(), values));
        } else if (node.isArray()) {
            node.forEach(child -> walk(child, key, values));
        } else if (node.isValueNode()) {
            String text = node.asText();
            if (key != null && isSensitiveField(key)) {
                values.add(text.strip());
            }
            readFreeText(text, values);
        }
    }

    // --- tiện ích ---

    private static boolean isSensitiveField(String name) {
        int dot = name.lastIndexOf('.');
        return SENSITIVE_FIELDS.contains(dot < 0 ? name : name.substring(dot + 1));
    }

    private static boolean worthChecking(String value, String callId) {
        if (value == null || value.length() < MIN_LENGTH) {
            return false;
        }
        if (value.contains("*") || value.endsWith(".x") || value.equalsIgnoreCase(callId)) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("null") || lower.equals("unknown") || lower.equals("none") || lower.equals("true")
                || lower.equals("false")) {
            return false;
        }
        return !(value.equals("0.0.0.0") || value.startsWith("127.") || value.startsWith("255."));
    }

    /** IPv4 kiểm bằng tay: đúng 4 phần số 0-255. */
    static boolean isIpv4(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitOnNonIpChars(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                current.append(c);
            } else if (!current.isEmpty()) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static boolean containsStandalone(String text, String value) {
        int from = 0;
        while (true) {
            int at = text.indexOf(value, from);
            if (at < 0) {
                return false;
            }
            int end = at + value.length();
            boolean leftOk = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            boolean rightOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = at + 1;
        }
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
