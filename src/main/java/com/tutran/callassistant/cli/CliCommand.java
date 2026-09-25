package com.tutran.callassistant.cli;

import java.util.List;

/**
 * Một câu lệnh của CLI (Command pattern).
 *
 * <p>Trước đây {@code CallAnalysisCli} là một class làm tất cả: phân tích tham số, chọn lệnh bằng
 * {@code switch}, điều phối cả pipeline, quét thư mục, và in kết quả. Giờ mỗi lệnh là một class tự
 * biết tên mình, cách dùng mình, và cách chạy mình - thêm một lệnh mới không phải sửa
 * {@link CommandLineDispatcher}.
 */
public interface CliCommand {

    /** Tên lệnh người dùng gõ (ví dụ {@code analyze}). */
    String name();

    /** Dòng hướng dẫn dùng, hiển thị trong phần usage. */
    String usage();

    /** Chạy lệnh với các tham số đứng sau tên lệnh. */
    void execute(List<String> arguments);
}
