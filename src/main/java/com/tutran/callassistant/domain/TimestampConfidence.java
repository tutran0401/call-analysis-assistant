package com.tutran.callassistant.domain;

/**
 * How trustworthy an event's absolute {@code timestamp} is, given that signaling
 * (server clock) and client logs (device clock) can drift, and that WebRTC logs only
 * carry a relative offset from process start with no absolute clock at all.
 */
public enum TimestampConfidence {
    /** Parsed directly from an absolute timestamp in the source (signaling, end-call log). */
    EXACT,
    /** Derived by anchoring a relative offset (WebRTC log) to another event's absolute timestamp. */
    ANCHORED,
    /** No absolute timestamp could be determined; only file-local relative order is known. */
    UNKNOWN
}
