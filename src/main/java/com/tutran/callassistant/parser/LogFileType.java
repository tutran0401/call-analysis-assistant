package com.tutran.callassistant.parser;

/** The three raw log sources this system understands, identified by content. */
public enum LogFileType {
    SIGNALING_EXPORT,
    END_CALL,
    WEBRTC,
    UNKNOWN
}
