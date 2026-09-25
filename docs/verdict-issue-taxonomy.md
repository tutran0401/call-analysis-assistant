# Verdict & Issue Taxonomy (T5)

Đây là bản đọc-hiểu-được-bằng-tiếng-Việt của package
`com.tutran.callassistant.analysis.verdict` — code (`IssueCategoryRegistry`) mới là nguồn
chân lý; tài liệu này tồn tại để có thể review taxonomy mà không cần đọc Java. Khi sửa 1
bên thì nhớ đồng bộ bên còn lại.

Thứ tự quyết định verdict nằm ở danh sách rule trong `RuleVerdictEngine` (package con
`analysis/verdict/rule/`), và các tiêu chí gắn cờ chất lượng nằm ở `analysis/verdict/quality/`
— mỗi mục trong 2 bảng dưới đây tương ứng đúng một class.

## Verdict (PROJECT_SPEC.md mục 4.1)

| Verdict | Tiêu chí `RuleVerdictEngine` kiểm tra |
| --- | --- |
| `UNKNOWN` | Không có signaling data nào cả; hoặc thiếu end-call log ở **cả hai** bên; hoặc cuộc gọi có vẻ đã kết nối (thấy `OK_ACK_OK`) nhưng chưa từng thấy `BYE` (nhiều khả năng do data bị cắt/thiếu, không phải cuộc gọi thật đang diễn ra) |
| `FAIL` | Có signaling data và ít nhất một bên có end-call log, nhưng chưa từng quan sát được sự kiện `OK_ACK_OK` (đã xác nhận kết nối) |
| `SUCCESS` | Đã thấy `OK_ACK_OK` và sau đó có `BYE` — có thể kèm cờ chất lượng kém (xem bên dưới) |

Cờ chất lượng (chỉ áp dụng cho `SUCCESS`) được gắn khi **một trong hai bên** vượt ngưỡng,
kiểm tra theo thứ tự sau (khớp điều kiện nào trước thì dùng điều kiện đó):

1. `audio.packetLostPercent > 5.0%` → `NETWORK_PACKET_LOSS`
2. `audio.jitter > 30.0ms` HOẶC `transport.currentRttMs > 300.0ms` → `NETWORK_DELAY_JITTER`
3. `audio.audioMos < 3.5` (và không rơi vào 2 điều kiện trên) → `UNKNOWN` (chất lượng rõ
   ràng kém, nhưng không có chỉ số mạng cụ thể nào giải thích được lý do)

Các ngưỡng này là baseline của Sprint 1, **chưa được hiệu chỉnh theo ground truth** (data
mẫu không kèm ground truth). Chúng là cấu hình (`call-analysis.quality-thresholds.*` trong
`application.yml`, bind vào `QualityThresholds`) nên thử một ngưỡng khác không cần sửa code;
phần mô tả điều kiện phát hiện trong `IssueCategoryRegistry` cũng được dựng từ chính các
giá trị đó, nên tài liệu và code không thể nói hai ngưỡng khác nhau — việc tinh chỉnh ngưỡng là công
việc tường minh của Sprint 3 (mục 7.1 T1).

## Issue Category (PROJECT_SPEC.md mục 4.2)

Áp dụng cho cuộc gọi `FAIL` và cuộc gọi `SUCCESS` có cờ chất lượng kém.

### NETWORK_PACKET_LOSS

- **Định nghĩa**: cuộc gọi kết nối được và duy trì kết nối, nhưng chất lượng giảm do mất
  gói tin — không phải lỗi thiết lập kết nối.
- **Triệu chứng**: `audio.packetLostPercent` cao; `audio.audioMos` thấp.
- **Evidence cần có**: periodic stats trong end-call log.
- **Điều kiện phát hiện**: `audio.packetLostPercent > 5%` HOẶC `audio.audioMos < 3.5` ở
  một trong hai bên.
- **Điểm mơ hồ đã biết**: thường đi kèm với vấn đề jitter/RTT; nếu thiếu các field đó thì
  không phải lúc nào cũng phân biệt được đâu là nguyên nhân chính.

### NETWORK_DELAY_JITTER

- **Định nghĩa**: chất lượng giảm do độ trễ/jitter chứ không phải do mất gói trực tiếp.
- **Triệu chứng**: `audio.jitter` cao; `transport.currentRttMs` cao.
- **Evidence cần có**: periodic stats trong end-call log.
- **Điều kiện phát hiện**: `audio.jitter > 30ms` HOẶC `transport.currentRttMs > 300ms`.
- **Điểm mơ hồ đã biết**: field RTT/jitter không phải lúc nào cũng có (tuỳ thiết bị/phiên
  bản); khi thiếu thì không thể phân biệt với `NETWORK_PACKET_LOSS`, và report phải nêu
  rõ điều này thay vì đoán bừa.

