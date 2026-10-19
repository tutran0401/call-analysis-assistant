package com.tutran.callassistant.evaluation;

import java.util.List;

/**
 * Kết quả đo rò rỉ độc lập ({@link RawValueOracle}): giá trị gốc đọc từ dữ liệu case, tìm nguyên văn ở đầu ra.
 *
 * @param valuesChecked  số giá trị gốc khác nhau đã đọc được từ dữ liệu (khác 0 thì phép đo mới có nghĩa)
 * @param inputLeaks     số lần (giá trị, lượt chạy) xuất hiện trong AI request
 * @param outputLeaks    số lần (giá trị, lượt chạy) xuất hiện trong response trả người dùng
 * @param logLeaks       số lần (giá trị, dòng log) xuất hiện trong log đã qua bộ lọc
 * @param logRawMentions số lần (giá trị, event) xuất hiện trong log <i>trước</i> bộ lọc - chứng tỏ bộ lọc được thử
 * @param examples       mô tả (không lộ giá trị) của những giá trị đã lọt, tối đa vài mục
 */
public record IndependentLeakage(int valuesChecked,
                                 int inputLeaks,
                                 int outputLeaks,
                                 int logLeaks,
                                 int logRawMentions,
                                 List<String> examples) {

    public IndependentLeakage {
        examples = List.copyOf(examples);
    }

    public int total() {
        return inputLeaks + outputLeaks + logLeaks;
    }
}
