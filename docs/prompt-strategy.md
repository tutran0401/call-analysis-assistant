# Prompt Strategy (Sprint 2, cập nhật Sprint 3)

Tài liệu cho phần AI Analysis Engine: AI nhận gì, được phép làm gì, bị kiểm gì. Nguồn thật:
`src/main/resources/ai/prompts/analysis-system.vi.txt`, `src/main/resources/ai/ai-output-schema-v1.json`,
`application/ai/AiContextBuilder`, `application/guardrail/AiResponseGuard`.

## Nguyên tắc

1. **AI không thấy log gốc.** Input là JSON đã chuẩn hoá: intent/focus, kết luận rule (`ruleBaseline`),
   evidence (có ID), chỉ số đã tính, giới hạn dữ liệu, timeline rút gọn, taxonomy.
2. **AI không được tạo dữ kiện.** Evidence ID chỉ lấy từ danh sách được cấp. Khi cần số liệu, AI viện dẫn
   `{{M001}}` từ bảng metrics; code thay bằng cả tên, giá trị và đơn vị sau khi kiểm tra. Không cho AI tự viết số.
3. **Không đủ dữ liệu thì nói thẳng.** Evidence thiếu hoặc mâu thuẫn → `verdict = UNKNOWN`; phần thiếu phải
   nêu rõ ảnh hưởng thế nào.
4. **Một câu trả lời, một schema.** Structured output với `strict: true`; mọi field bắt buộc, không field lạ.

## Input gửi AI

```json
{
  "intent": "ANALYZE_WITH_FOCUS",
  "focus": ["AUDIO_QUALITY"],
  "ruleBaseline": {"verdict": "...", "qualityFlag": false, "issueCategory": "...", "confidenceLevel": "...", "summary": "..."},
  "evidence":  [{"id": "EV001", "source": "...", "time": "...", "description": "..."}],
  "metrics":   [{"ref": "{{M001}}", "name": "...", "value": "...", "source": "..."}],
  "dataLimitations": ["..."],
  "timeline":  [{"leg": "CALLER", "source": "SIGNALING", "event": "INIT_CALL"}],
  "taxonomy":  [{"category": "...", "definition": "...", "symptoms": ["..."], "knownAmbiguity": "..."}]
}
```

- Timeline bỏ sự kiện lặp liên tiếp, cắt tối đa 80 mục, **không có mốc giờ**. Mỗi chỉ số có một `ref`
  (`{{M001}}`, `{{M002}}`...), cùng tên, giá trị và nguồn. G05 chỉ nhận token có thật; code chèn cả tên lẫn
  giá trị/đơn vị để AI không thể lấy số thời lượng gán sang packet loss.
- Mô tả evidence và tóm tắt rule gửi AI chỉ giữ ý nghĩa sự kiện; con số trong mô tả và mốc giờ được che khỏi
  context. Report vẫn giữ nguyên evidence có số và mốc gốc để người đọc truy xuất. Cách này ngăn AI chép số
  request TURN từ evidence vào lời văn khi không có chỉ số tương ứng.
- **Không** gửi: câu hỏi gốc, `rawLine`, `attributes` của event.
- Toàn bộ JSON đi qua `InputSanitizer`; còn dù chỉ một giá trị nhạy cảm thì **không gửi** (fallback).

## Output (`ai-output-schema-v1.json`)

`verdict`, `qualityFlag`, `issueCategory`, `confidenceLevel`, `summary`, `evidenceIds`, `analysis`,
`suggestions`. Enum khớp `report-schema-v3.json`.

AI đưa ra đủ các field theo spec (mục 6.1 T5), nhưng **không field nào của AI được ghi thẳng vào report**:
`AnalysisReportComposer` chỉ lấy `summary`, `analysis`, `suggestions` và thứ tự evidence được trích; verdict,
category, cờ chất lượng, evidence, chỉ số luôn từ rule. Từ Sprint 3, đề xuất của AI được **nối sau** bộ đề xuất chuẩn
theo category (không thay thế), và `confidenceLevel` của AI bị bỏ qua: độ tin cậy do `EvidenceBasedConfidencePolicy`
tính từ độ mạnh evidence, dữ liệu thiếu và kết quả đối chiếu AI-vs-rule.

## Tham số gọi (OpenAI `gpt-4o-mini`)

| Tham số | Giá trị | Lý do |
| --- | --- | --- |
| `temperature` | 0 | Giảm dao động giữa các lần chạy (metric Consistency) |
| `seed` | cố định | Như trên; OpenAI chỉ cam kết "cố gắng", không đảm bảo tuyệt đối |
| `response_format` | `json_schema`, `strict: true` | Ép đúng schema; Guardrails vẫn kiểm lại |
| Timeout | 12 s mỗi lần (HTTP) + 30 s cứng cho cả lần gọi (`TimeLimitedAiCaller`) | Provider treo không được giữ request; 30 s ≥ 2 lần thử |
| Retry | 1 lần thử lại (`max-attempts: 2`), chỉ với lỗi tạm thời | Timeout, mất kết nối, HTTP 408/429/5xx. Không bao giờ retry 400/401/403, phản hồi hỏng hay bị từ chối. Đo thực tế: khoảng 1/10 lần gọi OpenAI treo quá hạn rồi lần sau chạy 3-4 giây bình thường |

## Guardrails (code, không phải prompt)

