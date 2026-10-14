package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.MediaDropDetector;
import com.tutran.callassistant.analysis.verdict.MediaDropDetector.MediaDrop;
import com.tutran.callassistant.analysis.verdict.VerdictContext;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cuộc gọi đã kết nối, media đã chạy, rồi bị <b>ngắt bất thường</b> giữa chừng: một bên mất kết nối với bên
 * kia và không nhận được tiếng nào cho tới lúc cúp máy. Theo mục 4.1 của spec đây là {@code FAIL}
 * ("không thiết lập được <i>hoặc bị ngắt bất thường</i>"), dù signaling vẫn đi đủ tới {@code BYE}.
 *
 * <p>Category là {@link IssueCategory#ICE_FAILURE}: mục 4.2 không có category riêng cho "rớt cuộc gọi",
 * và nguồn evidence ở đây đúng là nguồn spec gán cho ICE_FAILURE - trạng thái kết nối media
 * ({@code onIceConnectionChange: DISCONNECTED}, {@code PARTNER_LOST_NETWORK}) trong log client. Ranh giới với
 * {@link MediaFailedAfterConnectRule}: rule đó bắt ICE {@code failed} (media chưa bao giờ lên), rule này bắt
 * media đã lên rồi mất.
 *
 * <p>Ví dụ thật: {@code new_dataset/FE4EF5E7} - nói chuyện ~30 s, rồi cả hai bên cùng ngừng nhận audio trong
 * cùng một giây; BYE của callee không tới được server, caller nhận {@code PARTNER_LOST_NETWORK} rồi mới cúp.
 */
@Component
@Order(46)
public class MediaDroppedAfterConnectRule implements VerdictRule {

    private final MediaDropDetector mediaDropDetector;

    public MediaDroppedAfterConnectRule(MediaDropDetector mediaDropDetector) {
        this.mediaDropDetector = mediaDropDetector;
    }

    @Override
    public Optional<RuleVerdictResult> apply(VerdictContext context) {
        Optional<MediaDrop> found = mediaDropDetector.find(context.timeline());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MediaDrop drop = found.get();

        context.recordCallSetupStarted();
        context.recordCallConfirmed(context.callConfirmed().orElseThrow());
        context.recordEvidence(drop.lastMediaSample(),
                "Lần cuối " + drop.leg() + " còn nhận được audio; sau đó không nhận được byte audio nào trong "
                        + drop.silentSeconds() + " s cho tới lúc kết thúc");
        context.recordEvidence(drop.connectionLost(), drop.partnerLostNetwork()
                ? "Client " + drop.leg() + " nhận tín hiệu PARTNER_LOST_NETWORK: phía " + drop.partner()
                        + " mất mạng, trước khi " + drop.leg() + " cúp máy"
                : "Kết nối media (ICE) phía " + drop.leg() + " chuyển sang DISCONNECTED trước khi " + drop.leg()
                        + " cúp máy; log không cho biết bên nào mất mạng");
        context.recordCallEnded(context.callEnded().orElseThrow());

        String cause = drop.partnerLostNetwork()
                ? drop.partner() + " mất mạng, " + drop.leg()
                : "kết nối media giữa hai bên bị đứt (chưa xác định được bên nào mất mạng), " + drop.leg();
        return Optional.of(context.fail(IssueCategory.ICE_FAILURE,
                "Cuộc gọi đã kết nối và có media nhưng bị ngắt bất thường giữa chừng: " + cause
                        + " không nhận được tiếng trong " + drop.silentSeconds() + " s cuối trước khi cúp máy."));
    }

    @Override
    public String criterion() {
        return "Cuộc gọi đã có media nhưng một bên báo mất mạng (PARTNER_LOST_NETWORK) hoặc ICE DISCONNECTED trước khi chính bên đó cúp máy, và không nhận được audio cho tới lúc kết thúc - bị ngắt bất thường (FAIL, mục 4.1).";
    }
}
