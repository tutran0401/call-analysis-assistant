package com.tutran.callassistant.ingest.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.port.out.SignalingFetchError;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Sprint 3 T4/T8: lỗi Elasticsearch được phân loại và không bao giờ làm hỏng request. */
class ElasticsearchErrorsTest {

    @Test
    void connectionRefusedIsUnavailableAndRetryable() {
        SignalingFetchError kind = ElasticsearchErrors.classify(new IOException("x", new ConnectException("refused")));

        assertThat(kind).isEqualTo(SignalingFetchError.ES_UNAVAILABLE);
        assertThat(kind.retryable()).isTrue();
    }

    @Test
    void socketTimeoutIsTimeoutAndNotRetried() {
        SignalingFetchError kind = ElasticsearchErrors.classify(new IOException(new SocketTimeoutException("30s")));

        assertThat(kind).isEqualTo(SignalingFetchError.ES_TIMEOUT);
        assertThat(kind.retryable()).isFalse();
    }

    @Test
    void missingIndexIsRecognisedFromTheServerError() {
        ElasticsearchException missing = new ElasticsearchException("search", ErrorResponse.of(r -> r
                .status(404)
                .error(e -> e.type("index_not_found_exception").reason("no such index"))));

        assertThat(ElasticsearchErrors.classify(missing)).isEqualTo(SignalingFetchError.ES_INDEX_MISSING);
        assertThat(ElasticsearchErrors.classify(new IllegalStateException("?"))).isEqualTo(SignalingFetchError.ES_ERROR);
    }

    @Test
    void warningCodeRoundTripsToALimitation() {
        assertThat(SignalingFetchError.fromWarning("ES_TIMEOUT: could not query")).contains(SignalingFetchError.ES_TIMEOUT);
        assertThat(SignalingFetchError.fromWarning("ES_NOPE: x")).isEmpty();
        assertThat(SignalingFetchError.fromWarning("Thiếu caller_endcall.log")).isEmpty();
        assertThat(SignalingFetchError.ES_INDEX_MISSING.limitation()).contains("import");
    }

    @Test
    void unreachableElasticsearchReturnsAClassifiedWarningInsteadOfThrowing() {
        // Cổng 1 trên localhost: không có gì lắng nghe -> connection refused ngay lập tức.
        ElasticsearchProperties properties = new ElasticsearchProperties("localhost", 1, "call-signaling-events", 500, 500);
        ElasticsearchClient client = new ElasticsearchConfiguration().elasticsearchClient(properties);
        ElasticsearchSignalingSource source = new ElasticsearchSignalingSource(client, properties);

        NormalizedEvents events = source.fetch(new CallReference("CALL-1", Path.of(".")));

        assertThat(events.events()).isEmpty();
        assertThat(events.warnings()).singleElement().asString().startsWith("ES_UNAVAILABLE:");
    }
}
