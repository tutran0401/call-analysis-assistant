package com.tutran.callassistant.ingest.file;

/** Ba loại nguồn log gốc mà hệ thống này hiểu được, nhận diện theo nội dung. */
public enum LogFileType {
    SIGNALING_EXPORT,
    END_CALL,
    WEBRTC,
    UNKNOWN
}
