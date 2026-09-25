package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.VerdictContext;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Không có signaling log thì không thể biết cuộc gọi đã đi tới đâu, nên không kết luận được.
 * Xét đầu tiên: mọi rule sau đều dựa vào mốc tiến trình lấy từ signaling.
 */
@Component
@Order(10)
public class MissingSignalingDataRule implements VerdictRule {

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        if (context.hasSignalingData()) {
            return Optional.empty();
        }
        context.noteDataLimitation("No signaling data available for this call");
        return Optional.of(context.unknown(
                "Not enough evidence to conclude: no signaling data was found for this call."));
    }
}
