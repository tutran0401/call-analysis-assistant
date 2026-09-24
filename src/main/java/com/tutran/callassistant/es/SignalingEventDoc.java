package com.tutran.callassistant.es;

/**
 * The Elasticsearch document shape for one signaling event. Denormalizes {@code callId}
 * onto every event (the mentor-provided export only carries it once, at the top of the
 * file) so that a query "by Call-ID" - the access pattern the whole system relies on - is
 * a simple term filter.
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
