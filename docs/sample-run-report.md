# Đánh giá độ chính xác & soát dữ liệu mẫu (Sprint 1)

**Ngày chạy:** 2026-09-26
**Lệnh:** `analyze <callDir>` cho từng cuộc gọi, signaling lấy qua **Elasticsearch thật**
(1.059 event / 20 cuộc gọi, index `call-signaling-events`)
**Test:** 81/81 pass

## Kết quả

| | Trước khi sửa | **Sau khi sửa** |
| --- | --- | --- |
| ✅ Đúng | 7 / 13 | **13 / 13** |
| ⬜ Không kết luận (`UNKNOWN`) | 5 / 13 | **0** |
| ❌ Sai | 1 / 13 | **0** |

Cả **7** cuộc gọi trong `success/` đều ra `SUCCESS`; cả **6** cuộc gọi trong `fail/` đều ra `FAIL`.
Bộ `for_test/` (không nhãn) ra **1 `SUCCESS` + 6 `FAIL`**, không còn ca `UNKNOWN` nào. 0 cảnh báo
parser, 0 exception trên toàn bộ 20 cuộc gọi.

Tài liệu này ghi lại: cách đo (mục 1), soát dữ liệu vào (mục 2), **3 lỗi code đã tìm ra và sửa**
(mục 3), kết quả kiểm chứng lại (mục 4), bộ `for_test` (mục 5), **issue category của từng case**
(mục 6), và các câu còn chờ mentor (mục 8).

---

## 1. Cách đo

Data mẫu gồm 3 thư mục và chúng **không cùng vai trò**:

| Thư mục | Vai trò | Cách đánh giá |
| --- | --- | --- |
| `success/` (7 cuộc gọi) | **Ground truth** — mentor đã xác định là cuộc gọi thành công | Kỳ vọng `SUCCESS`; lệch là sai |
| `fail/` (6 cuộc gọi) | **Ground truth** — mentor đã xác định là cuộc gọi thất bại | Kỳ vọng `FAIL`; lệch là sai |
| `for_test/` (7 cuộc gọi) | **Không có nhãn** | Không đo đúng/sai được; phải tự đọc log thô để xem kết luận có căn cứ |

Nên phép đo độ chính xác chỉ thực hiện được trên **13 cuộc gọi** của `success/` + `fail/`.

`UNKNOWN` **không phải câu trả lời sai** — theo `PROJECT_SPEC.md` mục 3.3, không đủ dữ liệu thì
*phải* trả `UNKNOWN` kèm lý do. Nhưng nó cũng không phải câu trả lời đúng: nó là **từ chối kết
luận**. Vì vậy bảng ở trên tách riêng 3 nhóm, và mục tiêu là giảm nhóm giữa **mà không** đổi lấy
việc đoán bừa.

---

## 2. Soát dữ liệu vào — `sample-data/` có vấn đề gì?

Đã rà toàn bộ 20 thư mục / 58 file, đối chiếu **nội dung thật** với tên file.

### 2.1. Những gì sạch (không cần sửa)

| Kiểm tra | Kết quả |
| --- | --- |
| `callId` trong `signaling.json` vs tên thư mục | **20/20 khớp** |
| Field `role` trong end-call log vs leg suy từ tên file | **17/17 khớp** |
| File rỗng · file lỗi encoding | **0 · 0** |
| Export signaling bị cắt (`truncated`) | **1** — `DE7DD314` (200/201 event), mất 1 event cuối không ảnh hưởng verdict; parser đã cảnh báo đúng chỗ |

### 2.2. Đặc điểm chi phối: phần lớn cuộc gọi thiếu end-call log

Đây **không phải lỗi dữ liệu** — người dùng thật cũng chỉ đính kèm được file họ có:

| Mức độ đầy đủ | Số cuộc gọi |
| --- | --- |
| Có **cả 2** end-call log | 3 / 20 |
| Chỉ có **1** end-call log | 11 / 20 |
| **Không có end-call log nào** (chỉ có webrtc log) | **6 / 20** |

Trước khi sửa, đúng 6 cuộc gọi này là 6 ca `UNKNOWN` — tương ứng 1-1. Tức 100% số ca từ chối kết
luận đến từ một nguyên nhân duy nhất trong code, không phải từ dữ liệu.

### 2.3. Hai file đặt sai tên

| File | Tên gợi ý | Nội dung thật | Hệ thống xử lý |
| --- | --- | --- | --- |
| `success/EE129C8F/calleer_webrtc.log` | webrtc (gõ sai "callee") | webrtc của callee | ✅ Đúng — nhận diện leg theo chuỗi `callee` |
| `for_test/9B556E56/caller_webrtc.log` | webrtc | **end-call log TSV** | ✅ Đúng — nhận diện theo nội dung |

File thứ hai **chưa từng được ghi nhận trước đây**: nó chứa `#H1 #ts #tag appUserId …`, tức end-call
log bị đặt tên thành `_webrtc.log`. Vì bộ nhận diện đọc nội dung chứ không tin tên file, cuộc gọi
này vẫn có end-call log của caller.

> **Kết luận mục 2: dữ liệu vào không có lỗi nào cần sửa.** Vấn đề nằm hoàn toàn ở phía code.

---

## 3. Ba lỗi code — đã sửa

