package com.tutran.callassistant.analysis.verdict;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ngưỡng cảnh báo chất lượng của rule baseline.
 *
 * <p>Trước đây các ngưỡng này là hằng số {@code public static final} nằm lẫn trong registry taxonomy,
 * nên muốn thử một ngưỡng khác là phải sửa code và build lại. Giờ chúng là cấu hình
 * ({@code call-analysis.quality-thresholds.*} trong {@code application.yml}) - đúng thứ Sprint 3 T1
 * "Improve Accuracy &amp; Consistency" cần để hiệu chỉnh bằng dữ liệu accuracy thật, vì data mẫu
 * Sprint 1 không kèm ground truth để calibrate.
 *
 * <p>Giá trị mặc định khai báo bằng {@link DefaultValue} là baseline đã ghi trong tài liệu; để trống
 * trong config thì dùng đúng các giá trị đó. Record này cố tình chỉ có <b>một</b> constructor: thêm
 * constructor thứ hai sẽ làm Spring không biết chọn cái nào để bind.
 */
@ConfigurationProperties(prefix = "call-analysis.quality-thresholds")
public record QualityThresholds(
        @DefaultValue("5.0") double packetLossPercent,
        @DefaultValue("30.0") double jitterMs,
        @DefaultValue("300.0") double rttMs,
        @DefaultValue("3.5") double mos,
        @DefaultValue("2") int signalingRetransmitCount
) {
    /** Ngưỡng baseline của Sprint 1, dùng cho test và chỗ nào không có Spring container. */
    public static QualityThresholds defaults() {
        return new QualityThresholds(5.0, 30.0, 300.0, 3.5, 2);
    }
}
