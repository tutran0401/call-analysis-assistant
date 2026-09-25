package com.tutran.callassistant.application.port.in;

import com.tutran.callassistant.application.AnalysisOutcome;
import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.SignalingSourcePreference;

/**
 * Use case chính của sản phẩm: cho log của một cuộc gọi, trả về report chuẩn.
 *
 * <p>Đây là cửa vào duy nhất của tầng phân tích. Sprint 1 có một adapter gọi nó (CLI);
 * Sprint 2 thêm Chat API/Web UI gọi đúng interface này chứ không gọi xuyên vào bên trong.
 */
public interface AnalyzeCallUseCase {

    AnalysisOutcome analyze(CallReference call, SignalingSourcePreference signalingSource);
}
