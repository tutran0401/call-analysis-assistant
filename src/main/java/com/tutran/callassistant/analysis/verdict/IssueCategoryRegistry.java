package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.IssueCategoryDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Taxonomy verdict &amp; issue category (T5, mục 4.2): mỗi category nghĩa là gì, biểu hiện ra sao,
 * evidence nào chứng minh được, điều kiện phát hiện xác định nào rule engine đang kiểm tra, và nó dễ
 * bị nhầm với cái gì.
 *
 * <p>Điều kiện phát hiện được dựng từ chính {@link QualityThresholds} mà rule engine dùng, nên tài
 * liệu taxonomy không thể nói một ngưỡng trong khi code kiểm tra một ngưỡng khác. Được mirror ở dạng
 * đọc-hiểu-được trong {@code docs/verdict-issue-taxonomy.md}, nên nội dung ở đây cũng bằng tiếng Việt.
 *
 * <p>{@link IssueCategoryDefinition#knownAmbiguity()} được report dùng trực tiếp cho dòng
 * "Khả dĩ khác" ở mục 4.5 - đó là chỗ nói cho người đọc biết kết luận này còn có thể nhầm với gì.
 */
@Component
public class IssueCategoryRegistry {

    private final Map<IssueCategory, IssueCategoryDefinition> definitions;

    public IssueCategoryRegistry(QualityThresholds thresholds) {
        this.definitions = buildDefinitions(thresholds);
    }

    /** Registry với ngưỡng baseline, dùng cho test và chỗ nào không có Spring container. */
    public static IssueCategoryRegistry withDefaults() {
        return new IssueCategoryRegistry(QualityThresholds.defaults());
    }

    public IssueCategoryDefinition definitionOf(IssueCategory category) {
        return definitions.get(category);
    }

    public Map<IssueCategory, IssueCategoryDefinition> all() {
        return definitions;
    }

    private static Map<IssueCategory, IssueCategoryDefinition> buildDefinitions(QualityThresholds thresholds) {
        return Map.of(
                IssueCategory.NETWORK_PACKET_LOSS, new IssueCategoryDefinition(
                        IssueCategory.NETWORK_PACKET_LOSS,
                        "Cuộc gọi kết nối được và giữ được kết nối, nhưng chất lượng audio/video giảm vì "
                                + "gói tin bị mất trên đường truyền - không phải lỗi thiết lập kết nối.",
                        List.of("Mất gói cả cuộc cao (audio.packetsLost / packetsReceived)", "MOS thấp"),
                        List.of("End-call log: call summary / periodic stats "
                                + "(audio.packetLostPercent, audio.audioMos)"),
                        "mất gói CẢ CUỘC (audio.packetsLost / (packetsLost + packetsReceived)) > "
                                + thresholds.packetLossPercent() + "% ở một trong hai bên; log không có bộ đếm cộng dồn "
                                + "thì dùng mẫu audio.packetLostPercent cao nhất. (MOS < " + thresholds.mos()
                                + " mà mất gói không vượt ngưỡng: vẫn gắn cờ nhưng category UNKNOWN)",
                        "NETWORK_DELAY_JITTER - mất gói cao và jitter/RTT cao thường đi cùng nhau; nếu log "
                                + "thiếu jitter/RTT thì không phân định được cái nào là nguyên nhân chính"
                ),
                IssueCategory.NETWORK_DELAY_JITTER, new IssueCategoryDefinition(
                        IssueCategory.NETWORK_DELAY_JITTER,
                        "Cuộc gọi kết nối được và giữ được kết nối, nhưng chất lượng giảm vì độ trễ hoặc "
                                + "jitter mạng, chứ không phải do mất gói.",
                        List.of("audio.jitter cao", "transport.currentRttMs cao"),
                        List.of("End-call log: periodic stats (audio.jitter, transport.currentRttMs)"),
                        "jitter TRUNG BÌNH (audio.jitter) > " + thresholds.jitterMs() + "ms HOẶC RTT TRUNG BÌNH "
                                + "(transport.currentRttMs) > " + thresholds.rttMs() + "ms ở một bên, tính trên các mẫu "
                                + "periodic stats có media (mẫu cao nhất chỉ để đối chiếu)",
                        "NETWORK_PACKET_LOSS - field RTT/jitter không phải lúc nào cũng có trong log "
                                + "(tuỳ thiết bị/phiên bản); khi thiếu thì không tách được khỏi mất gói, và "
                                + "report phải nói rõ điều đó thay vì đoán"
                ),
                IssueCategory.ICE_FAILURE, new IssueCategoryDefinition(
                        IssueCategory.ICE_FAILURE,
                        "Kết nối media (ICE) không thiết lập được - connectivity check thất bại, không bao giờ "
                                + "đạt connected - HOẶC đã có media rồi bị ngắt bất thường giữa chừng (spec 4.1: "
                                + "FAIL gồm cả cuộc gọi bị ngắt bất thường; 4.2 không có category riêng cho ca này).",
                        List.of("Trạng thái ICE trong log WebRTC chuyển sang failed",
                                "Client state timeline không bao giờ đạt CONFIRMED sau INVITE/OK",
                                "Giữa cuộc gọi: client báo PARTNER_LOST_NETWORK hoặc ICE DISCONNECTED trước khi "
                                        + "chính bên đó cúp máy, và không nhận được audio cho tới lúc kết thúc"),
                        List.of("WebRTC log (ICE_CONNECTION_STATE_CHANGE)",
                                "End-call log: client state timeline"),
                        "Một sự kiện ICE_CONNECTION_STATE_CHANGE chuyển sang trạng thái failed, "
                                + "hoặc client state timeline không bao giờ đạt CONFIRMED, "
                                + "hoặc (sau OK_ACK_OK) end-call log của một bên có PARTNER_LOST_NETWORK/ICE "
                                + "DISCONNECTED trước khi bên đó cúp máy VÀ audio.bytesReceivedPts = 0 liên tục >= 3 s "
                                + "tới lúc kết thúc. DISCONNECTED đơn lẻ không tính - lúc cúp máy bình thường cũng có",
                        "TURN_FAILURE - khó tách khỏi lỗi TURN nếu không có chi tiết theo từng candidate pair; "
                                + "khi có lỗi TURN rõ ràng trong cùng khoảng thời gian thì nên ưu tiên TURN_FAILURE. "
                                + "Với ca bị ngắt giữa chừng: log chỉ cho biết một bên mất mạng, không cho biết vì sao "
                                + "(sóng yếu, đổi mạng, tắt app...)"
                ),
                IssueCategory.TURN_FAILURE, new IssueCategoryDefinition(
                        IssueCategory.TURN_FAILURE,
                        "Không thiết lập được (hoặc suy giảm) kết nối media vì lỗi allocation/relay của TURN "
                                + "phía client.",
                        List.of("Không tạo được socket tới TURN server (chưa gửi đi request nào)",
                                "Đã gửi request allocate nhưng không nhận được phản hồi nào"),
                        List.of("WebRTC log: TURN_SOCKET_ERROR, TURN_ALLOCATE_REQUEST, TURN_ALLOCATE_RESPONSE"),
                        "Có TURN_SOCKET_ERROR; HOẶC có request allocate gửi đi mà không có phản hồi nào. "
                                + "Cố tình KHÔNG dựa vào việc đếm lỗi: mọi cuộc gọi SUCCESS trong data mẫu đều "
                                + "sẵn có 20 dòng probe error và 4 dòng allocate error response hoàn toàn bình "
                                + "thường, nên đếm lỗi sẽ gắn nhầm cho cả cuộc gọi tốt",
                        "Theo mục 9 của spec, chẩn đoán nghẽn hay quá tải TURN server nằm ngoài phạm vi - ở đây "
                                + "chỉ kết luận được là client không đi hết được vòng request/response với TURN, "
                                + "chứ không kết luận được lỗi nằm ở phía server hay phía mạng của thiết bị"
                ),
                IssueCategory.SIGNALING_FAILURE, new IssueCategoryDefinition(
                        IssueCategory.SIGNALING_FAILURE,
                        "Không thiết lập được cuộc gọi vì vấn đề ở tầng signaling: timeout, gửi lại quá nhiều, "
                                + "hoặc không tìm thấy session của bên nhận.",
                        List.of("Gửi lại INVITE/BYE nhiều lần",
                                "Xuất hiện \"No sessions found\" trong end-call log",
                                "Signaling ghi rõ CANCEL/FAIL_HARD trước khi đạt CONFIRMED"),
                        List.of("Signaling log (gửi lại, timeout, CANCEL, FAIL_HARD)",
                                "End-call log: client state timeline"),
                        "Có CANCEL/FAIL_HARD trước khi đạt OK_ACK_OK, hoặc chưa từng quan sát được OK_ACK_OK, "
                                + "hoặc số lần gửi lại INVITE/BYE > " + thresholds.signalingRetransmitCount(),
                        "Cuộc gọi bị người dùng chủ động huỷ (CANCEL) cũng rơi vào category này, nhưng bản chất "
                                + "không phải lỗi hệ thống - mục 4.2 chưa có nhãn cho hành vi người dùng, đang chờ "
                                + "mentor xác nhận"
                ),
                IssueCategory.NONE, new IssueCategoryDefinition(
                        IssueCategory.NONE,
                        "Đã đối chiếu với mọi điều kiện phát hiện ở trên và không điều kiện nào khớp: cuộc gọi "
                                + "kết nối được, kết thúc bình thường, và không chỉ số chất lượng nào vượt ngưỡng.",
                        List.of(),
                        List.of("Signaling đạt OK_ACK_OK rồi BYE",
                                "Mọi chỉ số chất lượng đang có đều trong ngưỡng"),
                        "Không category nào khác khớp VÀ verdict là SUCCESS không kèm cờ chất lượng",
                        "Không có evidence không đồng nghĩa với không có vấn đề: một bên thiếu end-call log thì "
                                + "không đóng góp chỉ số chất lượng nào, nên NONE chỉ có nghĩa là không tìm thấy gì "
                                + "trong phần dữ liệu thực sự có - cần đọc kèm mục Giới hạn dữ liệu"
                ),
                IssueCategory.UNKNOWN, new IssueCategoryDefinition(
                        IssueCategory.UNKNOWN,
                        "Có dấu hiệu bất thường hoặc thiếu dữ liệu, nhưng không đủ evidence để quy về một "
                                + "category cụ thể.",
                        List.of(),
                        List.of(),
                        "Không điều kiện phát hiện nào khác khớp, hoặc thiếu nguồn dữ liệu cần thiết",
                        "Không dùng cho cuộc gọi sạch - trường hợp đó là NONE"
                )
        );
    }
}
