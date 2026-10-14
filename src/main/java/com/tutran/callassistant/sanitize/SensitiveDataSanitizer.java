package com.tutran.callassistant.sanitize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engine dùng chung cho Input Sanitizer và Output Sanitizer: Detector (regex + tên field) → Policy
 * ({@link SensitiveKind}) → thay thế.
 *
 * <p>Các luật được thử theo <b>thứ tự ưu tiên</b>; kết quả khớp chồng lên vùng đã được luật ưu tiên hơn
 * xử lý thì bị bỏ (ví dụ JWT nằm trong {@code Authorization: Bearer ...} chỉ bị thay một lần, và IP bên
 * trong URL TURN không bị xử lý lại). Mọi luật bỏ qua giá trị đã là placeholder / pseudonym, nên chạy lại
 * trên kết quả đã sanitize không đổi gì (idempotent) - Output Sanitizer có thể chạy lên input đã sạch mà
 * không bị coi là "còn rò rỉ".
 */
@Component
public class SensitiveDataSanitizer {

    /** Dấu nháy tuỳ chọn, kể cả dạng JSON bị escape ({@code \"}). */
    private static final String Q = "(?:\\\\?\")?";
    /** Giá trị kết thúc ở khoảng trắng, nháy, dấu phân cách hay ngoặc đóng. */
    private static final String VALUE = "[^\\s\"',;&}\\\\]+";
    private static final String NOT_PLACEHOLDER = "(?!(?:USER|DEVICE|SESSION|TURN)_[0-9a-f]{6}\\b|\\[)";
    private static final String KEY_START = "(?<![A-Za-z0-9])";
    private static final String SEPARATOR = Q + "\\s*[:=]\\s*" + Q;

    private static final String IPV4_OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";

    private record Rule(SensitiveKind kind, Pattern pattern, int group) {
        static Rule of(SensitiveKind kind, String regex, int group) {
            return new Rule(kind, Pattern.compile(regex), group);
        }

