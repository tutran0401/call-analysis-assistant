package com.tutran.callassistant.analysis.metrics;

import com.tutran.callassistant.analysis.SignalingCommands;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.metrics.MetricResult;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Các chỉ số dạng đếm (mục 4.3): số lần gửi lại INVITE/BYE, và số lần gặp
 * "No sessions found".
 *
 * <p>Số lần gửi lại được suy ra từ số {@code requestId} phân biệt của cùng một lệnh: mỗi
 * lần gửi lại sinh một requestId mới, nên lần gửi đầu tiên không tính là gửi lại (trừ 1).
 */
@Component
public class SignalingCountCalculator {

    private static final String REQUEST_ID = "requestId";
    private static final String NO_SESSIONS_MARKER = "no session";
    /** Hai field text tự do duy nhất trong data mẫu: {@code msg} (end-call log), {@code message} (WebRTC log). */
    private static final List<String> FREE_TEXT_FIELDS = List.of("msg", "message");

    public MetricResult<Integer> inviteRetransmitCount(CallTimeline timeline) {
        return retransmitCount(timeline, SignalingCommands.INVITE);
    }

    public MetricResult<Integer> byeRetransmitCount(CallTimeline timeline) {
        return retransmitCount(timeline, SignalingCommands.BYE);
    }

    private MetricResult<Integer> retransmitCount(CallTimeline timeline, String cmd) {
        Set<String> requestIds = timeline.forSource(EventSource.SIGNALING).stream()
                .filter(e -> cmd.equals(e.eventType()))
                .map(e -> e.attribute(REQUEST_ID))
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (requestIds.isEmpty()) {
            return MetricResult.notAvailable("retransmits", MetricSources.SIGNALING,
                    "no " + cmd + " event found");
        }
        return MetricResult.of(requestIds.size() - 1, "retransmits", MetricSources.SIGNALING);
    }

    /**
     * Chỉ đếm được khi trong dữ liệu có ít nhất một field text tự do để mà tìm. Signaling
     * export hoàn toàn có cấu trúc (chỉ có cmd/service/csid/...), nên nếu thiếu hẳn end-call
     * log thì phải báo N/A thay vì trả 0 - một con số 0 sai ở đây sẽ bị đọc thành "không hề
     * gặp lỗi session".
     */
    public MetricResult<Integer> noSessionsFoundCount(CallTimeline timeline) {
        List<CanonicalEvent> events = timeline.events();
        boolean anyFreeTextField = events.stream().anyMatch(this::hasFreeTextField);
        if (!anyFreeTextField) {
            return MetricResult.notAvailable("occurrences", MetricSources.SIGNALING_OR_END_CALL,
                    "no free-text log message field present in the provided logs to search");
        }
        long count = events.stream()
                .flatMap(this::freeTextValues)
                .filter(text -> text != null && text.toLowerCase().contains(NO_SESSIONS_MARKER))
                .count();
        return MetricResult.of((int) count, "occurrences", MetricSources.SIGNALING_OR_END_CALL);
    }

    private boolean hasFreeTextField(CanonicalEvent event) {
        return FREE_TEXT_FIELDS.stream().anyMatch(field -> event.attribute(field) != null);
    }

    private Stream<String> freeTextValues(CanonicalEvent event) {
        return FREE_TEXT_FIELDS.stream().map(event::attribute);
    }
}
