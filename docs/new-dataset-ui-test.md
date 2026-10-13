# Kết quả chạy Web UI trên bộ `new_dataset` (13/10/2026)

Bộ data mới mentor gửi: 7 cuộc gọi trong `sample-data/new_dataset/` (không kèm nhãn success/fail).
Mỗi case được chạy trên **Web UI thật** (React + Spring Boot, profile `web`) đúng như người dùng thao tác:
gõ câu hỏi, đính kèm toàn bộ file `*.log` của cuộc gọi, bấm **Phân tích**. Màn hình được chụp tự động
bằng Chrome (Playwright), toàn trang, trước và sau khi bấm. Ảnh chụp và response JSON nằm ở
`reports/new-dataset-ui-test/` (không đưa lên git vì chứa Call-ID production).

## Môi trường chạy

| Thành phần | Cấu hình |
| --- | --- |
| Backend | `java -jar ... --spring.profiles.active=web`, commit `cf95341` |
| AI | OpenAI `gpt-4o-mini` thật (`CALL_ANALYSIS_AI_PROVIDER=openai`), context mode `structured` |
| Signaling | Elasticsearch 8.15 local (Docker): `import sample-data/new_dataset` → 7 cuộc gọi, 753 sự kiện |
| Câu hỏi (giống nhau cho mọi case) | "Phân tích giúp mình cuộc gọi này: cuộc gọi có thành công không, chất lượng thế nào và nguyên nhân nếu có vấn đề?" |
| Call-ID | Để trống: hệ thống tự lấy từ nội dung end-call log |

## Vấn đề phát hiện khi chạy và đã sửa

1. **3/7 cuộc gọi dùng định dạng signaling export mới**, tool không đọc được: `06bca48c`, `4fd495c1`, `b5f19381`.
   File cũ có dạng `{callId, events: [{cmd, service, level}]}`, file mới là document ES nguyên bản
   `{call_id, logs: [{grpcMethod, service: {name}, log: {level}, message}]}`. Bản trước khi sửa trả
   **UNKNOWN, "Không có dữ liệu signaling"** cho cả 3 (log CLI: `cli-before-fix.txt`). Đã sửa: commit
   `c9c0ea5` thêm `SignalingExportFormat`, dùng chung cho parser file và lệnh import ES, nên đọc được cả hai dạng.
2. **"Số lần No sessions found" lệch giữa CLI và Web UI.** Định dạng mới có field `message` (text tự do của
   server, ví dụ `[OUT][NATs][INVITE][CALLEE] No sessions found for Call-ID ...`). Đường đọc file đếm được
   3/4/3 lần, nhưng document ES không lưu `message` nên Web UI báo 0. Đã sửa ở commit `cf95341`. Sau khi sửa,
   so từng chỉ số của 7 case thì CLI (đọc file) và Web UI (qua ES) **khớp 100%**.

Test: 272/272 pass (gồm 2 test tích hợp với ES thật), thêm test cho định dạng mới ở parser và importer.

## Kết quả từng case (sau khi sửa)

| # | Call-ID | File đính kèm | Kết luận | Cờ chất lượng | Tin cậy | Chỉ số chính | AI | Thời gian |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 06BCA48C | 4 file đủ 2 bên | SUCCESS | NETWORK_PACKET_LOSS | HIGH | kết nối 516.9 s; loss caller 38.6 %; "No sessions found" 3 lần | OK | 6.1 s |
| 2 | 4FD495C1 | 4 file đủ 2 bên | SUCCESS | NETWORK_PACKET_LOSS | HIGH | kết nối 91.8 s; loss caller 18 %; "No sessions found" 4 lần | OK | 3.3 s |
| 3 | 70C388AD | caller endcall + webrtc, `user-UDMAMJ44TXW_webrtc.log` | SUCCESS | NETWORK_DELAY_JITTER | MEDIUM | RTT caller 317 ms; không có end-call log của callee | OK | 2.9 s |
| 4 | B5F19381 | 4 file đủ 2 bên | SUCCESS | NETWORK_PACKET_LOSS | HIGH | loss 49.5 % / 48.1 %; MOS caller 1.94 | OK | 5.1 s |
| 5 | FC3187B4 | 6 file: 1 caller + **2 thiết bị callee** | SUCCESS | NETWORK_DELAY_JITTER | HIGH | RTT 549 / 555 ms; loss 0 % | OK | 3.1 s |
| 6 | FE4E4BDC | 4 file đủ 2 bên | SUCCESS | NETWORK_PACKET_LOSS | HIGH | callee: MOS 1.0, loss 100 %, RTT 1466 ms | OK | 3.4 s |
| 7 | FE4EF5E7 | 4 file đủ 2 bên | SUCCESS | NETWORK_PACKET_LOSS | HIGH | callee: MOS 1.0, loss 27.6 %, RTT 1253 ms | OK | 3.8 s |

