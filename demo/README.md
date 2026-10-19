# Demo Scripts - Final Demo (PROJECT_SPEC.md mục 10)

## Chuẩn bị

```bash
docker compose up -d elasticsearch kibana
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar import sample-data/success sample-data/fail sample-data/for_test
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar --spring.profiles.active=web     # cần .env có OPENAI_API_KEY
cd web && npm run dev                                                                         # Web UI http://localhost:5173
```

## Chạy

```bash
python demo/run_demo.py                 # cả 6 scenario
python demo/run_demo.py --only 6        # một scenario
```

Script gọi đúng Chat API mà Web UI dùng, in kết luận từng câu hỏi và lưu `demo/out/scenario-N-qK.json` (response)
và `.txt` (report theo mẫu 4.5). Khi demo trực tiếp trên Web UI, kéo thả đúng các file trong bảng dưới.

| # | Scenario | Cuộc gọi | File đính kèm | Câu hỏi | Kỳ vọng (spec) |
| --- | --- | --- | --- | --- | --- |
| 1 | Bình thường | `DE7DD314` | 4 file | "Phân tích cuộc gọi này giúp mình" | `SUCCESS`, không cờ |
| 2 | Chất lượng kém | `B5F19381` (new_dataset; mất gói cả cuộc 8,48 %) | 4 file | "Vì sao bên nhận nghe bị rè?" | `SUCCESS` + cờ, `NETWORK_PACKET_LOSS` |
| 3 | Lỗi thiết lập | `1B009D42` | `caller_endcall.log`, `caller_webrtc.log` | "Vì sao cuộc gọi này không kết nối được?" | `FAIL`, `SIGNALING_FAILURE` |
| 4 | Lỗi media | `2D9057AA` | `callee_endcall.log`, `callee_webrtc.log` | "Có lỗi kết nối media không?" | `FAIL`, `ICE_FAILURE` |
| 5 | Thiếu dữ liệu | `DE7DD314` | chỉ `caller_webrtc.log` | "Phân tích cuộc gọi này giúp mình" | `UNKNOWN`, nêu rõ file thiếu - **xem ghi chú** |
| 6 | Nhất quán + sensitive | `DE7DD314` | 4 file, `caller_webrtc.log` chèn JWT, phone, IP | 3 cách hỏi | cùng verdict; không lộ giá trị nhạy cảm |

## Ghi chú scenario 5 (chưa thống nhất với Mentor)

Kết quả đã chạy (2026-10-14): **`UNKNOWN`, độ tin cậy `LOW`**. "Giới hạn dữ liệu" nêu đủ 3 file thiếu, việc không có
signaling, và việc không xác định được Call-ID. Lý do: WebRTC log không chứa Call-ID, nên khi chỉ đính kèm đúng một
file WebRTC mà không điền Call-ID thì hệ thống không biết đây là cuộc gọi nào và không lấy được signaling.

Nếu điền thêm Call-ID (hoặc file duy nhất là end-call log, loại log có Call-ID), signaling được lấy từ Elasticsearch.
Khi đó rule thấy cuộc gọi đã CONFIRMED và kết thúc bằng BYE, nên kết luận `SUCCESS` độ tin cậy `MEDIUM`, kèm danh sách
file thiếu và đề xuất bổ sung từng file. Rule cố ý **không** ép "một file → UNKNOWN": cuộc gọi `success/5E0800AE` do
Mentor gán nhãn chỉ có một file mà vẫn là `SUCCESS`. Đây là câu hỏi đang chờ Mentor xác nhận (Known Limitations).

## Điểm nên chỉ ra khi demo

- **Độ tin cậy kèm căn cứ** (dòng "Căn cứ độ tin cậy") và mục **"Căn cứ kết luận"** (4 câu hỏi của mục 7.1 T2).
- **Mã yêu cầu** (`req-…`) trên UI. Tìm cùng mã đó trong log backend: thấy dòng `ai_call` và `chat_request` của
  đúng request đó, không có nội dung log hay câu hỏi gốc.
- **Fallback**: tắt mạng hoặc đặt sai key rồi hỏi lại. UI báo "Không kết nối được dịch vụ AI — đây là kết quả
  rule-based", report vẫn đủ mục.
- **Kibana**: `http://localhost:5601/app/dashboards#/view/ca-dashboard` cập nhật theo từng lượt hỏi.
