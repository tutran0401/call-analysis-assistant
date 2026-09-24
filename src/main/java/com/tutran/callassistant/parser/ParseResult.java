package com.tutran.callassistant.parser;

import com.tutran.callassistant.domain.CanonicalEvent;

import java.util.List;

/**
 * Result of normalizing one raw log file: the events it produced, plus any lines that
 * could not be parsed. Malformed input must never throw past a parser - it is recorded
 * here instead so the pipeline can keep going and the report can mention data quality
 * issues if relevant.
 */
public record ParseResult(List<CanonicalEvent> events, List<String> warnings) {

    public static ParseResult empty() {
        return new ParseResult(List.of(), List.of());
    }
}
