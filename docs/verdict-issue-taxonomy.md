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
| `UNKNOWN` | Không có signaling data nào cả; hoặc không có log client nào (end-call **lẫn** WebRTC) ở cả hai bên; hoặc cuộc gọi có vẻ đã kết nối (thấy `OK_ACK_OK`) nhưng chưa từng thấy `BYE` (nhiều khả năng do data bị cắt/thiếu, không phải cuộc gọi thật đang diễn ra) |
| `FAIL` | Một trong ba trường hợp: (a) signaling ghi rõ `CANCEL`/`FAIL_HARD` trước khi đạt `OK_ACK_OK` — bằng chứng này đủ mạnh nên **không cần** log client; (b) có log client và chưa từng quan sát được `OK_ACK_OK`; (c) đã đạt `OK_ACK_OK` và có `BYE`, nhưng log WebRTC cho thấy ICE của một bên `=> failed` nên bên đó không hề có media |
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

**Mọi report đều nêu đúng một issue category**, kể cả cuộc gọi thành công hoàn toàn — khi đó là
`NONE`. Mục 4.2 của spec mô tả category là chỉ áp dụng cho `FAIL` và `SUCCESS` có cờ chất lượng kém;
`NONE` là phần **mở rộng so với spec** để mục "Vấn đề chất lượng / nguyên nhân khả dĩ" của report
không bao giờ rỗng — **cần mentor xác nhận**.

Lý do mở rộng: khi mục này để trống, người đọc không phân biệt được "hệ thống đã kiểm tra đủ và
không tìm thấy vấn đề" với "hệ thống chưa kiểm tra / không có dữ liệu để kiểm tra".

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

- **Định nghĩa**: không thiết lập được (hoặc suy giảm) kết nối media vì client không đi hết được vòng
  request/response với TURN server.
- **Triệu chứng**: không tạo được socket tới TURN server (chưa gửi đi request nào); hoặc đã gửi
  request allocate nhưng không nhận được phản hồi nào.
- **Evidence cần có**: WebRTC log — `TURN_SOCKET_ERROR`, `TURN_ALLOCATE_REQUEST`,
  `TURN_ALLOCATE_RESPONSE`.
- **Điều kiện phát hiện**: có `TURN_SOCKET_ERROR`; HOẶC có request allocate gửi đi mà không có phản
  hồi nào.
- **Cố tình KHÔNG dựa vào việc đếm lỗi**: mọi cuộc gọi SUCCESS trong data mẫu đều sẵn có 20 dòng
  `TURN probe error response` và 4 dòng `Received TURN allocate error response` — đó là thử thách
  credential dài hạn và nhiễu dò đường theo từng candidate, hoàn toàn bình thường. Bản phát hiện đầu
  tiên so khớp từ khoá tự do đã bị gỡ vì gắn nhầm cho cả 7 cuộc gọi tốt.
- **Điểm mơ hồ đã biết**: theo mục 9 của spec, chẩn đoán nghẽn hay quá tải TURN server nằm ngoài phạm
  vi — ở đây chỉ kết luận được là client không đi hết được vòng request/response, chứ không nói được
  lỗi nằm ở phía server hay phía mạng của thiết bị.

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

### NONE

- **Định nghĩa**: đã đối chiếu với mọi điều kiện phát hiện ở trên và không điều kiện nào khớp —
  cuộc gọi kết nối được, kết thúc bình thường, và không chỉ số chất lượng nào vượt ngưỡng.
- **Evidence cần có**: signaling đạt `OK_ACK_OK` rồi `BYE`; mọi chỉ số chất lượng đang có đều trong
  ngưỡng.
- **Điều kiện phát hiện**: không category nào khác khớp VÀ verdict là `SUCCESS` không kèm cờ chất
  lượng.
- **Điểm mơ hồ đã biết**: không có evidence không có nghĩa là không có vấn đề. Một bên thiếu
  end-call log thì không đóng góp chỉ số chất lượng nào, nên `NONE` chỉ có nghĩa "không tìm thấy gì
  trong phần dữ liệu thực sự có" — phải đọc kèm mục *Giới hạn dữ liệu* của report.
- **Khác `UNKNOWN` thế nào**: `NONE` là kết luận khẳng định (cuộc gọi tốt); `UNKNOWN` là thừa nhận
  không quy được nguyên nhân. Gộp hai cái làm một sẽ khiến cuộc gọi hoàn hảo bị đọc thành "có vấn đề
  nhưng không rõ là gì".

### UNKNOWN

- Có dấu hiệu bất thường hoặc thiếu dữ liệu, nhưng không đủ evidence để gán vào một category cụ thể.
- Không dùng cho cuộc gọi sạch — trường hợp đó là `NONE`.

## Một điểm bất thường trong data thật đã ảnh hưởng đến thiết kế này

Số hiệu schema `#HN` trong end-call log (xem `sample-data/sample.md`) **không phải** một định danh cố
định: đúng một schema logic (ví dụ "call summary", hay bản ghi periodic quality stats
~150 field) lại xuất hiện dưới các số *khác nhau* giữa log của caller và callee trong
**cùng một cuộc gọi mẫu** (`sample-data/success/DE7DD314-F432-45CB-BCB4-AE9103CC0919`). Vì vậy
`EndCallLogParser` và `EndCallSchemaClassifier` phân loại từng header theo **tên các
field mà nó khai báo**, chứ không bao giờ theo số hiệu — cùng nguyên tắc "nhận diện theo
nội dung, không theo tên/số có thể thay đổi" đã dùng để nhận diện loại file.