| Mã | Kiểm | Kết quả khi vi phạm |
| --- | --- | --- |
| G02 | Không phải JSON / sai schema / thiếu field / field lạ | REJECT → fallback |
| G03 | `verdict`, `issueCategory`, `confidenceLevel` ngoài taxonomy | REJECT → fallback |
| G01 | `evidenceIds` hoặc evidence ID trong lời văn không tồn tại; hoặc kết luận không-UNKNOWN mà không dẫn evidence nào | REJECT → fallback |
| G05 | Token metric không tồn tại hoặc số viết trực tiếp trong summary/analysis/suggestions | REJECT → fallback |
| G04 | Verdict AI ≠ verdict rule | Verdict `UNKNOWN`, độ tin cậy `LOW`, lời văn AI bị bỏ, kèm cờ |
| G04 | Cùng verdict nhưng khác category / cờ chất lượng | Giữ giá trị của rule, độ tin cậy `LOW`, kèm cờ |

G05 áp dụng cả số đếm một chữ số và timestamp viết trong lời văn. AI cần viết số đếm bằng chữ, còn thời điểm
thì dẫn evidence ID. Cách kiểm cũ cho phép dùng nhầm một số ở timestamp hoặc chỉ số khác; phép làm tròn tự do
cũng đã bỏ. Bộ đánh giá report kiểm lại trên cụm tên + giá trị + đơn vị do code sinh.

## Request Parser (T4)

`AiRequestParser` bọc `RuleRequestParser`, rule chạy **trước**: nếu rule xác định được câu hỏi trong phạm vi thì giữ
nguyên và không gọi AI (cùng câu hỏi luôn cùng intent, không tốn lượt gọi). Chỉ khi rule trả `OUT_OF_SCOPE` cho câu
hỏi không rỗng thì AI được hỏi, và AI chỉ có thể *cứu* nó thành trong phạm vi - không bao giờ biến câu trong phạm vi thành
bị từ chối. Call-ID luôn do regex trích. Câu hỏi qua Input Sanitizer trước khi gửi. Mọi lỗi AI → dùng kết quả của rule.
Đã thử với OpenAI thật: câu diễn đạt lạ ("... đang nói dở thì mất hút, sao vậy?") được cứu thành `DISCONNECT`; câu về
nấu phở / chọn quà sinh nhật vẫn bị từ chối.

## Hai cách dựng context (T11)

`call-analysis.ai.context-mode`: `structured` (mặc định - evidence, chỉ số, timeline, taxonomy là mảng JSON) hoặc
`semi` (cùng dữ kiện viết thành một đoạn văn bản `narrative`; `ruleBaseline`, intent, focus vẫn có cấu trúc). So sánh
trên cùng bộ case ở `docs/context-mode-comparison.md`.

## Chính sách khi AI và rule mâu thuẫn - cần Mentor xác nhận

Spec cho hai lựa chọn: "`UNKNOWN` hoặc gắn cờ cần kiểm tra". Đang dùng **`UNKNOWN`** vì khi hai nguồn mâu
thuẫn thì "không đủ evidence để kết luận" là câu trả lời trung thực. Cái giá: nếu rule đúng (13/13 trên
tập có nhãn ở Sprint 1) và AI sai thì verdict cuối bị hạ xuống `UNKNOWN` thay vì giữ đáp án đúng. Chưa đo được
tần suất vì chưa chạy AI thật; đo xong trên `dev` (cột "AI vs Rule" của `summary.md`) sẽ quyết định giữ hay đổi
sang "giữ verdict rule + gắn cờ". Đổi chỉ cần sửa `AiResponseGuard.compareWithRule` và `AnalysisReportComposer`.

## Chưa làm

- Đo đầy đủ Request Parser AI trên một bộ câu hỏi diễn đạt lạ do Mentor viết (bộ `dev` do chính tôi viết nên Intent
  Accuracy 100% chưa phản ánh câu hỏi lạ).


## Thay đổi Sprint 3 (T1 - từ phân tích lỗi trên `dev`)

Chạy `dev` với `call-analysis.ai.include-rule-baseline=false` (AI không thấy kết luận rule) lộ ra AI tự kết luận chỉ
đạt 61.7% verdict, 40.7% consistency. Ba nguyên nhân và cách sửa:

| Lỗi quan sát | Nguyên nhân | Sửa |
| --- | --- | --- |
| AI trả `UNKNOWN` khi thiếu log một bên (case-001/003/019, `var-foreign-call-file`) | Prompt chưa từng định nghĩa SUCCESS / FAIL / UNKNOWN (mục 4.1); "có dataLimitations" bị hiểu là "không đủ dữ liệu" | Quy tắc 4a: tiêu chí từng verdict + "thiếu một vài file không làm verdict thành UNKNOWN nếu signaling đủ" |
| Lỗi TURN bị gọi là `SIGNALING_FAILURE` (case-010/014/018/020) | Taxonomy gửi AI thiếu nguồn evidence bắt buộc | Taxonomy kèm `requiredEvidence`; quy tắc 4b: evidence TURN/ICE ưu tiên hơn signaling |
| Cùng cuộc gọi có thể nhận prompt khác nhau giữa các lần chạy | `taxonomy.all()` là `Map.of`, thứ tự lặp đổi theo mỗi lần khởi động JVM | Gửi taxonomy theo thứ tự enum `IssueCategory` |

`requiredEvidence` cố ý không kèm ngưỡng số (5%, 300 ms...). Nếu AI nhắc ngưỡng đó, Guardrails G05 sẽ coi là
"số liệu ngoài bộ chỉ số" và từ chối. Kết quả trước / sau: `docs/accuracy-consistency-analysis.md`.
