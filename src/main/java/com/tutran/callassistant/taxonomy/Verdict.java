package com.tutran.callassistant.taxonomy;

/**
 * Call outcome per PROJECT_SPEC.md §4.1.
 */
public enum Verdict {
    /** Call was established and ended normally. May still carry a quality flag. */
    SUCCESS,
    /** Call was never established, or was terminated abnormally. */
    FAIL,
    /** Not enough evidence to conclude (missing files, missing data, contradictory evidence). */
    UNKNOWN
}
