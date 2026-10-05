package com.tutran.callassistant.sanitize;

import java.util.EnumMap;
import java.util.Map;

/**
 * Đếm số giá trị nhạy cảm đã xử lý, theo loại. Đây là "Sensitive fields detected" trong metric
 * Security Leakage (input) ở mục 6.5.
 */
public final class SanitizationSummary {

    private final Map<SensitiveKind, Integer> counts = new EnumMap<>(SensitiveKind.class);

    public void record(SensitiveKind kind) {
        counts.merge(kind, 1, Integer::sum);
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int count(SensitiveKind kind) {
        return counts.getOrDefault(kind, 0);
    }

    public Map<SensitiveKind, Integer> counts() {
        return Map.copyOf(counts);
    }

    @Override
    public String toString() {
        return counts.toString();
    }
}
