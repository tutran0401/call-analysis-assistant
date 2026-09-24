package com.tutran.callassistant.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Imports the mentor-provided {@code signaling.json} exports into a local Elasticsearch
 * index (T1), so the pipeline can query signaling "by Call-ID" the way the production
 * system would, instead of reading the export file directly at analysis time.
 *
 * <p>Re-running the import is safe: each document's id is a deterministic hash of the
 * fields that make an event unique, so importing the same file twice overwrites the same
 * documents rather than duplicating them.
 */
@Component
public class SignalingIndexer {

    private final ElasticsearchClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SignalingIndexer(ElasticsearchClient client) {
        this.client = client;
    }

    public void ensureIndex(String indexName) throws IOException {
        boolean exists = client.indices().exists(e -> e.index(indexName)).value();
        if (exists) {
            return;
        }
        TypeMapping mapping = TypeMapping.of(m -> m
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
        client.indices().create(c -> c.index(indexName).mappings(mapping));
    }

    /** Imports one call folder's {@code signaling.json}. Returns the number of events indexed. */
    public int importFile(String indexName, Path signalingJsonFile) throws IOException {
        JsonNode root = objectMapper.readTree(signalingJsonFile.toFile());
        String callId = root.path("callId").asText();
        JsonNode events = root.path("events");
        if (!events.isArray() || events.isEmpty()) {
            return 0;
        }

        List<BulkOperationEntry> entries = new ArrayList<>();
        for (JsonNode event : events) {
            String timestamp = event.path("@timestamp").asText(null);
            if (timestamp == null) {
                continue;
            }
            SignalingEventDoc doc = new SignalingEventDoc(
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
                    event.toString()
            );
            entries.add(new BulkOperationEntry(deterministicId(callId, doc), doc));
        }

        if (entries.isEmpty()) {
            return 0;
        }

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (BulkOperationEntry entry : entries) {
            bulkBuilder.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(entry.id())
                    .document(entry.doc())));
        }

        BulkResponse response = client.bulk(bulkBuilder.build());
        if (response.errors()) {
            List<String> errors = response.items().stream()
                    .map(BulkResponseItem::error)
                    .filter(java.util.Objects::nonNull)
                    .map(e -> e.reason())
                    .toList();
            throw new IOException("Bulk import had errors for " + signalingJsonFile + ": " + errors);
        }
        return entries.size();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String deterministicId(String callId, SignalingEventDoc doc) {
        String key = String.join("|", callId, doc.timestamp(), String.valueOf(doc.cmd()),
                String.valueOf(doc.requestId()), String.valueOf(doc.service()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record BulkOperationEntry(String id, SignalingEventDoc doc) {
    }
}
