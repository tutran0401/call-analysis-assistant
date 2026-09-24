package com.tutran.callassistant.parser;

import java.util.List;
import java.util.Set;

/**
 * Phân loại schema của một header end-call log dựa trên tên các field nó khai báo, vì
 * (theo Javadoc của {@link EndCallLogParser}) tag số {@code #HN} không phải một định danh
 * ổn định giữa các file. Thứ tự kiểm tra rất quan trọng: các schema được kiểm tra từ đặc
 * trưng nhất trở đi vì có một số field bị trùng lặp giữa các schema (ví dụ cả SIGNAL và
 * LOG_MESSAGE đều dùng {@code msg}; cả PERIODIC_STATS và END_CALL_SUMMARY đều dùng
 * {@code audio.audioMos}).
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