"AI OK" nghĩa là không case nào bị degraded/fallback về rule. Guardrails nhận cả 7 phản hồi, không có cảnh
báo xử lý. Kết luận, cờ chất lượng và độ tin cậy trên UI (có AI) trùng với bản chạy rule-only bằng CLI
(`cli-after-fix.txt`) ở cả 7 case. Lưu ý như Sprint 2: AI có thấy rule baseline nên sự trùng này không phải
bằng chứng độc lập.

## Nhận xét (cần mentor xác nhận)

- **Chưa có nhãn nên chưa tính được accuracy.** Nếu anh gửi nhãn (thành công/thất bại, có vấn đề chất lượng
  không), em sẽ thêm 7 case vào benchmark và chạy `evaluate`.
- **Cả 7 cuộc gọi đều bị gắn cờ chất lượng.** Lý do: chỉ số lấy giá trị **tệ nhất trong cả cuộc gọi**, và cuộc
  nào cũng có ít nhất một mẫu vượt ngưỡng (RTT > 300 ms hoặc loss > 5 %). Một spike ngắn cũng đủ bật cờ. Đây
  là ứng viên cho Sprint 3 T1, ví dụ dùng p95 hoặc thời lượng vượt ngưỡng thay cho max.
- **Đơn vị jitter có thể sai (lỗi từ Sprint 1, chưa sửa).** `audio.jitter` lấy từ WebRTC stats; theo chuẩn W3C
  đơn vị này là **giây**, nhưng report ghi "ms". Ví dụ case 6 có jitter callee `0.289`, nhiều khả năng là
  289 ms, vượt xa ngưỡng 30 ms, nhưng rule hiện không bao giờ bật được nhánh jitter. Em chưa sửa vì sẽ đổi
  nhãn của benchmark `dev`; xin anh xác nhận đơn vị trước.
- **Case 6 và 7: callee có MOS 1.0 và loss lên tới 100 %** nhưng kết luận vẫn là "SUCCESS + cờ chất lượng".
  Nếu thực tế bên nhận mất tiếng hoàn toàn thì nên xếp loại nặng hơn (one-way audio). Cần anh cho biết
  kỳ vọng.
- **Case 5: một callee đăng nhập trên 2 thiết bị** (iOS nghe máy, Android chỉ đổ chuông). Tool gộp cả hai vào
  một bên CALLEE. Kết quả đúng vì thiết bị Android không có mẫu media, nhưng report chưa nói rõ có 2 thiết bị.
- **Case 3: file `user-UDMAMJ44TXW_webrtc.log`** không đặt tên theo caller/callee. Tool nêu điều này ở "Giới hạn
  dữ liệu", nhưng bảng chỉ số vẫn ghi "không có webrtc log của CALLEE", hơi mâu thuẫn. Phần "Phân tích" của AI
  cũng viết "có sự hiện diện của jitter" trong khi jitter đo được chỉ 0.021: đây là suy diễn định tính mà
  Guardrails hiện chưa bắt được (Guardrails chỉ kiểm con số và evidence ID).
