package com.tutran.callassistant.cli;

import org.springframework.stereotype.Component;

import java.io.PrintStream;

/**
 * Chỗ duy nhất trong toàn bộ code ghi ra console.
 *
 * <p>Nhờ vậy tầng application và tầng phân tích không còn một dòng {@code System.out} nào - chúng
 * trả về dữ liệu, còn việc in là của adapter. Cũng nhờ vậy mà test có thể bắt output bằng cách đưa
 * vào một {@link PrintStream} khác.
 */
@Component
public class ConsoleWriter {

    private final PrintStream out;
    private final PrintStream err;

    public ConsoleWriter() {
        this(System.out, System.err);
    }

    public ConsoleWriter(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public void line(String text) {
        out.println(text);
    }

    public void blankLine() {
        out.println();
    }

    public void separator(int width) {
        out.println("=".repeat(width));
    }

    public void error(String text) {
        err.println(text);
    }
}
