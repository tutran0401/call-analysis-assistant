package com.tutran.callassistant.application.request;

/** Ba intent của PROJECT_SPEC.md mục 4.4. */
public enum Intent {
    /** "Phân tích cuộc gọi này giúp mình" - report đầy đủ. */
    ANALYZE_CALL,
    /** "Vì sao bên nhận không nghe được?" - report đầy đủ + ưu tiên khía cạnh được hỏi. */
    ANALYZE_WITH_FOCUS,
    /** Ngoài phạm vi phân tích cuộc gọi - từ chối theo mẫu cố định. */
    OUT_OF_SCOPE
}
