package com.tutran.callassistant.testsupport;

import com.tutran.callassistant.analysis.timeline.EventDeduplicator;
import com.tutran.callassistant.analysis.timeline.TimelineBuilder;
import com.tutran.callassistant.analysis.timeline.WebRtcElapsedTimeAnchor;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.ingest.file.EndCallLogParser;
import com.tutran.callassistant.ingest.file.EndCallSchemaClassifier;
import com.tutran.callassistant.ingest.file.LogFile;
import com.tutran.callassistant.ingest.file.SignalingExportParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Trỏ tới các cuộc gọi thật trong {@code sample-data/} và dựng timeline từ chúng.
 *
 * <p>Tồn tại vì ba test class khác nhau đều cần đúng một việc "đọc cuộc gọi thật này ra thành
 * timeline"; trước đây mỗi class tự viết lại đoạn đó, nên một thay đổi ở tầng ingest phải sửa ở ba
 * chỗ. Cố tình dùng parser thật (không mock) vì toàn bộ giá trị kỳ vọng trong test được tính tay từ
 * chính các file mẫu này.
 */
public final class SampleCalls {

    /** Cuộc gọi thành công, đầy đủ end-call log cả hai bên - dùng cho phần lớn số liệu tính tay. */
    public static final String SUCCESS_FULL_LOGS = "DE7DD314-F432-45CB-BCB4-AE9103CC0919";
    /** Signaling nhìn bình thường nhưng không có end-call log của bên nào cả. */
    public static final String SUCCESS_NO_CLIENT_LOGS = "6A7CE985-4A1A-44D1-84B1-DBB0B0B90448";
    /** Chỉ có end-call log của caller. */
    public static final String SUCCESS_CALLER_LOG_ONLY = "C8CF631E-0C6B-46E4-92E7-280E7B6A5394";
    /** Có một file đặt sai tên: {@code calleer_webrtc.log}. */
    public static final String SUCCESS_MISNAMED_FILE = "EE129C8F-EAD0-4302-AB68-920D32F8B8B7";
    /** Bị từ chối sau INVITE. */
    public static final String FAIL_REJECTED_AFTER_INVITE = "1B009D42-49CD-479E-B26C-3A2994AEB720";
    /** Caller huỷ ngay trong lúc INIT_CALL, chưa từng gửi INVITE. */
    public static final String FAIL_CANCELLED_BEFORE_INVITE = "703100CF-5742-467E-9E0E-34E45F60FF58";

    private static final Path SAMPLE_DATA = Path.of("sample-data");
    private static final String SIGNALING_FILE = "signaling.json";

    private SampleCalls() {
    }

    public static Path success(String callId) {
        return SAMPLE_DATA.resolve("success").resolve(callId);
    }

    public static Path fail(String callId) {
        return SAMPLE_DATA.resolve("fail").resolve(callId);
    }

    public static TimelineBuilder timelineBuilder() {
        return new TimelineBuilder(new EventDeduplicator(), List.of(new WebRtcElapsedTimeAnchor()));
    }

    /** Signaling export của cuộc gọi, chuẩn hoá thành canonical event. */
    public static List<CanonicalEvent> signalingEvents(Path callDirectory, String callId) {
        return new SignalingExportParser()
                .parse(LogFile.of(callDirectory.resolve(SIGNALING_FILE), callId))
                .events();
    }

    public static List<CanonicalEvent> endCallEvents(Path file, String callId) {
        return new EndCallLogParser(new EndCallSchemaClassifier()).parse(LogFile.inDirectory(file, callId)).events();
    }

    /** Timeline chỉ từ signaling - dùng cho các cuộc gọi không có client log, hoặc để test N/A. */
    public static CallTimeline signalingOnlyTimeline(Path callDirectory, String callId) {
        return timelineBuilder().build(callId, signalingEvents(callDirectory, callId));
    }

    /**
     * Timeline từ signaling cộng mọi end-call log có mặt trong thư mục. Cố tình <b>không</b> nạp
     * WebRTC log: các test dựa vào helper này còn khẳng định chỉ số suy ra từ WebRTC phải là N/A
     * tường minh khi nguồn đó thiếu.
     */
    public static CallTimeline timelineWithEndCallLogs(Path callDirectory, String callId) {
        List<CanonicalEvent> events = new ArrayList<>(signalingEvents(callDirectory, callId));
        for (Leg leg : List.of(Leg.CALLER, Leg.CALLEE)) {
            Path endCallLog = callDirectory.resolve(leg.name().toLowerCase(java.util.Locale.ROOT) + "_endcall.log");
            if (Files.exists(endCallLog)) {
                events.addAll(endCallEvents(endCallLog, callId));
            }
        }
        return timelineBuilder().build(callId, events);
    }
}
