package com.tutran.callassistant.analysis.metrics;

/**
 * Nhãn nguồn dữ liệu hiển thị ở cột "Nguồn" của bảng chỉ số trong report (mục 4.5). Mọi
 * chỉ số phải nói rõ nó được tính từ nguồn log nào.
 */
public final class MetricSources {

    public static final String SIGNALING = "Signaling";
    public static final String END_CALL = "End Call log";
    public static final String WEBRTC = "WebRTC log";
    public static final String SIGNALING_OR_END_CALL = SIGNALING + "/" + END_CALL;

    private MetricSources() {
    }
}
