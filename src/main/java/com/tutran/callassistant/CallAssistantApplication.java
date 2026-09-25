package com.tutran.callassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Điểm khởi động ứng dụng.
 *
 * <p>Kiến trúc được chia thành các tầng phụ thuộc một chiều từ ngoài vào trong:
 *
 * <pre>
 *   cli, ingest, report  (adapter: biết về file, Elasticsearch, console)
 *            |
 *            v
 *   application          (use case + port: chỉ biết interface, không biết công nghệ)
 *            |
 *            v
 *   analysis             (timeline, metrics, rule - logic nghiệp vụ)
 *            |
 *            v
 *   domain               (model thuần: không Spring, không IO, không framework)
 * </pre>
 *
 * <p>Muốn hiểu hệ thống làm gì, đọc {@code application.CallAnalysisPipeline} - tám dòng, mỗi dòng là
 * một thành phần trong kiến trúc mục tiêu ở PROJECT_SPEC.md mục 3.1.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CallAssistantApplication {

    public static void main(String[] args) {
        // Report được render bằng tiếng Việt; encoding mặc định của stdout/stderr trên Windows là
        // codepage của hệ điều hành, không phải UTF-8, nên dấu tiếng Việt bị lỗi font.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        SpringApplication.run(CallAssistantApplication.class, args);
    }
}
