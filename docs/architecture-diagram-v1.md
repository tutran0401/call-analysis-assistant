# Architecture Diagram v1 (Sprint 1)

Adapts PROJECT_SPEC.md §3.1's target architecture to what actually exists after
Sprint 1: a CLI pipeline with no AI, no sanitizer, and no web UI yet. Package names are
the real ones under `src/main/java/com/tutran/callassistant/`.

```mermaid
flowchart TD
    subgraph cli["cli — Sprint 1 entrypoint (Web UI is Sprint 2)"]
        CLI["CallAnalysisCli\n(import / analyze / demo)"]
    end

    subgraph es["es — T1"]
        Indexer["SignalingIndexer\n(bulk import, idempotent)"]
        Fetcher["EsSignalingEventFetcher\n(query by Call-ID)"]
        ESStore[(Elasticsearch\nlocal, Docker)]
        Indexer -->|signaling.json| ESStore
        Fetcher -->|term query: callId| ESStore
    end

    subgraph parser["parser — T3 Log Normalizer"]
        Detect["FileTypeDetector\n(by content, not filename)"]
        EndCall["EndCallLogParser\n+ EndCallSchemaClassifier"]
        WebRtc["WebRtcLogParser\n(iOS + Android engine formats)"]
        SigJson["SignalingJsonParser\n(local file fallback)"]
        DirLoader["CallLogDirectoryLoader"]
        Detect --> EndCall
        Detect --> WebRtc
        DirLoader --> Detect
    end

    subgraph domain["domain — T2"]
        CanonicalEvent["CanonicalEvent\n(callId, leg, source, timestamp,\ntimestampConfidence, attributes, rawLine)"]
    end

    subgraph timeline["timeline — T4"]
        TimelineBuilder["TimelineBuilder\n(dedupe, cross-source sort,\nWebRTC clock-skew anchoring)"]
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
        Renderer["ReportRenderer\n(§4.5 text layout)"]
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
    Renderer --> Stdout(["stdout report\n(Web UI: Sprint 2)"])

    classDef future fill:#eee,stroke:#999,color:#999,stroke-dasharray: 4 3
    class Sanitizer,AIEngine,Guardrails,WebUI future
    Sanitizer["Input/Output Sanitizer\n(Sprint 2)"]
    AIEngine["AI Analysis Engine\n(Sprint 2)"]
    Guardrails["Guardrails\n(Sprint 2)"]
    WebUI["Web UI\n(Sprint 2)"]
    Metrics -.->|Sprint 2| Sanitizer -.-> AIEngine -.-> Guardrails -.-> Builder
    Stdout -.->|Sprint 2| WebUI
```

## What changed vs. the target architecture (§3.1)

- **No Chat API / Request Parser / File Validator yet** — Sprint 1 takes a call
  directory path directly on the CLI instead of a chat message with attachments; intent
  parsing has no reason to exist without a user question yet.
- **No Input/Output Sanitizer, AI Analysis Engine, or Guardrails** — nothing is sent to
  an AI provider in Sprint 1 (§3.2: "phần nào tính được bằng code thì không giao cho
  AI"), so there is no sanitizer boundary to sit at yet. `docs/sensitive-data-inventory.md`
  is the groundwork for Sprint 2's sanitizer.
- **Report Renderer prints to stdout**, not a Web UI. The `Report` POJO and JSON Schema
  are already the exact contract the Web UI (Sprint 2 T1/T2) will consume — only the
  transport changes.
- **Two paths into signaling data**: the target architecture always queries
  Elasticsearch; Sprint 1's CLI also supports `--from-file` (parsing `signaling.json`
  directly) so the demo works even before/without running `import`, useful for quick
  debugging against a specific sample call.
