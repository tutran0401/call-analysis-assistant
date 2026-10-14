package com.tutran.callassistant.report.confidence;

/**
 * Kết quả đối chiếu verdict của AI với rule - một trong các tín hiệu của độ tin cậy (mục 7.2).
 *
 * <ul>
 *   <li>{@code NOT_COMPARED}: report thuần rule (CLI, Sprint 1) - không có AI nên không có gì để đối chiếu.</li>
 *   <li>{@code AGREES}: AI đề xuất cùng verdict, cờ chất lượng và issue category với rule.</li>
 *   <li>{@code DISAGREES}: Guardrails gắn cờ AI lệch rule (G04).</li>
 *   <li>{@code UNAVAILABLE}: đã thử gọi AI nhưng không dùng được (timeout, lỗi, bị từ chối) - report degraded.</li>
 * </ul>
 */
public enum AiAgreement {
    NOT_COMPARED, AGREES, DISAGREES, UNAVAILABLE
}
