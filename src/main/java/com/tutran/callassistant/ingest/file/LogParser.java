package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.domain.event.NormalizedEvents;

/**
 * Chuẩn hoá một định dạng log gốc thành {@link com.tutran.callassistant.domain.event.CanonicalEvent}
 * (T3 Log Normalizer, Strategy).
 *
 * <p>Mỗi parser tự khai báo cả hai việc thuộc về định dạng của nó: <i>nhận ra</i> định dạng đó
 * ({@link #recognizes}) và <i>đọc</i> nó ({@link #parse}). Nhờ vậy thêm một nguồn log thứ tư chỉ
 * là thêm một class implement interface này - không phải sửa bộ nhận diện loại file, cũng không
 * phải sửa chỗ quét thư mục.
 */
public interface LogParser {

    /** Loại file mà parser này phụ trách. */
    LogFileType type();

    /**
     * Dòng đầu tiên có nội dung của file có đúng định dạng của parser này không.
     *
     * <p>Nhận diện theo <b>nội dung</b>, không theo tên/đuôi file: bản thân data mẫu đã có một
     * file đặt sai tên ({@code calleer_webrtc.log}), và spec yêu cầu rõ không được phân loại sai
     * một cách âm thầm chỉ vì tên file sai.
     */
    boolean recognizes(String firstMeaningfulLine);

    NormalizedEvents parse(LogFile file);
}
