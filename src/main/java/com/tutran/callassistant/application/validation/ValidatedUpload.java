package com.tutran.callassistant.application.validation;

import java.nio.file.Path;
import java.util.List;

/**
 * Kết quả của File Validator: thư mục tạm chỉ chứa các file đã được chấp nhận (để pipeline đọc như
 * một thư mục cuộc gọi bình thường), danh sách file bị loại, và các giới hạn dữ liệu cần ghi vào
 * report.
 *
 * <p>Phải đóng ({@link #close()}) sau khi phân tích xong để xoá thư mục tạm - dùng try-with-resources.
 *
 * @param callId      Call-ID thống nhất của các file được nhận, hoặc {@value #UNKNOWN_CALL_ID}
 * @param directory   thư mục tạm chứa file đã nhận
 * @param limitations giới hạn dữ liệu do bước validate phát hiện (file bị loại, thiếu file, ...)
 */
public record ValidatedUpload(String callId,
                              Path directory,
                              List<String> accepted,
                              List<RejectedFile> rejected,
                              List<String> limitations,
                              Runnable cleanup) implements AutoCloseable {

    public static final String UNKNOWN_CALL_ID = "UNKNOWN";

    public boolean hasCallId() {
        return !UNKNOWN_CALL_ID.equals(callId);
    }

    @Override
    public void close() {
        cleanup.run();
    }
}