### ICE_FAILURE

- **Định nghĩa**: không thiết lập được kết nối media vì quá trình kiểm tra kết nối ICE
  thất bại.
- **Triệu chứng**: `onIceConnectionChange` trong WebRTC log chuyển sang `FAILED`/
  `DISCONNECTED`; state timeline của end-call log không bao giờ đạt `CONFIRMED`.
- **Evidence cần có**: sự kiện `ICE_CONNECTION_STATE_CHANGE` trong WebRTC log; state
  timeline của end-call log.
- **Điều kiện phát hiện**: có sự kiện `ICE_CONNECTION_STATE_CHANGE` với message nhắc đến
  `FAILED`/`DISCONNECTED`.
- **Điểm mơ hồ đã biết**: khó tách biệt với `TURN_FAILURE` nếu thiếu chi tiết theo từng
  candidate pair — xem thêm bên dưới.

### TURN_FAILURE

- **Định nghĩa**: không thiết lập được (hoặc bị suy giảm) kết nối media do lỗi
  allocation/relay của TURN quan sát được từ phía client.
- **Chưa tự động phát hiện được trong Sprint 1.** Phiên bản đầu tiên so khớp từ khóa tự
  do "turn" + "error"/"fail", nhưng cách này báo nhầm các phản hồi giao thức TURN bình
  thường, tự phục hồi được thành lỗi thật — đã xác nhận trên một cuộc gọi `SUCCESS` thật,
  sạch, ra tới 26/16 false positive (đây là bước "thách thức" (challenge) tiêu chuẩn của
  cơ chế credential dài hạn, và các lần thử lại xin quyền (permission) đơn giản bị bỏ để
  chuyển sang candidate pair khác đang hoạt động tốt). Hiện tại lỗi này được gộp vào
  `ICE_FAILURE` (qua trạng thái kết thúc ICE đáng tin cậy) hoặc `SIGNALING_FAILURE`. Một
  bộ phát hiện TURN thật sự — có lẽ cần theo dõi trạng thái theo từng candidate pair chứ
  không phải so khớp từ khóa từng dòng — được để lại làm Known Limitations.
- **Dù sao cũng ngoài phạm vi** (PROJECT_SPEC.md mục 9): chẩn đoán nghẽn/quá tải TURN
  server, kể cả khi đã phát hiện được lỗi TURN phía client một cách đáng tin cậy.

### SIGNALING_FAILURE

- **Định nghĩa**: cuộc gọi không thiết lập được do vấn đề ở tầng signaling: timeout, gửi
  lại quá nhiều lần, hoặc không tìm thấy session của callee.
- **Triệu chứng**: gửi lại INVITE/BYE nhiều lần; "No sessions found" trong end-call log;
  `callErrorCode` khác 0 và cuộc gọi kết thúc trước khi đạt `CONFIRMED`.
- **Evidence cần có**: log retry/timeout ở signaling log; state timeline/log_detail của
  end-call log.
- **Điều kiện phát hiện**: đây là **category FAIL mặc định** của rule engine Sprint 1 khi
  chưa từng thấy `OK_ACK_OK` và không có evidence lỗi ICE — tức cuộc gọi chưa đi đủ xa để
  có thể xảy ra vấn đề ở tầng media.
- **Điểm mơ hồ đã biết**: `callErrorCode` khác 0 có thể chỉ phản ánh hành động hợp lệ của
  người dùng (bận, từ chối) chứ không phải lỗi hệ thống — ví dụ cuộc gọi mẫu thật
  `1B009D42-49CD-479E-B26C-3A2994AEB720` bị từ chối với `callErrorCode: 428` ("Người này
  hiện chưa thể nhận cuộc gọi") ngay trước khi kịp gửi INVITE. Evidence cần được đọc theo
  ngữ cảnh, không nên coi là bằng chứng lỗi kỹ thuật.

### UNKNOWN

- Không đủ evidence để gán vào một category cụ thể.

## Một điểm bất thường trong data thật đã ảnh hưởng đến thiết kế này

Số hiệu schema `#HN` trong end-call log (xem `sample-data/sample.md`) **không phải** một định danh cố
định: đúng một schema logic (ví dụ "call summary", hay bản ghi periodic quality stats
~150 field) lại xuất hiện dưới các số *khác nhau* giữa log của caller và callee trong
**cùng một cuộc gọi mẫu** (`sample-data/success/DE7DD314-F432-45CB-BCB4-AE9103CC0919`). Vì vậy
`EndCallLogParser` và `EndCallSchemaClassifier` phân loại từng header theo **tên các
field mà nó khai báo**, chứ không bao giờ theo số hiệu — cùng nguyên tắc "nhận diện theo
nội dung, không theo tên/số có thể thay đổi" đã dùng để nhận diện loại file.
