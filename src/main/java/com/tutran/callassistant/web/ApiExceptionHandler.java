package com.tutran.callassistant.web;

import com.tutran.callassistant.sanitize.OutputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Biến mọi lỗi thành thông báo thân thiện, đã qua Output Sanitizer (mục 6.2: không để giá trị nhạy cảm
 * lọt ra qua error message). Chi tiết kỹ thuật chỉ ghi vào log (cũng đã được sanitize ở logback).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final OutputSanitizer sanitizer;

    public ApiExceptionHandler(OutputSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "File đính kèm vượt giới hạn kích thước cho phép (10 MB mỗi file).");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> badRequest(MissingServletRequestPartException e) {
        return respond(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ: thiếu dữ liệu gửi lên.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException e) {
        return respond(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled error while serving request", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                "Hệ thống gặp sự cố khi phân tích. Vui lòng thử lại; nếu vẫn lỗi, kiểm tra lại file log đính kèm.");
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(sanitizer.sanitize(message)));
    }
}
