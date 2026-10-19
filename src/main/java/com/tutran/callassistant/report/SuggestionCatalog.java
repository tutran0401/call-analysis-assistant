package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.verdict.IssueCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bộ đề xuất chuẩn (Sprint 3 T2): tra theo issue category, cộng với đề xuất bổ sung dữ liệu theo từng
 * giới hạn dữ liệu.
 *
 * <p>Đây là <b>phần bắt buộc</b> của mục "Đề xuất": report nào cũng có, kể cả khi AI không dùng được.
 * Đề xuất của AI (nếu có) được nối vào sau, không thay thế bộ chuẩn - nhờ vậy cùng một loại vấn đề luôn
 * nhận cùng các bước điều tra cơ bản, đúng yêu cầu nhất quán ở mục 8.2.
 */
@Component
public class SuggestionCatalog {

    private static final Map<IssueCategory, List<String>> SUGGESTIONS = Map.of(
            IssueCategory.NETWORK_PACKET_LOSS, List.of(
                    "Kiểm tra chất lượng mạng của bên bị ảnh hưởng trong thời điểm cuộc gọi.",
                    "Đối chiếu periodic stats để xem mất gói xảy ra liên tục hay theo từng đợt.",
                    "So sánh ISP/ASN của bên bị ảnh hưởng với các cuộc gọi khác cùng thời điểm."),
            IssueCategory.NETWORK_DELAY_JITTER, List.of(
                    "Kiểm tra độ trễ/jitter mạng của bên bị ảnh hưởng; bổ sung RTT/jitter nếu log hiện thiếu.",
                    "Đối chiếu periodic stats để xem RTT/jitter tăng vào thời điểm nào của cuộc gọi.",
                    "Kiểm tra bên bị ảnh hưởng có dùng mạng di động hoặc Wi-Fi yếu không."),
            IssueCategory.ICE_FAILURE, List.of(
                    "Xem evidence để biết bên nào mất kết nối media và vào lúc nào (ICE failed khi thiết "
                            + "lập, hay DISCONNECTED/PARTNER_LOST_NETWORK giữa cuộc gọi); kiểm tra mạng của bên "
                            + "đó tại thời điểm này.",
                    "Nếu thiếu webrtc.log/end-call log của bên nào thì bổ sung để đối chiếu trạng thái ICE "
                            + "của cả hai bên.",
                    "Kiểm tra candidate được gom (host/srflx/relay) trong WebRTC log để biết có đường relay "
                            + "dự phòng hay không."),
            // Đề xuất cho TURN cố tình KHÔNG nhắc credential hay cổng 3478: hai hình thái lỗi TURN cần
            // hai hướng điều tra khác hẳn nhau (không tạo nổi socket là vấn đề mạng/định tuyến cục bộ,
            // còn gửi được mà không có phản hồi mới là vấn đề đường tới server). Phần tóm tắt của
            // report đã nói rõ hình thái nào, nên đề xuất ở đây chỉ nêu bước chung cho cả hai.
            IssueCategory.TURN_FAILURE, List.of(
                    "Đọc phần tóm tắt để biết TURN hỏng theo kiểu nào: không tạo được socket, hay gửi "
                            + "được request nhưng không có phản hồi.",
                    "Nếu không tạo được socket: kiểm tra mạng/định tuyến cục bộ của thiết bị "
                            + "(VPN, firewall, quyền truy cập mạng) - lỗi xảy ra trước khi có gói nào rời máy.",
                    "Nếu đã gửi request mà không có phản hồi: kiểm tra đường tới TURN server và khả năng "
                            + "gói UDP bị chặn trên đường đi."),
            IssueCategory.SIGNALING_FAILURE, List.of(
                    "Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.",
                    "Đếm số lần gửi lại INVITE và \"No sessions found\" trong bảng chỉ số để biết callee có "
                            + "được với tới hay không.",
                    "Kiểm tra WARN/ERROR của các service signaling trong cùng khoảng thời gian."),
            IssueCategory.UNKNOWN, List.of(
                    "Bổ sung thêm log/dữ liệu liên quan để có đủ căn cứ kết luận nguyên nhân cụ thể.",
                    "Kiểm tra thủ công timeline của cuộc gọi quanh các evidence đã nêu.")
    );

    private static final Pattern MISSING_FILE = Pattern.compile("^Thiếu (caller|callee)_(endcall|webrtc)\\.log");

    public List<String> suggestionsFor(IssueCategory category) {
        return SUGGESTIONS.getOrDefault(category, List.of());
    }

    /** Đề xuất bổ sung dữ liệu, mỗi giới hạn dữ liệu nhận ra được một câu; giới hạn khác thì bỏ qua. */
    public List<String> forDataLimitations(List<String> limitations) {
        List<String> out = new ArrayList<>();
        for (String limitation : limitations) {
            Matcher missing = MISSING_FILE.matcher(limitation);
            if (missing.find()) {
                String file = missing.group(1) + "_" + missing.group(2) + ".log";
                out.add("Bổ sung " + file + " để " + ("endcall".equals(missing.group(2))
                        ? "có call summary (MOS, packet loss) và client state timeline của "
                        : "kiểm tra sự kiện ICE / TURN / PeerConnection của ") + missing.group(1) + ".");
            } else if (limitation.toLowerCase(Locale.ROOT).contains("cắt bớt")) {
                out.add("Xuất lại signaling đầy đủ của cuộc gọi (bản hiện có bị cắt bớt) rồi import lại.");
            } else if (limitation.toLowerCase(Locale.ROOT).contains("signaling")) {
                out.add("Kiểm tra Call-ID và việc đã import signaling của cuộc gọi vào Elasticsearch local.");
            }
        }
        return out.stream().distinct().toList();
    }
}
