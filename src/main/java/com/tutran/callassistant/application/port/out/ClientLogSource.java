package com.tutran.callassistant.application.port.out;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.domain.event.NormalizedEvents;

/**
 * Nguồn client log (end-call log + WebRTC log) của một cuộc gọi - những file người dùng
 * đính kèm. Sprint 1 đọc từ thư mục trên đĩa; Sprint 2 sẽ là file upload từ Web UI, nhưng
 * tầng phân tích không cần biết khác biệt đó.
 */
public interface ClientLogSource {

    NormalizedEvents load(CallReference call);
}
