package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.verdict.Evidence;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Phần giải thích xác định (Sprint 3 T2) - trả lời bốn câu hỏi của mục 7.1 mà không cần AI:
 * chuyện gì đã xảy ra, vì sao kết luận vậy, evidence nào hỗ trợ, cần điều tra gì tiếp.
 *
 * <p>Có phần này thì report degraded hoặc AI mâu thuẫn rule vẫn tự giải thích được, thay vì chỉ còn một
 * dòng tóm tắt. Phân tích của AI (khi có) nằm ngay dưới và đi sâu hơn.
 */
@Component
public class VerdictExplainer {

    public List<String> explain(RuleVerdictResult verdict, List<String> suggestions) {
        String evidenceIds = verdict.evidence().stream().map(Evidence::id).collect(Collectors.joining(", "));
        String why = verdict.criterion().isBlank() ? verdict.summary() : verdict.criterion();
        return List.of(
                "Chuyện gì đã xảy ra: " + verdict.summary(),
                "Vì sao kết luận " + verdict.verdict() + ": " + why,
                "Evidence hỗ trợ: " + (evidenceIds.isEmpty() ? "không có evidence nào." : evidenceIds + "."),
                "Cần điều tra tiếp: " + (suggestions.isEmpty()
                        ? "không cần - không phát hiện vấn đề nào." : suggestions.get(0)));
    }
}
