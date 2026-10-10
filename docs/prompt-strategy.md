# Prompt Strategy (Sprint 2)

Tài liệu cho phần AI Analysis Engine: AI nhận gì, được phép làm gì, bị kiểm gì. Nguồn thật:
`src/main/resources/ai/prompts/analysis-system.vi.txt`, `src/main/resources/ai/ai-output-schema-v1.json`,
`application/ai/AiContextBuilder`, `application/guardrail/AiResponseGuard`.

## Nguyên tắc

1. **AI không thấy log gốc.** Input là JSON đã chuẩn hoá: intent/focus, kết luận rule (`ruleBaseline`),
   evidence (có ID), chỉ số đã tính, giới hạn dữ liệu, timeline rút gọn, taxonomy.
2. **AI không được tạo dữ kiện.** Evidence ID chỉ lấy từ danh sách được cấp; số liệu chỉ lấy nguyên văn từ
   chỉ số/evidence. Prompt nói rõ, **và** Guardrails kiểm lại bằng code - không tin prompt.
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
  "metrics":   [{"name": "...", "value": "...", "source": "..."}],
  "dataLimitations": ["..."],
  "timeline":  [{"leg": "CALLER", "source": "SIGNALING", "event": "INIT_CALL"}],
  "taxonomy":  [{"category": "...", "definition": "...", "symptoms": ["..."], "knownAmbiguity": "..."}]
}
```

- Timeline bỏ sự kiện lặp liên tiếp, cắt tối đa 80 mục, **không có mốc giờ** - mọi con số AI được nhắc đã
  nằm trong `metrics` và `evidence`, nên kiểm "số liệu ngoài bộ chỉ số" (G05) là phép so tập hợp đơn giản.
- **Không** gửi: câu hỏi gốc, `rawLine`, `attributes` của event.
- Toàn bộ JSON đi qua `InputSanitizer`; còn dù chỉ một giá trị nhạy cảm thì **không gửi** (fallback).

## Output (`ai-output-schema-v1.json`)

`verdict`, `qualityFlag`, `issueCategory`, `confidenceLevel`, `summary`, `evidenceIds`, `analysis`,
`suggestions`. Enum khớp `report-schema-v2.json`.

AI đưa ra đủ các field theo spec (mục 6.1 T5), nhưng **không field nào của AI được ghi thẳng vào report**:
`AnalysisReportComposer` chỉ lấy `summary`, `analysis`, `suggestions` và thứ tự evidence được trích; verdict,
category, cờ chất lượng, độ tin cậy, evidence, chỉ số luôn từ rule.

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
| G01 | `evidenceIds` không tồn tại; hoặc kết luận không-UNKNOWN mà không dẫn evidence nào | REJECT → fallback |
| G05 | Số trong summary/analysis/suggestions không có trong metrics/evidence | REJECT → fallback |
| G04 | Verdict AI ≠ verdict rule | Verdict `UNKNOWN`, độ tin cậy `LOW`, lời văn AI bị bỏ, kèm cờ |
| G04 | Cùng verdict nhưng khác category / cờ chất lượng | Giữ giá trị của rule, độ tin cậy `LOW`, kèm cờ |

**Dung sai G05:** số nguyên một chữ số đứng một mình ("2 bên", "1 lần") không bị tính là số liệu; mọi số thập
phân, số từ hai chữ số, hay số kèm đơn vị (`%`, `ms`, `s`, giây, gói, lần) đều phải có trong dữ liệu đầu vào.
Định dạng được chuẩn hoá (`6.20` = `6.2`, `2,9` = `2.9`).

**Làm tròn trung thực được chấp nhận** (bài học từ lần chạy OpenAI thật đầu tiên): `gpt-4o-mini` thường viết
"MOS 4.4" khi chỉ số là `4.40337`. Bản G05 đầu bắt khớp chuỗi tuyệt đối nên loại khoảng 40% report hoàn toàn đúng,
chỉ vì làm tròn. Giờ một số được chấp nhận nếu nó là bản làm tròn của một giá trị có thật: ít chữ số thập phân hơn
nguồn và lệch không quá nửa đơn vị ở chữ số cuối của nó (4.4 ~ 4.40337 hợp lệ; 4.5 hay 11.4 so với 11.3208 thì không).
Số bịa hoàn toàn vẫn bị bắt. Prompt cũng nói rõ quy tắc này. Sau sửa, 12/12 lần chạy liên tiếp cùng một case đều được chấp nhận.

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
