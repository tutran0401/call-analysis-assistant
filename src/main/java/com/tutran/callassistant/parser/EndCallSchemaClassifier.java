package com.tutran.callassistant.parser;

import java.util.List;
import java.util.Set;

/**
 * Classifies an end-call log header's schema by the field names it declares, since (per
 * {@link EndCallLogParser}'s class doc) the numeric {@code #HN} tag is not a stable
 * identifier across files. Order matters: schemas are checked most-specific first because
 * some share overlapping field names (e.g. both SIGNAL and LOG_MESSAGE use {@code msg};
 * both PERIODIC_STATS and END_CALL_SUMMARY use {@code audio.audioMos}).
 */
final class EndCallSchemaClassifier {

    private EndCallSchemaClassifier() {
    }

    static String classify(List<String> fields) {
        Set<String> set = Set.copyOf(fields);

        if (set.contains("role") && set.contains("callMode")) {
            return "CALL_SUMMARY";
        }
        if (set.contains("ackCmd") && set.contains("cmd") && set.contains("payload")) {
            return "SIGNALING_CMD";
        }
        if (set.contains("category") && set.contains("execTime") && set.contains("subcmd")) {
            return "QOS";
        }
        if (set.contains("originator") && set.contains("signal")) {
            return "SIGNAL";
        }
        if (set.contains("msg") && set.contains("status") && set.contains("type")) {
            return "LOG_MESSAGE";
        }
        if (set.contains("initCallConfig") && set.contains("webrtcConfig")) {
            return "INIT_CONFIG";
        }
        if (set.contains("candidateType") && set.contains("foundation") && set.contains("ip")) {
            return "LOCAL_CANDIDATE";
        }
        if (hasAnyPrefixed(set, "endCall.") || hasAnyPrefixed(set, "setup.") || hasAnyPrefixed(set, "answer.")) {
            return "END_CALL_SUMMARY";
        }
        if (set.contains("audio.audioMos") || hasAnyPrefixed(set, "transport.")) {
            return "PERIODIC_STATS";
        }
        return "UNKNOWN_LOG_SCHEMA";
    }

    private static boolean hasAnyPrefixed(Set<String> fields, String prefix) {
        return fields.stream().anyMatch(f -> f.startsWith(prefix));
    }
}
