package com.tutran.callassistant.analysis.verdict.rule;

import com.tutran.callassistant.analysis.verdict.VerdictContext;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;

import java.util.Optional;

/**
 * Một rule xác định (deterministic) trong chuỗi quyết định verdict (Chain of Responsibility).
 *
 * <p>Rule nào <i>áp dụng được</i> cho cuộc gọi này thì trả về kết luận và chuỗi dừng lại; rule
 * nào không áp dụng thì trả về {@link Optional#empty()} để rule sau xét tiếp. Nhờ vậy thứ tự
 * quyết định trở thành thứ tự các rule (khai báo bằng
 * {@link org.springframework.core.annotation.Order}) thay vì một chuỗi if/else lồng nhau, và
 * thêm một tình huống mới là thêm một class chứ không phải sửa engine (Open/Closed).
 *
 * <p>Rule cuối cùng trong chuỗi phải luôn khớp, để không bao giờ có cuộc gọi nào không ra
 * verdict.
 */
public interface VerdictRule {

    Optional<RuleVerdictResult> apply(VerdictContext context);
}