Cả ba **tồn tại từ trước** lần cấu trúc lại code (bản refactor đã được xác minh cho ra output giống
hệt từng byte trên cả 20 cuộc gọi), và được tìm ra nhờ đối chiếu verdict với log thô.

### 3.1. Lỗi 1 — bỏ qua tín hiệu chấm dứt tường minh của signaling

Data mẫu dùng **20 lệnh signaling** khác nhau; `SignalingCommands` chỉ biết **7**. Phần lớn 13 lệnh
còn lại là nhiễu vô hại (`PAIR_PING`, `LOG_STATS`, `ACK_*`), nhưng **hai lệnh mang thông tin quyết
định** lại bị bỏ qua:

| Lệnh | Số event | Số cuộc gọi | Ý nghĩa | Xuất hiện cùng `OK_ACK_OK`? |
| --- | --- | --- | --- | --- |
| `CANCEL` | 96 | **7** | Bên gọi chủ động huỷ | **0 / 7 lần** |
| `FAIL_HARD` | 2 | 1 | Server từ chối cứng | **0 / 1 lần** |

Vì cả hai **chưa bao giờ** đi cùng `OK_ACK_OK`, trong tập dữ liệu này chúng là bằng chứng **tin cậy
100%** cho "cuộc gọi kết thúc trước khi kết nối được" — mà rule engine không dùng. Hậu quả:
`7B56D7AD` (`CANCEL` ×15) và `E9D6C112` (`CANCEL` ×10), **cả hai thuộc `fail/`**, bị trả `UNKNOWN`
chỉ vì thiếu end-call log, dù signaling đã chứng minh dứt khoát.

**Đã sửa:** thêm `ExplicitTerminationRule` (thứ tự 15, **đứng trước** `MissingClientLogsRule`) — có
`CANCEL`/`FAIL_HARD` trước khi đạt `OK_ACK_OK` thì kết luận `FAIL`, không cần log client. Thiếu file
client không làm một bằng chứng đã đủ mạnh yếu đi.

### 3.2. Lỗi 2 — không nhận ra trạng thái ICE trong log định dạng native

`WebRtcLogParser.classify()` chỉ khớp tên callback kiểu iOS (`onIceConnectionChange`). Log native
`peer_connection.cc` lại ghi trạng thái ICE thành câu kể, và còn có một dòng song song chứa chữ
`standardized`:

```text
peer_connection.cc: [004:592][8431] (line 2032): Changing IceConnectionState new => checking
peer_connection.cc: [005:191][8431] (line 2054): Changing standardized IceConnectionState new => checking
peer_connection.cc: [020:195][8431] (line 2032): Changing IceConnectionState checking => failed
```

Chuỗi `iceconnectionstate` **không chứa** `iceconnectionchange`, nên mọi dòng như trên bị phân loại
thành `ENGINE_LOG` vô nghĩa. Quy mô: **16 trong 28 file `*_webrtc.log`** *có* ghi trạng thái ICE mà
hệ thống nhận ra **0** sự kiện — nhánh `ICE_FAILURE` **chưa từng chạy một lần nào**.

**Hậu quả — ca `2D9057AA`, câu trả lời sai duy nhất:**

| Tầng | Quan sát được |
| --- | --- |
| Signaling | `INIT_CALL → INVITE → RINGING → OK → OK_ACK_OK → BYE` — nhìn như cuộc gọi hoàn chỉnh |
| WebRTC (callee) | `checking => failed` tại mốc 20,195s — media bên nhận **không bao giờ lên được** |
| End-call (callee) | MOS = **0.000 trên cả 25 dòng** periodic stats |
| Verdict trước khi sửa | `SUCCESS` + cờ chất lượng kém `UNKNOWN` |

Hệ thống **thấy triệu chứng** (MOS = 0 nên gắn cờ) nhưng **không thấy nguyên nhân** nằm ngay trong
log.

**Đã sửa, 2 phần:**

1. `classify()` nhận thêm `iceconnectionstate` — bắt được cả hai biến thể của dạng native.
2. Thêm `MediaFailedAfterConnectRule` (thứ tự 45): đã `OK_ACK_OK` và có `BYE`, nhưng ICE của một bên
   `=> failed` → `FAIL` / `ICE_FAILURE`.

**Cái bẫy đã tránh:** `IceFailureDetector` trước đây coi cả `disconnected` là lỗi. Nếu giữ nguyên,
việc nhận diện mới sẽ biến **mọi cuộc gọi kết thúc bình thường** thành `ICE_FAILURE` — vì có 3 file
kết thúc bằng `connected => disconnected` và **cả 3 đều thuộc cuộc gọi SUCCESS** (đó là teardown lúc
cúp máy). Nên detector đã được siết lại chỉ nhận `failed`; cả data mẫu chỉ có đúng 1 file như vậy.
Có test chống hồi quy riêng cho điều này.

### 3.3. Lỗi 3 — chỉ end-call log được coi là bằng chứng phía client

`MissingClientLogsRule` coi `*_endcall.log` là nguồn duy nhất chứng minh trạng thái phía client. Lý
lẽ của rule vẫn đúng (signaling là góc nhìn server, không cho biết client có nghe được không —
`2D9057AA` là bằng chứng sống cho điều đó), nhưng `*_webrtc.log` **cũng** là log của thiết bị người
dùng, và `IceConnectionState` còn nói trực tiếp hơn về việc media có lên được hay không.

