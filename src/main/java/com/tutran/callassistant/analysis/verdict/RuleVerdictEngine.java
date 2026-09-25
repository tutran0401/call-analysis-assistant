package com.tutran.callassistant.analysis.verdict;

import com.tutran.callassistant.analysis.verdict.quality.QualityInspector;
import com.tutran.callassistant.analysis.verdict.rule.ConnectedSuccessfullyRule;
import com.tutran.callassistant.analysis.verdict.rule.ConnectedWithoutByeRule;
import com.tutran.callassistant.analysis.verdict.rule.FailedToConnectRule;
import com.tutran.callassistant.analysis.verdict.rule.MissingClientLogsRule;
import com.tutran.callassistant.analysis.verdict.rule.MissingSignalingDataRule;
import com.tutran.callassistant.analysis.verdict.rule.VerdictRule;
import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.timeline.CallTimeline;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Baseline rule xác định (deterministic) cho verdict + issue category (mục 4.1/4.2), chỉ dựng
 * từ timeline và chỉ số mà code đã tính với độ chắc chắn 100% - không có AI tham gia (mục 3.2:
 * đây là tín hiệu rule mà verdict của AI sẽ được đối chiếu vào ở Sprint 2).
 *
 * <p>Bản thân class này <b>không chứa logic quyết định nào</b>. Nó chỉ làm ba việc: chạy mọi
 * {@link DataLimitationDetector} để ghi nhận dữ liệu thiếu, rồi chạy chuỗi
 * {@link VerdictRule} theo thứ tự và lấy kết luận đầu tiên khớp. Toàn bộ thứ tự quyết định
 * (trước đây là một chuỗi if/else dài trong một method) giờ đọc được ngay ở danh sách rule:
 *
 * <ol>
 *   <li>{@link MissingSignalingDataRule} - không có signaling thì UNKNOWN</li>
 *   <li>{@link MissingClientLogsRule} - thiếu end-call log cả hai bên thì UNKNOWN</li>
 *   <li>{@link FailedToConnectRule} - chưa từng CONFIRMED thì FAIL</li>
 *   <li>{@link ConnectedWithoutByeRule} - đã kết nối mà không thấy kết thúc thì UNKNOWN</li>
 *   <li>{@link ConnectedSuccessfullyRule} - kết nối và kết thúc bình thường thì SUCCESS</li>
 * </ol>
 */
@Component
public class RuleVerdictEngine implements VerdictEngine {

    private final List<DataLimitationDetector> dataLimitationDetectors;
    private final List<VerdictRule> rules;

    public RuleVerdictEngine(List<DataLimitationDetector> dataLimitationDetectors, List<VerdictRule> rules) {
        this.dataLimitationDetectors = List.copyOf(dataLimitationDetectors);
        this.rules = List.copyOf(rules);
    }

    /** Engine với đầy đủ detector/rule theo đúng thứ tự, dùng cho test và chỗ không có Spring container. */
    public static RuleVerdictEngine withDefaults() {
        QualityThresholds thresholds = QualityThresholds.defaults();
        return new RuleVerdictEngine(
                List.of(new MissingEndCallLogDetector()),
                List.of(new MissingSignalingDataRule(),
                        new MissingClientLogsRule(),
                        new FailedToConnectRule(new IceFailureDetector()),
                        new ConnectedWithoutByeRule(),
                        new ConnectedSuccessfullyRule(QualityInspector.withDefaults(thresholds))));
    }

    @Override
    public RuleVerdictResult evaluate(CallTimeline timeline, CallMetrics metrics) {
        VerdictContext context = new VerdictContext(timeline, metrics);
        dataLimitationDetectors.forEach(detector -> detector.detect(context));

        for (VerdictRule rule : rules) {
            Optional<RuleVerdictResult> result = rule.apply(context);
            if (result.isPresent()) {
                return result.get();
            }
        }
        // Rule cuối chuỗi luôn khớp, nên tới đây là chuỗi rule đã bị cấu hình sai chứ không
        // phải dữ liệu vào có vấn đề.
        throw new IllegalStateException("No verdict rule matched call " + timeline.callId()
                + "; the rule chain must end with a rule that always applies");
    }
}
