package com.tutran.callassistant.domain.metrics;

/**
 * Một chỉ số đã tính được: hoặc là một giá trị kèm đơn vị và nguồn, hoặc - theo
 * PROJECT_SPEC.md mục 4.3 - một N/A tường minh kèm lý do. Chỉ số không bao giờ được mặc
 * định về 0 khi thiếu dữ liệu.
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

    /** Giá trị có vượt ngưỡng cảnh báo trên không (thiếu dữ liệu thì không coi là vượt). */
    public boolean exceeds(double threshold) {
        return value instanceof Number number && number.doubleValue() > threshold;
    }

    /** Giá trị có tụt xuống dưới ngưỡng cảnh báo dưới không (thiếu dữ liệu thì không coi là tụt). */
    public boolean fallsBelow(double threshold) {
        return value instanceof Number number && number.doubleValue() < threshold;
    }
}
