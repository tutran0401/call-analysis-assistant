package com.tutran.callassistant.application;

import com.tutran.callassistant.application.validation.UploadedFile;

import java.util.List;

/**
 * Một lượt hỏi từ Web UI: câu hỏi tiếng Việt (có thể rỗng) + các file log đính kèm.
 *
 * @param callId Call-ID người dùng khai báo riêng (ô "Call-ID" trên UI, hoặc {@code call_id} của case benchmark),
 *               hoặc {@code null}. Cần khi chỉ đính kèm WebRTC log - loại log này không chứa Call-ID nên không có
 *               cách nào khác để biết cuộc gọi nào mà tra signaling. Call-ID viết trong câu hỏi được ưu tiên hơn.
 */
public record ChatRequest(String message, List<UploadedFile> files, String callId) {

    public ChatRequest {
        message = message == null ? "" : message;
        files = files == null ? List.of() : List.copyOf(files);
        callId = callId == null || callId.isBlank() ? null : callId.trim();
    }

    public ChatRequest(String message, List<UploadedFile> files) {
        this(message, files, null);
    }
}
