package com.tutran.callassistant.es;

import com.tutran.callassistant.parser.ParseResult;

/** Retrieves a call's signaling events, normalized to {@link com.tutran.callassistant.domain.CanonicalEvent}. */
public interface SignalingEventFetcher {

    ParseResult fetchByCallId(String callId);
}
