package com.tutran.callassistant.ingest.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.domain.event.CanonicalEvent;
import com.tutran.callassistant.domain.event.EventSource;
import com.tutran.callassistant.domain.event.Leg;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import com.tutran.callassistant.domain.event.TimestampConfidence;
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
 * <p>Elasticsearch không chạy hoặc index chưa tồn tại thì trả về kết quả rỗng kèm cảnh báo, chứ
 * không ném exception: pipeline phải chạy tiếp được để còn dùng nguồn dự phòng
 * ({@link com.tutran.callassistant.ingest.FallbackSignalingSource}).
 */
@Component
public class ElasticsearchSignalingSource implements SignalingEventSource {

    /** Số sự kiện tối đa lấy về cho một cuộc gọi; export mẫu của mentor nhỏ hơn nhiều mức này. */
    private static final int MAX_EVENTS_PER_CALL = 1000;
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String CALL_ID_FIELD = "callId";

    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;

    public ElasticsearchSignalingSource(ElasticsearchClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public NormalizedEvents fetch(CallReference call) {
        String indexName = properties.signalingIndex();
        try {
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
        } catch (IOException e) {
            return NormalizedEvents.unreadable("Could not query Elasticsearch index " + indexName
                    + " for callId=" + call.callId() + " - " + e.getMessage());
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
