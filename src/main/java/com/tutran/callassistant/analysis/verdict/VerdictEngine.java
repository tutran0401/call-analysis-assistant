package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;

/**
 * Ra verdict cho một cuộc gọi từ timeline và chỉ số đã tính.
 *
 * <p>Sprint 1 có đúng một implementation: {@link RuleVerdictEngine} thuần rule, xác định
 * (deterministic). Interface tồn tại vì Sprint 2 sẽ thêm một engine có AI tham gia, và theo mục
 * 3.2 nó không thay thế mà <i>bọc</i> engine rule lại: AI đề xuất verdict, Guardrails đối chiếu
 * với kết luận của rule, lệch nhau thì hạ về UNKNOWN. Pipeline chỉ phụ thuộc vào interface này
 * nên việc bọc thêm đó không phải sửa pipeline (Liskov + Open/Closed).
 */
public interface VerdictEngine {

    RuleVerdictResult evaluate(CallTimeline timeline, CallMetrics metrics);
}
