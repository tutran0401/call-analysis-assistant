package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.event.Leg;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Nêu rõ bên nào thiếu end-call log. Thiếu một bên vẫn kết luận được nhưng độ tin cậy phải hạ
 * xuống (xem {@code report.confidence}); thiếu cả hai thì
 * {@link com.tutran.callassistant.analysis.verdict.rule.MissingClientLogsRule} sẽ trả UNKNOWN.
 */
@Component
@Order(10)
public class MissingEndCallLogDetector implements DataLimitationDetector {

    @Override
    public void detect(VerdictContext context) {
        for (Leg leg : com.tutran.callassistant.domain.metrics.CallMetrics.QUALITY_LEGS) {
            if (!context.hasEndCallLog(leg)) {
                context.noteDataLimitation("Thiếu " + leg.name().toLowerCase(java.util.Locale.ROOT)
                        + "_endcall.log");
            }
        }
    }
}