Ba cuộc gọi `success/` bị chặn bởi rule này đều có bằng chứng ICE đủ rõ:

| Call-ID | Trạng thái ICE trong webrtc log |
| --- | --- |
| `5E0800AE` | caller: `checking => connected` |
| `6A7CE985` | caller **và** callee: `checking => connected` |
| `F3D7914B` | caller: `checking => connected`; callee: `connected => disconnected` (teardown) |

**Đã sửa:** rule nhận cả hai loại log client. Vẫn giữ nguyên `MissingEndCallLogDetector` nên report
tiếp tục nêu rõ thiếu file nào, và độ tin cậy vẫn bị hạ xuống `MEDIUM` — kết luận được nhưng không
giả vờ là dữ liệu đầy đủ.

### 3.4. Chuỗi rule sau khi sửa

```text
10  MissingSignalingDataRule        không có signaling            -> UNKNOWN
15  ExplicitTerminationRule   (mới) CANCEL/FAIL_HARD trước OK_ACK_OK -> FAIL
20  MissingClientLogsRule     (sửa) không có log client nào       -> UNKNOWN
30  FailedToConnectRule             chưa từng CONFIRMED           -> FAIL
40  ConnectedWithoutByeRule         kết nối mà không thấy kết thúc -> UNKNOWN
45  MediaFailedAfterConnectRule (mới) ICE một bên failed          -> FAIL / ICE_FAILURE
50  ConnectedSuccessfullyRule       kết nối và kết thúc bình thường -> SUCCESS
```

---

## 4. Kiểm chứng lại sau khi sửa

### 4.1. `success/` — kỳ vọng SUCCESS hết

| Call-ID | Verdict | Cờ CL | Issue Category | Tin cậy | Đối chiếu |
| --- | --- | --- | --- | --- | --- |
| `5E0800AE` | `SUCCESS` | Không | *(không nêu)* | MEDIUM | ✅ Đúng — kết luận từ ICE `=> connected` ở webrtc log |
| `6A7CE985` | `SUCCESS` | Không | *(không nêu)* | MEDIUM | ✅ Đúng — kết luận từ ICE `=> connected` cả 2 bên |
| `70A1F889` | `SUCCESS` | Không | *(không nêu)* | MEDIUM | ✅ Đúng |
| `C8CF631E` | `SUCCESS` | Không | *(không nêu)* | MEDIUM | ✅ Đúng |
| `DE7DD314` | `SUCCESS` | Không | *(không nêu)* | HIGH | ✅ Đúng |
| `EE129C8F` | `SUCCESS` | Không | *(không nêu)* | HIGH | ✅ Đúng |
| `F3D7914B` | `SUCCESS` | Không | *(không nêu)* | MEDIUM | ✅ Đúng — `disconnected` cuối cùng được hiểu đúng là teardown |

Cả 7 ca đều **không gắn cờ chất lượng** nên **không nêu issue category** — đúng thiết kế: báo một
category ở cuộc gọi hoàn toàn bình thường sẽ bị đọc thành "có vấn đề nhưng không rõ là gì".

**7/7 đúng.**

### 4.2. `fail/` — kỳ vọng FAIL hết

| Call-ID | Verdict | Issue Category | Bằng chứng quyết định category | Tin cậy | Đối chiếu |
| --- | --- | --- | --- | --- | --- |
| `1B009D42` | `FAIL` | `SIGNALING_FAILURE` | *vắng* `OK_ACK_OK`, không có tín hiệu chấm dứt | MEDIUM | ✅ Đúng |
| `2D9057AA` | `FAIL` | **`ICE_FAILURE`** | ICE `checking => failed` (callee) sau khi đã CONFIRMED | MEDIUM | ✅ Đúng — trước khi sửa là `SUCCESS` |
| `703100CF` | `FAIL` | `SIGNALING_FAILURE` | `CANCEL` ×11 tường minh | MEDIUM | ✅ Đúng |
| `7B56D7AD` | `FAIL` | `SIGNALING_FAILURE` | `CANCEL` ×15 tường minh | MEDIUM | ✅ Đúng — trước khi sửa là `UNKNOWN` |
| `D114749E` | `FAIL` | `SIGNALING_FAILURE` | *vắng* `OK_ACK_OK`, không có tín hiệu chấm dứt | MEDIUM | ✅ Đúng |
| `E9D6C112` | `FAIL` | `SIGNALING_FAILURE` | `CANCEL` ×10 tường minh | MEDIUM | ✅ Đúng — trước khi sửa là `UNKNOWN` |

**6/6 đúng.**

### 4.3. Report của ca khó nhất (`2D9057AA`)

Chuỗi evidence đọc được thành câu chuyện, và mốc lỗi ICE nằm đúng vị trí thời gian giữa lúc kết nối
và lúc cúp máy — chứng tỏ việc neo thời gian cho log WebRTC hoạt động đúng:

```text
Kết luận: FAIL
Tóm tắt: Call was confirmed at the signaling layer but ICE connectivity failed for CALLEE,
         so that side never had media - the call did not actually work despite a
         normal-looking signaling flow.

1. [EV001][signaling 23:43:01.013]        INIT_CALL: call setup started
2. [EV002][signaling 23:43:07.787]        OK_ACK_OK: call confirmed, both legs connected
3. [EV003][callee_webrtc.log 23:43:22.031] ICE connectivity failed for CALLEE - that leg never had media
4. [EV004][signaling 23:43:41.679]        BYE from CALLEE

| Sự kiện ICE/TURN (callee) | ICE_CONNECTION_STATE_CHANGE=4 | WebRTC log |
```

