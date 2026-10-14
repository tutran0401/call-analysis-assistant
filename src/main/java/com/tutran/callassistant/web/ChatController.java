package com.tutran.callassistant.web;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tutran.callassistant.application.ChatAnalysisService;
import com.tutran.callassistant.application.ChatRequest;
import com.tutran.callassistant.application.ChatResponse;
import com.tutran.callassistant.application.port.out.AiProvider;
import com.tutran.callassistant.application.validation.UploadedFile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chat API (PROJECT_SPEC.md mục 6.1 T2): nhận câu hỏi + file log (multipart), giao toàn bộ cho
 * {@link ChatAnalysisService}. Controller chỉ làm việc của tầng web - đổi multipart sang model thuần,
 * giới hạn số file - không chứa logic phân tích nào.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    static final int MAX_FILES = 8;
    /** Câu hỏi phân tích cuộc gọi chỉ vài câu; giới hạn để một request không đẩy văn bản tuỳ ý vào parser / log. */
    static final int MAX_MESSAGE_CHARS = 2000;

    private final ChatAnalysisService service;
    private final AiProvider aiProvider;
    private final ElasticsearchClient elasticsearch;

    public ChatController(ChatAnalysisService service, AiProvider aiProvider, ElasticsearchClient elasticsearch) {
        this.service = service;
        this.aiProvider = aiProvider;
        this.elasticsearch = elasticsearch;
    }

    @PostMapping(path = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatResponse chat(@RequestParam(value = "message", required = false, defaultValue = "") String message,
                             @RequestParam(value = "files", required = false) List<MultipartFile> files,
                             @RequestParam(value = "callId", required = false) String callId)
            throws IOException {
        if (message.length() > MAX_MESSAGE_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Câu hỏi quá dài (tối đa " + MAX_MESSAGE_CHARS + " ký tự). Hãy mô tả ngắn gọn yêu cầu phân tích.");
        }
        List<MultipartFile> attached = files == null ? List.of() : files;
        if (attached.size() > MAX_FILES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chỉ nhận tối đa " + MAX_FILES + " file mỗi lần phân tích");
        }
        List<UploadedFile> uploads = new ArrayList<>();
        for (MultipartFile file : attached) {
            uploads.add(new UploadedFile(file.getOriginalFilename(), file.getBytes()));
        }
        return service.handle(new ChatRequest(message, uploads, callId));
    }

    /** Trạng thái các phụ thuộc, để Web UI báo trước khi người dùng gửi (AI chưa cấu hình, ES chưa chạy). */
    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean aiConfigured = !"none".equals(aiProvider.modelName());
        return Map.of(
                "status", "UP",
                "ai", Map.of("model", aiProvider.modelName(), "configured", aiConfigured),
                "elasticsearch", elasticsearchUp() ? "UP" : "DOWN");
    }

    private boolean elasticsearchUp() {
        try {
            return elasticsearch.ping().value();
        } catch (Exception e) {
            return false;
        }
    }
}
