package com.tutran.callassistant.ingest.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.application.port.out.SignalingFetchError;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.domain.event.TimestampConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query index Elasticsearch local (do {@link ElasticsearchSignalingArchive} nạp dữ liệu) theo
 * Call-ID - đúng cách truy cập mà kiến trúc mục tiêu ở mục 3.1 mô tả.
 *
 * <p>Elasticsearch không chạy, quá timeout hoặc index chưa tồn tại thì trả về kết quả rỗng kèm cảnh báo
 * có mã {@link SignalingFetchError}, chứ không ném exception (kể cả {@code ElasticsearchException} là
 * RuntimeException): pipeline phải chạy tiếp được để còn dùng nguồn dự phòng
 * ({@link com.tutran.callassistant.ingest.FallbackSignalingSource}) và báo rõ trong "Giới hạn dữ liệu".
 */
@Component
public class ElasticsearchSignalingSource implements SignalingEventSource {

    /** Số sự kiện tối đa lấy về cho một cuộc gọi; export mẫu của mentor nhỏ hơn nhiều mức này. */
    private static final int MAX_EVENTS_PER_CALL = 1000;
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String CALL_ID_FIELD = "callId";
    /** Một lần thử lại có kiểm soát, chỉ cho lỗi kết nối (Sprint 3 T8). */
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_PAUSE_MS = 200;
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSignalingSource.class);

    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;

    public ElasticsearchSignalingSource(ElasticsearchClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public NormalizedEvents fetch(CallReference call) {
        String indexName = properties.signalingIndex();
        for (int attempt = 1; ; attempt++) {
            long started = System.nanoTime();
            try {
                NormalizedEvents events = search(indexName, call);
                log.info("es_query index={} hits={} latency_ms={} attempt={}", indexName, events.events().size(),
                        elapsedMillis(started), attempt);
                return events;
            } catch (IOException | RuntimeException e) {
                SignalingFetchError kind = ElasticsearchErrors.classify(e);
                log.warn("es_query index={} error_kind={} latency_ms={} attempt={}", indexName, kind,
                        elapsedMillis(started), attempt);
                if (kind.retryable() && attempt < MAX_ATTEMPTS) {
                    pause();
                    continue;
                }
                // Mã lỗi đứng đầu cảnh báo để tầng trên nhận ra và ghi vào "Giới hạn dữ liệu".
                return NormalizedEvents.unreadable(kind.name() + ": could not query Elasticsearch index "
                        + indexName + " for callId=" + call.callId() + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    private NormalizedEvents search(String indexName, CallReference call) throws IOException {
        SearchResponse<SignalingEventDoc> response = client.search(search -> search
                        .index(indexName)
                        .size(MAX_EVENTS_PER_CALL)
                        .sort(sort -> sort.field(field -> field.field(TIMESTAMP_FIELD).order(SortOrder.Asc)))
                        .query(query -> query.term(term -> term.field(CALL_ID_FIELD).value(call.callId()))),
                SignalingEventDoc.class);

        List<CanonicalEvent> events = new ArrayList<>();
        long sequenceNumber = 0;
        for (var hit : response.hits().hits()) {
            SignalingEventDoc doc = hit.source();
            if (doc != null) {
                events.add(toCanonicalEvent(doc, ++sequenceNumber));
            }
        }
        return NormalizedEvents.of(events);
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private CanonicalEvent toCanonicalEvent(SignalingEventDoc doc, long sequenceNumber) {
        Map<String, String> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, "service", doc.service());
        putIfPresent(attributes, "level", doc.level());
        putIfPresent(attributes, "csid", doc.csid());
        putIfPresent(attributes, "requestId", doc.requestId());
        putIfPresent(attributes, "appUserId", doc.appUserId());
        putIfPresent(attributes, "isp", doc.isp());
        putIfPresent(attributes, "asn", doc.asn());
        putIfPresent(attributes, "countryCode", doc.countryCode());
        // Text tự do của server (chỉ có ở export dạng mới) - cần cho phép đếm "No sessions found".
        putIfPresent(attributes, "message", doc.message());
        if (doc.latencyMs() != null) {
            attributes.put("latencyMs", String.valueOf(doc.latencyMs()));
        }

        return new CanonicalEvent(
                doc.callId(),
                Leg.SERVER,
                EventSource.SIGNALING,
                "es:" + properties.signalingIndex(),
                Instant.parse(doc.timestamp()),
                doc.timestamp(),
                TimestampConfidence.EXACT,
                sequenceNumber,
                doc.cmd() != null ? doc.cmd() : "SIGNALING_EVENT",
                attributes,
                doc.rawEvent()
        );
    }

    private void putIfPresent(Map<String, String> attributes, String key, String value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }
}
