package com.tutran.callassistant.application.request;

import java.util.Set;

/**
 * Kết quả hiểu câu hỏi.
 *
 * @param callIdInText Call-ID người dùng ghi trong câu hỏi, hoặc {@code null}
 */
public record ParsedRequest(Intent intent, Set<CallFocus> focus, String callIdInText) {

    /** Câu từ chối cố định cho {@link Intent#OUT_OF_SCOPE} (mục 3.3: "từ chối theo mẫu cố định"). */
    public static final String REFUSAL_MESSAGE =
            "Mình chỉ hỗ trợ phân tích cuộc gọi (kết luận, evidence, chỉ số và đề xuất xử lý). "
                    + "Bạn hãy mô tả cuộc gọi cần phân tích và đính kèm file log của cuộc gọi đó nhé.";

    public ParsedRequest {
        focus = Set.copyOf(focus);
    }

    public boolean isOutOfScope() {
        return intent == Intent.OUT_OF_SCOPE;
    }
}
