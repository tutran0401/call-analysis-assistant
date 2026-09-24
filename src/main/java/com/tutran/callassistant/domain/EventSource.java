package com.tutran.callassistant.domain;

/** Which of the three raw log sources an event was normalized from. */
public enum EventSource {
    SIGNALING,
    END_CALL,
    WEBRTC
}
