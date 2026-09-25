package com.tutran.callassistant.application.port.out;

/**
 * Nhận tiến độ import theo từng cuộc gọi, để adapter (CLI) in ngay trong lúc chạy thay vì
 * phải chờ tới khi xong toàn bộ mới biết gì. Use case không cần biết đầu ra là stdout,
 * log hay progress bar trên web.
 */
@FunctionalInterface
public interface ImportProgressListener {

    void onCallImported(String callId, int eventCount);

    static ImportProgressListener ignore() {
        return (callId, eventCount) -> {
        };
    }
}
