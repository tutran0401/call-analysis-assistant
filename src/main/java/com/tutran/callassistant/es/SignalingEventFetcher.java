package com.tutran.callassistant.es;

import com.tutran.callassistant.parser.ParseResult;

/** Lấy signaling event của một cuộc gọi, đã chuẩn hoá thành {@link com.tutran.callassistant.domain.CanonicalEvent}. */
public interface SignalingEventFetcher {

    ParseResult fetchByCallId(String callId);
}
