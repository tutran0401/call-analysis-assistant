package com.tutran.callassistant.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tutran.callassistant.domain.report.Report;

import java.util.List;

/**
 * Kết quả đo của <b>một lần</b> gửi một câu hỏi của một case (mục 6.5). Chỉ giữ số đếm rò rỉ chứ không
 * giữ nội dung request AI, để {@code results.json} không bao giờ chứa lại dữ liệu nhạy cảm.
 *
 * @param questionIndex    thứ tự câu hỏi trong case (0-based)
 * @param run              lần chạy lặp (0-based), dùng đo Consistency
 * @param pipelineOk       trả được report hợp lệ (kể cả degraded) hoặc lời từ chối hợp lệ; {@code false} nghĩa là exception
 * @param inputProbeHits   số giá trị nhạy cảm đã chèn ({@code sensitive_values}) còn thấy trong request gửi AI
 * @param inputResidual    số giá trị nhạy cảm detector còn phát hiện được trong request gửi AI
 * @param outputProbeHits  như trên nhưng cho response trả người dùng (report, ghi chú, message)
 * @param outputResidual   như trên
 * @param report           chỉ để tính metric trong bộ nhớ, không ghi vào {@code results.json} (đã có {@code samples/})
 * @param aiCalled         request có thực sự tới AI provider hay không
 */
public record RunRecord(String caseId,
                        int questionIndex,
                        String question,
                        int run,
                        String intent,
                        String verdict,
                        String issueCategory,
                        boolean qualityFlag,
                        String confidenceLevel,
                        boolean degraded,
                        String fallbackReason,
                        long latencyMillis,
                        boolean pipelineOk,
                        String error,
                        boolean aiCalled,
                        int inputProbeHits,
                        int inputResidual,
                        int outputProbeHits,
                        int outputResidual,
                        List<String> warnings,
                        @JsonIgnore Report report,
                        @JsonIgnore String renderedText) {

    public RunRecord {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
