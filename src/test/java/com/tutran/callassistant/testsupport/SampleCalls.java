package com.tutran.callassistant.testsupport;

import com.tutran.callassistant.analysis.timeline.ClockSkewAnchor;
import com.tutran.callassistant.analysis.timeline.EventDeduplicator;
import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.analysis.timeline.TimelineBuilder;
import com.tutran.callassistant.analysis.timeline.WebRtcElapsedTimeAnchor;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.ingest.file.DirectoryClientLogSource;
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
    /**
     * Signaling đi đủ INIT_CALL...OK_ACK_OK...BYE như một cuộc gọi hoàn hảo, nhưng ICE của callee
     * {@code checking => failed} nên bên đó không hề có media (MOS = 0 trên cả 25 dòng periodic stats).
     * Ca mà chỉ signaling không bao giờ phát hiện được.
     */
    public static final String FAIL_MEDIA_NEVER_CONNECTED = "2D9057AA-C496-48B2-946A-98FA2896D086";
    /**
     * Bị huỷ tường minh ({@code CANCEL} ×15) và <b>không có end-call log nào</b>; webrtc log cũng không
     * có dòng ICE nào - nên chỉ signaling mới kết luận được.
     */
    public static final String FAIL_CANCELLED_NO_CLIENT_LOGS = "7B56D7AD-1FF1-4EDB-B212-06EC12CA73FB";
    /** TURN hỏng kiểu "không tạo nổi socket": chưa gửi đi được request allocate nào. */
    public static final String FAIL_TURN_SOCKET = "703100CF-5742-467E-9E0E-34E45F60FF58";

    // --- for_test/ - tập không gắn nhãn sẵn (không phải success/fail do mentor phân loại trước), dùng
    // làm bằng chứng "khớp tính tay" cho acceptance criteria vì kết quả không thể suy ra được từ tên
    // thư mục. ---
    /** Thành công đầy đủ, cả hai leg có end-call + webrtc log. */
    public static final String FOR_TEST_FULL_LOGS = "271D1FAF-26D4-4150-AC3F-9204505BD84B";
    /** Bị từ chối cứng (FAIL_HARD) sau RINGING; thiếu caller_endcall.log. */
    public static final String FOR_TEST_FAIL_HARD = "0EC7B700-6B5D-45A3-9BB6-1463B56C9634";
    /** Chỉ có đúng một sự kiện INIT_CALL, không có end-call log nào. */
    public static final String FOR_TEST_INIT_ONLY = "9B556E56-24D3-43BA-853D-7972AD009865";
    /** Caller huỷ trong lúc INIT_CALL; có caller_endcall.log nhưng chưa từng có media nên không có
     *  PERIODIC_STATS. */
    public static final String FOR_TEST_CANCELLED_A = "0A6C2821-0A19-49F4-9C05-8F8BCEACD64F";
    /** Cùng dạng với {@link #FOR_TEST_CANCELLED_A}, một cuộc gọi thật độc lập khác. */
    public static final String FOR_TEST_CANCELLED_B = "45AA3011-1034-4D13-82A4-634A72D432B6";
    /**
     * TURN hỏng kiểu "gửi request nhưng không có phản hồi": 60 request allocate, 0 response.
     * Không có end-call log nên chỉ webrtc log mới nói được nguyên nhân.
     */
    public static final String FOR_TEST_TURN_NO_RESPONSE = "AA9791CE-13D8-4DD7-942D-4D78304FD458";
    /**
     * Bẫy false positive: log có 24 dòng lỗi TURN <b>nhưng</b> 40 request đã gửi và có phản hồi về,
     * tức TURN vẫn hoạt động - không được gắn TURN_FAILURE cho ca này.
     */
    public static final String FOR_TEST_TURN_NOISY_BUT_OK = "311A9B6A-0D30-4C30-9E01-9A42E4EF11E6";

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

    public static Path forTest(String callId) {
        return SAMPLE_DATA.resolve("for_test").resolve(callId);
    }

    public static TimelineBuilder timelineBuilder() {
        return new TimelineBuilder(new EventDeduplicator(), List.of(new ClockSkewAnchor(), new WebRtcElapsedTimeAnchor()));
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

    /**
     * Timeline từ signaling cộng <b>mọi</b> log client có trong thư mục - end-call lẫn WebRTC, nhận
     * diện theo nội dung nên file đặt sai tên vẫn vào đúng chỗ.
     *
     * <p>Dùng cho các test cần bằng chứng tầng media (trạng thái ICE), ví dụ phân biệt một cuộc gọi
     * thật sự thành công với một cuộc gọi có signaling hoàn hảo nhưng media chết.
     */
    public static CallTimeline timelineWithAllClientLogs(Path callDirectory, String callId) {
        List<CanonicalEvent> events = new ArrayList<>(signalingEvents(callDirectory, callId));
        events.addAll(DirectoryClientLogSource.withDefaults()
                .load(new CallReference(callId, callDirectory))
                .events());
        return timelineBuilder().build(callId, events);
    }
}
