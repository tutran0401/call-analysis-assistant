package com.tutran.callassistant.application.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Ghép prompt hệ thống + context đã sanitize + schema output thành {@link AiPrompt}. */
@Component
public class AiPromptFactory {

    private static final String SYSTEM_PROMPT_RESOURCE = "/ai/prompts/analysis-system.vi.txt";
    private static final String OUTPUT_SCHEMA_RESOURCE = "/ai/ai-output-schema-v1.json";

    private final String systemPrompt;
    private final JsonNode outputSchema;

    public AiPromptFactory() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream prompt = open(SYSTEM_PROMPT_RESOURCE); InputStream schema = open(OUTPUT_SCHEMA_RESOURCE)) {
            this.systemPrompt = new String(prompt.readAllBytes(), StandardCharsets.UTF_8).strip();
            this.outputSchema = mapper.readTree(schema);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load AI prompt resources", e);
        }
    }

    public AiPrompt create(String sanitizedContextJson) {
        return new AiPrompt(systemPrompt, sanitizedContextJson, outputSchema);
    }

    public JsonNode outputSchema() {
        return outputSchema;
    }

    private InputStream open(String resource) {
        InputStream in = getClass().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Resource not found: " + resource);
        }
        return in;
    }
}
