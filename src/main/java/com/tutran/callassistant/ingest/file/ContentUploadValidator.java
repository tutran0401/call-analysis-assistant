package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.application.port.out.UploadValidator;
import com.tutran.callassistant.application.validation.RejectedFile;
import com.tutran.callassistant.application.validation.UploadedFile;
import com.tutran.callassistant.application.validation.ValidatedUpload;
import com.tutran.callassistant.domain.event.Leg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * File Validator (T3): nhận diện loại file theo <b>nội dung</b>, loại file hỏng / rỗng / quá lớn /
 * thuộc Call-ID khác, và báo file thiếu thành giới hạn dữ liệu.
 *
 * <p>Quy ước về Call-ID: nếu người dùng ghi Call-ID trong câu hỏi thì đó là Call-ID mong đợi; nếu
 * không, lấy Call-ID xuất hiện nhiều nhất trong nội dung các file (hoà thì lấy file đầu tiên). File
 * nào mang Call-ID khác bị loại - trộn log của hai cuộc gọi sẽ cho kết luận sai mà nhìn vẫn hợp lý.
 * WebRTC không chứa Call-ID: chỉ nhận khi upload không có bằng chứng trộn nhiều cuộc gọi.
 *
 * <p>Thiếu end-call log <i>không</i> báo ở đây: {@code MissingEndCallLogDetector} đã báo ở tầng
 * verdict, báo lại sẽ lặp trong report. Chỉ webrtc log thiếu mới chưa có ai báo.
 */
@Component
public class ContentUploadValidator implements UploadValidator {

    private static final int BINARY_SCAN_BYTES = 8192;
    private static final String SIGNALING_IGNORED =
            "signaling được lấy từ Elasticsearch theo Call-ID, không đọc từ file đính kèm";

    private final LogFileTypeDetector typeDetector;
    private final long maxFileBytes;

    public ContentUploadValidator(LogFileTypeDetector typeDetector,
                                  @Value("${call-analysis.upload.max-file-bytes:10485760}") long maxFileBytes) {
        this.typeDetector = typeDetector;
        this.maxFileBytes = maxFileBytes;
    }

    @Override
    public ValidatedUpload validate(List<UploadedFile> files, String requestedCallId) {
        UploadWorkspace workspace = UploadWorkspace.create();
        try {
            return doValidate(files, requestedCallId, workspace);
        } catch (RuntimeException e) {
            workspace.close();
            throw e;
        }
    }