        static Rule ofIgnoreCase(SensitiveKind kind, String regex, int group) {
            return new Rule(kind, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), group);
        }
    }

    private record Span(int start, int end, SensitiveKind kind, String original) {
    }

    private static String idRule(String keyRegex) {
        return KEY_START + "(?:" + keyRegex + ")" + SEPARATOR + "(" + NOT_PLACEHOLDER + VALUE + ")";
    }

    /** Thứ tự = ưu tiên. */
    private static final List<Rule> RULES = List.of(
            // SDP nguyên khối: chứa ufrag/pwd/fingerprint/IP candidate cùng lúc nên bỏ cả khối.
            Rule.of(SensitiveKind.SDP,
                    "(?<![\\w=])(v=0(?:\\\\r\\\\n|\\\\n|\\r?\\n)[\\s\\S]*?)(?=\"|$)", 1),
            Rule.ofIgnoreCase(SensitiveKind.AUTHORIZATION,
                    KEY_START + "authorization" + SEPARATOR
                            + "((?!\\[)(?:(?:bearer|basic)\\s+)?[^\\s\"',;}\\\\]+)", 1),
            Rule.ofIgnoreCase(SensitiveKind.AUTHORIZATION, "\\bbearer\\s+((?!\\[)[A-Za-z0-9._~+/=-]{8,})", 1),
            Rule.of(SensitiveKind.JWT,
                    "\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*", 0),
            Rule.of(SensitiveKind.API_KEY,
                    "\\b(?:sk-[A-Za-z0-9_-]{16,}|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{20,}|AIza[0-9A-Za-z_-]{30,})", 0),
            Rule.ofIgnoreCase(SensitiveKind.API_KEY,
                    KEY_START + "(?:api[_-]?key|secret(?:[_-]?key)?|password|passwd|pwd|access[_-]?token"
                            + "|refresh[_-]?token|auth[_-]?token|token|credential|private[_-]?key)"
                            + SEPARATOR + "((?!\\[)" + VALUE + ")", 1),
            Rule.ofIgnoreCase(SensitiveKind.ICE_CREDENTIAL,
                    "a=ice-(?:ufrag|pwd):\\s*([^\\s\"\\\\]+)", 1),
            Rule.ofIgnoreCase(SensitiveKind.ICE_CREDENTIAL,
                    KEY_START + "(?:ice[_-]?)?(?:ufrag|pwd)" + SEPARATOR + "((?!\\[)" + VALUE + ")", 1),
            Rule.ofIgnoreCase(SensitiveKind.DTLS_FINGERPRINT,
                    "a=fingerprint:\\S+\\s+((?:[0-9A-F]{2}:){15,}[0-9A-F]{2})", 1),
            Rule.ofIgnoreCase(SensitiveKind.DTLS_FINGERPRINT, "\\b(?:[0-9A-F]{2}:){31}[0-9A-F]{2}\\b", 0),
            Rule.ofIgnoreCase(SensitiveKind.TURN_SERVER, "\\bturns?:(?!\\[TURN_SERVER_REDACTED\\])[^\\s\"',;\\\\]+", 0),
            Rule.of(SensitiveKind.EMAIL, "[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}", 0),
            Rule.ofIgnoreCase(SensitiveKind.USER_ID,
                    idRule("(?:(?:partner[_-]?)?(?:app|call)[_-]?)?user[_-]?id"), 1),
            Rule.ofIgnoreCase(SensitiveKind.DEVICE_ID, idRule("device[_-]?id"), 1),
            Rule.ofIgnoreCase(SensitiveKind.SESSION_ID, idRule("session[_-]?id|csid"), 1),
            Rule.ofIgnoreCase(SensitiveKind.TURN_SESSION, idRule("turn[_-]?session[_-]?info|turn[_-]?logging[_-]?id"), 1),
            // "_" được coi là dấu phân cách (không dùng \w ở biên): số điện thoại / IP hay nằm trong tên file
            // ("0933444555_webrtc.log") hoặc khoá ghép ("ip_113.161.45.67") - security-eval S08 lộ ra lỗ này.
            Rule.of(SensitiveKind.PHONE,
                    "(?<![A-Za-z0-9.])(?:\\+84|84|0)[\\s.-]?[2-9](?:[\\s.-]?\\d){8}(?![A-Za-z0-9])", 0),
            Rule.of(SensitiveKind.IP_ADDRESS,
                    // Loopback / 0.0.0.0 là địa chỉ bind của chính máy chủ, không phải IP của client.
                    "(?<![A-Za-z0-9.])(?!127\\.|0\\.0\\.0\\.0)(?:" + IPV4_OCTET + "\\.){3}" + IPV4_OCTET
                            + "(?![\\w.]*\\d)", 0),
            Rule.of(SensitiveKind.IP_ADDRESS, "(?<![\\w:])(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}(?![\\w:])", 0),
            Rule.of(SensitiveKind.IP_ADDRESS,
                    "(?<![\\w:])(?:[0-9a-fA-F]{1,4}:){1,7}:(?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4}){0,6})?(?![\\w:])", 0),
            Rule.of(SensitiveKind.IP_ADDRESS,
                    "(?<![\\w:])::(?:[0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}(?![\\w:])", 0));

    /** Tên field (đã chuẩn hoá: chữ thường, bỏ {@code _} và {@code -}) → loại dữ liệu cần pseudonymize. */
    private static final Map<String, SensitiveKind> ID_KEYS = Map.ofEntries(
            Map.entry("userid", SensitiveKind.USER_ID),
            Map.entry("appuserid", SensitiveKind.USER_ID),
            Map.entry("calluserid", SensitiveKind.USER_ID),
            Map.entry("partnerappuserid", SensitiveKind.USER_ID),
            Map.entry("partnercalluserid", SensitiveKind.USER_ID),
            Map.entry("deviceid", SensitiveKind.DEVICE_ID),
            Map.entry("sessionid", SensitiveKind.SESSION_ID),
            Map.entry("csid", SensitiveKind.SESSION_ID),
            Map.entry("requestid", SensitiveKind.SESSION_ID),
            Map.entry("turnsessioninfo", SensitiveKind.TURN_SESSION),
            Map.entry("turnloggingid", SensitiveKind.TURN_SESSION));

    private static final Map<String, SensitiveKind> SECRET_KEYS = Map.ofEntries(
            Map.entry("authorization", SensitiveKind.AUTHORIZATION),
            Map.entry("apikey", SensitiveKind.API_KEY),
            Map.entry("xapikey", SensitiveKind.API_KEY),
            Map.entry("password", SensitiveKind.API_KEY),
            Map.entry("passwd", SensitiveKind.API_KEY),
            Map.entry("pwd", SensitiveKind.API_KEY),
            Map.entry("secret", SensitiveKind.API_KEY),
            Map.entry("secretkey", SensitiveKind.API_KEY),
            Map.entry("token", SensitiveKind.API_KEY),
            Map.entry("accesstoken", SensitiveKind.API_KEY),
            Map.entry("refreshtoken", SensitiveKind.API_KEY),
            Map.entry("authtoken", SensitiveKind.API_KEY),
            Map.entry("credential", SensitiveKind.API_KEY),
            Map.entry("privatekey", SensitiveKind.API_KEY),
            Map.entry("icepwd", SensitiveKind.ICE_CREDENTIAL),
            Map.entry("iceufrag", SensitiveKind.ICE_CREDENTIAL));

    private static final Pattern PSEUDONYM = Pattern.compile("^(?:USER|DEVICE|SESSION|TURN)_[0-9a-f]{6}$");

    private final Pseudonymizer pseudonymizer;

    public SensitiveDataSanitizer(Pseudonymizer pseudonymizer) {
        this.pseudonymizer = pseudonymizer;
    }

    /** Sanitize một đoạn text, ghi số lần xử lý vào {@code summary}. */
    public String sanitizeText(String text, SanitizationSummary summary) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        List<Span> spans = detect(text);
        if (spans.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text);
        spans.sort(Comparator.comparingInt(Span::start).reversed());
        for (Span span : spans) {
            out.replace(span.start(), span.end(), replacementFor(span.kind(), span.original()));
            summary.record(span.kind());
        }
        return out.toString();
    }

    /** Số giá trị nhạy cảm <b>còn sót</b> trong text - dùng đo Security Leakage (khác 0 nghĩa là rò rỉ). */
    public int countResidual(String text) {
        return text == null ? 0 : detect(text).size();
    }

    /** Sanitize đệ quy cả cây JSON (nested - S06): theo tên field trước, rồi theo nội dung chuỗi. */
    public JsonNode sanitizeJson(JsonNode node, SanitizationSummary summary) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                copy.set(field.getKey(), sanitizeField(field.getKey(), field.getValue(), summary));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> copy.add(sanitizeJson(child, summary)));
            return copy;
        }
        if (node.isTextual()) {
            return JsonNodeFactory.instance.textNode(sanitizeText(node.asText(), summary));
        }
        return node;
    }

    private JsonNode sanitizeField(String key, JsonNode value, SanitizationSummary summary) {
        String normalised = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (normalised.equals("sdp") || normalised.equals("sdpoffer") || normalised.equals("sdpanswer")) {
            if (isPlaceholder(value)) {
                return value;
            }
            summary.record(SensitiveKind.SDP);
            return JsonNodeFactory.instance.textNode(SensitiveKind.SDP.replacement());
        }
        SensitiveKind secret = SECRET_KEYS.get(normalised);
        if (secret != null && !value.isNull() && !isPlaceholder(value)) {
            summary.record(secret);
            return JsonNodeFactory.instance.textNode(secret.replacement());
        }
        SensitiveKind id = ID_KEYS.get(normalised);
        if (id != null && value.isValueNode() && !value.isNull() && !isPlaceholder(value)) {
            summary.record(id);
            return JsonNodeFactory.instance.textNode(pseudonymizer.pseudonym(id.replacement(), value.asText()));
        }
        return sanitizeJson(value, summary);
    }

    private static boolean isPlaceholder(JsonNode value) {
        if (!value.isTextual()) {
            return false;
        }
        String text = value.asText();
        return text.startsWith("[") || PSEUDONYM.matcher(text).matches();
    }

    private String replacementFor(SensitiveKind kind, String original) {
        return kind.action() == SanitizeAction.PSEUDONYMIZE
                ? pseudonymizer.pseudonym(kind.replacement(), original)
                : kind.replacement();
    }

    private List<Span> detect(String text) {
        List<Span> accepted = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                int start = matcher.start(rule.group());
                int end = matcher.end(rule.group());
                if (start >= 0 && end > start && !overlaps(accepted, start, end)) {
                    accepted.add(new Span(start, end, rule.kind(), text.substring(start, end)));
                }
            }
        }
        return accepted;
    }

    private static boolean overlaps(List<Span> spans, int start, int end) {
        for (Span span : spans) {
            if (start < span.end() && end > span.start()) {
                return true;
            }
        }
        return false;
    }
}
