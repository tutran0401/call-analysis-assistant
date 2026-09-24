package com.tutran.callassistant.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.tutran.callassistant.domain.CanonicalEvent;
import com.tutran.callassistant.domain.EventSource;
import com.tutran.callassistant.domain.Leg;
import com.tutran.callassistant.domain.TimestampConfidence;
import com.tutran.callassistant.parser.ParseResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Query index Elasticsearch local (do {@link SignalingIndexer} nạp dữ liệu) theo Call-ID. */
@Component
public class EsSignalingEventFetcher implements SignalingEventFetcher {

    private final ElasticsearchClient client;
    private final String indexName;

    public EsSignalingEventFetcher(ElasticsearchClient client,
                                    org.springframework.core.env.Environment env) {
        this.client = client;
        this.indexName = env.getProperty("elasticsearch.signaling-index", "call-signaling-events");
    }

    @Override
    public ParseResult fetchByCallId(String callId) {
        try {
            SearchResponse<SignalingEventDoc> response = client.search(s -> s
                            .index(indexName)
                            .size(1000)
                            .sort(sort -> sort.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)))
                            .query(q -> q.term(t -> t.field("callId").value(callId))),
                    SignalingEventDoc.class);

            List<CanonicalEvent> events = new ArrayList<>();
            long seq = 0;
            for (var hit : response.hits().hits()) {
                SignalingEventDoc doc = hit.source();
                if (doc == null) {
                    continue;
                }
                events.add(toCanonicalEvent(doc, ++seq));
            }
            return new ParseResult(events, List.of());
        } catch (IOException e) {
            return new ParseResult(List.of(), List.of(
                    "Could not query Elasticsearch index '" + indexName + "' for callId=" + callId
                            + " - " + e.getMessage()));
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

        String eventType = doc.cmd() != null ? doc.cmd() : "SIGNALING_EVENT";
        return new CanonicalEvent(
                doc.callId(),
                Leg.SERVER,
                EventSource.SIGNALING,
                "es:call-signaling-events",
                Instant.parse(doc.timestamp()),
                doc.timestamp(),
                TimestampConfidence.EXACT,
                sequenceNumber,
                eventType,
                attributes,
                doc.rawEvent()
        );
    }

    private void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
