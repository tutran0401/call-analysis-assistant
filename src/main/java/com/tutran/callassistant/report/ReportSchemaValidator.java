package com.tutran.callassistant.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/** Validates a {@link Report} against {@code schema/report-schema-v1.json} (T8). */
public final class ReportSchemaValidator {

    private static final String SCHEMA_RESOURCE = "/schema/report-schema-v1.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchema schema = loadSchema();

    public Set<ValidationMessage> validate(Report report) {
        JsonNode node = objectMapper.valueToTree(report);
        return schema.validate(node);
    }

    private JsonSchema loadSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream in = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Schema resource not found: " + SCHEMA_RESOURCE);
            }
            return factory.getSchema(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + SCHEMA_RESOURCE, e);
        }
    }
}
