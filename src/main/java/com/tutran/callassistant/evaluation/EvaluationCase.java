package com.tutran.callassistant.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Một case benchmark, theo định dạng ở PROJECT_SPEC.md mục 6.3 cộng vài field tuỳ chọn. Các field
 * {@code expected_*} đều có thể vắng: metric nào cần field đó thì bỏ qua case ấy thay vì tính sai.
 *
 * @param expectedIntent    {@code OUT_OF_SCOPE} cho case câu hỏi ngoài phạm vi; mặc định {@code null} = trong phạm vi
 * @param expectedEvidence  Evidence ID kỳ vọng - chỉ có khi mentor cung cấp ground truth ở mức evidence
 * @param labelSource       nguồn của nhãn (mentor / kiểm tay / suy từ spec) - để báo cáo phân biệt ground truth thật và nhãn tạm
 * @param sensitiveValues   giá trị nhạy cảm đã chèn vào file case này; runner kiểm chúng không lọt vào AI request / report / log
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationCase(
        String caseId,
        String callId,
        List<FileRef> files,
        List<String> questions,
        String expectedIntent,
        String expectedVerdict,
        Boolean expectedQualityFlag,
        String expectedIssueCategory,
        List<String> expectedEvidence,
        String split,
        String labelSource,
        String notes,
        List<String> sensitiveValues,
        List<String> expectedIntents
) {

    public EvaluationCase {
        files = files == null ? List.of() : List.copyOf(files);
        questions = questions == null ? List.of() : List.copyOf(questions);
        expectedEvidence = expectedEvidence == null ? List.of() : List.copyOf(expectedEvidence);
        sensitiveValues = sensitiveValues == null ? List.of() : List.copyOf(sensitiveValues);
        expectedIntents = expectedIntents == null ? List.of() : List.copyOf(expectedIntents);
    }

    /** Backward compatible case-level label; null means unlabelled, never an inferred correct intent. */
    public EvaluationCase(String caseId, String callId, List<FileRef> files, List<String> questions,
                          String expectedIntent, String expectedVerdict, Boolean expectedQualityFlag,
                          String expectedIssueCategory, List<String> expectedEvidence, String split,
                          String labelSource, String notes, List<String> sensitiveValues) {
        this(caseId, callId, files, questions, expectedIntent, expectedVerdict, expectedQualityFlag,
                expectedIssueCategory, expectedEvidence, split, labelSource, notes, sensitiveValues, List.of());
    }

    public String expectedIntentAt(int questionIndex) {
        return expectedIntents.isEmpty() ? expectedIntent : expectedIntents.get(questionIndex);
    }

    public boolean expectsOutOfScope() {
        return expectedIntents.isEmpty() ? "OUT_OF_SCOPE".equals(expectedIntent)
                : expectedIntents.stream().allMatch("OUT_OF_SCOPE"::equals);
    }

    /**
     * Nhãn issue category là ground truth thật (Mentor gắn) chứ không phải nhãn tạm lấy từ output của rule engine.
     * Quy ước trong {@code cases.yaml}: {@code label_source} chứa {@code category=mentor}.
     */
    public boolean hasMentorCategoryLabel() {
        return expectedIssueCategory != null && labelSource != null && labelSource.contains("category=mentor");
    }
}
