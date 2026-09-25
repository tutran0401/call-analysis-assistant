package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.SignalingImportService;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.domain.event.NormalizedEvents;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lấy signaling log bằng cách đọc thẳng file export trong thư mục cuộc gọi, không cần
 * Elasticsearch.
 *
 * <p>Đây là đường dành cho demo/debug nhanh (cờ {@code --from-file} của CLI) và cũng là phương án
 * dự phòng của {@link com.tutran.callassistant.ingest.FallbackSignalingSource} khi index chưa có dữ liệu của cuộc gọi.
 */
@Component
public class LocalFileSignalingSource implements SignalingEventSource {

    private final SignalingExportParser parser;

    public LocalFileSignalingSource(SignalingExportParser parser) {
        this.parser = parser;
    }

    public static LocalFileSignalingSource withDefaults() {
        return new LocalFileSignalingSource(new SignalingExportParser());
    }

    @Override
    public NormalizedEvents fetch(CallReference call) {
        Path exportFile = call.resolve(SignalingImportService.SIGNALING_EXPORT_FILENAME);
        if (!Files.exists(exportFile)) {
            return NormalizedEvents.unreadable("No "
                    + SignalingImportService.SIGNALING_EXPORT_FILENAME + " found in " + call.directory());
        }
        return parser.parse(LogFile.of(exportFile, call.callId()));
    }
}
