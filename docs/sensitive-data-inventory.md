# Sensitive Data Inventory & Data Classification Policy (T9)

Mở rộng bảng khởi điểm ở PROJECT_SPEC.md mục 5.2 với các field thực sự phát hiện được
trong lúc xây dựng parser Sprint 1, bằng cách rà soát data mẫu thật trong `fail/`,
`success/`, `for_test/`. Chưa có sanitizer nào được cài đặt (việc đó thuộc Sprint 2, mục
6.2) — đây chỉ là bản kiểm kê (inventory) mà sanitizer sau này sẽ phải tuân theo. Sprint 1
chưa gửi gì sang AI provider nên chưa có nguy cơ rò rỉ, nhưng các parser đã trích các
field này vào `CanonicalEvent.attributes()`, nên chính sách dưới đây sẽ là cơ sở để
Sprint 2 biết cần mask/drop gì trước khi dữ liệu đến được LLM.

Các mức phân loại: `PUBLIC`, `INTERNAL`, `SENSITIVE`, `SECRET` (theo mục 5.2).

## Từ bảng khởi điểm mentor cung cấp

| Field | Phân loại | Chính sách | Quan sát được ở đâu |
| --- | --- | --- | --- |
| Packet loss, RTT, jitter, MOS, call duration | Internal | Allow | Periodic stats / call summary trong end-call log |
| ISP / ASN / country | Internal | Allow | Signaling events (`isp`, `asn`, `countryCode`) |
| Số điện thoại, email | Sensitive | Mask | Không thấy trong data mẫu hiện tại, nhưng vẫn phải mask nếu xuất hiện trong data sau này |
| Địa chỉ IP của client | Sensitive | Mask | Các field ICE candidate trong WebRTC/end-call log (`transport.localCandidate.ip`, `transport.remoteCandidate.ip`, dòng SDP `candidate:` gốc), mô tả network của thiết bị (`pdp_ip0:100.81.44.x/32:Cellular` — bản thân client đã tự che một phần octet ở một số field, nhưng **không** che ở các IP trong ICE candidate, những IP này là địa chỉ đầy đủ) |
| User ID (kể cả ID nội bộ), Device ID | Sensitive | Pseudonymize | `appUserId`, `callUserId`, `partnerAppUserId`, `partnerCallUserId` (end-call log, signaling events); `deviceId` (nằm trong khối JSON `log_detail` của end-call log) |
| Session ID, csid | Sensitive | Pseudonymize nếu cần correlate | `sessionId`, `csid`, `requestId` (end-call log + signaling events) |
| JWT, Authorization Header, API Key, TURN credential | Secret | Drop | Không thấy auth header trong các log phía client này; TURN credential thì có (xem bên dưới) |
| Chi tiết hạ tầng nội bộ (tên pod, host nội bộ, IP TURN server) | Internal/Sensitive | Minimize | URL TURN server trong `iceServers.urls` (nằm trong khối JSON config), ví dụ `turn:14.238.152.70:3478` |

## Các field bổ sung phát hiện được trong lúc xây parser Sprint 1

| Field | Phân loại | Chính sách | Quan sát được ở đâu |
| --- | --- | --- | --- |
| ICE ufrag/pwd trong SDP (`a=ice-ufrag`, `a=ice-pwd`) | Secret | Drop | Khối SDP offer/answer (JSON) nhúng trong các dòng `SIGNALING_CMD` của end-call log |
| DTLS fingerprint (`a=fingerprint:sha-256 ...`) | Secret | Drop | Cùng khối SDP nói trên |
| `turnSessionInfo` / `turnLoggingId` | Sensitive | Pseudonymize | Nằm trong JSON `rtcConfig` nhúng ở các dòng `log_detail` của end-call log. Lưu ý: bản thân client **đã tự che một phần** `turnSessionInfo` trong data mẫu (ví dụ `"DE7D****************************0919"`) — sanitizer không được giả định rằng mọi field cần mask vẫn còn ở dạng rõ (plain) khi đến tay mình. |
| Nội dung lỗi hiển thị cho người dùng bằng tiếng Việt (`callErrorMsg`, `callError`) | Internal | Allow | Các dòng `recv_cmd` của end-call log, ví dụ `"Người này hiện chưa thể nhận cuộc gọi"`. Là văn bản tự do nhưng mô tả trạng thái cuộc gọi, không phải dữ liệu cá nhân — giữ mức Internal trừ khi data sau này chèn thông tin định danh vào đây. |
| `callErrorCode` | Internal | Allow | Cùng các dòng trên. Là mã trạng thái dạng số, bản thân không nhạy cảm. |
| Toàn bộ nội dung SDP (`sdpOffer`/`sdpAnswer`) | Sensitive | Minimize | Chứa cả ICE ufrag/pwd, DTLS fingerprint và IP candidate cùng một chỗ — nên coi cả khối này là nhạy cảm cho đến khi được làm sạch, thay vì chỉ regex ra từng phần đã biết là xấu. |
| Văn bản tự do trong log message (`msg`, `message` ở các dòng `LOG_MESSAGE`/`SIGNAL`) | Internal | Minimize | Có thể chứa ID hoặc giá trị config chèn ngay trong câu (ví dụ `deviceId=...` xuất hiện ngay trong message của `log_detail`) — cần được làm sạch giống như các field có cấu trúc, không chỉ làm sạch riêng các field có cấu trúc. |

## Tóm tắt phân loại theo loại dữ liệu

| Phân loại | Ý nghĩa trong dự án này | Ví dụ |
| --- | --- | --- |
| `PUBLIC` | An toàn để hiển thị/ghi log ở bất kỳ đâu | (chưa xác định được loại nào trong data này — mọi thứ phía client đều mang ít nhất context về thiết bị/người dùng) |
| `INTERNAL` | An toàn trong nội bộ team/hệ thống, không gửi thô ra AI provider bên ngoài nếu chưa review | Metrics, ISP/ASN/country, mã lỗi/thông báo lỗi |
| `SENSITIVE` | Bắt buộc phải mask hoặc pseudonymize trước khi ra khỏi hệ thống | ID người dùng/thiết bị/session, IP của client, địa chỉ TURN server |
| `SECRET` | Bắt buộc drop hoàn toàn, không bao giờ log hay gửi đi đâu cả | ICE ufrag/pwd, DTLS fingerprint, mọi credential dài hạn của TURN |

## Ý nghĩa đối với Input Sanitizer của Sprint 2

Input cho AI Analysis Engine (theo mục 3.3, "AI chỉ nhận minimum necessary context") nên
được dựng từ `CallTimeline`/`CallMetrics`/`Evidence` — vốn đã tóm tắt sự kiện thành một
tập attribute nhỏ gọn — thay vì từ chuỗi `rawLine`/`rawEvent` gốc, vì chính các chuỗi gốc
đó là nơi chứa các khối SDP/JSON nhúng (và do đó chứa cả các field mức `SECRET` ở trên).
Sanitizer vẫn cần làm sạch các field `SENSITIVE`/`INTERNAL` được chuyển tiếp (ID, IP),
nhưng việc giữ log gốc nằm ngoài context gửi cho AI ngay từ đầu đã loại bỏ được phần lớn
nguy cơ rò rỉ dữ liệu, đơn giản vì cấu trúc thiết kế đã như vậy.
