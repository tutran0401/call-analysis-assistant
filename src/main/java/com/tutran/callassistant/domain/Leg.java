package com.tutran.callassistant.domain;

/** Sự kiện thuộc về bên nào của cuộc gọi. */
public enum Leg {
    CALLER,
    CALLEE,
    /** Sự kiện signaling phía server không gắn với một bên (leg) cụ thể nào. */
    SERVER,
    UNKNOWN
}
