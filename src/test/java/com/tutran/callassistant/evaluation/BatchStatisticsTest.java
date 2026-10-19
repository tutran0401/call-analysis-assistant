package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.application.ChatResponse;
import com.tutran.callassistant.application.request.Intent;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.testsupport.TestReports;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BatchStatisticsTest {

    private static ChatResponse response(String verdict, String category, boolean degraded) {
        Report base = TestReports.successWithPacketLoss();
        Report report = new Report(base.callId(), verdict, false, category, List.of(), "MEDIUM", "s", List.of(),
                List.of(), List.of(), List.of());
        return new ChatResponse("req-1", Intent.ANALYZE_CALL, Set.of(), report, null, degraded, null, List.of(), "", null, List.of());
    }

    @Test
    void countsDistributionsAndRates() {
        BatchStatistics stats = new BatchStatistics();
        stats.add(response("SUCCESS", "NONE", false), 100);
        stats.add(response("FAIL", "ICE_FAILURE", true), 300);
        stats.add(response("UNKNOWN", "UNKNOWN", true), 200);
        stats.addSkipped();
        stats.addError();

        assertThat(stats.analysed()).isEqualTo(3);
        assertThat(stats.verdicts()).containsEntry("SUCCESS", 1).containsEntry("FAIL", 1).containsEntry("UNKNOWN", 1);
        assertThat(stats.unknownRate()).isEqualTo(new Ratio(1, 3));
        assertThat(stats.fallbackRate()).isEqualTo(new Ratio(2, 3));
        assertThat(stats.percentile(50)).isEqualTo(200);
        assertThat(stats.percentile(95)).isEqualTo(300);
        assertThat(stats.toMarkdown("Batch", "rule")).contains("| ICE_FAILURE | 1 | 33.3% (1/3) |")
                .contains("bỏ qua (không có log client): 1").contains("lỗi: 1");
    }

    @Test
    void emptyBatchReportsNotApplicableRatherThanZero() {
        assertThat(new BatchStatistics().toMarkdown("Batch", "rule")).contains("Tỉ lệ UNKNOWN: N/A");
    }
}
