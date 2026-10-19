package com.tutran.callassistant.evaluation;

/**
 * Các metric của PROJECT_SPEC.md mục 6.5. Mỗi {@link Ratio} nói rõ mẫu số: một metric không đo được
 * (thiếu nhãn) hiện là "chưa đo", không bị tính nhầm thành 0% hay 100%.
 *
 * @param verdictAccuracy       verdict cuối cùng (sau AI + Guardrails) đúng nhãn
 * @param ruleVerdictAccuracy   verdict của rule baseline đúng nhãn (đối chứng)
 * @param issueCategoryAccuracy chỉ tính case FAIL và SUCCESS có cờ chất lượng, và <b>chỉ</b> case có nhãn category của
 *                              Mentor ({@link EvaluationCase#hasMentorCategoryLabel()}); chưa có nhãn thì "chưa đo"
 * @param categoryVsProvisional cùng phép so nhưng với nhãn tạm (lấy từ output rule, đã kiểm tay) - chỉ nói AI có giữ
 *                              đúng category của rule hay không, <b>không</b> phải độ chính xác
 * @param qualityFlagAccuracy   cờ chất lượng đúng nhãn
 * @param intentAccuracy        phân loại đúng trong phạm vi / ngoài phạm vi
 * @param consistencyCases      số case mà mọi lần chạy (3 cách hỏi x N lần) cho cùng verdict + issue category
 * @param consistencyRuns       tỉ lệ lần chạy trùng với kết quả phổ biến nhất của case
 * @param metricCorrectness     bảng chỉ số trong report trùng khớp bảng do Metrics Calculator tính
 * @param templateCompliance    report đủ mục, đúng thứ tự theo mẫu 4.5
 * @param unsupportedClaims     phản hồi AI bị Guardrails bắt vì evidence ID / số liệu không có trong input (mục tiêu 0)
 * @param deliveredUnsupportedClaims report cuối cùng giao cho người dùng có chứa số liệu / evidence ID không có trong
 *                              dữ liệu của chính nó (kiểm lại độc lập, mục tiêu 0) - đo thứ người dùng thực sự thấy
 * @param pipelineSuccess       request trả report hợp lệ (kể cả degraded)
 * @param degradedRate          tỉ lệ request phải fallback về rule
 * @param inputLeakage          giá trị nhạy cảm còn sót trong request gửi AI (mục tiêu 0)
 * @param outputLeakage         giá trị nhạy cảm còn sót trong response trả người dùng (mục tiêu 0)
 * @param logLeakage            giá trị nhạy cảm còn sót trong application log sau bộ lọc logback (mục tiêu 0)
 * @param latencyP50Millis      {@code null} nếu chưa có request nào
 * @param avgAiTokens           token trung bình mỗi lần gọi phân tích AI; {@code null} nếu chưa có lần nào báo token
 */
public record EvaluationMetrics(Ratio verdictAccuracy,
                                Ratio ruleVerdictAccuracy,
                                Ratio issueCategoryAccuracy,
                                Ratio categoryVsProvisional,
                                Ratio qualityFlagAccuracy,
                                Ratio intentAccuracy,
                                Ratio consistencyCases,
                                Ratio consistencyRuns,
                                Ratio metricCorrectness,
                                Ratio templateCompliance,
                                Ratio unsupportedClaims,
                                Ratio deliveredUnsupportedClaims,
                                Ratio pipelineSuccess,
                                Ratio degradedRate,
                                int inputLeakage,
                                int outputLeakage,
                                int logLeakage,
                                int sensitiveValuesInjected,
                                Long latencyP50Millis,
                                Long latencyP95Millis,
                                Double avgAiTokens) {
}
