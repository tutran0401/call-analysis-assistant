package com.tutran.callassistant.sanitize.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Structured log (Sprint 3 T5): mỗi event là một dòng JSON để Kibana / công cụ khác đọc được mà không phải
 * parse text.
 *
 * <p>Dùng layout riêng thay vì encoder JSON dựng sẵn vì encoder đó ghi message <b>gốc</b> - bỏ qua
 * {@code %san}. Ở đây message và stack trace đi qua đúng hai converter đã dùng cho log text, nên log JSON
 * không thể lộ cái mà log text đã che (S08).
 *
 * <p>Field: {@code ts, level, logger, thread, request_id, message, error}. Các dòng {@code key=value} trong
 * message (ví dụ {@code ai_call ... latency_ms=}) giữ nguyên để tìm kiếm full-text.
 */
public class SanitizingJsonLayout extends LayoutBase<ILoggingEvent> {

    /** Cùng khoá MDC với {@code application.RequestIds.MDC_KEY}. */
    static final String REQUEST_ID_KEY = "request_id";

    private final ObjectMapper mapper = new ObjectMapper();
    private final SanitizingMessageConverter message = new SanitizingMessageConverter();
    private final SanitizingThrowableConverter throwable = new SanitizingThrowableConverter();

    @Override
    public void start() {
        throwable.start();
        super.start();
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        ObjectNode line = mapper.createObjectNode();
        line.put("ts", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        line.put("level", event.getLevel().toString());
        line.put("logger", event.getLoggerName());
        line.put("thread", event.getThreadName());
        String requestId = event.getMDCPropertyMap().get(REQUEST_ID_KEY);
        if (requestId != null) {
            line.put("request_id", requestId);
        }
        line.put("message", message.convert(event));
        if (event.getThrowableProxy() != null) {
            line.put("error", throwable.convert(event).strip());
        }
        try {
            return mapper.writeValueAsString(line) + System.lineSeparator();
        } catch (JsonProcessingException e) {
            // Không bao giờ để log làm hỏng request; ghi bản tối thiểu không có nội dung message.
            return "{\"level\":\"" + event.getLevel() + "\",\"message\":\"<unserializable log event>\"}"
                    + System.lineSeparator();
        }
    }
}
