package com.tutran.callassistant.application.port.out;

import com.tutran.callassistant.application.validation.UploadedFile;
import com.tutran.callassistant.application.validation.ValidatedUpload;

import java.util.List;

/**
 * File Validator (PROJECT_SPEC.md mục 6.1 T3). Không bao giờ ném exception vì file xấu: file hỏng,
 * rỗng, sai Call-ID, vượt cỡ đều thành {@code RejectedFile}, để pipeline vẫn ra report (có thể UNKNOWN).
 */
public interface UploadValidator {

    /**
     * @param requestedCallId Call-ID người dùng ghi trong câu hỏi, hoặc {@code null} nếu không có
     */
    ValidatedUpload validate(List<UploadedFile> files, String requestedCallId);
}
