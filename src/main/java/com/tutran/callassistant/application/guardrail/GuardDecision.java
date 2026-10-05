package com.tutran.callassistant.application.guardrail;

import java.util.List;

/**
 * Quyết định của Guardrails.
 *
 * <ul>
 *   <li>{@code ACCEPT}: dùng phân tích của AI nguyên vẹn.</li>
 *   <li>{@code ACCEPT_WITH_FLAGS}: dùng được nhưng phải gắn cờ (AI lệch rule); {@code flags} nêu rõ lệch ở đâu.</li>
 *   <li>{@code REJECT}: bỏ phản hồi AI, pipeline fallback về rule-based; {@code reasons} nêu vi phạm.</li>
 * </ul>
 *
 * @param verdictOverride {@code "UNKNOWN"} khi AI và rule mâu thuẫn về verdict, ngược lại {@code null}
 *                        (giữ verdict của rule)
 */
public record GuardDecision(Outcome outcome,
                            AiAnalysis analysis,
                            List<String> reasons,
                            List<String> flags,
                            String verdictOverride) {

    public enum Outcome { ACCEPT, ACCEPT_WITH_FLAGS, REJECT }

    public GuardDecision {
        reasons = List.copyOf(reasons);
        flags = List.copyOf(flags);
    }

    public static GuardDecision reject(List<String> reasons) {
        return new GuardDecision(Outcome.REJECT, null, reasons, List.of(), null);
    }

    public boolean isRejected() {
        return outcome == Outcome.REJECT;
    }
}
