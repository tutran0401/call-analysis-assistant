package com.tutran.callassistant.application;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Mã tương quan của một request (Sprint 3 T5): cùng một {@code request_id} xuất hiện trong mọi dòng log,
 * trong phản hồi API và trên UI, để từ một lỗi người dùng báo lại tìm được đúng các dòng log liên quan.
 *
 * <p>Tầng web đặt mã vào MDC ở đầu request; chạy ngoài web (CLI, Evaluation Runner) thì sinh mã mới.
 */
public final class RequestIds {

    public static final String MDC_KEY = "request_id";

    private RequestIds() {
    }

    public static String newId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Mã của request hiện tại, hoặc một mã mới nếu luồng này không nằm trong request web nào. */
    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : newId();
    }
}
