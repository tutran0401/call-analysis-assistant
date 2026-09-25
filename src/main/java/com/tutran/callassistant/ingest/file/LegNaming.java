package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.domain.event.Leg;

import java.util.Locale;

/**
 * Suy ra {@link Leg} từ một đoạn text (tên file, hoặc field {@code role} trong log).
 *
 * <p>Gom về một chỗ vì trước đây đúng logic này được viết hai lần - một lần trong loader thư
 * mục, một lần trong parser end-call log - và hai bản đó có thể bị sửa lệch nhau. Lưu ý thứ tự
 * kiểm tra: phải xét "callee" trước "caller" vì chuỗi "caller" không chứa "callee" nhưng một
 * tên file đặt sai như "calleer_webrtc.log" thì chứa cả "callee".
 */
final class LegNaming {

    private LegNaming() {
    }

    static Leg fromText(String text) {
        if (text == null) {
            return Leg.UNKNOWN;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("callee")) {
            return Leg.CALLEE;
        }
        if (lower.contains("caller")) {
            return Leg.CALLER;
        }
        return Leg.UNKNOWN;
    }
}
