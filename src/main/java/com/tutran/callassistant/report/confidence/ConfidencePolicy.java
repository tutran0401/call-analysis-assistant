package com.tutran.callassistant.report.confidence;

import com.tutran.callassistant.domain.report.ConfidenceLevel;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;

/**
 * Suy ra độ tin cậy của một kết luận (Strategy).
 *
 * <p>Tách khỏi enum {@link ConfidenceLevel} vì cách suy ra sẽ thay đổi, còn thang đo thì không:
 * Sprint 1 chỉ dựa vào độ đầy đủ dữ liệu ({@link DataCompletenessConfidencePolicy}), còn thiết kế
 * đầy đủ của Sprint 3 T3 có tính tới mức độ đồng thuận giữa AI và rule. Đổi chính sách là thêm một
 * implementation, không phải sửa enum hay sửa chỗ dựng report.
 *
 * <p>Độ tin cậy luôn là kết quả tính toán xác định (deterministic), không bao giờ là một con số do
 * AI tự sinh ra (mục 7.2).
 */
public interface ConfidencePolicy {

    ConfidenceLevel confidenceFor(RuleVerdictResult verdict);
}
