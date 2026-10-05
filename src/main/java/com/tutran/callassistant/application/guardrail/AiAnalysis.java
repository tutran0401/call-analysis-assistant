package com.tutran.callassistant.application.guardrail;

import java.util.List;

/** Phản hồi AI đã parse và qua kiểm tra schema (T5 structured output). Vẫn chưa được tin cho tới khi qua mọi guard. */
public record AiAnalysis(
        String verdict,
        boolean qualityFlag,
        String issueCategory,
        String confidenceLevel,
        String summary,
        List<String> evidenceIds,
        String analysis,
        List<String> suggestions
) {
}
