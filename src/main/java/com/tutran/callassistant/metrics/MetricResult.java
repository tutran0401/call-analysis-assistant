package com.tutran.callassistant.metrics;

/**
 * A single computed metric: either a value with its unit and source, or - per
 * PROJECT_SPEC.md §4.3 - an explicit N/A with a reason. Metrics must never default a
 * missing value to 0.
 */
public record MetricResult<T>(T value, String unit, String source, String naReason) {

    public static <T> MetricResult<T> of(T value, String unit, String source) {
        if (value == null) {
            throw new IllegalArgumentException("use notAvailable(...) for a missing value");
        }
        return new MetricResult<>(value, unit, source, null);
    }

    public static <T> MetricResult<T> notAvailable(String unit, String source, String reason) {
        return new MetricResult<>(null, unit, source, reason);
    }

    public boolean isAvailable() {
        return value != null;
    }

    public String display() {
        if (!isAvailable()) {
            return "N/A (" + naReason + ")";
        }
        return unit.isEmpty() ? String.valueOf(value) : value + " " + unit;
    }
}
