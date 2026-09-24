package com.tutran.callassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class CallAssistantApplication {
    public static void main(String[] args) {
        // Reports are rendered in Vietnamese; the JVM's default stdout/stderr encoding on
        // Windows is the platform codepage, not UTF-8, which garbles diacritics.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        SpringApplication.run(CallAssistantApplication.class, args);
    }
}
