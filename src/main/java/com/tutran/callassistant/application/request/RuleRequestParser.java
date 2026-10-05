package com.tutran.callassistant.application.request;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Request Parser dựa trên từ khoá - xác định, không cần AI, nên cũng là đường dự phòng khi AI lỗi.
 *
 * <p>Câu hỏi được chuẩn hoá (bỏ dấu, chữ thường, bỏ dấu câu) rồi khớp theo <b>ranh giới từ</b>:
 * cụm "loi" không được khớp trong "xin loi" nên phải loại trừ tường minh, và "re" (rè) không được
 * khớp trong "report". Thứ tự quyết định:
 * <ol>
 *   <li>Có tín hiệu "phân tích mạnh" (phân tích, vì sao, ...) hoặc Call-ID → trong phạm vi.</li>
 *   <li>Có dấu hiệu ngoài phạm vi (viết email, dịch, thời tiết, ...) mà không có tín hiệu mạnh → từ chối.</li>
 *   <li>Có từ khoá về cuộc gọi → trong phạm vi; không có gì liên quan → từ chối.</li>
 * </ol>
 */
@Component
public class RuleRequestParser implements RequestParser {

    private static final Pattern CALL_ID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private static final List<String> STRONG_ANALYSIS = List.of(
            "phan tich", "kiem tra", "vi sao", "tai sao", "nguyen nhan", "danh gia", "dieu tra", "debug");

    private static final List<String> CALL_TERMS = List.of(
            "cuoc goi", "goi", "call", "log", "caller", "callee", "ben nhan", "ben goi", "nguoi nhan",
            "nguoi goi", "loi", "bi loi", "chat luong", "ket noi", "tieng", "nghe", "signaling",
            "ice", "turn", "webrtc", "mos", "jitter", "rtt", "packet loss", "bye", "invite");

    private static final List<String> OUT_OF_SCOPE_TERMS = List.of(
            "viet email", "viet thu", "soan email", "soan thu", "viet bai", "viet tho", "lam tho", "dich",
            "thoi tiet", "nau an", "cong thuc", "ke chuyen", "cau chuyen", "chuyen cuoi", "viet code",
            "lap trinh", "bai tap", "gia vang", "ty gia", "chung khoan", "tin tuc", "bong da",
            "xin chao", "ban la ai", "ten ban");

    private static final Map<CallFocus, List<String>> FOCUS_TERMS = new EnumMap<>(CallFocus.class);

    static {
        FOCUS_TERMS.put(CallFocus.AUDIO_QUALITY, List.of(
                "nghe", "re", "mat tieng", "tieng", "am thanh", "voice", "audio", "chat luong", "mos", "giat",
                "lag", "tre", "vo tieng", "mat goi", "packet loss", "jitter", "rtt"));
        FOCUS_TERMS.put(CallFocus.SETUP, List.of(
                "khong ket noi", "khong goi duoc", "goi khong duoc", "khong thiet lap", "thiet lap",
                "do chuong", "khong reo", "chuong", "khong ai nghe may", "timeout", "khong tim thay",
                "signaling", "ket noi cham", "khong len cuoc goi"));
        FOCUS_TERMS.put(CallFocus.DISCONNECT, List.of(
                "rot", "ngat", "bi cat", "cup may", "ket thuc", "bye", "mat ket noi", "dut", "bi huy", "huy",
                "ai cup", "cup"));
        FOCUS_TERMS.put(CallFocus.MEDIA, List.of(
                "ice", "turn", "media", "peerconnection", "webrtc", "relay", "p2p"));
    }

    /** Cụm bị loại trước khi khớp: "xin lỗi" không phải "lỗi" của cuộc gọi. */
    private static final List<String> NEUTRALISED = List.of("xin loi");

    @Override
    public ParsedRequest parse(String message, boolean hasAttachments) {
        String raw = message == null ? "" : message;
        String callId = extractCallId(raw);
        String text = normalise(raw);

        if (text.isBlank()) {
            return hasAttachments || callId != null
                    ? new ParsedRequest(Intent.ANALYZE_CALL, Set.of(), callId)
                    : new ParsedRequest(Intent.OUT_OF_SCOPE, Set.of(), null);
        }

        boolean strong = callId != null || containsAny(text, STRONG_ANALYSIS);
        boolean mentionsCall = containsAny(text, CALL_TERMS);
        boolean outOfScopeMarker = containsAny(text, OUT_OF_SCOPE_TERMS);

        if (!strong && (outOfScopeMarker || !mentionsCall)) {
            return new ParsedRequest(Intent.OUT_OF_SCOPE, Set.of(), callId);
        }

        Set<CallFocus> focus = detectFocus(text);
        Intent intent = focus.isEmpty() ? Intent.ANALYZE_CALL : Intent.ANALYZE_WITH_FOCUS;
        return new ParsedRequest(intent, focus, callId);
    }

    private static Set<CallFocus> detectFocus(String text) {
        Set<CallFocus> focus = EnumSet.noneOf(CallFocus.class);
        FOCUS_TERMS.forEach((key, terms) -> {
            if (containsAny(text, terms)) {
                focus.add(key);
            }
        });
        return focus;
    }

    private static String extractCallId(String raw) {
        Matcher matcher = CALL_ID.matcher(raw);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    /** Bỏ dấu, chữ thường, mọi ký tự không phải chữ/số thành khoảng trắng; bọc khoảng trắng hai đầu. */
    static String normalise(String raw) {
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        String padded = " " + decomposed + " ";
        for (String phrase : NEUTRALISED) {
            padded = padded.replace(" " + phrase + " ", " ");
        }
        return padded;
    }

    private static boolean containsAny(String normalisedText, List<String> phrases) {
        for (String phrase : phrases) {
            if (normalisedText.contains(" " + phrase + " ")) {
                return true;
            }
        }
        return false;
    }
}