---

## 5. Bộ `for_test` — hệ thống trả ra gì

Không có nhãn nên không đo đúng/sai. Cột *Đánh giá độc lập* là kết luận khi tự đọc `signaling.json`
và `*_webrtc.log`. Kết quả: **6 `FAIL` · 1 `SUCCESS` · 0 `UNKNOWN`**.

| Call-ID | Chuỗi signaling | Verdict | Issue Category | Đánh giá độc lập |
| --- | --- | --- | --- | --- |
| `0A6C2821` | `INIT_CALL → CANCEL` | `FAIL` | `SIGNALING_FAILURE` | ✅ Có căn cứ — evidence trích dẫn thẳng `CANCEL`; nhưng bản chất là người dùng huỷ (mục 6.3) |
| `0EC7B700` | `INIT_CALL → INVITE → TRYING → RINGING → FAIL_HARD` | `FAIL` | `SIGNALING_FAILURE` | ✅ Có căn cứ — trích dẫn thẳng `FAIL_HARD`; category **đúng cả bản chất** |
| `271D1FAF` | `INIT_CALL → … → OK_ACK_OK → … → BYE` | `SUCCESS` | *(không nêu)* | ✅ Có đối chứng độc lập — webrtc *cả hai* bên `checking => connected` |
| `311A9B6A` | `INIT_CALL → CANCEL` ×19 | `FAIL` | `SIGNALING_FAILURE` | ✅ Có căn cứ; bản chất là người dùng huỷ |
| `45AA3011` | `INIT_CALL → CANCEL` | `FAIL` | `SIGNALING_FAILURE` | ✅ Có căn cứ; bản chất là người dùng huỷ |
| `9B556E56` | chỉ `INIT_CALL` | `FAIL` | `SIGNALING_FAILURE` | ⚠️ Suy từ sự *vắng mặt* của `OK_ACK_OK`, chỉ 1 evidence — nên hạ tin cậy xuống LOW |
| `AA9791CE` | `INIT_CALL → CANCEL` ×19 | `FAIL` | `SIGNALING_FAILURE` | ✅ Đã hết lệch — trước khi sửa là `UNKNOWN` dù cùng biểu hiện với 3 ca trên |

---

## 6. Issue Category cho từng case

### 6.1. Phân bố trên cả 20 cuộc gọi

| Issue Category | Số ca | Ghi chú |
| --- | --- | --- |
| `SIGNALING_FAILURE` | **11** | Nhưng gộp **3 nguyên nhân khác nhau** — xem 6.3 |
| `ICE_FAILURE` | **1** | `2D9057AA` |
| *(không nêu)* | **8** | 8 cuộc gọi `SUCCESS` không có vấn đề chất lượng |
| `NETWORK_PACKET_LOSS` | 0 | Không ca nào vượt ngưỡng — xem 6.4 |
| `NETWORK_DELAY_JITTER` | 0 | Không ca nào vượt ngưỡng — xem 6.4 |
| `TURN_FAILURE` | 0 | Cố tình chưa tự phát hiện — xem 6.4 |
| `UNKNOWN` | 0 | Không còn ca nào không quy được nguyên nhân |

### 6.2. Bảng đầy đủ 20 case

| # | Call-ID | Nhãn | Verdict | Cờ CL | Issue Category | Bằng chứng quyết định category |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `5E0800AE` | success | `SUCCESS` | Không | *(không nêu)* | kết nối + kết thúc bình thường |
| 2 | `6A7CE985` | success | `SUCCESS` | Không | *(không nêu)* | kết nối + kết thúc bình thường |
| 3 | `70A1F889` | success | `SUCCESS` | Không | *(không nêu)* | kết nối + kết thúc bình thường |
| 4 | `C8CF631E` | success | `SUCCESS` | Không | *(không nêu)* | kết nối + kết thúc bình thường |
| 5 | `DE7DD314` | success | `SUCCESS` | Không | *(không nêu)* | kết nối + kết thúc bình thường |
| 6 | `EE129C8F` | success | `SUCCESS` | Không | *(không nêu)* | kết nối + kết thúc bình thường |
| 7 | `F3D7914B` | success | `SUCCESS` | Không | *(không nêu)* | kết nối + kết thúc bình thường |
| 8 | `1B009D42` | fail | `FAIL` | Không | `SIGNALING_FAILURE` | *vắng* `OK_ACK_OK` |
| 9 | `2D9057AA` | fail | `FAIL` | Không | **`ICE_FAILURE`** | ICE `checking => failed` (callee) |
| 10 | `703100CF` | fail | `FAIL` | Không | `SIGNALING_FAILURE` | `CANCEL` ×11 |
| 11 | `7B56D7AD` | fail | `FAIL` | Không | `SIGNALING_FAILURE` | `CANCEL` ×15 |
| 12 | `D114749E` | fail | `FAIL` | Không | `SIGNALING_FAILURE` | *vắng* `OK_ACK_OK` |
| 13 | `E9D6C112` | fail | `FAIL` | Không | `SIGNALING_FAILURE` | `CANCEL` ×10 |
| 14 | `0A6C2821` | — | `FAIL` | Không | `SIGNALING_FAILURE` | `CANCEL` ×11 |
| 15 | `0EC7B700` | — | `FAIL` | Không | `SIGNALING_FAILURE` | `FAIL_HARD` ×2 |
| 16 | `271D1FAF` | — | `SUCCESS` | Không | *(không nêu)* | kết nối + kết thúc bình thường |
| 17 | `311A9B6A` | — | `FAIL` | Không | `SIGNALING_FAILURE` | `CANCEL` ×19 |
| 18 | `45AA3011` | — | `FAIL` | Không | `SIGNALING_FAILURE` | `CANCEL` ×11 |
| 19 | `9B556E56` | — | `FAIL` | Không | `SIGNALING_FAILURE` | *vắng* `OK_ACK_OK` |
| 20 | `AA9791CE` | — | `FAIL` | Không | `SIGNALING_FAILURE` | `CANCEL` ×19 |

