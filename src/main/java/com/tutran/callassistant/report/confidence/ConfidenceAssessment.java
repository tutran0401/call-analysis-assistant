package com.tutran.callassistant.report.confidence;

import com.tutran.callassistant.domain.report.ConfidenceLevel;

import java.util.List;

/**
 * Độ tin cậy kèm lý do: report hiện cả hai để người đọc biết <i>vì sao</i> là HIGH/MEDIUM/LOW thay vì
 * phải tin một nhãn trơn.
 */
public record ConfidenceAssessment(ConfidenceLevel level, List<String> reasons) {

    public ConfidenceAssessment {
        reasons = List.copyOf(reasons);
    }
}
