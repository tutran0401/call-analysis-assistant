package com.tutran.callassistant.report.confidence;

import com.tutran.callassistant.domain.verdict.RuleVerdictResult;

/**
 * Suy ra độ tin cậy của một kết luận (Strategy).
 *
 * <p>Hai bước, vì hai nhóm tín hiệu có mặt ở hai thời điểm khác nhau của pipeline:
 * <ol>
 *   <li>{@link #assess}: ngay khi rule ra kết luận - độ mạnh evidence, dữ liệu thiếu, thứ tự thời gian.</li>
 *   <li>{@link #withAi}: sau khi Guardrails đối chiếu AI với rule - AI khớp, lệch, hay không dùng được.</li>
 * </ol>
 *
 * <p>Độ tin cậy luôn là kết quả tính toán xác định (deterministic), không bao giờ là một con số hay nhãn
 * do AI tự sinh ra (mục 7.2) - {@code confidenceLevel} AI trả về bị bỏ qua.
 */
public interface ConfidencePolicy {

    ConfidenceAssessment assess(RuleVerdictResult verdict);

    ConfidenceAssessment withAi(ConfidenceAssessment ruleBased, AiAgreement agreement);
}
