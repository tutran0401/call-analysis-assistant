package com.tutran.callassistant.application.guardrail;

import com.tutran.callassistant.domain.report.MetricRow;
import com.tutran.callassistant.domain.report.Report;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** AI cites a metric; code supplies its complete label, value and unit, without rounding. */
public final class MetricReferences {
    private static final Pattern REFERENCE = Pattern.compile("\\{\\{M([0-9]+)}}");
    private static final Pattern NUMBER = Pattern.compile("\\p{N}+(?:[.,]\\p{N}+)?");
    private static final Pattern EVIDENCE = Pattern.compile("\\bEV[0-9]+\\b");

    private MetricReferences() { }

    public static String token(int index) {
        return "{{M" + String.format(java.util.Locale.ROOT, "%03d", index + 1) + "}}";
    }

    public static String render(MetricRow metric) {
        return "⟦" + metric.name() + ": " + metric.value() + "⟧";
    }

    /** Unknown/malformed references and all bare numeric claims fail closed. */
    public static List<String> inspect(String text, Report report) {
        List<String> problems = new ArrayList<>();
        String remainder = text;
        for (int i = 0; i < report.metrics().size(); i++) {
            remainder = remainder.replace(token(i), " ");
        }
        if (remainder.contains("{{") || remainder.contains("}}")) {
            problems.add("tham chiếu chỉ số không hợp lệ");
        }
        // Rendered blocks are reserved for code, never trusted as AI input.
        if (remainder.contains("⟦") || remainder.contains("⟧")) {
            problems.add("AI không được tự dựng giá trị chỉ số");
        }
        Matcher numbers = NUMBER.matcher(EVIDENCE.matcher(remainder).replaceAll(" "));
        while (numbers.find()) problems.add(numbers.group());
        return problems;
    }

    public static String expand(String text, Report report) {
        Matcher matcher = REFERENCE.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1;
            if (index < 0 || index >= report.metrics().size() || !matcher.group().equals(token(index))) {
                throw new IllegalArgumentException("Unknown metric reference");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(render(report.metrics().get(index))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Audit delivered text against complete metric bindings, not a bag of allowed numbers. */
    public static List<String> inspectDelivered(String text, Report report) {
        String remainder = text;
        for (MetricRow metric : report.metrics()) {
            remainder = remainder.replace(render(metric), " ");
        }
        // References must have been resolved before delivery.
        if (remainder.contains("{{") || remainder.contains("}}")) {
            return List.of("tham chiếu chỉ số chưa được render");
        }
        return inspect(remainder, report);
    }
}
