package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.analysis.SignalingCommands;
import com.tutran.callassistant.domain.event.EventSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Bản export signaling bị cắt bớt ({@code "truncated": true}) là dữ liệu thiếu: phải nêu ở "Giới hạn dữ liệu" (mục 3.3)
 * và hạ độ tin cậy (mục 7.2 - "thiếu một phần dữ liệu" là MEDIUM; chính sách độ tin cậy tự làm việc này khi có giới
 * hạn dữ liệu).
 *
 * <p>Trước đây cờ {@code truncated} chỉ thành một cảnh báo lúc parse file, và mất hẳn khi signaling đi qua
 * Elasticsearch: {@code DE7DD314} (200/201 sự kiện) ra HIGH với "Giới hạn dữ liệu: Không có" (peer evaluation).
 */
@Component
@Order(20)
public class TruncatedSignalingDetector implements DataLimitationDetector {

    @Override
    public void detect(VerdictContext context) {
        context.timeline().forSource(EventSource.SIGNALING).stream()
                .map(e -> e.attribute(SignalingCommands.EXPORT_TRUNCATED_ATTRIBUTE))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(counts -> context.noteDataLimitation("Bản export signaling bị cắt bớt (chỉ có " + counts
                        + " sự kiện): có thể thiếu sự kiện, kết luận dựa trên phần còn lại"));
    }
}