### 6.3. `SIGNALING_FAILURE` đang quá thô: 11 ca, 3 bản chất khác nhau

| Bản chất thật | Số ca | Call-ID | Đây có phải lỗi hệ thống? |
| --- | --- | --- | --- |
| Bên gọi **chủ động huỷ** (`CANCEL`) | **7** | `703100CF`, `7B56D7AD`, `E9D6C112`, `0A6C2821`, `311A9B6A`, `45AA3011`, `AA9791CE` | ❌ **Không** — là hành vi người dùng |
| Server **từ chối cứng** (`FAIL_HARD`) | 1 | `0EC7B700` | ✅ Có — đúng là lỗi tầng signaling |
| **Vắng** `OK_ACK_OK`, không tín hiệu chấm dứt nào | 3 | `1B009D42`, `D114749E`, `9B556E56` | ⚠️ Không xác định được |

Đây là **điểm yếu lớn nhất còn lại của taxonomy**. 7 trong 11 ca mang nhãn
`SIGNALING_FAILURE` thực chất **không phải lỗi hệ thống** — người dùng bấm huỷ. Verdict `FAIL`
thì đúng (cuộc gọi không thiết lập được), nhưng category đang ngụ ý sai nguyên nhân, và với
người đọc report để điều tra sự cố thì đó là thông tin gây nhiễu.

Mục 4.2 của `PROJECT_SPEC.md` chỉ có 6 category và cả 6 đều mô tả **lỗi kỹ thuật**; không có
nhãn nào cho "cuộc gọi không thành do hành vi người dùng". Rule engine vì vậy buộc phải dùng
nhánh fallback. → **câu hỏi số 1 cho mentor** (mục 8).

### 6.4. Ba category chưa dùng — và vì sao đó không phải bug

**`NETWORK_PACKET_LOSS` và `NETWORK_DELAY_JITTER`** chỉ áp dụng cho cuộc gọi `SUCCESS` bị gắn cờ
chất lượng. Đối chiếu chỉ số thật của cả 9 file có periodic stats cho thấy **không ca nào tiệm cận
ngưỡng**:

| Call-ID | Leg | MOS | Packet loss | RTT | Jitter | Vượt ngưỡng |
| --- | --- | --- | --- | --- | --- | --- |
| `70A1F889` | callee | 4.416 | 0.000 % | 104 ms | 0.022 ms | không |
| `C8CF631E` | caller | 4.420 | 0.000 % | 84 ms | 0.011 ms | không |
| `DE7DD314` | callee | 4.422 | 0.000 % | 63 ms | 0.010 ms | không |
| `DE7DD314` | caller | 4.424 | 0.000 % | 54 ms | 0.006 ms | không |
| `EE129C8F` | callee | 4.411 | 0.000 % | 69 ms | 0.018 ms | không |
| `EE129C8F` | caller | 4.421 | 0.000 % | 80 ms | 0.005 ms | không |
| `271D1FAF` | callee | 4.373 | 0.000 % | 18 ms | 0.003 ms | không |
| `271D1FAF` | caller | 4.153 | 0.000 % | 16 ms | 0.012 ms | không |
| `2D9057AA` | callee | **0.000** | 0.000 % | 0 ms | 0.000 ms | **MOS** |

*(Ngưỡng: packet loss > 5%, jitter > 30ms, RTT > 300ms, MOS < 3.5)*

Tám leg đầu cách ngưỡng rất xa — MOS ~4.4 so với sàn 3.5, RTT 16–104ms so với trần 300ms, jitter
~0.01ms so với trần 30ms. Ca duy nhất vượt ngưỡng là `2D9057AA` (MOS = 0), nhưng cuộc gọi đó giờ
được quy đúng về `ICE_FAILURE` — **nguyên nhân gốc**, chứ không phải triệu chứng MOS thấp.

→ Hai category này **chưa được data mẫu kiểm chứng lần nào**. Đó là một **lỗ hổng độ phủ (coverage
gap) cần khai báo**, không phải bug: logic có, ngưỡng có, nhưng chưa có cuộc gọi thật nào để chứng
minh nó chạy đúng. Cần mentor cung cấp thêm ca chất lượng kém, hoặc chờ tập `held-out`.

**`TURN_FAILURE`** cố tình chưa tự phát hiện (Known Limitations): cách so khớp từ khoá tự do cho
TURN từng sinh hàng chục false positive trên cuộc gọi SUCCESS sạch, nên lỗi tầng TURN hiện gộp vào
`ICE_FAILURE`/`SIGNALING_FAILURE`. Data mẫu có 4.481 dòng chứa chữ "TURN" nhưng đều là nhiễu giao
thức bình thường theo từng candidate.

