package com.tutran.callassistant.domain.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Kết quả chuẩn hoá <i>một nguồn</i> dữ liệu thành {@link CanonicalEvent}: các sự kiện
 * sinh ra được, cộng với những gì không đọc/không hiểu được.
 *
 * <p>Dữ liệu lỗi định dạng không bao giờ được phép ném exception ra khỏi adapter đầu vào
 * (PROJECT_SPEC.md mục 3.3 "parser không được crash") - thay vào đó được ghi lại ở
 * {@link #warnings()} để pipeline vẫn chạy tiếp được và report vẫn nêu được giới hạn dữ
 * liệu. Kiểu này là <b>hợp đồng trả về chung</b> của mọi port đầu vào (đọc file lẫn query
 * Elasticsearch), nên tầng application gộp nhiều nguồn lại chỉ bằng {@link #combine}
 * thay vì tự tay nối 2 list ở mỗi chỗ gọi.
 */
public record NormalizedEvents(List<CanonicalEvent> events, List<String> warnings) {

    public NormalizedEvents {
        events = List.copyOf(events);
        warnings = List.copyOf(warnings);
    }

    public static NormalizedEvents empty() {
        return new NormalizedEvents(List.of(), List.of());
    }

    public static NormalizedEvents of(List<CanonicalEvent> events) {
        return new NormalizedEvents(events, List.of());
    }

    /** Không chuẩn hoá được gì từ nguồn này; chỉ còn lý do để báo lại. */
    public static NormalizedEvents unreadable(String warning) {
        return new NormalizedEvents(List.of(), List.of(warning));
    }

    public NormalizedEvents withExtraWarnings(List<String> extraWarnings) {
        if (extraWarnings.isEmpty()) {
            return this;
        }
        List<String> merged = new ArrayList<>(warnings);
        merged.addAll(extraWarnings);
        return new NormalizedEvents(events, merged);
    }

    public NormalizedEvents combine(NormalizedEvents other) {
        List<CanonicalEvent> mergedEvents = new ArrayList<>(events);
        mergedEvents.addAll(other.events());
        List<String> mergedWarnings = new ArrayList<>(warnings);
        mergedWarnings.addAll(other.warnings());
        return new NormalizedEvents(mergedEvents, mergedWarnings);
    }

    public static NormalizedEvents merge(Collection<NormalizedEvents> parts) {
        NormalizedEvents result = empty();
        for (NormalizedEvents part : parts) {
            result = result.combine(part);
        }
        return result;
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }
}
