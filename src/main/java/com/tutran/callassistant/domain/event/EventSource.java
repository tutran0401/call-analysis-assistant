package com.tutran.callassistant.domain.event;

/** Sự kiện được chuẩn hoá từ nguồn log gốc nào trong 3 nguồn. */
public enum EventSource {
    SIGNALING,
    END_CALL,
    WEBRTC
}
