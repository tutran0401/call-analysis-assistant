package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.verdict.Evidence;
import com.tutran.callassistant.domain.verdict.IssueCategory;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import com.tutran.callassistant.domain.verdict.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Bốn câu hỏi của Sprint 3 T2, trả lời bằng code. */
class VerdictExplainerTest {

    private final VerdictExplainer explainer = new VerdictExplainer();

    @Test
    void answersWhatWhyWhichEvidenceAndNextStep() {
        RuleVerdictResult verdict = new RuleVerdictResult(Verdict.FAIL, false, IssueCategory.ICE_FAILURE,
                "ICE của CALLER failed.", List.of(new Evidence("EV001", "s", "t", "d", null),
                new Evidence("EV002", "s", "t", "d", null)), List.of(), "ICE của một bên chuyển sang failed (FAIL).");

        List<String> lines = explainer.explain(verdict, List.of("Kiểm tra mạng CALLER."));

        assertThat(lines).containsExactly(
                "Chuyện gì đã xảy ra: ICE của CALLER failed.",
                "Vì sao kết luận FAIL: ICE của một bên chuyển sang failed (FAIL).",
                "Evidence hỗ trợ: EV001, EV002.",
                "Cần điều tra tiếp: Kiểm tra mạng CALLER.");
    }

    @Test
    void cleanCallNeedsNoFollowUpAndFallsBackToSummaryWithoutCriterion() {
        RuleVerdictResult verdict = new RuleVerdictResult(Verdict.SUCCESS, false, IssueCategory.NONE, "Bình thường.",
                List.of(), List.of());

        List<String> lines = explainer.explain(verdict, List.of());

        assertThat(lines.get(1)).isEqualTo("Vì sao kết luận SUCCESS: Bình thường.");
        assertThat(lines.get(2)).contains("không có evidence");
        assertThat(lines.get(3)).contains("không cần");
    }
}
