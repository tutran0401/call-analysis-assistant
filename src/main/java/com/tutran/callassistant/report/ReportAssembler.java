package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.metrics.CallMetrics;
import com.tutran.callassistant.domain.report.Report;
import com.tutran.callassistant.domain.verdict.RuleVerdictResult;

/**
 * Dựng {@link Report} theo Report Schema v1 từ kết luận và chỉ số đã tính (T8).
 *
 * <p>Là interface vì đây đúng là đường biên mà Sprint 2 sẽ cắm vào: khi đó AI Analysis Engine điền
 * {@code summary}/{@code analysis}/{@code suggestions}, còn phần còn lại vẫn do code điền y như bây
 * giờ. Bố cục report luôn do code quyết định, AI chỉ điền field (mục 3.2).
 */
public interface ReportAssembler {

    Report assemble(String callId, RuleVerdictResult verdict, CallMetrics metrics);
}
