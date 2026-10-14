package com.tutran.callassistant.ingest.es;

import com.tutran.callassistant.application.port.out.AnalysisResultRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Sprint 3 T9: document kết quả chỉ có field tóm tắt, và lỗi ES không bao giờ làm hỏng request. */
class ElasticsearchResultArchiveTest {

    private static final AnalysisResultRecord RECORD = new AnalysisResultRecord(Instant.parse("2026-10-14T05:00:00Z"),
            "req-1234abcd", "web", "OK", "ANALYZE_CALL", "CALL_1f5832", "SUCCESS", "NONE", false, "HIGH", false, null,
            "gpt-4o-mini", 3400, 2500, 0, null);

    @Test
    void documentUsesTheSameSnakeCaseFieldsAsTheChatRequestLogLine() {
        Map<String, Object> doc = ElasticsearchResultArchive.toDocument(RECORD);

        assertThat(doc).containsEntry("@timestamp", "2026-10-14T05:00:00Z").containsEntry("request_id", "req-1234abcd")
                .containsEntry("call_ref", "CALL_1f5832").containsEntry("verdict", "SUCCESS")
                .containsEntry("latency_ms", 2500L).containsEntry("degraded", false);
        assertThat(ElasticsearchResultArchive.mapping().properties()).containsKeys(doc.keySet().toArray(String[]::new));
    }

    @Test
    void unreachableElasticsearchIsLoggedNotThrown() {
        ElasticsearchProperties properties = new ElasticsearchProperties("localhost", 1, "x", 300, 300);
        ElasticsearchResultArchive archive =
                new ElasticsearchResultArchive(new ElasticsearchConfiguration().elasticsearchClient(properties));

        assertThatCode(() -> archive.record(RECORD)).doesNotThrowAnyException();
    }
}
