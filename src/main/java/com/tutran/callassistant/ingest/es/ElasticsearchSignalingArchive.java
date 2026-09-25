package com.tutran.callassistant.ingest.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutran.callassistant.application.port.out.SignalingEventArchive;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Import các file export {@code signaling.json} mentor cung cấp vào một index Elasticsearch local
 * (T1), để pipeline query signaling "theo Call-ID" giống hệt hệ thống production thay vì đọc trực
 * tiếp file export lúc phân tích.
 *
 * <p>Chạy lại import nhiều lần là an toàn: id của mỗi document là một hash xác định
 * (deterministic) từ các field làm nên tính duy nhất của sự kiện, nên import cùng một file 2 lần sẽ
 * ghi đè lên đúng các document cũ chứ không tạo bản trùng.
 */
@Component
public class ElasticsearchSignalingArchive implements SignalingEventArchive {

    /** Số byte đầu của hash SHA-256 dùng làm document id - đủ dài để không trùng ở quy mô data mẫu. */
    private static final int ID_LENGTH_BYTES = 16;

    private final ElasticsearchClient client;
    private final ElasticsearchProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElasticsearchSignalingArchive(ElasticsearchClient client, ElasticsearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String name() {
        return properties.signalingIndex();
    }

    @Override
    public void ensureReady() throws IOException {
        String indexName = name();
        if (client.indices().exists(exists -> exists.index(indexName)).value()) {
            return;
        }
        client.indices().create(create -> create.index(indexName).mappings(mapping()));
    }

    /**
     * Các field dùng để filter/sort phải là {@code keyword}/{@code date} chứ không phải text đã
     * phân tích, nếu không term query theo Call-ID sẽ không khớp.
     */
    private TypeMapping mapping() {
        return TypeMapping.of(m -> m
                .properties("callId", Property.of(p -> p.keyword(k -> k)))
                .properties("timestamp", Property.of(p -> p.date(d -> d)))
                .properties("cmd", Property.of(p -> p.keyword(k -> k)))
                .properties("service", Property.of(p -> p.keyword(k -> k)))
                .properties("level", Property.of(p -> p.keyword(k -> k)))
                .properties("csid", Property.of(p -> p.keyword(k -> k)))
                .properties("requestId", Property.of(p -> p.keyword(k -> k)))
                .properties("appUserId", Property.of(p -> p.keyword(k -> k)))
                .properties("isp", Property.of(p -> p.keyword(k -> k)))
                .properties("asn", Property.of(p -> p.keyword(k -> k)))
                .properties("countryCode", Property.of(p -> p.keyword(k -> k)))
                .properties("latencyMs", Property.of(p -> p.integer(i -> i)))
                .properties("rawEvent", Property.of(p -> p.text(t -> t))));
    }

    @Override
    public int index(Path signalingExportFile) throws IOException {
        JsonNode root = objectMapper.readTree(signalingExportFile.toFile());
        String callId = root.path("callId").asText();
        JsonNode events = root.path("events");
        if (!events.isArray() || events.isEmpty()) {
            return 0;
        }

        List<IndexedDocument> documents = new ArrayList<>();
        for (JsonNode event : events) {
            String timestamp = event.path("@timestamp").asText(null);
            if (timestamp == null) {
                continue;
            }
            SignalingEventDoc doc = toDocument(callId, timestamp, event);
            documents.add(new IndexedDocument(deterministicId(callId, doc), doc));
        }
        if (documents.isEmpty()) {
            return 0;
        }

        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (IndexedDocument document : documents) {
            bulk.operations(operation -> operation.index(idx -> idx
                    .index(name())
                    .id(document.id())
                    .document(document.doc())));
        }
        BulkResponse response = client.bulk(bulk.build());
        if (response.errors()) {
            List<String> reasons = response.items().stream()
                    .map(BulkResponseItem::error)
                    .filter(Objects::nonNull)
                    .map(error -> error.reason())
                    .toList();
            throw new IOException("Bulk import had errors for " + signalingExportFile + ": " + reasons);
        }
        return documents.size();
    }

    private SignalingEventDoc toDocument(String callId, String timestamp, JsonNode event) {
        return new SignalingEventDoc(
                callId,
                timestamp,
                textOrNull(event, "cmd"),
                textOrNull(event, "service"),
                textOrNull(event, "level"),
                textOrNull(event, "csid"),
                textOrNull(event, "requestId"),
                textOrNull(event, "appUserId"),
                textOrNull(event, "isp"),
                textOrNull(event, "asn"),
                textOrNull(event, "countryCode"),
                event.hasNonNull("latencyMs") ? event.get("latencyMs").asInt() : null,
                event.toString());
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String deterministicId(String callId, SignalingEventDoc doc) {
        String key = String.join("|", callId, doc.timestamp(), String.valueOf(doc.cmd()),
                String.valueOf(doc.requestId()), String.valueOf(doc.service()));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, ID_LENGTH_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record IndexedDocument(String id, SignalingEventDoc doc) {
    }
}
