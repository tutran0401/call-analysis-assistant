package com.tutran.callassistant.application.port.out;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.domain.event.NormalizedEvents;

/**
 * Nguồn signaling log (góc nhìn server) của một cuộc gọi.
 *
 * <p>Có nhiều implementation cùng tồn tại: query Elasticsearch, đọc file export local, và
 * một decorator ghép hai cái đó lại thành "thử ES trước, không có thì dùng file". Tầng
 * phân tích chỉ biết interface này, nên việc đổi/ghép nguồn không ảnh hưởng tới nó
 * (Dependency Inversion).
 */
public interface SignalingEventSource {

    NormalizedEvents fetch(CallReference call);
}
