# Accuracy & Consistency Analysis (Sprint 3 T1)

> Ngày chạy: 2026-10-14. Bộ case `benchmark/dev/cases.yaml` (29 case, 461 request mỗi lượt), `--repeat 5`, OpenAI
> `gpt-4o-mini` thật, signaling từ cây `sample-data/`. Bốn lượt chạy, kết quả gốc ở `reports/eval-s3-*` (không commit).

## Câu hỏi cần trả lời

Sprint 2 báo verdict 96.5% và consistency 100%, nhưng có một cảnh báo: **AI được thấy kết luận của rule**
(`ruleBaseline`) trong input. Việc AI "đồng ý với rule" vì thế chưa chứng minh AI tự phân tích đúng, và chính sách
G04 (AI lệch rule → `UNKNOWN`) chưa từng được kích hoạt trên AI thật.

Sprint 3 thêm cờ `call-analysis.ai.include-rule-baseline=false`. Bật cờ này thì AI chỉ nhận evidence, chỉ số,
timeline và taxonomy, phải tự kết luận. Guardrails vẫn đối chiếu với rule như cũ.

## Kết quả

### Bốn lượt chạy

| Lượt | Cấu hình | Verdict cuối | Consistency (case) | AI tự kết luận verdict khớp rule | AI khớp category | AI khớp cờ chất lượng | Fallback | Token / lần | P50 / P95 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | có baseline, prompt Sprint 2 | 96.5% (416/431) | 100% (27/27) | 100% | 100% | 100% | 0% | 3578 | 2916 / 4132 ms |
| B | **không** baseline, prompt Sprint 2 | **61.7%** (266/431) | **40.7%** (11/27) | 64.5% | 49.5% | 42.7% | 2.1% | 3535 | 3150 / 4370 ms |
| C | **không** baseline, prompt sửa (T1) | **96.5%** (416/431) | **100%** (27/27) | **100%** | 54.3% | 56.1% | 0% | 4012 | 3128 / 5076 ms |
| D | có baseline, prompt sửa (cấu hình chạy thật) | 96.5% (416/431) | 100% (27/27) | 100% | 100% | 100% | 0% | 4055 | 2948 / 4608 ms |

Ở mọi lượt: Metric Correctness 100%, Template Compliance 100%, Intent Accuracy 100%, Pipeline Success 100%,
Unsupported Claim (report giao cho người dùng) 0%, Security Leakage 0. Issue Category Accuracy của **report cuối**
là 100% ở mọi lượt, vì khi AI lệch category thì report giữ category của rule (G04) và chỉ gắn cờ.

Cột "AI tự kết luận ... khớp rule" đếm trên phản hồi thô của AI (trước Guardrails), lấy từ cờ G04 trong `results.json`.

### Đọc kết quả

1. **Lượt B xác nhận nghi ngờ của Sprint 2.** Khi không được nhìn kết luận rule, gpt-4o-mini chỉ khớp verdict ở
   64.5% lượt; G04 đổi phần lệch thành `UNKNOWN`, nên verdict cuối rớt xuống 61.7% và consistency còn 40.7%. Con số
   96.5% của Sprint 2 phần lớn là AI chép kết luận rule, không phải AI tự phân tích.
2. **Sau khi sửa prompt (lượt C), AI tự kết luận verdict khớp rule 100%** - lần này là bằng chứng độc lập: AI chỉ
   có evidence, không có đáp án. Verdict cuối và consistency về lại 96.5% / 100%.
3. **Category và cờ chất lượng AI tự đánh giá vẫn yếu** (khoảng 55%). Report không bị ảnh hưởng vì code giữ giá trị
   của rule, nhưng những report đó mang độ tin cậy LOW (AI lệch rule).
4. **Cấu hình chạy thật (lượt D) không bị sửa đổi làm xấu đi**: mọi chỉ số giữ nguyên so với lượt A. Chi phí là
   token tăng 13% (3578 → 4055) vì prompt và taxonomy dài hơn.

Ca sai duy nhất ở mọi lượt là `var-single-file` (15 lần): nhãn `UNKNOWN` theo scenario 5 của spec, trong khi rule
và AI đều kết luận `SUCCESS` vì signaling vẫn có. Đây là câu hỏi đang chờ Mentor, không phải lỗi phân tích. Xem
`demo/README.md` mục "Ghi chú scenario 5".

## Phân tích lỗi (lượt B) và cách sửa

