package com.tutran.callassistant.analysis.timeline;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loại các sự kiện trùng nhau khi cùng một dòng log đến từ nhiều đường (ví dụ signaling
 * vừa được import vào Elasticsearch vừa còn nguyên file export trong thư mục cuộc gọi).
 *
 * <p>Khoá định danh là (nguồn, file, dòng log gốc): hai dòng log gốc y hệt nhau trong cùng
 * một file vẫn là hai sự kiện khác nhau về mặt nội dung nhưng không phân biệt được, nên cố
 * tình coi là một - đổi lại là không bao giờ đếm lặp một sự kiện đã đọc hai lần.
 */
@Component
public class EventDeduplicator {

    public List<CanonicalEvent> deduplicate(List<CanonicalEvent> events) {
        Set<String> seen = new HashSet<>();
        List<CanonicalEvent> result = new ArrayList<>(events.size());
        for (CanonicalEvent event : events) {
            String key = event.source() + "|" + event.sourceFile() + "|" + event.rawLine();
            if (seen.add(key)) {
                result.add(event);
            }
        }
        return result;
    }
}
