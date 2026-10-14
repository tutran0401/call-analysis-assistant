package com.tutran.callassistant.ingest.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import com.tutran.callassistant.application.port.out.AnalysisResultArchive;
import com.tutran.callassistant.application.port.out.AnalysisResultRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghi kết quả phân tích vào index {@value #INDEX} của Elasticsearch local (Sprint 3 T9) để Kibana dựng view /
 * dashboard: phân bố verdict, tỉ lệ UNKNOWN, tỉ lệ fallback, latency.
 *
 * <p>Lỗi ghi (ES tắt, timeout) chỉ được log cảnh báo, không bao giờ làm hỏng request - kết quả cho người dùng
 * quan trọng hơn số liệu thống kê.
 */
public class ElasticsearchResultArchive implements AnalysisResultArchive {

    public static final String INDEX = "call-analysis-results";

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchResultArchive.class);

    private final ElasticsearchClient client;
    private volatile boolean indexReady;

    public ElasticsearchResultArchive(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void record(AnalysisResultRecord result) {
        try {
            ensureIndex();
            client.index(i -> i.index(INDEX).id(result.requestId()).document(toDocument(result)));
        } catch (IOException | RuntimeException e) {
            log.warn("result_archive error_kind={} request_id={}", ElasticsearchErrors.classify(e), result.requestId());
        }
    }

    private void ensureIndex() throws IOException {
        if (indexReady) {
            return;
        }
        if (!client.indices().exists(e -> e.index(INDEX)).value()) {
            client.indices().create(c -> c.index(INDEX).mappings(mapping()));
        }
        indexReady = true;
    }

    static TypeMapping mapping() {
        return TypeMapping.of(m -> m
                .properties("@timestamp", Property.of(p -> p.date(d -> d)))
                .properties("request_id", keyword())
                .properties("channel", keyword())
                .properties("result", keyword())
                .properties("intent", keyword())
                .properties("call_ref", keyword())
                .properties("verdict", keyword())
                .properties("issue_category", keyword())
                .properties("quality_flag", Property.of(p -> p.boolean_(b -> b)))
                .properties("confidence", keyword())
                .properties("degraded", Property.of(p -> p.boolean_(b -> b)))
                .properties("fallback_reason", keyword())
                .properties("model", keyword())
                .properties("ai_tokens", Property.of(p -> p.integer(i -> i)))
                .properties("latency_ms", Property.of(p -> p.long_(l -> l)))
                .properties("warnings", Property.of(p -> p.integer(i -> i)))
                .properties("error_kind", keyword()));
    }

    private static Property keyword() {
        return Property.of(p -> p.keyword(k -> k));
    }

    /** Tên field snake_case giống dòng log {@code chat_request}, để tìm kiếm ở Kibana và trong log là một. */
    static Map<String, Object> toDocument(AnalysisResultRecord r) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@timestamp", r.timestamp().toString());
        doc.put("request_id", r.requestId());
        doc.put("channel", r.channel());
        doc.put("result", r.result());
        doc.put("intent", r.intent());
        doc.put("call_ref", r.callRef());
        doc.put("verdict", r.verdict());
        doc.put("issue_category", r.issueCategory());
        doc.put("quality_flag", r.qualityFlag());
        doc.put("confidence", r.confidenceLevel());
        doc.put("degraded", r.degraded());
        doc.put("fallback_reason", r.fallbackReason());
        doc.put("model", r.model());
        doc.put("ai_tokens", r.aiTokens());
        doc.put("latency_ms", r.latencyMs());
        doc.put("warnings", r.warnings());
        doc.put("error_kind", r.errorKind());
        return doc;
    }
}
