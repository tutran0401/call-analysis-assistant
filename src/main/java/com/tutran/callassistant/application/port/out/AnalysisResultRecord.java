package com.tutran.callassistant.application.port.out;

import java.time.Instant;

/**
 * Một dòng kết quả phân tích để thống kê. Chỉ chứa field được phép theo mục 6.2 (giống dòng log
 * {@code ai_call}): mã request, Call-ID <b>dạng pseudonym</b>, kết luận, model, latency, fallback, token.
 * Không có summary, evidence hay bất kỳ văn bản tự do nào - nên không có chỗ cho dữ liệu nhạy cảm.
 *
 * @param channel {@code web} (Chat API) hoặc {@code batch} (lệnh batch)
 * @param result  {@code OK}, {@code REFUSED} (ngoài phạm vi) hoặc {@code ERROR}
 */
public record AnalysisResultRecord(Instant timestamp,
                                   String requestId,
                                   String channel,
                                   String result,
                                   String intent,
                                   String callRef,
                                   String verdict,
                                   String issueCategory,
                                   Boolean qualityFlag,
                                   String confidenceLevel,
                                   boolean degraded,
                                   String fallbackReason,
                                   String model,
                                   Integer aiTokens,
                                   long latencyMs,
                                   int warnings,
                                   String errorKind) {
}
