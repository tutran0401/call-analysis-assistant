package com.tutran.callassistant.evaluation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Một file đính kèm trong case. Hai dạng trong YAML:
 *
 * <pre>
 *   files: [caller_endcall.log, callee_webrtc.log]        # tên file trong thư mục của chính cuộc gọi (đúng mục 6.3)
 *   files:
 *     - from: 271D1FAF-.../callee_endcall.log             # file của cuộc gọi khác (biến thể "khác Call-ID")
 *       as: callee_endcall.log                            # tên đính kèm (biến thể "sai tên file")
 *     - from: caller_webrtc.log
 *       append: ["Authorization: Bearer eyJ...", "phone=0987654321"]   # chèn dòng nhạy cảm tổng hợp
 * </pre>
 *
 * @param from   {@code <tên file>} (trong thư mục của cuộc gọi) hoặc {@code <callId>/<tên file>}
 * @param as     tên file khi đính kèm; mặc định là tên gốc
 * @param append các dòng thêm vào cuối nội dung file (không bao giờ sửa file gốc)
 */
public record FileRef(String from, String as, List<String> append) {

    public FileRef {
        append = append == null ? List.of() : List.copyOf(append);
    }

    @JsonCreator
    public static FileRef fromJson(JsonNode node) {
        if (node.isTextual()) {
            return new FileRef(node.asText(), null, List.of());
        }
        if (!node.isObject() || !node.hasNonNull("from")) {
            throw new IllegalArgumentException("file entry must be a name or an object with 'from': " + node);
        }
        List<String> append = new java.util.ArrayList<>();
        node.path("append").forEach(line -> append.add(line.asText()));
        return new FileRef(node.get("from").asText(), node.hasNonNull("as") ? node.get("as").asText() : null, append);
    }

    public String attachedName() {
        if (as != null && !as.isBlank()) {
            return as;
        }
        int slash = from.lastIndexOf('/');
        return slash >= 0 ? from.substring(slash + 1) : from;
    }
}
