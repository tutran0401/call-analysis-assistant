package com.tutran.callassistant.web;

/**
 * Thân phản hồi lỗi cho Web UI: chỉ có thông báo thân thiện bằng tiếng Việt, không bao giờ có stack trace.
 *
 * @param requestId mã tương quan để đối chiếu với log server (Sprint 3 T5)
 */
public record ApiError(String error, String requestId) {
}
