package com.tutran.callassistant.application;

import com.tutran.callassistant.application.validation.UploadedFile;

import java.util.List;

/** Một lượt hỏi từ Web UI: câu hỏi tiếng Việt (có thể rỗng) + các file log đính kèm. */
public record ChatRequest(String message, List<UploadedFile> files) {

    public ChatRequest {
        message = message == null ? "" : message;
        files = files == null ? List.of() : List.copyOf(files);
    }
}
