package com.tutran.callassistant.domain.verdict;

/**
 * Các issue category theo PROJECT_SPEC.md mục 4.2. Xem
 * {@code analysis.verdict.IssueCategoryRegistry} để biết định nghĩa/triệu chứng/evidence/
 * điều kiện phát hiện/điểm mơ hồ của từng category.
 *
 * <p><b>Mọi report đều nêu một category</b>, kể cả cuộc gọi thành công hoàn toàn - khi đó là
 * {@link #NONE}. Trước đây cuộc gọi sạch để trống mục này, khiến người đọc không phân biệt được
 * "đã kiểm tra và không có vấn đề" với "chưa kiểm tra". Lưu ý mục 4.2 của spec mô tả category là
 * chỉ áp dụng cho {@code FAIL} và {@code SUCCESS} có cờ chất lượng; {@link #NONE} là phần bổ sung
 * để mục này không bao giờ rỗng - cần nêu với mentor khi review.
 */
public enum IssueCategory {
    NETWORK_PACKET_LOSS,
    NETWORK_DELAY_JITTER,
    ICE_FAILURE,
    TURN_FAILURE,
    SIGNALING_FAILURE,
    /**
     * Đã kiểm tra đủ và <b>không phát hiện vấn đề</b> nào.
     *
     * <p>Khác hẳn {@link #UNKNOWN}: {@code NONE} là một kết luận khẳng định (cuộc gọi tốt), còn
     * {@code UNKNOWN} là thừa nhận không quy được nguyên nhân. Gộp hai cái này lại sẽ khiến một
     * cuộc gọi hoàn hảo bị đọc thành "có vấn đề nhưng không rõ là gì".
     */
    NONE,
    /** Không xác định được nguyên nhân, dù có dấu hiệu bất thường hoặc thiếu dữ liệu để kết luận. */
    UNKNOWN
}
