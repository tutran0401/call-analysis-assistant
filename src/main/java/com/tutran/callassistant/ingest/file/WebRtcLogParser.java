package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse log engine WebRTC gốc (kiểu iOS {@code RTCLogging.mm} và kiểu Android/native
 * {@code peer_connection_factory.cc}).
 *
 * <p>Các log này chỉ có mốc thời gian tương đối {@code [seconds:millis]} tính từ lúc process khởi
 * động - hoàn toàn không có đồng hồ tuyệt đối trong nguồn này, nên mọi sự kiện ở đây đều được gắn
 * {@link TimestampConfidence#UNKNOWN};
 * {@link com.tutran.callassistant.analysis.timeline.WebRtcElapsedTimeAnchor} chịu trách nhiệm neo
 * chúng vào thời gian tuyệt đối bằng một nguồn khác.
 *
 * <p>Những dòng không khớp pattern header nào được coi là phần tiếp nối của message sự kiện trước
 * đó (log engine WebRTC hay xuống dòng giữa chừng một câu log, ví dụ khi dump thông tin audio
 * route nhiều dòng) thay vì bị báo là lỗi định dạng - chỉ khi cả file không có lấy một dòng nào
 * nhận diện được thì mới sinh cảnh báo.
 */
@Component
public class WebRtcLogParser implements ClientLogParser {

    /** Kiểu iOS: {@code [giây:ms][threadId] (vị trí): message}. */
    private static final Pattern FORMAT_IOS =
            Pattern.compile("^\\[(\\d+):(\\d+)]\\[(\\d+)]\\s+\\(([^)]+)\\):\\s*(.*)$");
    /** Kiểu native: {@code file.cc: [giây:ms][threadId] (line N): message}. */
    private static final Pattern FORMAT_NATIVE =
            Pattern.compile("^([\\w.]+\\.(?:cc|mm)):\\s+\\[(\\d+):(\\d+)]\\[(\\d+)]\\s+\\(line\\s+(\\d+)\\):\\s*(.*)$");

    /**
     * Pattern dùng để <i>nhận diện</i> file, lỏng hơn pattern dùng để parse: chỉ cần thấy dạng mốc
     * thời gian đặc trưng là đủ kết luận đây là log WebRTC, không cần dòng đó parse được trọn vẹn.
     */
    private static final Pattern SIGNATURE_IOS = Pattern.compile("^\\[\\d+:\\d+]\\[\\d+]\\s+\\(.*");
    private static final Pattern SIGNATURE_NATIVE =
            Pattern.compile("^[\\w.]+\\.(cc|mm):\\s+\\[\\d+:\\d+]\\[\\d+]\\s+\\(.*");

    private static final String ELAPSED_SUFFIX = "ms";

    @Override
    public LogFileType type() {
        return LogFileType.WEBRTC;
    }

    @Override
    public boolean recognizes(String firstMeaningfulLine) {
        return SIGNATURE_IOS.matcher(firstMeaningfulLine).matches()
                || SIGNATURE_NATIVE.matcher(firstMeaningfulLine).matches();
    }

    @Override
    public NormalizedEvents parse(LogFile file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.path(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return NormalizedEvents.unreadable("Cannot read file " + file.path() + ": " + e.getMessage());
        }

        List<PendingEvent> pendingEvents = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        PendingEvent current = null;

        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            PendingEvent started = startEvent(line, lineNumber);
            if (started != null) {
                current = started;
                pendingEvents.add(current);
            } else if (current != null) {
                current.appendContinuation(line);
            } else {
                warnings.add("Unrecognized leading line at " + file.path() + ":" + lineNumber
                        + " (no event to attach it to)");
            }
        }

        if (pendingEvents.isEmpty()) {
            warnings.add("No recognizable WebRTC log lines found in " + file.path());
        }
        return new NormalizedEvents(toEvents(pendingEvents, file), warnings);
    }

    private PendingEvent startEvent(String line, int lineNumber) {
        Matcher ios = FORMAT_IOS.matcher(line);
        if (ios.matches()) {
            return new PendingEvent(lineNumber,
                    elapsedMillis(ios.group(1), ios.group(2)),
                    Map.of("threadId", ios.group(3), "location", ios.group(4)),
                    ios.group(5), line);
        }
        Matcher nativeFormat = FORMAT_NATIVE.matcher(line);
        if (nativeFormat.matches()) {
            return new PendingEvent(lineNumber,
                    elapsedMillis(nativeFormat.group(2), nativeFormat.group(3)),
                    Map.of("sourceFile", nativeFormat.group(1), "threadId", nativeFormat.group(4),
                            "sourceLine", nativeFormat.group(5)),
                    nativeFormat.group(6), line);
        }
        return null;
    }

    private long elapsedMillis(String seconds, String millis) {
        return Long.parseLong(seconds) * 1000L + Long.parseLong(millis);
    }

    private List<CanonicalEvent> toEvents(List<PendingEvent> pendingEvents, LogFile file) {
        List<CanonicalEvent> events = new ArrayList<>(pendingEvents.size());
        for (PendingEvent pending : pendingEvents) {
            String message = pending.message.toString();
            Map<String, String> attributes = new LinkedHashMap<>(pending.attributes);
            attributes.put("message", message);
            events.add(new CanonicalEvent(
                    file.callId(),
                    file.legHint(),
                    EventSource.WEBRTC,
                    file.path().toString(),
                    null,
                    pending.elapsedMillis + ELAPSED_SUFFIX,
                    TimestampConfidence.UNKNOWN,
                    pending.lineNumber,
                    classify(message),
                    attributes,
                    pending.rawLine.toString()
            ));
        }
        return events;
    }

    /**
     * Cố tình thu hẹp phạm vi: chỉ tin vào đúng từ vựng có sẵn của engine, không so khớp văn bản tự do.
     *
     * <p>Phiên bản trước có so khớp thêm cả văn bản tự do chứa "turn"/"ice" cộng "error"/"fail" -
     * cách đó sinh ra false positive (ví dụ "TURN create permission error response, code=400" và
     * thử thách credential dài hạn code=401 đều chỉ là nhiễu giao thức bình thường theo từng
     * candidate, quan sát thấy hàng chục lần ngay cả trên một cuộc gọi SUCCESS sạch, chất lượng
     * cao) nên đã bị gỡ bỏ thay vì vá thêm.
     *
     * <p><b>Hai cách engine báo trạng thái ICE.</b> Bản iOS gọi callback theo tên
     * ({@code onIceConnectionChange: FAILED}); bản native {@code peer_connection.cc} lại ghi thành
     * câu kể: {@code Changing IceConnectionState checking => failed} (và một dòng song song
     * {@code Changing standardized IceConnectionState ...}). Trước đây chỉ dạng thứ nhất được nhận
     * ra, nên 16 trong 28 file webrtc của data mẫu <i>có</i> ghi trạng thái ICE mà hệ thống thấy 0
     * sự kiện - nhánh ICE_FAILURE chưa từng chạy một lần nào, và một cuộc gọi lỗi media thật
     * (ICE {@code => failed}, MOS = 0) bị kết luận SUCCESS. Khớp {@code iceconnectionstate} bắt được
     * cả hai biến thể của dạng thứ hai.
     */
    private String classify(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        // TURN: chỉ nhận đúng ba câu có từ vựng cố định của engine, đủ để phân biệt "hỏng thật" với
        // "nhiễu giao thức bình thường". Xem TurnFailureDetector để biết ba câu này ghép lại thành
        // chẩn đoán ra sao - và vì sao chỉ đếm lỗi thì không đủ.
        if (lower.contains("failed to create turn client socket")) {
            return "TURN_SOCKET_ERROR";
        }
        if (lower.contains("received turn allocate") && lower.contains("response")) {
            return "TURN_ALLOCATE_RESPONSE";
        }
        if (lower.contains("allocate request sent")) {
            return "TURN_ALLOCATE_REQUEST";
        }
        if (lower.contains("iceconnectionchange") || lower.contains("iceconnectionstate")) {
            return "ICE_CONNECTION_STATE_CHANGE";
        }
        if (lower.contains("onconnectionchange")) {
            return "PEER_CONNECTION_STATE_CHANGE";
        }
        if (lower.contains("icecandidate") || lower.contains("addice")) {
            return "ICE_CANDIDATE";
        }
        return "ENGINE_LOG";
    }

    /** Một sự kiện đang được gom, vì message của nó có thể trải trên nhiều dòng. */
    private static final class PendingEvent {
        private final int lineNumber;
        private final long elapsedMillis;
        private final Map<String, String> attributes;
        private final StringBuilder message;
        private final StringBuilder rawLine;

        private PendingEvent(int lineNumber, long elapsedMillis, Map<String, String> attributes,
                             String firstMessage, String firstRawLine) {
            this.lineNumber = lineNumber;
            this.elapsedMillis = elapsedMillis;
            this.attributes = attributes;
            this.message = new StringBuilder(firstMessage);
            this.rawLine = new StringBuilder(firstRawLine);
        }

        private void appendContinuation(String line) {
            rawLine.append('\n').append(line);
            message.append('\n').append(line);
        }
    }
}
