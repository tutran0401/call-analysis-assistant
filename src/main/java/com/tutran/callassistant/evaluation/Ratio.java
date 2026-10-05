package com.tutran.callassistant.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Tỉ lệ {@code hit/total}; {@code total = 0} nghĩa là chưa có dữ liệu để đo (không phải 0%). */
public record Ratio(int hit, int total) {

    /** {@code null} khi chưa đo được - không bao giờ mặc định về 0 hay 100. */
    @JsonProperty("percent")
    public Double percent() {
        return total == 0 ? null : 100.0 * hit / total;
    }

    public boolean measured() {
        return total > 0;
    }
}
