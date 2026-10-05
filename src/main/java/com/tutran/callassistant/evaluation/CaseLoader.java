package com.tutran.callassistant.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Đọc bộ case từ file YAML (mục 6.3) - đây là chỗ Mentor đưa tập {@code held-out} vào. File có thể là
 * một danh sách case, hoặc một object có khoá {@code cases:}. Lỗi định dạng báo rõ case nào, field nào,
 * để không phải đoán khi chạy bộ case lạ.
 */
public final class CaseLoader {

    private static final Set<String> VERDICTS = Set.of("SUCCESS", "FAIL", "UNKNOWN");
    private static final Set<String> SPLITS = Set.of("dev", "held-out");

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<EvaluationCase> load(Path file) {
        JsonNode root;
        try {
            root = mapper.readTree(file.toFile());
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read case file " + file + ": " + e.getMessage(), e);
        }
        JsonNode list = root != null && root.isObject() ? root.path("cases") : root;
        if (list == null || !list.isArray()) {
            throw new IllegalArgumentException("Case file must be a list of cases (or an object with 'cases'): " + file);
        }
        List<EvaluationCase> cases = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode node : list) {
            EvaluationCase parsed = parse(node);
            validate(parsed, ids);
            cases.add(parsed);
        }
        return List.copyOf(cases);
    }

    private EvaluationCase parse(JsonNode node) {
        try {
            return mapper.treeToValue(node, EvaluationCase.class);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid case " + node.path("case_id").asText("?") + ": "
                    + e.getMessage(), e);
        }
    }

    private void validate(EvaluationCase c, Set<String> seenIds) {
        String id = c.caseId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A case has no case_id");
        }
        if (!seenIds.add(id)) {
            throw new IllegalArgumentException("Duplicate case_id: " + id);
        }
        if (c.questions().isEmpty()) {
            throw new IllegalArgumentException("Case " + id + " has no questions");
        }
        if (!c.expectsOutOfScope() && (c.callId() == null || c.callId().isBlank())) {
            throw new IllegalArgumentException("Case " + id + " needs call_id (only OUT_OF_SCOPE cases may omit it)");
        }
        if (c.expectedVerdict() != null && !VERDICTS.contains(c.expectedVerdict())) {
            throw new IllegalArgumentException("Case " + id + ": expected_verdict must be one of " + VERDICTS);
        }
        if (c.split() != null && !SPLITS.contains(c.split())) {
            throw new IllegalArgumentException("Case " + id + ": split must be one of " + SPLITS);
        }
    }
}