**`UNKNOWN` (với vai trò category)** giờ không còn ca nào — trước khi sửa, `2D9057AA` mang category
này vì hệ thống thấy MOS = 0 mà không biết vì sao. Việc nó biến mất chính là bằng chứng lỗi ICE đã
được sửa đúng chỗ.

---

## 7. Những chỗ chạy đúng như thiết kế

- **Nhận diện theo nội dung cứu được file đặt sai tên** — xem mục 2.3.
- **`OK_ACK_OK` là mốc CONFIRMED đáng tin** — 9/9 cuộc gọi có `OK` đều có đúng 1 `OK_ACK_OK`; không
  ca nào có `ACK_OK` mà thiếu `OK_ACK_OK`. Giả định ánh xạ ghi trong `SignalingCommands` được dữ
  liệu xác nhận.
- **Chọn dòng periodic stats cuối cùng là đúng** — kiểm tra cả 9 file có periodic stats: không file
  nào có dòng cuối = 0 trong khi các dòng trước > 0. Riêng `2D9057AA` MOS = 0 trên *cả* 25 dòng, tức
  media thật sự không hoạt động chứ không phải lỗi chọn dòng.
- **Kỷ luật `N/A` tuyệt đối** — không chỗ nào âm thầm trả 0; mọi ô thiếu dữ liệu đều kèm lý do.
- **Không phụ thuộc nguồn dữ liệu** — chạy qua Elasticsearch và `--from-file` cho kết quả giống hệt.
- **Pipeline sạch** — 0 cảnh báo parser, 0 exception trên toàn bộ 20 cuộc gọi.
- **Nhánh `UNKNOWN` vẫn còn nguyên tác dụng** — nó không còn kích hoạt trên data mẫu vì cả 20 cuộc
  gọi đều có ít nhất một log client, nhưng vẫn có test khẳng định: chỉ có signaling, không có log
  client nào → `UNKNOWN`. Sửa lỗi ở đây là **mở thêm đường kết luận có bằng chứng**, không phải hạ
  tiêu chuẩn.

---

## 8. Câu hỏi cho mentor

1. **Cuộc gọi bị caller chủ động `CANCEL` thì gắn issue category nào?** Đây là câu quan trọng nhất:
   **7/11** ca mang nhãn `SIGNALING_FAILURE` thực chất là người dùng bấm huỷ, **không phải lỗi hệ
   thống** (xem mục 6.3). Verdict `FAIL` khớp ground truth, nhưng category đang ngụ ý sai nguyên nhân.
   Mục 4.2 hiện không có nhãn nào cho hành vi người dùng — cần thêm một category (ví dụ
   `USER_CANCELLED`), hay để nguyên và chấp nhận sai lệch này?
2. **`requestId` có đổi khi signaling gửi lại không?** `0EC7B700` có **15 event `INVITE`** nhưng báo
   **0 lần gửi lại**, vì cả 15 event chung đúng 1 `requestId` và cùng 1 `service`. Nếu thực tế
   retransmit vẫn giữ nguyên `requestId` thì chỉ số này **không bao giờ** phát hiện được gửi lại.
3. **`FAIL_HARD` và `CANCEL` có ngữ nghĩa chính xác là gì?** Xác nhận để chắc chắn việc dùng chúng
   làm bằng chứng trực tiếp là đúng.

Ngoài ra, hai việc nên làm không cần chờ mentor:

- **Hạ độ tin cậy xuống `LOW`** khi kết luận chỉ dựa trên 1 evidence duy nhất (`1B009D42`,
  `D114749E`, `9B556E56` — suy từ sự vắng mặt của `OK_ACK_OK` chứ không có tín hiệu chấm dứt nào).
- **Khai báo lỗ hổng độ phủ** của `NETWORK_PACKET_LOSS`/`NETWORK_DELAY_JITTER` vào Known Limitations:
  logic và ngưỡng đã có nhưng chưa có cuộc gọi thật nào trong data mẫu kiểm chứng (mục 6.4).

---

## Phụ lục A — Output thô bộ `for_test`

Kết quả nguyên văn của `demo sample-data/for_test` (signaling qua Elasticsearch), đã lược bỏ dòng log
khởi động của Spring Boot:

