package com.tutran.callassistant.ingest.file;

import com.tutran.callassistant.application.CallReference;
import com.tutran.callassistant.application.port.out.SignalingEventSource;
import com.tutran.callassistant.domain.event.NormalizedEvents;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Signaling lấy từ {@code signaling.json} trong cây dữ liệu mẫu theo Call-ID - đóng vai Elasticsearch
 * khi chạy offline: Evaluation Runner (mentor chạy mà không cần dựng ES), demo web UI, hay máy chưa bật
 * Docker. Bật bằng {@code call-analysis.signaling-root}; không có thì pipeline vẫn đi đường Elasticsearch.
 */
public class TreeSignalingSource implements SignalingEventSource {

    private final CallDirectoryIndex index;
    private final SignalingEventSource files;

    public TreeSignalingSource(CallDirectoryIndex index, SignalingEventSource files) {
        this.index = index;
        this.files = files;
    }

    @Override
    public NormalizedEvents fetch(CallReference call) {
        Optional<Path> directory = index.find(call.callId());
        if (directory.isEmpty()) {
            return NormalizedEvents.empty();
        }
        return files.fetch(new CallReference(call.callId(), directory.get()));
    }
}
