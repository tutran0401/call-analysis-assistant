package com.tutran.callassistant.domain.metrics;

/**
 * Chỉ số chất lượng của một bên (leg), lấy từ periodic stats của end-call log.
 *
 * @param packetLossPercent          mẫu {@code audio.packetLostPercent} cao nhất - tỉ lệ trong KHOẢNG giữa hai lần
 *                                   đo, chỉ dùng để đối chiếu
 * @param wholeCallPacketLossPercent mất gói của CẢ cuộc gọi, từ hai bộ đếm cộng dồn
 *                                   {@code audio.packetsLost / (packetsLost + packetsReceived)} - giá trị dùng cho
 *                                   ngưỡng {@code NETWORK_PACKET_LOSS}
 * @param meanRttMs                  RTT trung bình trên các mẫu có media - giá trị dùng cho ngưỡng
 *                                   {@code NETWORK_DELAY_JITTER} (mẫu cao nhất {@code rttMs} chỉ để đối chiếu)
 * @param meanJitterMs               jitter trung bình, như trên
 */
public record LegQualityMetrics(
        MetricResult<Double> mos,
        MetricResult<Double> packetLossPercent,
        MetricResult<Double> rttMs,
        MetricResult<Double> jitterMs,
        MetricResult<String> webrtcKeyEvents,
        MetricResult<Double> wholeCallPacketLossPercent,
        MetricResult<Double> meanRttMs,
        MetricResult<Double> meanJitterMs
) {

    /** Không có bộ đếm cộng dồn (test, dữ liệu dựng tay). */
    public LegQualityMetrics(MetricResult<Double> mos, MetricResult<Double> packetLossPercent,
                             MetricResult<Double> rttMs, MetricResult<Double> jitterMs,
                             MetricResult<String> webrtcKeyEvents) {
        this(mos, packetLossPercent, rttMs, jitterMs, webrtcKeyEvents,
                MetricResult.notAvailable("%", packetLossPercent.source(), "không có bộ đếm cộng dồn packetsLost / packetsReceived"),
                rttMs, jitterMs);
    }
}
