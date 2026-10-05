package com.tutran.callassistant.web;

import com.tutran.callassistant.testsupport.SampleCalls;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test trên Spring context thật (profile {@code web}, AI giả lập): chứng minh toàn bộ
 * wiring - controller, service, sanitizer, guardrail, validator, schema - chạy qua HTTP multipart. Không
 * cần Elasticsearch: nguồn signaling tự fallback khi ES không có.
 */
@SpringBootTest(properties = "call-analysis.ai.provider=fake")
@AutoConfigureMockMvc
@ActiveProfiles("web")
class ChatControllerIT {

    @Autowired
    private MockMvc mvc;

    private MockMultipartFile file(String name, Path path) throws Exception {
        return new MockMultipartFile("files", name, "text/plain", Files.readAllBytes(path));
    }

    @Test
    void chatReturnsAValidReportForAttachedLogs() throws Exception {
        Path dir = SampleCalls.success(SampleCalls.SUCCESS_FULL_LOGS);

        mvc.perform(multipart("/api/chat")
                        .file(file("caller_endcall.log", dir.resolve("caller_endcall.log")))
                        .file(file("callee_endcall.log", dir.resolve("callee_endcall.log")))
                        .file(file("caller_webrtc.log", dir.resolve("caller_webrtc.log")))
                        .file(file("callee_webrtc.log", dir.resolve("callee_webrtc.log")))
                        .param("message", "Phân tích giúp mình cuộc gọi này"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.report.callId").value(SampleCalls.SUCCESS_FULL_LOGS))
                .andExpect(jsonPath("$.report.verdict").exists())
                .andExpect(jsonPath("$.report.evidence").isArray())
                .andExpect(jsonPath("$.renderedText").value(org.hamcrest.Matchers.containsString("# Báo cáo")));
    }

    @Test
    void declaredCallIdIsUsedWhenTheFilesCarryNone() throws Exception {
        Path dir = SampleCalls.fail(SampleCalls.FAIL_CANCELLED_NO_CLIENT_LOGS);

        mvc.perform(multipart("/api/chat")
                        .file(file("caller_webrtc.log", dir.resolve("caller_webrtc.log")))
                        .param("message", "Phân tích giúp mình")
                        .param("callId", SampleCalls.FAIL_CANCELLED_NO_CLIENT_LOGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.callId").value(SampleCalls.FAIL_CANCELLED_NO_CLIENT_LOGS));
    }

    @Test
    void outOfScopeQuestionGetsTheFixedRefusal() throws Exception {
        mvc.perform(multipart("/api/chat").param("message", "Viết giúp mình một email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.report").doesNotExist())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Mình chỉ hỗ trợ")));
    }

    @Test
    void garbageFileDoesNotFailTheRequest() throws Exception {
        mvc.perform(multipart("/api/chat")
                        .file(new MockMultipartFile("files", "x_endcall.log", "text/plain", new byte[]{0, 1, 2}))
                        .param("message", "Phân tích cuộc gọi này"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.verdict").value("UNKNOWN"))
                .andExpect(jsonPath("$.report.dataLimitations[?(@ =~ /.*x_endcall.log.*/)]").exists());
    }

    @Test
    void tooManyFilesIsAFriendly400() throws Exception {
        var request = multipart("/api/chat");
        for (int i = 0; i < 9; i++) {
            request.file(new MockMultipartFile("files", "f" + i + ".log", "text/plain",
                    "x".getBytes(StandardCharsets.UTF_8)));
        }

        mvc.perform(request.param("message", "Phân tích"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("tối đa")));
    }

    @Test
    void healthReportsDependencies() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.ai.configured").value(true))
                .andExpect(jsonPath("$.elasticsearch").exists());
    }
}
