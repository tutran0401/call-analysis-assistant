package com.tutran.callassistant.sanitize.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.tutran.callassistant.sanitize.Pseudonymizer;
import com.tutran.callassistant.sanitize.SanitizationSummary;
import com.tutran.callassistant.sanitize.SensitiveDataSanitizer;

/** Như {@link SanitizingMessageConverter} nhưng cho stack trace ({@code %sanEx}): exception message hay chứa API key. */
public class SanitizingThrowableConverter extends ThrowableProxyConverter {

    private static final SensitiveDataSanitizer ENGINE = new SensitiveDataSanitizer(new Pseudonymizer(""));

    @Override
    public String convert(ILoggingEvent event) {
        return ENGINE.sanitizeText(super.convert(event), new SanitizationSummary());
    }
}
