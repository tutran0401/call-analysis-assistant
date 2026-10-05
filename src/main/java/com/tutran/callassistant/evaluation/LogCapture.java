package com.tutran.callassistant.evaluation;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tutran.callassistant.sanitize.SensitiveDataSanitizer;
import com.tutran.callassistant.sanitize.logging.SanitizingMessageConverter;
import com.tutran.callassistant.sanitize.logging.SanitizingThrowableConverter;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Bắt mọi log event trong lúc chạy benchmark để kiểm Security Leakage ở application log (mục 6.5).
 * Kiểm trên chuỗi <b>sau</b> bộ chuyển đổi logback ({@code %san}, {@code %sanEx}) - tức đúng thứ sẽ được
 * ghi ra console/file - và đồng thời đếm chuỗi gốc để biết sanitizer có thực sự được thử hay không
 * (nếu cả hai đều 0 thì benchmark chưa chứng minh được gì về log).
 */
final class LogCapture implements AutoCloseable {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root;

    LogCapture() {
        root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) instanceof Logger logger ? logger : null;
        if (root != null) {
            appender.start();
            root.addAppender(appender);
        }
    }

    /** Số giá trị nhạy cảm còn thấy trong log đã sanitize: probe đã chèn + thứ detector còn phát hiện được. */
    int leakedAfterSanitizing(List<String> probes, SensitiveDataSanitizer detector) {
        int leaked = 0;
        for (String line : sanitizedLines()) {
            leaked += countProbes(line, probes) + detector.countResidual(line);
        }
        return leaked;
    }

    /** Số event có chứa probe ở dạng gốc, trước sanitize. */
    int rawMentions(List<String> probes) {
        int mentions = 0;
        for (ILoggingEvent event : appender.list) {
            mentions += countProbes(event.getFormattedMessage(), probes);
        }
        return mentions;
    }

    private List<String> sanitizedLines() {
        List<String> lines = new ArrayList<>();
        SanitizingMessageConverter message = new SanitizingMessageConverter();
        SanitizingThrowableConverter throwable = new SanitizingThrowableConverter();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            lines.add(message.convert(event));
            lines.add(throwable.convert(event));
        }
        return lines;
    }

    static int countProbes(String text, List<String> probes) {
        if (text == null) {
            return 0;
        }
        int hits = 0;
        for (String probe : probes) {
            hits += text.contains(probe) ? 1 : 0;
        }
        return hits;
    }

    @Override
    public void close() {
        if (root != null) {
            root.detachAppender(appender);
            appender.stop();
        }
    }
}
