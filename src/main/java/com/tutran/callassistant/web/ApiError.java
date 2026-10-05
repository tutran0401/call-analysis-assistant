package com.tutran.callassistant.web;

/** Thân phản hồi lỗi cho Web UI: chỉ có thông báo thân thiện bằng tiếng Việt, không bao giờ có stack trace. */
public record ApiError(String error) {
}
