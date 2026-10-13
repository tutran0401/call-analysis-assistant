# Kết quả chạy Web UI trên bộ `new_dataset` (13/10/2026)

Bộ data mới mentor gửi: 7 cuộc gọi trong `sample-data/new_dataset/` (không kèm nhãn success/fail).
Mỗi case được chạy trên **Web UI thật** (React + Spring Boot, profile `web`) đúng như người dùng thao tác:
gõ câu hỏi, đính kèm toàn bộ file `*.log` của cuộc gọi, bấm **Phân tích**. Màn hình được chụp tự động
bằng Chrome (Playwright), toàn trang, trước và sau khi bấm. Ảnh chụp và response JSON nằm ở
`reports/new-dataset-ui-test/` (không đưa lên git vì chứa Call-ID production).

## Môi trường chạy

| Thành phần | Cấu hình |
| --- | --- |
| Backend | `java -jar ... --spring.profiles.active=web`, commit `605a6ea` |
| AI | OpenAI `gpt-4o-mini` thật (`CALL_ANALYSIS_AI_PROVIDER=openai`), context mode `structured` |
| Signaling | Elasticsearch 8.15 local (Docker): `import sample-data/new_dataset` → 7 cuộc gọi, 753 sự kiện |
| Câu hỏi (giống nhau cho mọi case) | "Phân tích giúp mình cuộc gọi này: cuộc gọi có thành công không, chất lượng thế nào và nguyên nhân nếu có vấn đề?" |
| Call-ID | Để trống: hệ thống tự lấy từ nội dung end-call log |

## Vấn đề phát hiện khi chạy và đã sửa

1. **3/7 cuộc gọi dùng định dạng signaling export mới**, tool không đọc được: `06bca48c`, `4fd495c1`, `b5f19381`.
   File cũ có dạng `{callId, events: [{cmd, service, level}]}`, file mới là document ES nguyên bản
   `{call_id, logs: [{grpcMethod, service: {name}, log: {level}, message}]}`. Bản trước khi sửa trả
   **UNKNOWN, "Không có dữ liệu signaling"** cho cả 3 (log CLI: `cli-before-fix.txt`). Đã sửa (`c9c0ea5`):
   thêm `SignalingExportFormat`, dùng chung cho parser file và lệnh import ES, đọc được cả hai dạng.
2. **"Số lần No sessions found" lệch giữa CLI và Web UI.** Định dạng mới có field `message` (text tự do của
   server, ví dụ `No sessions found for Call-ID ...`). Đường đọc file đếm được 3/4/3 lần, nhưng document ES
   không lưu `message` nên Web UI báo 0. Đã sửa (`cf95341`).
3. **Cuộc gọi bị ngắt giữa chừng vẫn ra SUCCESS** (`70C388AD`, `FE4EF5E7`). Spec mục 4.1 định nghĩa
   `FAIL` = "không thiết lập được **hoặc bị ngắt bất thường**", nhưng rule cũ chỉ bắt vế đầu (chưa từng
   CONFIRMED, hoặc ICE `=> failed`). Signaling của 2 cuộc gọi này vẫn đi đủ tới BYE nên nhìn như bình thường.
   Log client cho thấy thực tế:
   - `FE4EF5E7`: nói chuyện ~30 s; lúc 10:42:56 **cả hai bên cùng ngừng nhận audio trong cùng một giây**;
     callee cúp máy sau 2.7 s nhưng BYE của callee không bao giờ tới server; caller nhận ICE `DISCONNECTED`
     rồi `PARTNER_LOST_NETWORK`, chịu 9 s im lặng rồi mới cúp (BYE gửi lại 5 lần, không có ACK).
   - `70C388AD`: caller chỉ nhận đúng 37 gói audio trong giây đầu, sau đó **không nhận được tiếng nào trong
     11 s**, nhận `PARTNER_LOST_NETWORK` rồi cúp. MOS 4.36 / loss 0 % trong report chỉ tính được từ một mẫu đó.

   Đã sửa (`295aff6`, `605a6ea`): thêm rule `MediaDroppedAfterConnectRule` → `FAIL / ICE_FAILURE`. Mục 4.2
   không có category riêng cho "rớt cuộc gọi"; ICE_FAILURE là category có nguồn evidence khớp (trạng thái kết
   nối media trong log client). Điều kiện: end-call log của một bên có `PARTNER_LOST_NETWORK` hoặc ICE
   `DISCONNECTED` **trước khi chính bên đó cúp máy**, **và** bên đó không nhận được audio
   (`audio.bytesReceivedPts = 0`) liên tục ≥ 3 s tới lúc kết thúc. Cần đủ cả hai vì `DISCONNECTED` cũng xuất
   hiện lúc cúp máy bình thường, còn im lặng đơn lẻ có thể chỉ là bên kia tắt mic. Report nêu rõ bên nào mất
   mạng (theo `PARTNER_LOST_NETWORK`), nên đề xuất của AI hướng đúng vào CALLEE.
   Đã quét cả 30 cuộc gọi mẫu (cũ + mới): điều kiện chỉ khớp đúng 2 cuộc gọi này, không khớp cuộc gọi
   SUCCESS nào anh đã gán nhãn. Benchmark `dev` (rule-only) giữ nguyên 26/27 như trước.
