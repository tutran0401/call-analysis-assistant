package com.tutran.callassistant.ingest;

import com.tutran.callassistant.application.SignalingSourcePreference;
import com.tutran.callassistant.application.SignalingSourceResolver;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.ingest.es.ElasticsearchSignalingSource;
import com.tutran.callassistant.ingest.file.CallDirectoryIndex;
import com.tutran.callassistant.ingest.file.LocalFileSignalingSource;
import com.tutran.callassistant.ingest.file.TreeSignalingSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Nối các adapter đầu vào vào tầng application.
 *
 * <p>Đây là chỗ duy nhất biết rằng INDEXED nghĩa là thử Elasticsearch trước rồi mới tới file, còn
 * LOCAL_FILE nghĩa là chỉ đọc file. Muốn đổi cách ghép nguồn (bỏ hẳn fallback, hay thêm một lớp
 * cache) thì sửa ở đây - không phải sửa pipeline, cũng không phải sửa CLI.
 *
 * <p>Nếu đặt {@code call-analysis.signaling-root} (thư mục data mẫu), INDEXED thử cây thư mục đó
 * <b>trước</b> rồi mới tới Elasticsearch: chạy offline, kết quả không phụ thuộc ES đang có gì.
 */
@Configuration
public class IngestConfiguration {

    private static final String FALLBACK_REASON =
            "No signaling events found in Elasticsearch for %s; falling back to the local signaling.json "
                    + "(run the import command first for the ES-backed path).";
    private static final String TREE_MISS_REASON =
            "No signaling.json under call-analysis.signaling-root for %s; falling back to Elasticsearch.";

    @Bean
    public SignalingSourceResolver signalingSourceResolver(
            ElasticsearchSignalingSource indexed,
            LocalFileSignalingSource localFile,
            @Value("${call-analysis.signaling-root:}") String signalingRoot) {
        SignalingEventSource elasticsearchWithFileFallback = new FallbackSignalingSource(indexed, localFile, FALLBACK_REASON);
        SignalingEventSource indexedSource = signalingRoot.isBlank()
                ? elasticsearchWithFileFallback
                : new FallbackSignalingSource(
                        new TreeSignalingSource(new CallDirectoryIndex(Path.of(signalingRoot)), localFile),
                        elasticsearchWithFileFallback, TREE_MISS_REASON);

        Map<SignalingSourcePreference, SignalingEventSource> sources =
                new EnumMap<>(SignalingSourcePreference.class);
        sources.put(SignalingSourcePreference.INDEXED, indexedSource);
        sources.put(SignalingSourcePreference.LOCAL_FILE, localFile);
        return new SignalingSourceResolver(sources);
    }
}
