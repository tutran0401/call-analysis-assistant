package com.tutran.callassistant.evidence;

import com.tutran.callassistant.domain.CanonicalEvent;

/**
 * One citation backing a report's verdict, metric or analysis. Every field a rule
 * conclusion depends on must be traceable back to {@link #sourceEvent()}'s raw log line -
 * this record is that trace, formatted for display per the report template (§4.5:
 * {@code [EV02][signaling 10:00:06.320] OK_ACK: LegA CONFIRMED, LegB CONFIRMED}).
 */
public record Evidence(String id, String sourceLabel, String timestampDisplay, String description,
                        CanonicalEvent sourceEvent) {

    public String formatted() {
        return "[" + id + "][" + sourceLabel + " " + timestampDisplay + "] " + description;
    }
}
