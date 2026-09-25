package com.tutran.callassistant.application;

import java.util.List;

/** Tổng kết một lượt import signaling log vào Elasticsearch. */
public record ImportSummary(String indexName, List<ImportedCall> calls, List<String> warnings) {

    public ImportSummary {
        calls = List.copyOf(calls);
        warnings = List.copyOf(warnings);
    }

    public int totalCalls() {
        return calls.size();
    }

    public int totalEvents() {
        return calls.stream().mapToInt(ImportedCall::eventCount).sum();
    }

    /** Một cuộc gọi đã index xong. */
    public record ImportedCall(String callId, int eventCount) {
    }
}
