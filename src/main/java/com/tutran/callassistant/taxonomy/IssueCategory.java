package com.tutran.callassistant.taxonomy;

/**
 * Issue categories per PROJECT_SPEC.md §4.2, applicable to {@code FAIL} calls and to
 * {@code SUCCESS} calls flagged with poor quality. See {@link IssueCategoryRegistry} for
 * the definition/symptoms/evidence/detection/ambiguity of each.
 */
public enum IssueCategory {
    NETWORK_PACKET_LOSS,
    NETWORK_DELAY_JITTER,
    ICE_FAILURE,
    TURN_FAILURE,
    SIGNALING_FAILURE,
    UNKNOWN
}