4. **Đơn vị jitter sai từ Sprint 1** (`22f23d1`). `audio.jitter` lấy từ WebRTC stats, đơn vị **giây** (W3C),
   nhưng report in thẳng kèm "ms" (ví dụ "0.022 ms"), nên ngưỡng 30 ms không bao giờ bật được. Nay đổi sang ms
   (0.022 s → 22 ms; data mẫu có nhãn SUCCESS nằm ở 12-28 ms, đúng mức jitter bình thường của Opus). Trên cả
   30 cuộc gọi việc sửa này không làm đổi verdict hay category nào.

Test: 278/278 pass (gồm 2 test tích hợp với ES thật; thêm test cho định dạng signaling mới, đơn vị jitter,
và 6 test cho rule mới: 2 cuộc gọi thật bị rớt, 1 cuộc gọi thật có khoảng mất tiếng ngắn nhưng không rớt,
3 ca giả lập "chỉ DISCONNECTED", "chỉ im lặng", "mất mạng + im lặng"). Web UI: 12/12 pass.

## Kết quả từng case (sau khi sửa)

| # | Call-ID | File đính kèm | Kết luận | Cờ chất lượng / Category | Tin cậy | Chỉ số / evidence chính | AI | Thời gian |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 06BCA48C | 4 file đủ 2 bên | SUCCESS | Có - NETWORK_PACKET_LOSS | HIGH | kết nối 516.9 s; loss caller 38.6 %; "No sessions found" 3 lần | OK | 23.4 s |
| 2 | 4FD495C1 | 4 file đủ 2 bên | SUCCESS | Có - NETWORK_PACKET_LOSS | HIGH | kết nối 91.8 s; loss caller 18 %; "No sessions found" 4 lần | OK | 6.4 s |
| 3 | 70C388AD | caller endcall + webrtc, `user-UDMAMJ44TXW_webrtc.log` | **FAIL** | ICE_FAILURE | MEDIUM | CALLEE mất mạng; caller không nhận tiếng 11 s cuối; thiếu end-call log của callee | OK | 10.1 s |
| 4 | B5F19381 | 4 file đủ 2 bên | SUCCESS | Có - NETWORK_PACKET_LOSS | HIGH | loss 49.5 % / 48.1 %; MOS caller 1.94 | OK | 11.5 s |
| 5 | FC3187B4 | 6 file: 1 caller + **2 thiết bị callee** | SUCCESS | Có - NETWORK_DELAY_JITTER | HIGH | RTT 549 / 555 ms; loss 0 % | OK | 9.3 s |
| 6 | FE4E4BDC | 4 file đủ 2 bên | SUCCESS | Có - NETWORK_PACKET_LOSS | HIGH | callee: MOS 1.0, loss 100 % ở một mẫu, RTT 1466 ms, jitter 289 ms | OK | 12.1 s |
| 7 | FE4EF5E7 | 4 file đủ 2 bên | **FAIL** | ICE_FAILURE | HIGH | CALLEE mất mạng; caller không nhận tiếng 9 s cuối; BYE của callee không tới server | OK | 9.8 s |

"AI OK" nghĩa là không case nào bị degraded/fallback về rule; Guardrails nhận cả 7 phản hồi. Kết luận, category,
độ tin cậy và **mọi chỉ số** trên UI (qua ES, có AI) trùng với bản rule-only chạy bằng CLI đọc file
(`cli-after-fix.txt`). Lưu ý như Sprint 2: AI có thấy rule baseline nên sự trùng verdict không phải bằng chứng
độc lập. Thời gian phản hồi lần chạy này cao hơn lần trước (3-7 s), chủ yếu do độ trễ của OpenAI API.

## Nhận xét (cần mentor xác nhận)

- **Chưa có nhãn nên chưa tính được accuracy.** Nếu anh gửi nhãn, em sẽ thêm 7 case vào benchmark và chạy
  `evaluate`.
- **Category cho cuộc gọi bị rớt giữa chừng.** Em đang dùng `ICE_FAILURE` vì spec 4.2 chỉ có 6 category. Nếu
  anh muốn tách riêng (ví dụ "rớt do mất mạng"), em sẽ thêm category.
- **5 cuộc gọi SUCCESS đều bị gắn cờ chất lượng**, vì chỉ số lấy giá trị **tệ nhất trong cả cuộc gọi** và cuộc nào
  cũng có ít nhất một mẫu vượt ngưỡng (RTT > 300 ms hoặc loss > 5 %). Ứng viên cho Sprint 3 T1: dùng p95 hoặc
  thời lượng vượt ngưỡng thay cho max.
- **Case 6: callee có MOS 1.0, loss 100 %.** Log callee: ~3 s không nhận audio, một mẫu loss 100 %, rồi
  audio có lại ở mẫu cuối trước khi cúp; không có `PARTNER_LOST_NETWORK`/ICE `DISCONNECTED` → giữ SUCCESS + cờ
  chất lượng, không coi là rớt cuộc gọi.
- **Case 5: một callee đăng nhập trên 2 thiết bị** (iOS nghe máy, Android chỉ đổ chuông). Tool gộp cả hai vào
  một bên CALLEE; kết quả đúng vì thiết bị Android không có mẫu media, nhưng report chưa nói rõ có 2 thiết bị.
- **Case 3: file `user-UDMAMJ44TXW_webrtc.log`** không đặt tên theo caller/callee nên tool không gán được bên;
  report nêu điều này ở "Giới hạn dữ liệu". Kết luận FAIL chỉ dựa vào end-call log của caller.
- **"Bên kết thúc"** ở case 7 ghi CALLER theo đúng định nghĩa spec 4.3 ("ai gửi BYE" ở signaling), dù thực tế
  callee bấm cúp trước - BYE của callee không tới được server. Phần evidence của report nêu rõ phía CALLEE
  mất mạng.
