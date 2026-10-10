# So sánh cách dựng context (Sprint 2, T11)

**Câu hỏi:** đưa evidence / chỉ số / timeline / taxonomy cho AI dưới dạng mảng JSON có cấu trúc (`structured`,
mặc định) hay viết thành một đoạn văn bản (`semi`) thì accuracy, consistency, token, latency khác nhau thế nào?

**Cách đo:** `gpt-4o-mini` thật, cùng bộ `dev` (29 case), 3 câu hỏi đầu của mỗi case × 2 lần lặp = 174 request, hai
lượt chạy liên tiếp cùng điều kiện, chỉ đổi `--call-analysis.ai.context-mode`. `ruleBaseline`, intent, focus ở cả hai
chế độ đều là JSON có cấu trúc; chỉ phần evidence / chỉ số / giới hạn dữ liệu / diễn biến / taxonomy đổi dạng.

| Metric | `structured` | `semi` | Chênh lệch |
| --- | --- | --- | --- |
| Verdict Accuracy | 96.3% (156/162) | 96.3% (156/162) | 0 |
| Issue Category Accuracy | 100% (84/84) | 100% (84/84) | 0 |
| Consistency (theo case) | 100% (27/27) | 100% (27/27) | 0 |
| Template Compliance | 100% | 100% | 0 |
| Phản hồi AI bị Guardrails bắt (G01/G05) | 1.9% (3/162) | 0% (0/162) | −3 lần |
| Tỉ lệ fallback (degraded) | 1.9% | 0% | −3 lần |
| **Token trung bình / lần gọi** | **3436** | **2495** | **−27%** |
| Latency P50 / P95 | 2527 / 3444 ms | 2584 / 3607 ms | ≈ 0 (trong dao động mạng) |

## Diễn giải - và giới hạn của phép đo

- **Accuracy và consistency không phân biệt được hai cách.** Cả hai đều đúng verdict ở 156/162; 6 lần sai là cùng một
  case (`var-single-file`, xem `docs/sprint2-report.md` câu hỏi 1) và là sai nhãn chứ không phải sai của AI.
  Verdict và category do rule quyết định, AI chỉ viết lời văn, nên accuracy vốn ít phụ thuộc cách dựng context; khác biệt
  nếu có sẽ hiện ở **chất lượng lời văn và độ bám dữ liệu**, thứ các metric này không đo.
- **`semi` rẻ hơn rõ rệt: ít hơn 27% token** (taxonomy và các khoá JSON lặp lại tốn nhiều token). Latency không khác
  đáng kể vì thời gian chủ yếu nằm ở sinh đầu ra, không phải đọc đầu vào.
- **`semi` không bị bắt G01/G05 lần nào, `structured` bị 3 lần - nhưng cả 3 lần đều là một case** (`case-015`, AI tự trừ hai
  mốc giờ thành "33 giây"). Với một case duy nhất và 2 lần lặp thì đây có thể là ngẫu nhiên; **chưa đủ để kết luận `semi`
  ít bịa số hơn**. Cần nhiều case khó hơn (nhiều con số, nhiều mốc giờ) mới thấy khác biệt thật.
- Cỡ mẫu nhỏ (162 request / chế độ, tập `dev` 27 cuộc gọi, nhãn category do rule sinh ra) nên mọi chênh lệch dưới ~5
  điểm phần trăm nằm trong nhiễu.

## Khuyến nghị tạm thời

Giữ `structured` làm mặc định: nó cho phép Guardrails và Evaluation Runner đối chiếu từng evidence / chỉ số theo ID,
và chưa có bằng chứng `semi` tốt hơn về chất lượng. **Nếu chi phí token là ràng buộc thật** thì `semi` giảm ~27% mà chưa
thấy mất gì ở các metric đã đo. Quyết định nên dựa trên lần đo lại với bộ câu hỏi lớn hơn của Sprint 3 (T1), có chấm
chất lượng lời văn.

Chạy lại: `evaluate --cases benchmark/dev/cases.yaml --data-root sample-data --repeat 2 --max-questions 3
[--call-analysis.ai.context-mode=semi]`.
