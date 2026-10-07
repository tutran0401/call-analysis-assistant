package com.tutran.callassistant.application;

import com.tutran.callassistant.application.request.CallFocus;
import com.tutran.callassistant.application.request.Intent;
import com.tutran.callassistant.domain.report.Report;

import java.util.List;
import java.util.Set;

/**
 * Kết quả một lượt hỏi, mọi chuỗi đã qua Output Sanitizer.
 *
 * @param report         {@code null} khi câu hỏi ngoài phạm vi (khi đó {@code message} là câu từ chối cố định)
 * @param degraded       AI không dùng được; report là kết quả rule-based
 * @param fallbackReason mã lý do fallback ({@code AI_TIMEOUT}, {@code GUARDRAIL_REJECTED}, ...), {@code null} nếu không fallback
 * @param renderedText   report đã render theo bố cục mục 4.5, để Web UI có bản sao chép nguyên văn
 * @param aiTokens       tổng token của lần gọi phân tích AI (prompt + completion), {@code null} nếu không gọi AI
 *                       hoặc provider không báo - dùng đo chi phí khi so sánh cách dựng context
 */
public record ChatResponse(String requestId,
                           Intent intent,
                           Set<CallFocus> focus,
                           Report report,
                           String message,
                           boolean degraded,
                           String fallbackReason,
                           List<String> warnings,
                           String renderedText,
                           Integer aiTokens) {

    public ChatResponse {
        focus = Set.copyOf(focus);
        warnings = List.copyOf(warnings);
    }
}
