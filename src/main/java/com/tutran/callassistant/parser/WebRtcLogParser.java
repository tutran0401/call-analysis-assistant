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
 * Parse log engine WebRTC gốc (kiểu iOS {@code RTCLogging.mm} và kiểu Android/native
 * {@code peer_connection_factory.cc}). Các log này chỉ có mốc thời gian tương đối
 * {@code [seconds:millis]} tính từ lúc process khởi động - hoàn toàn không có đồng hồ
 * tuyệt đối trong nguồn này, nên mọi sự kiện ở đây đều được gắn
 * {@link TimestampConfidence#UNKNOWN}; {@code TimelineBuilder} chịu trách nhiệm neo
 * chúng vào thời gian tuyệt đối bằng một nguồn khác.
 *
 * <p>Những dòng không khớp pattern header nào được coi là phần tiếp nối của message sự
 * kiện trước đó (log engine WebRTC hay xuống dòng giữa chừng một câu log, ví dụ khi dump
 * thông tin audio route nhiều dòng) thay vì bị báo là lỗi định dạng - chỉ khi cả file
 * không có lấy một dòng nào nhận diện được thì mới sinh cảnh báo.
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
        // Cố tình thu hẹp phạm vi: chỉ tin vào đúng tên callback có sẵn của engine
        // (onIceConnectionChange/onConnectionChange/onIceCandidate). Phiên bản trước có
        // so khớp thêm cả văn bản tự do chứa "turn"/"ice" cộng "error"/"fail" - cách đó
        // sinh ra false positive (ví dụ "TURN create permission error response,
        // code=400" và thử thách credential dài hạn code=401 đều chỉ là nhiễu giao thức
        // bình thường theo từng candidate, quan sát thấy hàng chục lần ngay cả trên một
        // cuộc gọi SUCCESS sạch, chất lượng cao) nên đã bị gỡ bỏ thay vì vá thêm; một lỗi
        // media thật sự đã được ICE_CONNECTION_STATE_CHANGE với message FAILED/
        // DISCONNECTED nắm bắt đủ tin cậy rồi.
        String lower = message.toLowerCase();
        if (lower.contains("iceconnectionchange")) {
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