| # | Lỗi | Case | Nguyên nhân | Sửa (commit `Sprint 3 S3-5b`) |
| --- | --- | --- | --- | --- |
| 1 | AI trả `UNKNOWN` khi cuộc gọi kết luận được | case-001, 003, 008, 013, 019, `var-foreign-call-file` (100% lượt), và một phần ở 10 case khác | Prompt **chưa từng định nghĩa** SUCCESS / FAIL / UNKNOWN (mục 4.1). Gặp `dataLimitations` (thiếu log một bên), AI hiểu là "không đủ dữ liệu" | Quy tắc 4a trong prompt: tiêu chí từng verdict, và "thiếu một vài file không làm verdict thành UNKNOWN nếu signaling đủ" |
| 2 | Lỗi TURN bị gọi là `SIGNALING_FAILURE` | case-010, 011, 014, 018, 020 | Taxonomy gửi AI chỉ có định nghĩa và triệu chứng, thiếu nguồn evidence bắt buộc | Taxonomy kèm `requiredEvidence`; quy tắc 4b: evidence TURN/ICE ưu tiên hơn signaling |
| 3 | Cùng một cuộc gọi có thể nhận prompt khác nhau giữa các lần khởi động | (rủi ro consistency) | `IssueCategoryRegistry.all()` là `Map.of`, thứ tự lặp đổi theo mỗi lần chạy JVM | Gửi taxonomy theo thứ tự enum `IssueCategory`; test `taxonomyIsSentInAFixedOrderWithItsRequiredEvidence` |

`requiredEvidence` cố ý **không** kèm ngưỡng số (5%, 300 ms...). Nếu AI nhắc lại ngưỡng, Guardrails G05 sẽ coi đó là
số liệu ngoài bộ chỉ số và từ chối cả câu trả lời.

## Lệch còn lại (lượt C)

| Kiểu lệch | Case | Ghi chú |
| --- | --- | --- |
| AI gắn cờ chất lượng kém cho **mọi** cuộc gọi SUCCESS mà rule coi là sạch (189/189 lượt), thường kèm `NETWORK_PACKET_LOSS` | case-001 tới 007, mọi `var-*` của DE7DD314 | Gồm cả cuộc gọi **không có chỉ số chất lượng nào** (chỉ có WebRTC log). AI không biết ngưỡng (packet loss 5%, MOS 3.5), và có vẻ coi "thiếu chỉ số" là dấu hiệu chất lượng kém. Rule dùng đúng ngưỡng trong taxonomy. Không có lượt nào AI bỏ sót cờ |
| `ICE_FAILURE` thay cho `SIGNALING_FAILURE` | case-008, 012, 019 | Log client có sự kiện ICE trong khi lỗi gốc ở tầng signaling; đúng loại nhập nhằng đã ghi trong taxonomy |
| `SIGNALING_FAILURE` thay cho `TURN_FAILURE` | case-010, 013, 014, 018, 020 (khoảng 60% lượt, trước đó 70-100%) | Đã giảm nhưng chưa hết |

**Chưa sửa và lý do:** cách tự nhiên nhất là đưa ngưỡng vào context và cho G05 chấp nhận các ngưỡng đó như "số đã
biết". Việc này đụng vào Guardrails (lớp chống bịa số) ngay cuối sprint, cần một vòng benchmark nữa để chứng minh
không làm tăng Unsupported Claim. Ghi vào Known Limitations; cấu hình chạy thật vẫn bật `ruleBaseline`, nên category
trong report luôn là của rule.

## Consistency

- Theo case: 27/27 ở lượt A, C, D (mỗi case 3 cách hỏi × 5 lần). Lượt B chỉ 11/27: AI không thấy baseline thì dao
  động giữa `UNKNOWN` và verdict đúng.
- Consistency cao **một phần do thiết kế** (verdict và category cuối do rule quyết định). Lượt C cho thấy cả phần AI
  tự kết luận verdict cũng ổn định (100% khớp rule ở 431 lượt).
- Lời văn AI (summary / analysis) vẫn thay đổi giữa các lần chạy; metric 6.5 không đo phần này.

## Chạy lại

```bash
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar evaluate --cases benchmark/dev/cases.yaml \
    --data-root sample-data --repeat 5 --out reports/eval-with-baseline
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar evaluate --cases benchmark/dev/cases.yaml \
    --data-root sample-data --repeat 5 --out reports/eval-no-baseline --call-analysis.ai.include-rule-baseline=false
```
