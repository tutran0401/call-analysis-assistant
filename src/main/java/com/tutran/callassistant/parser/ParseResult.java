package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.CanonicalEvent;

import java.util.List;

/**
 * Kết quả chuẩn hoá một file log gốc: các sự kiện sinh ra được, cộng với những dòng
 * không parse được. Dữ liệu lỗi định dạng không bao giờ được phép ném exception ra khỏi
 * parser - thay vào đó được ghi lại ở đây để pipeline vẫn chạy tiếp được và report có thể
 * nhắc tới vấn đề chất lượng dữ liệu nếu cần.
 */
public record ParseResult(List<CanonicalEvent> events, List<String> warnings) {

    public static ParseResult empty() {
        return new ParseResult(List.of(), List.of());
    }
}
