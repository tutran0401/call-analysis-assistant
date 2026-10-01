package com.tutran.callassistant.ingest.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test cho adapter Elasticsearch bằng client giả - không cần ES thật chạy. */
class ElasticsearchSignalingTest {

    private static final String CALL_ID = "DE7DD314-F432-45CB-BCB4-AE9103CC0919";
    private static final Path EXPORT = Path.of("sample-data/success/" + CALL_ID + "/signaling.json");

    private final ElasticsearchProperties properties = ElasticsearchProperties.defaults();

    @Test
    @SuppressWarnings("unchecked")
    void sourceReturnsUnreadableResultInsteadOfThrowingWhenElasticsearchIsDown() throws IOException {
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        when(client.search(any(Function.class), any(Class.class))).thenThrow(new IOException("connection refused"));

        NormalizedEvents result = new ElasticsearchSignalingSource(client, properties)
                .fetch(new CallReference(CALL_ID, Path.of(".")));

        assertThat(result.events()).isEmpty();
        assertThat(result.warnings()).singleElement().asString().contains("connection refused");
    }

    @Test
    void archiveGeneratesSameDocumentIdsWhenImportingTheSameFileTwice() throws IOException {
        List<List<String>> idsPerRun = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            ElasticsearchClient client = mock(ElasticsearchClient.class);
            BulkResponse response = mock(BulkResponse.class);
            when(response.errors()).thenReturn(false);
            ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
            when(client.bulk(captor.capture())).thenReturn(response);

            int indexed = new ElasticsearchSignalingArchive(client, properties).index(EXPORT);

            List<String> ids = captor.getValue().operations().stream()
                    .map(op -> op.index().id()).toList();
            assertThat(ids).hasSize(indexed).doesNotContainNull();
            idsPerRun.add(ids);
        }
        assertThat(idsPerRun.get(0)).isEqualTo(idsPerRun.get(1)).doesNotHaveDuplicates();
    }

    @Test
    void archiveFailsLoudlyWhenBulkReportsErrors() throws IOException {
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        BulkResponse response = mock(BulkResponse.class);
        when(response.errors()).thenReturn(true);
        when(response.items()).thenReturn(List.of());
        when(client.bulk(any(BulkRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> new ElasticsearchSignalingArchive(client, properties).index(EXPORT))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Bulk import had errors");
    }

    @Test
    void archiveSkipsFilesWithoutEvents(@TempDir Path dir) throws IOException {
        Path empty = dir.resolve("signaling.json");
        Files.writeString(empty, "{\"callId\":\"X\",\"events\":[]}");
        ElasticsearchClient client = mock(ElasticsearchClient.class);

        assertThat(new ElasticsearchSignalingArchive(client, properties).index(empty)).isZero();
        Mockito.verifyNoInteractions(client);
    }
}
