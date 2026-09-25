package com.tutran.callassistant.ingest.file;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Phân loại schema của một header end-call log dựa trên <b>tập tên field</b> nó khai báo, vì
 * (xem Javadoc của {@link EndCallLogParser}) tag số {@code #HN} không phải một định danh ổn định
 * giữa các file.
 *
 * <p>Các luật được khai báo thành một danh sách có thứ tự thay vì một chuỗi {@code if} - thứ tự
 * <b>rất quan trọng</b> vì một số field bị trùng giữa các schema (cả SIGNAL và LOG_MESSAGE đều
 * dùng {@code msg}; cả PERIODIC_STATS và END_CALL_SUMMARY đều dùng {@code audio.audioMos}), nên
 * schema đặc trưng hơn phải được xét trước. Viết thành danh sách khiến thứ tự đó nhìn thấy được
 * và thêm một schema mới chỉ là thêm một dòng.
 */
@Component
public class EndCallSchemaClassifier {

    /** Nhãn cho header không khớp schema nào đã biết - dữ liệu vẫn được giữ, chỉ là không đặt tên được. */
    public static final String UNKNOWN_SCHEMA = "UNKNOWN_LOG_SCHEMA";

    private static final List<SchemaRule> RULES = List.of(
            rule("CALL_SUMMARY", requiresAll("role", "callMode")),
            rule("SIGNALING_CMD", requiresAll("ackCmd", "cmd", "payload")),
            rule("QOS", requiresAll("category", "execTime", "subcmd")),
            rule("SIGNAL", requiresAll("originator", "signal")),
            rule("LOG_MESSAGE", requiresAll("msg", "status", "type")),
            rule("INIT_CONFIG", requiresAll("initCallConfig", "webrtcConfig")),
            rule("LOCAL_CANDIDATE", requiresAll("candidateType", "foundation", "ip")),
            rule("END_CALL_SUMMARY", requiresAnyPrefix("endCall.", "setup.", "answer.")),
            rule("PERIODIC_STATS", requiresAll("audio.audioMos").or(requiresAnyPrefix("transport.")))
    );

    public String classify(List<String> fields) {
        Set<String> declaredFields = Set.copyOf(fields);
        return RULES.stream()
                .filter(rule -> rule.matches().test(declaredFields))
                .map(SchemaRule::schemaType)
                .findFirst()
                .orElse(UNKNOWN_SCHEMA);
    }

    private static SchemaRule rule(String schemaType, Predicate<Set<String>> matches) {
        return new SchemaRule(schemaType, matches);
    }

    private static Predicate<Set<String>> requiresAll(String... requiredFields) {
        return declared -> Set.of(requiredFields).stream().allMatch(declared::contains);
    }

    private static Predicate<Set<String>> requiresAnyPrefix(String... prefixes) {
        return declared -> declared.stream()
                .anyMatch(field -> Set.of(prefixes).stream().anyMatch(field::startsWith));
    }

    private record SchemaRule(String schemaType, Predicate<Set<String>> matches) {
    }
}
