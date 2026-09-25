package com.tutran.callassistant.ingest.es;

/**
 * Cấu trúc document Elasticsearch cho một sự kiện signaling.
 *
 * <p>Denormalize {@code callId} vào từng sự kiện (file export mentor cung cấp chỉ có nó một lần, ở
 * đầu file) để việc query "theo Call-ID" - cách truy cập mà cả hệ thống dựa vào - chỉ đơn giản là
 * một term filter.
 */
public record SignalingEventDoc(
        String callId,
        String timestamp,
        String cmd,
        String service,
        String level,
        String csid,
        String requestId,
        String appUserId,
        String isp,
        String asn,
        String countryCode,
        Integer latencyMs,
        String rawEvent
) {
}
