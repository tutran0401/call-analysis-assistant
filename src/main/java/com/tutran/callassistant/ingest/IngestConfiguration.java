package com.tutran.callassistant.ingest;

import com.tutran.callassistant.application.SignalingSourcePreference;
import com.tutran.callassistant.application.SignalingSourceResolver;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.ingest.es.ElasticsearchSignalingSource;
import com.tutran.callassistant.ingest.file.LocalFileSignalingSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * Nối các adapter đầu vào vào tầng application.
 *
 * <p>Đây là chỗ duy nhất biết rằng INDEXED nghĩa là thử Elasticsearch trước rồi mới tới file, còn
 * LOCAL_FILE nghĩa là chỉ đọc file. Muốn đổi cách ghép nguồn (bỏ hẳn fallback, hay thêm một lớp
 * cache) thì sửa ở đây - không phải sửa pipeline, cũng không phải sửa CLI.
 */
@Configuration
public class IngestConfiguration {

    private static final String FALLBACK_REASON =
            "No signaling events found in Elasticsearch for %s; falling back to the local signaling.json "
                    + "(run the import command first for the ES-backed path).";

    @Bean
    public SignalingSourceResolver signalingSourceResolver(ElasticsearchSignalingSource indexed,
                                                           LocalFileSignalingSource localFile) {
        Map<SignalingSourcePreference, SignalingEventSource> sources =
                new EnumMap<>(SignalingSourcePreference.class);
        sources.put(SignalingSourcePreference.INDEXED,
                new FallbackSignalingSource(indexed, localFile, FALLBACK_REASON));
        sources.put(SignalingSourcePreference.LOCAL_FILE, localFile);
        return new SignalingSourceResolver(sources);
    }
}
