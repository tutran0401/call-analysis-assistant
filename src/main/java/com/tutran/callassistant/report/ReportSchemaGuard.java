package com.tutran.callassistant.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import com.tutran.callassistant.domain.report.Report;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Validate report theo {@code schema/report-schema-v1.json} (T8).
 *
 * <p>Khác với bản trước: validator này giờ <b>thật sự nằm trong pipeline</b> chứ không chỉ được gọi
 * trong test. Một report sai schema sẽ hiện thành ghi chú xử lý ngay lúc chạy, thay vì chỉ bị phát
 * hiện khi có người nhớ viết test cho nó.
 */
@Component
public class ReportSchemaGuard implements ReportGuard {

    private static final String SCHEMA_RESOURCE = "/schema/report-schema-v1.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchema schema = loadSchema();

    @Override
    public List<String> inspect(Report report) {
        return validate(report).stream()
                .map(violation -> "Report schema violation: " + violation.getMessage())
                .toList();
    }

    /** Kết quả validate thô, dùng cho test cần khẳng định không có vi phạm nào. */
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
