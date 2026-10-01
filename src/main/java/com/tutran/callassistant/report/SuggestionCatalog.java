package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.verdict.IssueCategory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Đề xuất hướng xử lý/điều tra tiếp, tra theo issue category.
 *
 * <p>Sprint 1 là một bảng tra cố định cho từng category - đề xuất phải nhất quán và không được bịa.
 * Từ Sprint 2, AI sẽ sinh phần này; khi đó bảng ở đây trở thành <b>giá trị dự phòng</b> cho lúc gọi
 * AI thất bại, đúng nguyên tắc "AI failure không được làm hỏng pipeline" ở mục 3.3.
 */
@Component
public class SuggestionCatalog {

    private static final Map<IssueCategory, List<String>> SUGGESTIONS = Map.of(
            IssueCategory.NETWORK_PACKET_LOSS, List.of(
                    "Kiểm tra chất lượng mạng của bên bị ảnh hưởng trong thời điểm cuộc gọi."),
            IssueCategory.NETWORK_DELAY_JITTER, List.of(
                    "Kiểm tra độ trễ/jitter mạng của bên bị ảnh hưởng; bổ sung RTT/jitter nếu log hiện thiếu."),
            IssueCategory.ICE_FAILURE, List.of(
                    "Bổ sung đầy đủ webrtc.log của cả hai bên để kiểm tra chi tiết quá trình ICE."),
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
                    "Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân."),
            IssueCategory.UNKNOWN, List.of(
                    "Bổ sung thêm log/dữ liệu liên quan để có đủ căn cứ kết luận nguyên nhân cụ thể.")
    );

    public List<String> suggestionsFor(IssueCategory category) {
        return SUGGESTIONS.getOrDefault(category, List.of());
    }
}
