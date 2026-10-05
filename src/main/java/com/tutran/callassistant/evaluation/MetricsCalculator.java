package com.tutran.callassistant.evaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tính metric ở mục 6.5 từ các {@link RunRecord} - thuần logic, không I/O, nên kiểm được bằng số tính tay.
 * Mọi metric chỉ tính trên phần dữ liệu có nhãn; thiếu nhãn thì mẫu số nhỏ đi (hoặc bằng 0 = chưa đo).
 */
public final class MetricsCalculator {

    /** Cách hỏi được dùng đo Consistency: {@value #CONSISTENCY_QUESTIONS} cách đầu tiên của mỗi case. */
    public static final int CONSISTENCY_QUESTIONS = 3;

    /** Thứ tự các mục bắt buộc của report theo mẫu mục 4.5. */
    static final List<String> TEMPLATE_ORDER = List.of(
            "# Báo cáo phân tích cuộc gọi", "Call-ID:", "Kết luận:", "Cờ chất lượng:", "Độ tin cậy:", "Tóm tắt:",
            "## Evidence chính", "## Chỉ số cuộc gọi", "## Vấn đề chất lượng / nguyên nhân khả dĩ", "## Đề xuất",
            "## Giới hạn dữ liệu");

    private static final Set<String> UNSUPPORTED_CODES = Set.of("G01", "G05");

    public EvaluationMetrics compute(List<EvaluationCase> cases, List<RunRecord> records,
                                     Map<String, RuleBaseline> rules, int logLeakage) {
        Map<String, EvaluationCase> byId = new HashMap<>();
        cases.forEach(c -> byId.put(c.caseId(), c));
        List<RunRecord> analysed = records.stream().filter(r -> !byId.get(r.caseId()).expectsOutOfScope()).toList();
        List<RunRecord> okAnalysed = analysed.stream().filter(RunRecord::pipelineOk).toList();

        return new EvaluationMetrics(
                verdictAccuracy(okAnalysed, byId),
                ruleVerdictAccuracy(cases, rules),
                issueCategoryAccuracy(okAnalysed, byId),
                qualityFlagAccuracy(okAnalysed, byId),
                intentAccuracy(records, byId),
                consistencyCases(okAnalysed),
                consistencyRuns(okAnalysed),
                metricCorrectness(okAnalysed, rules),
                templateCompliance(okAnalysed),
                unsupportedClaims(okAnalysed),
                new Ratio((int) records.stream().filter(RunRecord::pipelineOk).count(), records.size()),
                new Ratio((int) analysed.stream().filter(RunRecord::degraded).count(), analysed.size()),
                records.stream().mapToInt(r -> r.inputProbeHits() + r.inputResidual()).sum(),
                records.stream().mapToInt(r -> r.outputProbeHits() + r.outputResidual()).sum(),
                logLeakage,
                cases.stream().mapToInt(c -> c.sensitiveValues().size()).sum(),
                percentile(records, 50),
                percentile(records, 95));
    }

    private Ratio verdictAccuracy(List<RunRecord> records, Map<String, EvaluationCase> byId) {
        int total = 0;
        int hit = 0;
        for (RunRecord r : records) {
            String expected = byId.get(r.caseId()).expectedVerdict();
            if (expected != null) {
                total++;
                hit += expected.equals(r.verdict()) ? 1 : 0;
            }
        }
        return new Ratio(hit, total);
    }

    private Ratio ruleVerdictAccuracy(List<EvaluationCase> cases, Map<String, RuleBaseline> rules) {
        int total = 0;
        int hit = 0;
        for (EvaluationCase c : cases) {
            RuleBaseline rule = rules.get(c.caseId());
            if (c.expectedVerdict() != null && rule != null) {
                total++;
                hit += c.expectedVerdict().equals(rule.verdict()) ? 1 : 0;
            }
        }
        return new Ratio(hit, total);
    }

    /** Theo spec chỉ tính case {@code FAIL} và {@code SUCCESS} có cờ chất lượng, và phải có nhãn category. */
    private Ratio issueCategoryAccuracy(List<RunRecord> records, Map<String, EvaluationCase> byId) {
        int total = 0;
        int hit = 0;
        for (RunRecord r : records) {
            EvaluationCase c = byId.get(r.caseId());
            boolean relevant = "FAIL".equals(c.expectedVerdict())
                    || ("SUCCESS".equals(c.expectedVerdict()) && Boolean.TRUE.equals(c.expectedQualityFlag()));
            if (relevant && c.expectedIssueCategory() != null) {
                total++;
                hit += c.expectedIssueCategory().equals(r.issueCategory()) ? 1 : 0;
            }
        }
        return new Ratio(hit, total);
    }

