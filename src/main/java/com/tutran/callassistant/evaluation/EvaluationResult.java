package com.tutran.callassistant.evaluation;

import java.util.List;
import java.util.Map;

/**
 * Toàn bộ kết quả một lần chạy benchmark.
 *
 * @param model     model AI đã dùng ({@code none} nghĩa là cả lần chạy là rule-based)
 * @param samples   caseId -> báo cáo đã render của câu hỏi đầu tiên, lần chạy đầu, để người đọc soi bằng mắt
 * @param contextMode cách dựng context đã dùng ({@code structured} hoặc {@code semi}) - biến số của T11
 * @param logProbeMentionsRaw số log event còn chứa giá trị nhạy cảm ở dạng gốc (trước bộ lọc) - chứng tỏ bộ lọc log thực sự được thử
 */
public record EvaluationResult(String model,
                               String contextMode,
                               int repeat,
                               List<EvaluationCase> cases,
                               List<RunRecord> records,
                               Map<String, RuleBaseline> rules,
                               EvaluationMetrics metrics,
                               Map<String, String> samples,
                               int logProbeMentionsRaw) {
}