```text
================================================================================
Call folder: sample-data\for_test\0A6C2821-0A19-49F4-9C05-8F8BCEACD64F
# Báo cáo phân tích cuộc gọi
Call-ID: 0A6C2821-0A19-49F4-9C05-8F8BCEACD64F
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Call failed to establish: signaling shows an explicit CANCEL before the call was ever confirmed (no OK_ACK_OK observed).

## Evidence chính
1. [EV001][signaling 2026-09-12 19:40:59.592] INIT_CALL: call setup started
2. [EV002][signaling 2026-09-12 19:41:05.816] CANCEL: call terminated before it was ever confirmed

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (no OK_ACK_OK event found after INIT_CALL for Setup time) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (no INVITE event found) | Signaling |
| Số lần gửi lại INVITE | N/A (no INVITE event found) | Signaling |
| Số lần "No sessions found" | 0 occurrences | Signaling/End Call log |
| Thời gian đổ chuông | N/A (no RINGING event found) | Signaling |
| Thời lượng kết nối | N/A (no OK_ACK_OK event found) | Signaling |
| Bên kết thúc | N/A (no BYE command observed) | Signaling |
| Số lần gửi lại BYE | N/A (no BYE event found) | Signaling |
| MOS (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Packet loss (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| RTT (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Jitter (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (caller) | none observed | WebRTC log |
| MOS (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Packet loss (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| RTT (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Jitter (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (no webrtc log found for CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: SIGNALING_FAILURE

## Đề xuất
- Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.

## Giới hạn dữ liệu
- Missing callee_endcall.log

================================================================================
Call folder: sample-data\for_test\0EC7B700-6B5D-45A3-9BB6-1463B56C9634
# Báo cáo phân tích cuộc gọi
Call-ID: 0EC7B700-6B5D-45A3-9BB6-1463B56C9634
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Call failed to establish: signaling shows an explicit FAIL_HARD before the call was ever confirmed (no OK_ACK_OK observed).

## Evidence chính
1. [EV001][signaling 2026-09-18 05:30:29.450] INIT_CALL: call setup started
2. [EV002][signaling 2026-09-18 05:31:02.861] FAIL_HARD: call terminated before it was ever confirmed

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (no OK_ACK_OK event found after INIT_CALL for Setup time) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | 9.402 s | Signaling |
| Số lần gửi lại INVITE | 0 retransmits | Signaling |
| Số lần "No sessions found" | 0 occurrences | Signaling/End Call log |
| Thời gian đổ chuông | N/A (no OK event found after RINGING for ringing time) | Signaling |
| Thời lượng kết nối | N/A (no OK_ACK_OK event found) | Signaling |
| Bên kết thúc | N/A (no BYE command observed) | Signaling |
| Số lần gửi lại BYE | N/A (no BYE event found) | Signaling |
| MOS (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Packet loss (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| RTT (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Jitter (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (caller) | none observed | WebRTC log |
| MOS (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Packet loss (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| RTT (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Jitter (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (callee) | ICE_CANDIDATE=4 | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: SIGNALING_FAILURE

## Đề xuất
- Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.

## Giới hạn dữ liệu
- Missing caller_endcall.log

================================================================================
Call folder: sample-data\for_test\271D1FAF-26D4-4150-AC3F-9204505BD84B
# Báo cáo phân tích cuộc gọi
Call-ID: 271D1FAF-26D4-4150-AC3F-9204505BD84B
Kết luận: SUCCESS
Cờ chất lượng: Không
Độ tin cậy: HIGH
Tóm tắt: Call established and ended normally with no quality issues detected.

## Evidence chính
1. [EV001][signaling 2026-09-18 15:15:17.755] INIT_CALL: call setup started
2. [EV002][signaling 2026-09-18 15:15:21.596] OK_ACK_OK: call confirmed, both legs connected
3. [EV003][signaling 2026-09-18 15:16:41.954] BYE from CALLER

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | 3.840 s | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (no TRYING event found after INVITE for time to reach callee) | Signaling |
| Số lần gửi lại INVITE | 1 retransmits | Signaling |
| Số lần "No sessions found" | 0 occurrences | Signaling/End Call log |
| Thời gian đổ chuông | 3.032 s | Signaling |
| Thời lượng kết nối | 80.358 s | Signaling |
| Bên kết thúc | CALLER | Signaling |
| Số lần gửi lại BYE | 1 retransmits | Signaling |
| MOS (caller) | 4.15346 | End Call log |
| Packet loss (caller) | 0.0 % | End Call log |
| RTT (caller) | 16.0 ms | End Call log |
| Jitter (caller) | 0.012 ms | End Call log |
| Sự kiện ICE/TURN (caller) | ICE_CONNECTION_STATE_CHANGE=4 | WebRTC log |
| MOS (callee) | 4.37331 | End Call log |
| Packet loss (callee) | 0.0 % | End Call log |
| RTT (callee) | 18.0 ms | End Call log |
| Jitter (callee) | 0.003 ms | End Call log |
| Sự kiện ICE/TURN (callee) | ICE_CONNECTION_STATE_CHANGE=4 | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Không phát hiện vấn đề.

## Đề xuất
- Không có đề xuất bổ sung.

## Giới hạn dữ liệu
- Không có.

================================================================================
Call folder: sample-data\for_test\311A9B6A-0D30-4C30-9E01-9A42E4EF11E6
# Báo cáo phân tích cuộc gọi
Call-ID: 311A9B6A-0D30-4C30-9E01-9A42E4EF11E6
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Call failed to establish: signaling shows an explicit CANCEL before the call was ever confirmed (no OK_ACK_OK observed).

## Evidence chính
1. [EV001][signaling 2026-09-19 08:01:00.142] INIT_CALL: call setup started
2. [EV002][signaling 2026-09-19 08:01:06.802] CANCEL: call terminated before it was ever confirmed

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (no OK_ACK_OK event found after INIT_CALL for Setup time) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (no INVITE event found) | Signaling |
| Số lần gửi lại INVITE | N/A (no INVITE event found) | Signaling |
| Số lần "No sessions found" | 0 occurrences | Signaling/End Call log |
| Thời gian đổ chuông | N/A (no RINGING event found) | Signaling |
| Thời lượng kết nối | N/A (no OK_ACK_OK event found) | Signaling |
| Bên kết thúc | N/A (no BYE command observed) | Signaling |
| Số lần gửi lại BYE | N/A (no BYE event found) | Signaling |
| MOS (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Packet loss (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| RTT (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Jitter (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (caller) | none observed | WebRTC log |
| MOS (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Packet loss (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| RTT (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Jitter (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (no webrtc log found for CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: SIGNALING_FAILURE

## Đề xuất
- Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.

## Giới hạn dữ liệu
- Missing callee_endcall.log

================================================================================
Call folder: sample-data\for_test\45AA3011-1034-4D13-82A4-634A72D432B6
# Báo cáo phân tích cuộc gọi
Call-ID: 45AA3011-1034-4D13-82A4-634A72D432B6
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Call failed to establish: signaling shows an explicit CANCEL before the call was ever confirmed (no OK_ACK_OK observed).

## Evidence chính
1. [EV001][signaling 2026-09-12 04:51:11.274] INIT_CALL: call setup started
2. [EV002][signaling 2026-09-12 04:51:17.602] CANCEL: call terminated before it was ever confirmed

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (no OK_ACK_OK event found after INIT_CALL for Setup time) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (no INVITE event found) | Signaling |
| Số lần gửi lại INVITE | N/A (no INVITE event found) | Signaling |
| Số lần "No sessions found" | 0 occurrences | Signaling/End Call log |
| Thời gian đổ chuông | N/A (no RINGING event found) | Signaling |
| Thời lượng kết nối | N/A (no OK_ACK_OK event found) | Signaling |
| Bên kết thúc | N/A (no BYE command observed) | Signaling |
| Số lần gửi lại BYE | N/A (no BYE event found) | Signaling |
| MOS (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Packet loss (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| RTT (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Jitter (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (caller) | none observed | WebRTC log |
| MOS (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Packet loss (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| RTT (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Jitter (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (no webrtc log found for CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: SIGNALING_FAILURE

## Đề xuất
- Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.

## Giới hạn dữ liệu
- Missing callee_endcall.log

================================================================================
Call folder: sample-data\for_test\9B556E56-24D3-43BA-853D-7972AD009865
# Báo cáo phân tích cuộc gọi
Call-ID: 9B556E56-24D3-43BA-853D-7972AD009865
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Call failed to establish: no OK_ACK_OK (confirmed) event was observed at the signaling layer, and no ICE connection failure evidence was found, so the call is attributed to a signaling-layer failure.

## Evidence chính
1. [EV001][signaling 2026-09-19 08:01:55.680] INIT_CALL: call setup started

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (no OK_ACK_OK event found after INIT_CALL for Setup time) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (no INVITE event found) | Signaling |
| Số lần gửi lại INVITE | N/A (no INVITE event found) | Signaling |
| Số lần "No sessions found" | 0 occurrences | Signaling/End Call log |
| Thời gian đổ chuông | N/A (no RINGING event found) | Signaling |
| Thời lượng kết nối | N/A (no OK_ACK_OK event found) | Signaling |
| Bên kết thúc | N/A (no BYE command observed) | Signaling |
| Số lần gửi lại BYE | N/A (no BYE event found) | Signaling |
| MOS (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Packet loss (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| RTT (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Jitter (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (caller) | N/A (no webrtc log found for CALLER) | WebRTC log |
| MOS (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Packet loss (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| RTT (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Jitter (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (no webrtc log found for CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: SIGNALING_FAILURE

## Đề xuất
- Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.

## Giới hạn dữ liệu
- Missing callee_endcall.log

================================================================================
Call folder: sample-data\for_test\AA9791CE-13D8-4DD7-942D-4D78304FD458
# Báo cáo phân tích cuộc gọi
Call-ID: AA9791CE-13D8-4DD7-942D-4D78304FD458
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Call failed to establish: signaling shows an explicit CANCEL before the call was ever confirmed (no OK_ACK_OK observed).

## Evidence chính
1. [EV001][signaling 2026-09-11 09:49:32.045] INIT_CALL: call setup started
2. [EV002][signaling 2026-09-11 09:49:40.399] CANCEL: call terminated before it was ever confirmed

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (no OK_ACK_OK event found after INIT_CALL for Setup time) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (no INVITE event found) | Signaling |
| Số lần gửi lại INVITE | N/A (no INVITE event found) | Signaling |
| Số lần "No sessions found" | 0 occurrences | Signaling/End Call log |
| Thời gian đổ chuông | N/A (no RINGING event found) | Signaling |
| Thời lượng kết nối | N/A (no OK_ACK_OK event found) | Signaling |
| Bên kết thúc | N/A (no BYE command observed) | Signaling |
| Số lần gửi lại BYE | N/A (no BYE event found) | Signaling |
| MOS (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Packet loss (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| RTT (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Jitter (caller) | N/A (no periodic stats rows found for CALLER (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (caller) | none observed | WebRTC log |
| MOS (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Packet loss (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| RTT (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Jitter (callee) | N/A (no periodic stats rows found for CALLEE (end-call log missing or leg not present)) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (no webrtc log found for CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: SIGNALING_FAILURE

## Đề xuất
- Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.

## Giới hạn dữ liệu
- Missing caller_endcall.log
- Missing callee_endcall.log

```

---

*Bản trình bày trực quan (HTML): [Độ chính xác rule baseline](https://claude.ai/code/artifact/f8665a78-c5ba-4887-9bf6-7ba4373fe8d6)*
