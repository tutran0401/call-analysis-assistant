package com.tutran.callassistant.sanitize;

/** Hành động chính sách áp cho một loại dữ liệu nhạy cảm. */
public enum SanitizeAction {
    /** Giữ nguyên. */
    ALLOW,
    /** Thay bằng placeholder cố định, mất hẳn giá trị. */
    MASK,
    /** Thay bằng định danh giả ổn định (cùng giá trị gốc → cùng giá trị giả) để vẫn correlate được. */
    PSEUDONYMIZE,
    /** Bỏ hẳn giá trị. */
    DROP,
    /** Giữ lại phần tối thiểu cần thiết, bỏ chi tiết hạ tầng. */
    MINIMIZE
}
