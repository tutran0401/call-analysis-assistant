# Architecture Diagram v1 (Sprint 1)

Chuyển thể kiến trúc mục tiêu ở PROJECT_SPEC.md mục 3.1 thành những gì thực sự đã có sau
Sprint 1: một pipeline chạy CLI, chưa có AI, chưa có sanitizer, chưa có web UI. Tên package
dưới đây là tên thật trong `src/main/java/com/tutran/callassistant/`.

```mermaid
flowchart TD
    subgraph cli["cli — Entrypoint Sprint 1 (Web UI để dành Sprint 2)"]
        CLI["CallAnalysisCli\n(import / analyze / demo)"]
    end

    subgraph es["es — T1"]
        Indexer["SignalingIndexer\n(bulk import, idempotent)"]
        Fetcher["EsSignalingEventFetcher\n(query theo Call-ID)"]
        ESStore[(Elasticsearch\nlocal, Docker)]
        Indexer -->|signaling.json| ESStore
        Fetcher -->|term query: callId| ESStore
    end

    subgraph parser["parser — T3 Log Normalizer"]
        Detect["FileTypeDetector\n(theo nội dung, không theo tên file)"]
        EndCall["EndCallLogParser\n+ EndCallSchemaClassifier"]
        WebRtc["WebRtcLogParser\n(định dạng engine iOS + Android)"]
        SigJson["SignalingJsonParser\n(fallback đọc file local)"]
        DirLoader["CallLogDirectoryLoader"]
        Detect --> EndCall
        Detect --> WebRtc
        DirLoader --> Detect
    end

    subgraph domain["domain — T2"]
        CanonicalEvent["CanonicalEvent\n(callId, leg, source, timestamp,\ntimestampConfidence, attributes, rawLine)"]
    end

    subgraph timeline["timeline — T4"]
        TimelineBuilder["TimelineBuilder\n(loại trùng, sắp thứ tự đa nguồn,\nneo lệch đồng hồ cho WebRTC)"]
    end

    subgraph analysis["taxonomy (T5) + metrics (T6) + evidence (T7)"]
        Metrics["CallMetricsCalculator"]
        RuleEngine["RuleVerdictEngine\n+ EvidenceEngine"]
        Taxonomy["IssueCategoryRegistry"]
        RuleEngine --> Taxonomy
    end

    subgraph report["report — T8"]
        Builder["ReportBuilder"]
        Schema["report-schema-v1.json\n+ ReportSchemaValidator"]
        Renderer["ReportRenderer\n(bố cục theo mục 4.5)"]
        Builder --> Schema
        Builder --> Renderer
    end

    CLI --> Fetcher
    CLI --> SigJson
    CLI --> DirLoader
    EndCall --> CanonicalEvent
    WebRtc --> CanonicalEvent
    SigJson --> CanonicalEvent
    Fetcher --> CanonicalEvent
    CanonicalEvent --> TimelineBuilder
    TimelineBuilder --> Metrics
    TimelineBuilder --> RuleEngine
    Metrics --> RuleEngine
    RuleEngine --> Builder
    Metrics --> Builder
    Renderer --> Stdout(["report ra stdout\n(Web UI: Sprint 2)"])

    classDef future fill:#eee,stroke:#999,color:#999,stroke-dasharray: 4 3
    class Sanitizer,AIEngine,Guardrails,WebUI future
    Sanitizer["Input/Output Sanitizer\n(Sprint 2)"]
    AIEngine["AI Analysis Engine\n(Sprint 2)"]
    Guardrails["Guardrails\n(Sprint 2)"]
    WebUI["Web UI\n(Sprint 2)"]
    Metrics -.->|Sprint 2| Sanitizer -.-> AIEngine -.-> Guardrails -.-> Builder
    Stdout -.->|Sprint 2| WebUI
```

## Khác gì so với kiến trúc mục tiêu (mục 3.1)

- **Chưa có Chat API / Request Parser / File Validator** — Sprint 1 nhận trực tiếp đường
  dẫn thư mục cuộc gọi qua CLI thay vì tin nhắn chat kèm file đính kèm; chưa cần phân tích
  intent vì chưa có câu hỏi của người dùng.
- **Chưa có Input/Output Sanitizer, AI Analysis Engine, hay Guardrails** — Sprint 1 chưa
  gửi gì sang AI provider (mục 3.2: "phần nào tính được bằng code thì không giao cho AI"),
  nên chưa cần ranh giới sanitizer. `docs/sensitive-data-inventory.md` là bước chuẩn bị
  cho sanitizer của Sprint 2.
- **Report Renderer in ra stdout**, chưa có Web UI. POJO `Report` và JSON Schema đã đúng
  là hợp đồng (contract) mà Web UI (Sprint 2 T1/T2) sẽ tiêu thụ — chỉ thay đổi phương
  tiện hiển thị.
- **Có 2 đường lấy signaling data**: kiến trúc mục tiêu luôn query Elasticsearch; CLI
  Sprint 1 hỗ trợ thêm `--from-file` (đọc trực tiếp `signaling.json`) để demo chạy được
  ngay cả khi chưa/không chạy `import`, tiện cho việc debug nhanh với một cuộc gọi cụ thể.
