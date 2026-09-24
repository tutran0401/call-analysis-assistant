package com.tutran.callassistant.taxonomy;

/**
 * Các issue category theo PROJECT_SPEC.md mục 4.2, áp dụng cho cuộc gọi {@code FAIL} và
 * cuộc gọi {@code SUCCESS} có gắn cờ chất lượng kém. Xem {@link IssueCategoryRegistry} để
 * biết định nghĩa/triệu chứng/evidence/điều kiện phát hiện/điểm mơ hồ của từng category.
 */
public enum IssueCategory {
    NETWORK_PACKET_LOSS,
    NETWORK_DELAY_JITTER,
    ICE_FAILURE,
    TURN_FAILURE,
    SIGNALING_FAILURE,
    UNKNOWN
}
