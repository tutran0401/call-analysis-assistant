package com.tutran.callassistant.application.port.in;

import com.tutran.callassistant.application.ImportSummary;
import com.tutran.callassistant.application.port.out.ImportProgressListener;

import java.nio.file.Path;
import java.util.List;

/**
 * Use case chuẩn bị dữ liệu (T1): nạp các file export {@code signaling.json} mentor cung
 * cấp vào Elasticsearch local, để lúc phân tích hệ thống query theo Call-ID giống như
 * production.
 */
public interface ImportSignalingUseCase {

    ImportSummary importFrom(List<Path> dataRoots, ImportProgressListener listener);
}
