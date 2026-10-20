package com.tutran.callassistant.testsupport;

import com.tutran.callassistant.domain.report.EvidenceItem;
import com.tutran.callassistant.domain.report.MetricRow;
import com.tutran.callassistant.domain.report.Report;

import java.util.List;

/** Report rule-based tổng hợp dùng chung cho test AI context / guardrails / orchestration. */
public final class TestReports {

    private TestReports() {
    }

    /** SUCCESS có cờ chất lượng kém do packet loss phía callee. */
    public static Report successWithPacketLoss() {
        return new Report("CALL-1", "SUCCESS", true, "NETWORK_PACKET_LOSS",
                List.of("NETWORK_DELAY_JITTER chưa xác nhận được do thiếu RTT / jitter"),
                "MEDIUM",
                "Cuộc gọi thiết lập thành công, kết thúc bình thường; callee có packet loss cao.",
                List.of(new EvidenceItem("EV001", "signaling", "10:00:06.320", "OK_ACK: LegA CONFIRMED, LegB CONFIRMED"),
                        new EvidenceItem("EV002", "callee_endcall.log", "10:00:41.000", "Packet loss phía callee = 9.8%"),
                        new EvidenceItem("EV003", "signaling", "10:01:41.610", "BYE từ CALLER")),
                List.of(new MetricRow("Thời gian thiết lập", "6.2 s", "Signaling"),
                        new MetricRow("Thời lượng kết nối", "95.3 s", "Signaling"),
                        new MetricRow("MOS (callee)", "2.9", "End Call"),
                        new MetricRow("Packet loss (callee)", "9.8%", "End Call"),
                        new MetricRow("RTT / Jitter", "N/A (không có trong file log)", "End Call")),
                List.of("Kiểm tra chất lượng mạng phía callee."),
                List.of("Thiếu callee_webrtc.log"));
    }

    /** Hợp lệ theo ai-output-schema, khớp {@link #successWithPacketLoss()}. */
    public static String agreeingAiJson() {
        return """
                {"verdict":"SUCCESS","qualityFlag":true,"issueCategory":"NETWORK_PACKET_LOSS",
                 "confidenceLevel":"MEDIUM",
                 "summary":"Cuộc gọi thành công nhưng callee bị mất gói.",
                 "evidenceIds":["EV002","EV001"],
                 "analysis":"Cuộc gọi kết nối bình thường (EV001). {{M004}} (EV002) cùng {{M003}} cho thấy chất lượng media kém.",
                 "suggestions":["Kiểm tra mạng phía callee."]}""";
    }
}
