package com.tutran.callassistant.domain.report;

/** Một dòng trong bảng "Chỉ số cuộc gọi" của report (mục 4.5). */
public record MetricRow(String name, String value, String source) {
}
