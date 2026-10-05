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
| Timeout | 15 s (HTTP) + 20 s (cứng, `TimeLimitedAiCaller`) | Provider treo không được giữ request |
| Retry | không | Retry có kiểm soát là Sprint 3 T8 |

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

## Chính sách khi AI và rule mâu thuẫn - cần Mentor xác nhận

Spec cho hai lựa chọn: "`UNKNOWN` hoặc gắn cờ cần kiểm tra". Đang dùng **`UNKNOWN`** vì khi hai nguồn mâu
thuẫn thì "không đủ evidence để kết luận" là câu trả lời trung thực. Cái giá: nếu rule đúng (13/13 trên
tập có nhãn ở Sprint 1) và AI sai thì verdict cuối bị hạ xuống `UNKNOWN` thay vì giữ đáp án đúng. Chưa đo được
tần suất vì chưa chạy AI thật; đo xong trên `dev` (cột "AI vs Rule" của `summary.md`) sẽ quyết định giữ hay đổi
sang "giữ verdict rule + gắn cờ". Đổi chỉ cần sửa `AiResponseGuard.compareWithRule` và `AnalysisReportComposer`.

## Chưa làm

- **Request Parser bằng AI**: hiện chỉ có `RuleRequestParser` (từ khoá, xác định). Bản AI cần provider thật và
  chỉ đáng làm nếu đo thấy từ khoá không đủ trên câu hỏi lạ.
- **So sánh cách dựng context** (T11 "Nếu kịp"): semi-structured vs structured evidence.
