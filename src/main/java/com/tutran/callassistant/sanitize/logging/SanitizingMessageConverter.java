package com.tutran.callassistant.sanitize.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.tutran.callassistant.sanitize.Pseudonymizer;
import com.tutran.callassistant.sanitize.SanitizationSummary;
import com.tutran.callassistant.sanitize.SensitiveDataSanitizer;

/**
 * Logback converter ({@code %san}) sanitize message của mọi dòng application log (S08). Logback tạo
 * converter ngoài Spring nên dùng một engine tĩnh với salt ngẫu nhiên - đủ vì log không cần correlate
 * pseudonym giữa các lần khởi động.
 */
public class SanitizingMessageConverter extends ClassicConverter {

    private static final SensitiveDataSanitizer ENGINE = new SensitiveDataSanitizer(new Pseudonymizer(""));

    @Override
    public String convert(ILoggingEvent event) {
        return ENGINE.sanitizeText(event.getFormattedMessage(), new SanitizationSummary());
    }
}
