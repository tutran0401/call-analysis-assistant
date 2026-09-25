package com.tutran.callassistant.domain.verdict;

/**
 * Kết luận cuộc gọi theo PROJECT_SPEC.md mục 4.1.
 */
public enum Verdict {
    /** Cuộc gọi thiết lập được và kết thúc bình thường. Vẫn có thể kèm cờ chất lượng kém. */
    SUCCESS,
    /** Cuộc gọi chưa từng thiết lập được, hoặc bị ngắt bất thường. */
    FAIL,
    /** Không đủ evidence để kết luận (thiếu file, thiếu dữ liệu, evidence mâu thuẫn). */
    UNKNOWN
}
