package com.tutran.callassistant.sanitize;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * "Safe AI Context" (mục 6.2): context đã sanitize + số giá trị nhạy cảm đã phát hiện/xử lý, và số giá
 * trị còn sót sau khi xử lý (phải bằng 0 - đây là tử số của Security Leakage input).
 */
public record SanitizedContext(JsonNode content, SanitizationSummary summary, int residual) {
}
