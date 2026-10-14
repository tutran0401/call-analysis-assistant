package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.application.ChatResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Thống kê phân bố cho lệnh {@code batch} (Sprint 3 T10): verdict, issue category, tỉ lệ UNKNOWN, tỉ lệ fallback,
 * latency. Không có nhãn nên đây là <i>mô tả</i> tập dữ liệu, không phải đo độ chính xác.
 */
public final class BatchStatistics {

    private final Map<String, Integer> verdicts = new TreeMap<>();
    private final Map<String, Integer> categories = new TreeMap<>();
    private final Map<String, Integer> confidences = new TreeMap<>();
    private final List<Long> latencies = new ArrayList<>();
    private int analysed;
    private int degraded;
    private int errors;
    private int skipped;

    public void add(ChatResponse response, long latencyMs) {
        if (response.report() == null) {
            errors++;
            return;
        }
        analysed++;
        verdicts.merge(response.report().verdict(), 1, Integer::sum);
        categories.merge(response.report().issueCategory(), 1, Integer::sum);
        confidences.merge(response.report().confidenceLevel(), 1, Integer::sum);
        degraded += response.degraded() ? 1 : 0;
        latencies.add(latencyMs);
    }

    public void addError() {
        errors++;
    }

    /** Call-ID có trong ES nhưng không tìm thấy log client nào dưới thư mục data. */
    public void addSkipped() {
        skipped++;
    }

    public int analysed() {
        return analysed;
    }

    public Map<String, Integer> verdicts() {
        return verdicts;
    }

    public Map<String, Integer> categories() {
        return categories;
    }

    public Ratio unknownRate() {
        return new Ratio(verdicts.getOrDefault("UNKNOWN", 0), analysed);
    }

    public Ratio fallbackRate() {
        return new Ratio(degraded, analysed);
    }

    public String toMarkdown(String title, String model) {
        StringBuilder md = new StringBuilder("# ").append(title).append("\n\n");
        md.append("- Model: `").append(model).append("`\n");
        md.append("- Đã phân tích: ").append(analysed).append(", lỗi: ").append(errors)
                .append(", bỏ qua (không có log client): ").append(skipped).append("\n");
        md.append("- Tỉ lệ UNKNOWN: ").append(fmt(unknownRate())).append("\n");
        md.append("- Tỉ lệ fallback (degraded): ").append(fmt(fallbackRate())).append("\n");
        md.append("- Latency P50 / P95: ").append(percentile(50)).append(" ms / ").append(percentile(95))
                .append(" ms\n\n");
        table(md, "Verdict", verdicts);
        table(md, "Issue category", categories);
        table(md, "Độ tin cậy", confidences);
        return md.toString();
    }

    private void table(StringBuilder md, String name, Map<String, Integer> counts) {
        md.append("| ").append(name).append(" | Số cuộc gọi | Tỉ lệ |\n| --- | --- | --- |\n");
        counts.forEach((key, count) -> md.append("| ").append(key).append(" | ").append(count).append(" | ")
                .append(fmt(new Ratio(count, analysed))).append(" |\n"));
        md.append('\n');
    }

    private static String fmt(Ratio ratio) {
        return ratio.measured()
                ? String.format(java.util.Locale.ROOT, "%.1f%% (%d/%d)", ratio.percent(), ratio.hit(), ratio.total())
                : "N/A (chưa có cuộc gọi nào)";
    }

    long percentile(int p) {
        if (latencies.isEmpty()) {
            return 0;
        }
        List<Long> sorted = latencies.stream().sorted().toList();
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