    private ValidatedUpload doValidate(List<UploadedFile> files, String requestedCallId, UploadWorkspace workspace) {
        List<RejectedFile> rejected = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (UploadedFile file : files) {
            screen(file, workspace, rejected).ifPresent(candidates::add);
        }

        String expected = chooseCallId(requestedCallId, candidates);
        boolean mixedCalls = expected != null && candidates.stream().anyMatch(c ->
                c.callId != null && !c.callId.equalsIgnoreCase(expected));
        List<Candidate> accepted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.type == LogFileType.SIGNALING_EXPORT) {
                reject(candidate, SIGNALING_IGNORED, rejected);
            } else if (expected != null && candidate.callId != null && !candidate.callId.equalsIgnoreCase(expected)) {
                reject(candidate, "thuộc Call-ID " + candidate.callId + ", khác Call-ID " + expected
                        + " của cuộc gọi đang phân tích", rejected);
            } else if (mixedCalls && candidate.callId == null) {
                reject(candidate, "không xác minh được Call-ID khi lượt tải lên có log của cuộc gọi khác; "
                        + "hãy tải lại log của đúng một cuộc gọi", rejected);
            } else {
                accepted.add(candidate);
            }
        }

        List<String> limitations = new ArrayList<>();
        rejected.forEach(r -> limitations.add(r.asLimitation()));
        noteNameMismatches(accepted, limitations);
        noteMissingWebRtc(accepted, limitations);

        String callId = expected != null ? expected : ValidatedUpload.UNKNOWN_CALL_ID;
        if (expected == null) {
            limitations.add("Không xác định được Call-ID từ file đính kèm hoặc câu hỏi");
        }
        List<String> acceptedNames = accepted.stream().map(c -> c.storedName).toList();
        return new ValidatedUpload(callId, workspace.directory(), acceptedNames, List.copyOf(rejected),
                List.copyOf(limitations), workspace::close);
    }

    /** Kiểm tra từng file độc lập; trả {@code empty} (kèm ghi lý do) nếu file không dùng được. */
    private Optional<Candidate> screen(UploadedFile file, UploadWorkspace workspace, List<RejectedFile> rejected) {
        String name = displayName(file.name());
        byte[] bytes = file.content();
        if (bytes.length == 0 || isBlank(bytes)) {
            rejected.add(new RejectedFile(name, "file rỗng"));
            return Optional.empty();
        }
        if (bytes.length > maxFileBytes) {
            rejected.add(new RejectedFile(name, "vượt giới hạn kích thước " + (maxFileBytes / (1024 * 1024)) + " MB"));
            return Optional.empty();
        }
        Optional<String> text = decodeText(bytes);
        if (text.isEmpty()) {
            rejected.add(new RejectedFile(name, "file hỏng hoặc không phải văn bản UTF-8"));
            return Optional.empty();
        }
        Path stored = workspace.store(file.name(), bytes);
        LogFileType type = typeDetector.detect(stored);
        if (type == LogFileType.UNKNOWN) {
            deleteQuietly(stored);
            rejected.add(new RejectedFile(name, "không nhận diện được loại log theo nội dung"));
            return Optional.empty();
        }
        String callId = CallIdExtractor.extract(type, text.get()).orElse(null);
        return Optional.of(new Candidate(name, stored, type, callId));
    }

    private String chooseCallId(String requested, List<Candidate> candidates) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> spelling = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate.callId != null) {
                String key = candidate.callId.toLowerCase(Locale.ROOT);
                counts.merge(key, 1, Integer::sum);
                spelling.putIfAbsent(key, candidate.callId);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> spelling.get(entry.getKey()))
                .orElse(null);
    }

    private void noteNameMismatches(List<Candidate> accepted, List<String> limitations) {
        for (Candidate candidate : accepted) {
            LogFileType byName = typeFromName(candidate.displayName);
            if (byName != null && byName != candidate.type) {
                limitations.add("Tên file " + candidate.displayName + " gợi ý " + label(byName)
                        + " nhưng nội dung là " + label(candidate.type) + "; đã phân tích theo nội dung");
            }
        }
    }

    private void noteMissingWebRtc(List<Candidate> accepted, List<String> limitations) {
        if (accepted.isEmpty()) {
            limitations.add("Không có file log hợp lệ nào để phân tích");
            return;
        }
        Set<Leg> present = EnumSet.noneOf(Leg.class);
        for (Candidate candidate : accepted) {
            if (candidate.type != LogFileType.WEBRTC) {
                continue;
            }
            Leg leg = LegNaming.fromText(candidate.displayName);
            if (leg == Leg.UNKNOWN) {
                limitations.add("Không xác định được bên (caller/callee) của " + candidate.displayName
                        + " qua tên file; không thể kết luận file webrtc nào còn thiếu");
                return;
            }
            present.add(leg);
        }
        for (Leg leg : List.of(Leg.CALLER, Leg.CALLEE)) {
            if (!present.contains(leg)) {
                limitations.add("Thiếu " + leg.name().toLowerCase(Locale.ROOT) + "_webrtc.log");
            }
        }
    }

    private void reject(Candidate candidate, String reason, List<RejectedFile> rejected) {
        deleteQuietly(candidate.path);
        rejected.add(new RejectedFile(candidate.displayName, reason));
    }

    private static LogFileType typeFromName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("endcall") || lower.contains("end_call")) {
            return LogFileType.END_CALL;
        }
        if (lower.contains("webrtc")) {
            return LogFileType.WEBRTC;
        }
        return null;
    }

    private static String label(LogFileType type) {
        return switch (type) {
            case END_CALL -> "end-call log";
            case WEBRTC -> "webrtc log";
            case SIGNALING_EXPORT -> "signaling export";
            case UNKNOWN -> "không rõ";
        };
    }

    private static boolean isBlank(byte[] bytes) {
        for (byte b : bytes) {
            if (!Character.isWhitespace(b)) {
                return false;
            }
        }
        return true;
    }

    /** UTF-8 nghiêm ngặt và không có byte NUL: file nhị phân / hỏng bị loại thay vì parse ra rác. */
    private static Optional<String> decodeText(byte[] bytes) {
        for (int i = 0; i < Math.min(bytes.length, BINARY_SCAN_BYTES); i++) {
            if (bytes[i] == 0) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    private static String displayName(String originalName) {
        return UploadWorkspace.safeName(originalName);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Workspace sẽ xoá cả thư mục khi đóng.
        }
    }

    private static final class Candidate {
        final String displayName;
        final String storedName;
        final Path path;
        final LogFileType type;
        final String callId;

        Candidate(String displayName, Path path, LogFileType type, String callId) {
            this.displayName = displayName;
            this.storedName = path.getFileName().toString();
            this.path = path;
            this.type = type;
            this.callId = callId;
        }
    }
}