    private Ratio qualityFlagAccuracy(List<RunRecord> records, Map<String, EvaluationCase> byId) {
        int total = 0;
        int hit = 0;
        for (RunRecord r : records) {
            Boolean expected = byId.get(r.caseId()).expectedQualityFlag();
            if (expected != null) {
                total++;
                hit += expected == r.qualityFlag() ? 1 : 0;
            }
        }
        return new Ratio(hit, total);
    }

    /** Chỉ tính request đã chạy xong: request lỗi pipeline không có intent để chấm (nó tính ở Pipeline Success). */
    private Ratio intentAccuracy(List<RunRecord> records, Map<String, EvaluationCase> byId) {
        int total = 0;
        int hit = 0;
        for (RunRecord r : records) {
            if (!r.pipelineOk()) {
                continue;
            }
            total++;
            boolean expectOutOfScope = byId.get(r.caseId()).expectsOutOfScope();
            boolean gotOutOfScope = "OUT_OF_SCOPE".equals(r.intent());
            hit += expectOutOfScope == gotOutOfScope ? 1 : 0;
        }
        return new Ratio(hit, total);
    }

    private Map<String, List<RunRecord>> consistencyGroups(List<RunRecord> records) {
        Map<String, List<RunRecord>> groups = new LinkedHashMap<>();
        for (RunRecord r : records) {
            if (r.questionIndex() < CONSISTENCY_QUESTIONS) {
                groups.computeIfAbsent(r.caseId(), k -> new ArrayList<>()).add(r);
            }
        }
        return groups;
    }

    private static String outcomeOf(RunRecord r) {
        return r.verdict() + "|" + r.issueCategory();
    }

    private Ratio consistencyCases(List<RunRecord> records) {
        Map<String, List<RunRecord>> groups = consistencyGroups(records);
        int consistent = 0;
        for (List<RunRecord> group : groups.values()) {
            consistent += group.stream().map(MetricsCalculator::outcomeOf).distinct().count() == 1 ? 1 : 0;
        }
        return new Ratio(consistent, groups.size());
    }

    private Ratio consistencyRuns(List<RunRecord> records) {
        int total = 0;
        int agree = 0;
        for (List<RunRecord> group : consistencyGroups(records).values()) {
            Map<String, Integer> counts = new HashMap<>();
            group.forEach(r -> counts.merge(outcomeOf(r), 1, Integer::sum));
            total += group.size();
            agree += counts.values().stream().max(Integer::compare).orElse(0);
        }
        return new Ratio(agree, total);
    }

    private Ratio metricCorrectness(List<RunRecord> records, Map<String, RuleBaseline> rules) {
        int total = 0;
        int hit = 0;
        for (RunRecord r : records) {
            RuleBaseline rule = rules.get(r.caseId());
            if (rule != null && r.report() != null) {
                total++;
                hit += rule.metrics().equals(r.report().metrics()) ? 1 : 0;
            }
        }
        return new Ratio(hit, total);
    }

    private Ratio templateCompliance(List<RunRecord> records) {
        int total = 0;
        int hit = 0;
        for (RunRecord r : records) {
            if (r.renderedText() != null && !r.renderedText().isEmpty()) {
                total++;
                hit += followsTemplate(r.renderedText()) ? 1 : 0;
            }
        }
        return new Ratio(hit, total);
    }

    static boolean followsTemplate(String text) {
        int from = 0;
        for (String marker : TEMPLATE_ORDER) {
            int index = text.indexOf(marker, from);
            if (index < 0) {
                return false;
            }
            from = index + marker.length();
        }
        return true;
    }

    /** Mẫu số = request mà phản hồi AI thực sự tới được Guardrails; tử số = bị bắt vì G01 / G05. */
    private Ratio unsupportedClaims(List<RunRecord> records) {
        int reachedGuard = 0;
        int unsupported = 0;
        for (RunRecord r : records) {
            boolean reached = r.aiCalled() && (r.fallbackReason() == null
                    || "GUARDRAIL_REJECTED".equals(r.fallbackReason())
                    || "OUTPUT_SCHEMA_INVALID".equals(r.fallbackReason()));
            if (!reached) {
                continue;
            }
            reachedGuard++;
            unsupported += r.warnings().stream().anyMatch(w -> UNSUPPORTED_CODES.contains(codeOf(w))) ? 1 : 0;
        }
        return new Ratio(unsupported, reachedGuard);
    }

    private static String codeOf(String warning) {
        return warning.length() >= 3 ? warning.substring(0, 3) : warning;
    }

    private Long percentile(List<RunRecord> records, int percent) {
        if (records.isEmpty()) {
            return null;
        }
        List<Long> sorted = records.stream().map(RunRecord::latencyMillis).sorted(Comparator.naturalOrder()).toList();
        int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }
}
