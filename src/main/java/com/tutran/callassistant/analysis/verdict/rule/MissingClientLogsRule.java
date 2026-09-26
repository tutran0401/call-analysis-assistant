package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.VerdictContext;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Không có log phía client của bên nào thì không kết luận, kể cả khi luồng signaling nhìn hoàn
 * toàn bình thường.
 *
 * <p>Signaling chỉ là góc nhìn server: nó cho biết các lệnh đã đi qua, nhưng không cho biết
 * phía client có thật sự nghe/nói được không. Một cuộc gọi đủ INIT_CALL...BYE vẫn có thể là
 * cuộc gọi hai bên không nghe thấy gì - xem {@link MediaFailedAfterConnectRule}, đúng tình huống
 * đó có thật trong data mẫu - nên xác nhận SUCCESS chỉ dựa vào signaling là vượt quá những gì
 * evidence chứng minh được.
 *
 * <p><b>Cả hai loại log client đều tính.</b> Trước đây rule này chỉ chấp nhận
 * {@code *_endcall.log}, nhưng {@code *_webrtc.log} cũng là log của thiết bị người dùng, và
 * trạng thái {@code IceConnectionState} trong đó còn nói trực tiếp hơn về việc media có lên được
 * hay không. Vì chỉ tính end-call log, 6/20 cuộc gọi trong data mẫu bị trả UNKNOWN dù có WebRTC
 * log đủ rõ - trong đó 3 cuộc gọi thuộc tập ground truth {@code success/} và cả 3 đều có ICE
 * {@code checking => connected} trong webrtc log.
 */
@Component
@Order(20)
public class MissingClientLogsRule implements VerdictRule {

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        if (context.hasClientEvidence(Leg.CALLER) || context.hasClientEvidence(Leg.CALLEE)) {
            return Optional.empty();
        }
        return Optional.of(context.unknown(
                "Not enough evidence to conclude: no client-side log (end-call or WebRTC) is "
                        + "available for either leg."));
    }
}
