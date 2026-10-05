package com.tutran.callassistant.evaluation;

import com.tutran.callassistant.application.validation.UploadedFile;
import com.tutran.callassistant.ingest.file.CallDirectoryIndex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Biến {@link FileRef} của một case thành các {@link UploadedFile} y như người dùng đính kèm trên Web UI,
 * để Evaluation Runner đi qua đúng đường đi của một request thật (validator, sanitizer, ...).
 */
public final class CaseMaterializer {

    private final CallDirectoryIndex dataRoot;

    public CaseMaterializer(CallDirectoryIndex dataRoot) {
        this.dataRoot = dataRoot;
    }

    public List<UploadedFile> materialize(EvaluationCase c) {
        List<UploadedFile> uploads = new ArrayList<>();
        for (FileRef ref : c.files()) {
            uploads.add(new UploadedFile(ref.attachedName(), read(c, ref)));
        }
        return uploads;
    }

    private byte[] read(EvaluationCase c, FileRef ref) {
        Path path = resolve(c, ref);
        try {
            byte[] content = Files.readAllBytes(path);
            if (ref.append().isEmpty()) {
                return content;
            }
            String extra = "\n" + String.join("\n", ref.append()) + "\n";
            byte[] tail = extra.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[content.length + tail.length];
            System.arraycopy(content, 0, combined, 0, content.length);
            System.arraycopy(tail, 0, combined, content.length, tail.length);
            return combined;
        } catch (IOException e) {
            throw new IllegalArgumentException("Case " + c.caseId() + ": cannot read " + path, e);
        }
    }

    private Path resolve(EvaluationCase c, FileRef ref) {
        String from = ref.from();
        int slash = from.indexOf('/');
        String callId = slash >= 0 ? from.substring(0, slash) : c.callId();
        String name = slash >= 0 ? from.substring(slash + 1) : from;
        Path directory = dataRoot.find(callId).orElseThrow(() -> new IllegalArgumentException(
                "Case " + c.caseId() + ": no data directory for call " + callId + " under " + dataRoot.root()));
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Case " + c.caseId() + ": file not found: " + file);
        }
        return file;
    }
}
