package com.tutran.callassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class CallAssistantApplication {
    public static void main(String[] args) {
        // Report được render bằng tiếng Việt; encoding mặc định của stdout/stderr trên
        // Windows là codepage của hệ điều hành, không phải UTF-8, nên dấu tiếng Việt bị lỗi font.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        SpringApplication.run(CallAssistantApplication.class, args);
    }
}
