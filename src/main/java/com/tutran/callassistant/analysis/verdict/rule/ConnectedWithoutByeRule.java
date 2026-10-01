package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.VerdictContext;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cuộc gọi đã kết nối nhưng không quan sát được lúc kết thúc thì trả UNKNOWN.
 *
 * <p>Đây gần như luôn là dấu hiệu dữ liệu bị cắt/thiếu (signaling export của mentor có giới hạn
 * số sự kiện trả về) chứ không phải một cuộc gọi thật đang diễn ra, nên kết luận "kết thúc bình
 * thường" là không có căn cứ.
 */
@Component
@Order(40)
public class ConnectedWithoutByeRule implements VerdictRule {

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        if (context.callEnded().isPresent()) {
            return Optional.empty();
        }
        context.recordCallSetupStarted();
        context.recordCallConfirmed(context.callConfirmed().orElseThrow());
        context.noteDataLimitation("Cuộc gọi có vẻ đã kết nối (quan sát được OK_ACK_OK) nhưng không "
                + "thấy BYE; dữ liệu signaling có thể bị cắt hoặc thiếu");
        return Optional.of(context.unknown(
                "Không đủ evidence để kết luận: cuộc gọi đã kết nối nhưng không quan sát được lúc "
                        + "kết thúc."));
    }
}
