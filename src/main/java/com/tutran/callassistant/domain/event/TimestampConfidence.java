package com.tutran.callassistant.domain.event;

/**
 * Độ tin cậy của {@code timestamp} tuyệt đối của một sự kiện, do đồng hồ server
 * (signaling) và đồng hồ thiết bị (client log) có thể bị lệch nhau, và log WebRTC thì
 * chỉ có mốc thời gian tương đối tính từ lúc process khởi động, hoàn toàn không có đồng
 * hồ tuyệt đối.
 */
public enum TimestampConfidence {
    /** Đọc trực tiếp từ timestamp tuyệt đối có sẵn trong nguồn (signaling, end-call log). */
    EXACT,
    /** Suy ra bằng cách neo mốc thời gian tương đối (log WebRTC) vào timestamp tuyệt đối của một sự kiện khác. */
    ANCHORED,
    /** Không xác định được timestamp tuyệt đối; chỉ biết thứ tự tương đối trong phạm vi 1 file. */
    UNKNOWN
}
