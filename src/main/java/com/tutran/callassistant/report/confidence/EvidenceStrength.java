package com.tutran.callassistant.report.confidence;

/**
 * Độ mạnh của bộ evidence đỡ cho một kết luận, phân loại xác định từ chính danh sách evidence.
 *
 * <ul>
 *   <li>{@code STRONG}: có ít nhất 2 evidence và có evidence từ <i>nguồn chính</i> của kết luận
 *       (taxonomy mục 4.2 - ví dụ packet loss phải thấy trong End Call log).</li>
 *   <li>{@code PARTIAL}: có evidence nhưng thiếu nguồn chính, hoặc chỉ có một dòng.</li>
 *   <li>{@code WEAK}: không có evidence, hoặc phải nêu nguyên nhân mà không quy được (category UNKNOWN).</li>
 * </ul>
 */
public enum EvidenceStrength {
    STRONG, PARTIAL, WEAK
}
