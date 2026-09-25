package com.tutran.callassistant.domain.verdict;

import com.tutran.callassistant.domain.event.CanonicalEvent;

/**
 * Một trích dẫn (citation) làm căn cứ cho verdict, chỉ số hay phân tích trong report. Mọi
 * field mà một kết luận của rule phụ thuộc vào đều phải trace được về dòng log gốc ở
 * {@link #sourceEvent()} - record này chính là đường trace đó, đã định dạng để hiển thị
 * theo mẫu report (mục 4.5:
 * {@code [EV02][signaling 10:00:06.320] OK_ACK: LegA CONFIRMED, LegB CONFIRMED}).
 */
public record Evidence(String id, String sourceLabel, String timestampDisplay, String description,
                       CanonicalEvent sourceEvent) {

    public String formatted() {
        return "[" + id + "][" + sourceLabel + " " + timestampDisplay + "] " + description;
    }
}
