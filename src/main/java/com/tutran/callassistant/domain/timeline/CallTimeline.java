package com.tutran.callassistant.domain.timeline;

import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.TimestampConfidence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Toàn bộ sự kiện của một cuộc gọi, đã loại trùng và sắp thứ tự. Những sự kiện không neo
 * được vào thời gian tuyệt đối nào (xem {@link TimestampConfidence#UNKNOWN}) được giữ lại
 * ở cuối, theo đúng thứ tự gốc trong từng file, chứ không bị bỏ đi.
 *
 * <p>Các phương thức truy vấn ở đây là <b>một chỗ duy nhất</b> để cả tầng metrics lẫn
 * tầng rule hỏi cùng một câu hỏi ("sự kiện signaling {@code X} sớm nhất là cái nào?",
 * "bên này có end-call log không?"). Trước khi gom về đây, đúng logic đó bị lặp lại ở cả
 * {@code CallMetricsCalculator} và rule engine, nên dễ bị sửa lệch nhau.
 */
public record CallTimeline(String callId, List<CanonicalEvent> events) {

    public CallTimeline {
        events = List.copyOf(events);
    }

    public List<CanonicalEvent> forLeg(Leg leg) {
        return events.stream().filter(e -> e.leg() == leg).toList();
    }

    public List<CanonicalEvent> forSource(EventSource source) {
        return events.stream().filter(e -> e.source() == source).toList();
    }

    public List<CanonicalEvent> forSourceAndLeg(EventSource source, Leg leg) {
        return events.stream().filter(e -> e.source() == source && e.leg() == leg).toList();
    }

    public boolean hasAnyEvent(EventSource source, Leg leg) {
        return events.stream().anyMatch(e -> e.source() == source && e.leg() == leg);
    }

    /** Sự kiện signaling sớm nhất có {@code eventType} đúng bằng {@code cmd} và có timestamp. */
    public Optional<CanonicalEvent> earliestSignaling(String cmd) {
        return earliestSignaling(cmd, null);
    }

    /** Như trên, nhưng chỉ xét các sự kiện không xảy ra trước mốc {@code notBefore}. */
    public Optional<CanonicalEvent> earliestSignaling(String cmd, Instant notBefore) {
        return events.stream()
                .filter(e -> e.source() == EventSource.SIGNALING)
                .filter(e -> cmd.equals(e.eventType()))
                .filter(e -> e.timestamp() != null)
                .filter(e -> notBefore == null || !e.timestamp().isBefore(notBefore))
                .min(Comparator.comparing(CanonicalEvent::timestamp));
    }

    public boolean hasLowConfidenceOrdering() {
        return events.stream().anyMatch(e -> e.timestampConfidence() == TimestampConfidence.UNKNOWN);
    }
}
