package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.VerdictContext;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Thiếu end-call log của cả hai bên thì không kết luận, kể cả khi luồng signaling nhìn hoàn
 * toàn bình thường.
 *
 * <p>Signaling chỉ là góc nhìn server: nó cho biết các lệnh đã đi qua, nhưng không cho biết
 * phía client có thật sự nghe/nói được không. Một cuộc gọi đủ INIT_CALL...BYE vẫn có thể là
 * cuộc gọi hai bên không nghe thấy gì, nên xác nhận SUCCESS chỉ dựa vào signaling là vượt quá
 * những gì evidence chứng minh được.
 */
@Component
@Order(20)
public class MissingClientLogsRule implements VerdictRule {

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        boolean hasCallerLog = context.hasEndCallLog(Leg.CALLER);
        boolean hasCalleeLog = context.hasEndCallLog(Leg.CALLEE);
        if (hasCallerLog || hasCalleeLog) {
            return Optional.empty();
        }
        return Optional.of(context.unknown(
                "Not enough evidence to conclude: end-call logs are missing for both legs."));
    }
}
