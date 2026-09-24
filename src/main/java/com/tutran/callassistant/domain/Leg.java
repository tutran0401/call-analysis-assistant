package com.tutran.callassistant.domain;

/** Which side of the call an event belongs to. */
public enum Leg {
    CALLER,
    CALLEE,
    /** Server-side signaling events are not tied to a single leg. */
    SERVER,
    UNKNOWN
}
