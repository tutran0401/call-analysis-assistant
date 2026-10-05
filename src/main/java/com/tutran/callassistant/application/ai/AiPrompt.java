package com.tutran.callassistant.application.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Thứ duy nhất được gửi sang AI. {@code userContent} là JSON context <b>đã sanitize</b> (dạng chuỗi);
 * {@code outputSchema} là JSON schema mà provider phải ép structured output theo.
 */
public record AiPrompt(String systemPrompt, String userContent, JsonNode outputSchema) {
}
