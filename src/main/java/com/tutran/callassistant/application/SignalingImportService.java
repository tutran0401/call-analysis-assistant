package com.tutran.callassistant.application;

import com.tutran.callassistant.application.port.in.ImportSignalingUseCase;
import com.tutran.callassistant.application.port.out.CallDirectoryScanner;
import com.tutran.callassistant.application.port.out.ImportProgressListener;
import com.tutran.callassistant.application.port.out.SignalingEventArchive;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Nạp mọi {@code signaling.json} tìm thấy dưới các thư mục gốc vào
 * {@link SignalingEventArchive} (T1). Một cuộc gọi không có file signaling thì bị bỏ qua,
 * và một file lỗi chỉ sinh cảnh báo chứ không làm dừng cả lượt import.
 */
@Service
public class SignalingImportService implements ImportSignalingUseCase {

    /** Tên file export signaling mà mentor cung cấp trong mỗi thư mục cuộc gọi. */
    public static final String SIGNALING_EXPORT_FILENAME = "signaling.json";

    private final SignalingEventArchive archive;
    private final CallDirectoryScanner directoryScanner;

    public SignalingImportService(SignalingEventArchive archive, CallDirectoryScanner directoryScanner) {
        this.archive = archive;
        this.directoryScanner = directoryScanner;
    }

    @Override
    public ImportSummary importFrom(List<Path> dataRoots, ImportProgressListener listener) {
        try {
            archive.ensureReady();
        } catch (IOException e) {
            return new ImportSummary(archive.name(), List.of(),
                    List.of("Could not prepare index " + archive.name() + ": " + e.getMessage()));
        }

        List<ImportSummary.ImportedCall> imported = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path dataRoot : dataRoots) {
            for (Path callDirectory : directoryScanner.callDirectoriesUnder(dataRoot)) {
                CallReference call = CallReference.ofDirectory(callDirectory);
                Path exportFile = call.resolve(SIGNALING_EXPORT_FILENAME);
                if (!Files.exists(exportFile)) {
                    continue;
                }
                try {
                    int eventCount = archive.index(exportFile);
                    imported.add(new ImportSummary.ImportedCall(call.callId(), eventCount));
                    listener.onCallImported(call.callId(), eventCount);
                } catch (IOException e) {
                    warnings.add("Could not import " + exportFile + ": " + e.getMessage());
                }
            }
        }
        return new ImportSummary(archive.name(), imported, warnings);
    }
}
