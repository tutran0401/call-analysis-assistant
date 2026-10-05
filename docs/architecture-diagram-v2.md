# Architecture Diagram v2 (Sprint 2)

Cập nhật từ `architecture-diagram-v1.md`: thêm Web UI, Chat API, File Validator, Request Parser,
Input/Output Sanitizer, AI Analysis, Guardrails, Fallback và Evaluation Runner. Phần pipeline
rule-based của Sprint 1 **không đổi** - nó trở thành baseline mà AI phải qua Guardrails để được
dùng. Tên package là tên thật trong `src/main/java/com/tutran/callassistant/`.

## Luồng một lượt hỏi trên Web UI

```mermaid
flowchart TD
    UI["Web UI (web/, React + Vite)<br/>câu hỏi + file log + Call-ID tuỳ chọn"]
    API["web/ChatController<br/>POST /api/chat (multipart), GET /api/health"]
    Svc["application/ChatAnalysisService<br/>điều phối + fallback"]
    Parser["application/request/RuleRequestParser<br/>intent, focus, Call-ID"]
    Val["ingest/file/ContentUploadValidator<br/>loại file theo nội dung, Call-ID, cỡ file"]
    Pipe["application/CallAnalysisPipeline (Sprint 1)<br/>normalize, timeline, metrics, rule verdict, report"]
    Ctx["application/ai/AiContextBuilder<br/>minimum necessary context"]
    InSan["sanitize/InputSanitizer"]
    AI["AiProvider (port)<br/>OpenAiProvider | FakeAiProvider | NoAiProvider"]
    Guard["application/guardrail/AiResponseGuard<br/>G01-G05"]
    Comp["application/AnalysisReportComposer<br/>AI chỉ thay lời văn"]
    OutSan["sanitize/OutputSanitizer"]
    Schema["report/ReportSchemaGuard<br/>report-schema-v2.json"]
    Rule["Report rule-based, degraded = true"]

    UI --> API --> Svc
    Svc --> Parser
    Parser -->|OUT_OF_SCOPE| Refuse["Câu từ chối cố định"]
    Parser --> Val --> Pipe
    Pipe --> Ctx --> InSan --> AI --> Guard
    Guard -->|ACCEPT / ACCEPT_WITH_FLAGS| Comp --> OutSan --> Schema --> UI
    Guard -->|REJECT| Rule
    AI -->|timeout / unavailable / invalid| Rule
    InSan -->|còn giá trị nhạy cảm| Rule
    Rule --> OutSan
```

Mọi nhánh lỗi của AI (chưa cấu hình, timeout, trả lời hỏng, bị Guardrails từ chối, context chưa
sạch) đều rơi về cùng một chỗ: report rule-based đánh dấu `degraded`. AI không bao giờ làm hỏng request.

## Phân vai code và AI (PROJECT_SPEC.md mục 3.2)

| Thành phần | Do ai | Ghi chú thực tế |
| --- | --- | --- |
| Chỉ số, evidence, issue category, cờ chất lượng, độ tin cậy | Code | `AnalysisReportComposer` luôn lấy từ report rule-based |
| Verdict | Rule đề xuất, AI đối chiếu | Lệch nhau → `UNKNOWN`, độ tin cậy `LOW`, kèm cờ G04 |
| Summary, analysis, suggestions | AI (sau Guardrails) | Số liệu và evidence ID bị kiểm bằng code (G01, G05) |
| Bố cục report | Code | `TextReportRenderer` / `ReportView.tsx`, AI chỉ điền field |
| Intent, focus | Code (từ khoá) | Bản AI chưa làm - xem Known Limitations |

## Dữ liệu đi sang AI

```mermaid
flowchart LR
    Report["Report rule-based<br/>evidence, metrics, limitations"] --> B["AiContextBuilder"]
    Timeline["CallTimeline<br/>chỉ leg + nguồn + tên event,<br/>không mốc giờ, không rawLine"] --> B
    Taxonomy["IssueCategoryRegistry"] --> B
    Intent["intent + focus<br/>(KHÔNG gửi câu hỏi gốc)"] --> B
    B --> S["InputSanitizer<br/>mask / drop / pseudonymize"]
    S -->|residual = 0| Send["gửi AI"]
    S -->|residual > 0| NoSend["không gửi, fallback"]
```

Câu hỏi gốc của người dùng và `rawLine`/`attributes` của event **không bao giờ** nằm trong context, nên
văn bản tự do và các khối SDP/JSON nhúng không phải là đường rò rỉ.

## Thành phần mới theo package

| Package | Nội dung |
| --- | --- |
| `web/` | `ChatController`, `ApiExceptionHandler` (lỗi thân thiện, đã sanitize) |
| `application/` | `ChatAnalysisService`, `AnalysisReportComposer`, `ChatRequest/Response`, `CallAnalysisDetail` |
| `application/request/` | `RequestParser`, `RuleRequestParser`, `Intent`, `CallFocus` |
| `application/validation/` + `port/out/UploadValidator` | File Validator (hợp đồng) |
| `application/ai/` | `AiContextBuilder`, `AiPromptFactory`, `TimeLimitedAiCaller`, `AiProviderException` |
| `application/guardrail/` | `AiResponseGuard`, `GuardDecision`, `AiAnalysis` |
| `application/port/out/AiProvider` | Cổng ra tới AI |
| `ai/` | Adapter: `openai/OpenAiProvider`, `FakeAiProvider`, `NoAiProvider`, `AiConfiguration` |
| `sanitize/` | `SensitiveDataSanitizer` (engine), `InputSanitizer`, `OutputSanitizer`, `Pseudonymizer`, `logging/*` |
| `ingest/file/` | `ContentUploadValidator`, `UploadWorkspace`, `CallIdExtractor`, `CallDirectoryIndex`, `TreeSignalingSource` |
| `evaluation/` | `EvaluationRunner`, `MetricsCalculator`, `CaseLoader`, `CaseMaterializer`, `EvaluationReportWriter` |
| `cli/EvaluateCommand` | `evaluate --cases ... --data-root ...` |
| `web/` (thư mục gốc repo) | Web UI React + Vite + TypeScript |

Quy tắc phụ thuộc một chiều của Sprint 1 vẫn giữ: `application` chỉ biết interface (`AiProvider`,
`UploadValidator`), các adapter (`ai/`, `ingest/`, `web/`, `cli/`) nằm ngoài.
