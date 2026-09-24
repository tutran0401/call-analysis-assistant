# AI Provider Proposal (T10)

**Trạng thái: bản nháp, đang chờ Mentor duyệt (hạn cuối Sprint 1, PROJECT_SPEC.md mục 1.4).**
Tài liệu này so sánh 2 phương án có thể chạy được từ máy cá nhân, theo đúng yêu cầu ở mục
5.1 T10. Sprint 1 chưa có AI nào trong pipeline cả (mục 3.2/3.3); đề xuất này chỉ chuẩn
bị trước cho những gì AI Analysis Engine của Sprint 2 sẽ gọi tới.

## AI thực sự cần làm gì (theo mục 3.2 / mục 6.1 T5)

Input: intent/focus (từ Request Parser) + timeline + evidence + chỉ số đã tính + taxonomy
— **không bao giờ** là log gốc (xem phần cuối của `docs/sensitive-data-inventory.md`).
Output: một object có cấu trúc khớp với các field mà AI cần điền trong `report-schema-v1.json`
(gợi ý `verdict`, `qualityFlag`, `issueCategory`, `summary`, `evidenceIds`, `analysis`,
`suggestions`), sau đó Guardrails sẽ đối chiếu với rule baseline. Vì vậy 2 tiêu chí quan
trọng nhất khi chọn provider là: **structured/JSON output đáng tin cậy** (Guardrails cần
parse được một cách xác định) và **latency chấp nhận được** cho một web UI tương tác
(Sprint 2 mục 6.5 đo P50/P95 end-to-end).

## Phương án A: Cloud API (Anthropic Claude, ví dụ Haiku hoặc Sonnet)

| Tiêu chí | Đánh giá |
| --- | --- |
| Dữ liệu gửi đi sau khi sanitize | Chỉ JSON đã sanitize (timeline/evidence/metrics, mục 6.2) rời khỏi máy, qua TLS tới API của Anthropic. Không có log gốc, không có field mức `SECRET` nào rời khỏi sanitizer. |
| Chi phí | Trả theo token. Context đã sanitize cho một cuộc gọi (timeline + evidence + metrics, không phải log gốc) khá nhỏ — vài trăm đến vài nghìn token — nên chi phí mỗi lần phân tích chỉ vài phần nghìn đến vài xu tuỳ tier model. Cần có API key và ngân sách thật; free-tier/trial credit có thể đủ dùng cho khối lượng phát triển Sprint 2-3. |
| Latency | Thường khoảng 1-3s cho một request structured-output cỡ nhỏ-trung bình ở tier model nhanh; ổn định, không phụ thuộc CPU/GPU máy cá nhân của người phát triển. |
| Hỗ trợ structured output | Mạnh: hỗ trợ structured output dạng constrained/tool-based và JSON mode, khớp trực tiếp với việc validate theo `report-schema-v1.json` ở Guardrails. |
| Khác | Cần kết nối internet và API key (phải quản lý chi phí/secret); không chạy "ngoài hệ thống của team" theo đúng nghĩa vì đây là gọi API công khai — được phép theo mục 1.3 (chỉ cấm hệ thống *nội bộ team*), nhưng vẫn nên nói rõ với Mentor đây là một phụ thuộc mạng bên ngoài. |

## Phương án B: Model chạy local qua Ollama (ví dụ Llama 3.1 8B Instruct hoặc Qwen2.5 7B Instruct)

| Tiêu chí | Đánh giá |
| --- | --- |
| Dữ liệu gửi đi sau khi sanitize | Không có gì rời khỏi máy cả — câu trả lời mạnh nhất có thể cho yêu cầu "AI chỉ nhận minimum necessary context" và cho rủi ro rò rỉ dữ liệu input/output, vì ngay từ đầu đã không có bước gửi qua mạng. |
| Chi phí | Chạy miễn phí (model open-weight, không tính phí theo token); chi phí duy nhất là compute/điện của máy cá nhân và dung lượng ổ đĩa để lưu model (vài GB). |
| Latency | Phụ thuộc rất nhiều vào phần cứng của người phát triển. Trên laptop chỉ chạy CPU, latency sẽ cao hơn rõ rệt so với phương án cloud (vài giây đến vài chục giây mỗi request) và throughput thấp hơn khi chạy benchmark tính nhất quán (mục 6.5, "chạy 5 lần × 3 cách hỏi"); máy có GPU đủ mạnh sẽ thu hẹp khoảng cách này đáng kể. Cần đo thực tế trên máy dev trước khi chốt. |
| Hỗ trợ structured output | Yếu hơn và phụ thuộc nhiều vào model: các model mở nhỏ ít đáng tin cậy hơn khi phải tuân thủ chặt JSON schema nếu không có thêm cơ chế hỗ trợ (ví dụ dùng tuỳ chọn `format: json` của Ollama để ràng buộc theo grammar, hoặc prompt chặt hơn + vòng lặp retry ở Guardrails). Sprint 2 sẽ tốn nhiều công sức hơn để đường xử lý "invalid → reject/fallback" của Guardrails chạy ổn định. |
| Khác | Chạy offline hoàn toàn được sau khi đã tải model về, tiện khi demo mà không cần mạng; nhưng benchmark tính nhất quán lặp lại (mục 6.5) sẽ tốn nhiều thời gian thực tế hơn đáng kể ở Sprint 2-3 nếu latency cao. |

## Đề xuất (bản nháp — hãy tự kiểm tra lại với cấu hình máy của bạn trước khi nộp)

Ưu tiên **Phương án A (cloud API)** để phát triển Sprint 2 nhanh hơn và có structured
output đáng tin cậy hơn, vì độ đúng của Guardrails phụ thuộc vào việc AI thực sự trả về
JSON hợp lệ theo schema một cách nhất quán — đây là bài toán khó hơn nếu dùng model local
nhỏ trên phần cứng chưa biết trước. Giữ **Phương án B (model local)** như phương án dự
phòng/chạy offline, và quay lại xem xét ở task stretch "So sánh cách dựng context"
(mục 6.1 T11) nếu chi phí hoặc kết nối mạng trở thành ràng buộc thực sự.

**Trước khi chốt:** hãy xác nhận ngân sách/quyền truy cập API thật cho Phương án A, và
nếu muốn giữ Phương án B, hãy đo thử latency thật trên GPU/CPU của bạn trước khi ghi con
số đó vào bảng như một sự thật thay vì một ước tính.
