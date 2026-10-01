# Kết quả chạy thử trên data mẫu (Sprint 1)

**Ngày chạy:** 2026-09-26, xác nhận lại 2026-09-29
**Lệnh:** `analyze <callDir>` cho từng cuộc gọi, signaling lấy qua **Elasticsearch thật**
(1.059 event / 20 cuộc gọi, index `call-signaling-events`)
**Test:** 112/112 pass · 0 cảnh báo parser · 0 exception · 0 vi phạm JSON schema

> **Xác nhận lại 2026-10-01:** chạy lại `mvn test` (112/112 pass: 104 cũ + 4 test hiệu chỉnh lệch đồng hồ + 4 test Elasticsearch) và `demo` trên cả 20
> cuộc gọi qua đường `--from-file` (Elasticsearch không chạy sẵn trên máy soạn báo cáo). Verdict,
> category và toàn bộ chỉ số của cả 20 cuộc gọi khớp 100% với dữ liệu ở tài liệu này — kể cả 7 cuộc
> gọi tập `for_test` ở Phụ lục A giống hệt từng byte. Phát sinh đúng 1 thông tin mới: `DE7DD314` tự
> báo signaling export bị cắt bớt (200/201 event) — xem ghi chú ở bảng mục 2; không ảnh hưởng verdict.

## Cách đọc tài liệu này

> **Data mẫu không kèm nhãn.** `sample-data/` chỉ có log và `sample.md` (đặc tả định dạng) — không có
> file ground truth nào. Tên ba thư mục `success/`, `fail/`, `for_test/` là **cách tổ chức data mẫu
> để chạy thử**, không phải nhãn verdict của mentor. Theo `PROJECT_SPEC.md` mục 1.4, ground truth sẽ
> do mentor cung cấp riêng; tới thời điểm này chưa có.

Vì vậy tài liệu này **không đưa ra con số accuracy nào**. Thay vào đó, với mỗi cuộc gọi nó trả lời:
hệ thống kết luận gì, và **kết luận đó có được bằng chứng trong log chống lưng hay không** — kiểm
bằng cách tự đọc lại `signaling.json` và `*_webrtc.log` / `*_endcall.log` thô.

Trọng tâm là tập **`for_test/`** (mục 1). 13 cuộc gọi còn lại được chạy thêm để mở rộng độ phủ
(mục 2). Mục 3 ghi lại 5 lỗi code tìm ra trong quá trình chạy và đã sửa.

---

## 1. Tập `for_test/` — 7 cuộc gọi

Kết quả: **6 `FAIL` · 1 `SUCCESS` có cờ chất lượng · 0 `UNKNOWN`**.

| Call-ID | File đính kèm | Kết luận | Category | Tin cậy | Căn cứ chính |
| :---- | :---- | :---- | :---- | :---- | :---- |
| `0A6C2821` | End call + WebRTC phía caller | FAIL | `TURN_FAILURE` | MEDIUM | Không tạo được socket TURN ở cả 8 port, chưa gửi đi request nào; client state dừng ở `INVITE_SENT`; `CANCEL` chỉ là hệ quả |
| `0EC7B700` | End call + WebRTC phía callee, WebRTC phía caller | FAIL | `SIGNALING_FAILURE` | MEDIUM | `FAIL_HARD` sau RINGING; client state của callee dừng ở `ANSWERED` |
| `271D1FAF` | Đủ 4 file | SUCCESS + cờ | `NETWORK_PACKET_LOSS` | HIGH | Thiết lập và kết thúc bình thường; mất gói đỉnh 11,32% (caller) / 7,55% (callee), 18/80 mẫu vượt ngưỡng 5% |
| `311A9B6A` | End call + WebRTC phía caller | FAIL | `SIGNALING_FAILURE` | MEDIUM | `CANCEL` ×19; TURN **vẫn có phản hồi** (40 request, 4 response) nên không quy về TURN |
| `45AA3011` | End call + WebRTC phía caller | FAIL | `TURN_FAILURE` | MEDIUM | Giống `0A6C2821` — cùng máy, cùng hình thái lỗi socket |
| `9B556E56` | 1 file tên `caller_webrtc.log`, nội dung thật là **end call log** | FAIL | `SIGNALING_FAILURE` | MEDIUM | Chỉ có `INIT_CALL`, không sự kiện kết thúc nào; client state dừng ở `WAITING_INIT_CALL` |
| `AA9791CE` | WebRTC phía caller | FAIL | `TURN_FAILURE` | MEDIUM | Gửi 60 request allocate tới TURN, **0 phản hồi**; không có end call log để xác nhận thêm |

### 1.1. Đối chiếu độc lập với log thô

Tự đọc lại log để kiểm từng kết luận, không dựa vào output của hệ thống:

| Kiểm tra | Kết quả |
| --- | --- |
| `0A6C2821`/`45AA3011` thật sự không gửi được request TURN? | ✓ 24 dòng lỗi TURN, **0 dòng** `allocate request sent` |
| `311A9B6A` TURN có thật sự hoạt động? | ✓ 40 request gửi đi, **4 phản hồi** nhận về |
| `AA9791CE` thật sự không có phản hồi? | ✓ 120 dòng request, **0 dòng** response |
| `271D1FAF` mất gói có thật vượt ngưỡng? | ✓ đỉnh 11,3208% / 7,54717%; 18 mẫu > 5% |
| `9B556E56` file `caller_webrtc.log` có đúng là end-call log? | ✓ nội dung bắt đầu bằng `#H1 #ts #tag appUserId …` |

Cả 5 kết luận đều có bằng chứng chống lưng.

### 1.2. Ba nhận xét

- **`0A6C2821` và `45AA3011` cùng một máy** — cùng dải IP `192.168.26.x`, cùng 4 TURN server, cùng
  hình thái lỗi socket, và cùng có VPN `tun0`. Cuộc gọi `703100CF` cũng vậy. Đây là **dữ kiện**,
  chưa đủ để kết luận VPN là nguyên nhân vì mới thấy trên một máy.
- **`311A9B6A` là ca phân định quan trọng nhất**: nó có 24 dòng lỗi TURN — nhiều bằng `0A6C2821` —
  nhưng vẫn nhận được phản hồi. Nếu phát hiện TURN bằng cách đếm lỗi thì ca này bị gắn sai. Xem 3.5.
- **`9B556E56` chỉ có 2 evidence** và không có tín hiệu chấm dứt nào; kết luận `FAIL` suy từ *sự vắng
  mặt* của `OK_ACK_OK`. Đây là kết luận yếu nhất trong cả 20 cuộc — độ tin cậy nên là `LOW` (mục 5).

---

## 2. 13 cuộc gọi còn lại — chạy mở rộng độ phủ

Hai thư mục `success/` và `fail/` được chạy thêm để có thêm hình thái dữ liệu, **không dùng làm thước
đo đúng/sai**.

| Thư mục | Call-ID | Kết luận | Category | Tin cậy | Căn cứ chính |
| --- | --- | --- | --- | --- | --- |
| `success/` | `5E0800AE` | SUCCESS | `NONE` | MEDIUM | Thiết lập + kết thúc bình thường; ICE `=> connected` (caller) |
| `success/` | `6A7CE985` | SUCCESS | `NONE` | MEDIUM | ICE `=> connected` cả hai bên |
| `success/` | `70A1F889` | SUCCESS | `NONE` | MEDIUM | — |
| `success/` | `C8CF631E` | SUCCESS | `NONE` | MEDIUM | — |
| `success/` | `DE7DD314` | SUCCESS | `NONE` | HIGH | Đủ 4 file; signaling export tự báo `truncated` (200/201 event) — không ảnh hưởng verdict |
| `success/` | `EE129C8F` | SUCCESS | `NONE` | HIGH | Đủ file; có 1 file gõ sai tên `calleer_webrtc.log` |
| `success/` | `F3D7914B` | SUCCESS | `NONE` | MEDIUM | ICE kết thúc `connected => disconnected` (teardown bình thường) |
| `fail/` | `1B009D42` | FAIL | `SIGNALING_FAILURE` | MEDIUM | Vắng `OK_ACK_OK`; client state dừng ở `WAITING_INIT_CALL` |
| `fail/` | `2D9057AA` | FAIL | `ICE_FAILURE` | MEDIUM | Signaling đủ `OK_ACK_OK`…`BYE` nhưng ICE callee `=> failed`, MOS = 0 trên cả 25 dòng |
| `fail/` | `703100CF` | FAIL | `TURN_FAILURE` | MEDIUM | Không tạo được socket TURN |
| `fail/` | `7B56D7AD` | FAIL | `TURN_FAILURE` | MEDIUM | 20 request allocate, 0 phản hồi |
| `fail/` | `D114749E` | FAIL | `SIGNALING_FAILURE` | MEDIUM | Vắng `OK_ACK_OK`, không tín hiệu chấm dứt |
| `fail/` | `E9D6C112` | FAIL | `TURN_FAILURE` | MEDIUM | 40 request allocate, 0 phản hồi |

**Ca đáng chú ý nhất là `2D9057AA`**: chỉ nhìn signaling thì đây là cuộc gọi hoàn hảo
(`INIT_CALL → INVITE → RINGING → OK → OK_ACK_OK → BYE`). Chỉ webrtc log của callee mới tiết lộ ICE
`checking => failed` tại mốc 20,195s, và end-call log xác nhận MOS = 0 trên **cả 25 dòng** periodic
stats — bên nhận chưa bao giờ có media. Đây là loại lỗi mà chỉ tầng signaling không bao giờ thấy được.

---

## 3. Năm lỗi code tìm ra khi chạy — đã sửa

Cả năm **tồn tại từ trước** lần cấu trúc lại code (bản refactor đã được xác minh cho ra output giống
hệt từng byte), và đều được tìm ra bằng cách đối chiếu kết luận với log thô.

### 3.1. Bỏ qua tín hiệu chấm dứt tường minh của signaling

Data mẫu dùng **20 lệnh signaling**; `SignalingCommands` chỉ biết **7**. Phần lớn 13 lệnh còn lại là
nhiễu vô hại (`PAIR_PING`, `LOG_STATS`, `ACK_*`), nhưng hai lệnh mang thông tin quyết định bị bỏ qua:

| Lệnh | Số event | Số cuộc gọi | Xuất hiện cùng `OK_ACK_OK`? |
| --- | --- | --- | --- |
| `CANCEL` | 96 | 7 | **0/7 lần** |
| `FAIL_HARD` | 2 | 1 | **0/1 lần** |

Vì chưa bao giờ đi cùng `OK_ACK_OK`, trong tập này chúng là bằng chứng đáng tin cho "cuộc gọi kết
thúc trước khi kết nối được". Trước khi sửa, hai cuộc gọi có `CANCEL` tường minh vẫn bị trả `UNKNOWN`
chỉ vì thiếu end-call log.

**Đã sửa:** thêm `ExplicitTerminationRule` (thứ tự 15, đứng trước `MissingClientLogsRule`).

### 3.2. Không nhận ra trạng thái ICE trong log định dạng native

`WebRtcLogParser.classify()` chỉ khớp callback kiểu iOS (`onIceConnectionChange`). Log native
`peer_connection.cc` ghi thành câu kể:

```text
peer_connection.cc: [020:195][8431] (line 2032): Changing IceConnectionState checking => failed
```

Chuỗi `iceconnectionstate` không chứa `iceconnectionchange` → mọi dòng như trên thành `ENGINE_LOG`.
Quy mô: **16/28 file webrtc** *có* ghi trạng thái ICE mà hệ thống nhận ra **0** sự kiện.

**Đã sửa:** nhận thêm `iceconnectionstate`; thêm `MediaFailedAfterConnectRule`. **Bẫy đã tránh:** 3
file kết thúc bằng `connected => disconnected` và cả 3 đều là cuộc gọi thành công — đó là teardown
lúc cúp máy, nên detector chỉ nhận `failed`.

### 3.3. Chỉ end-call log được coi là bằng chứng phía client

`*_webrtc.log` cũng là log của thiết bị người dùng (spec mục 1.2), và `IceConnectionState` còn nói
trực tiếp hơn về việc media có lên được hay không. Trước khi sửa, **6/20 cuộc gọi** bị trả `UNKNOWN`
dù có webrtc log đủ rõ.

**Đã sửa:** `MissingClientLogsRule` chấp nhận cả hai loại log client.

### 3.4. Chỉ số chất lượng lấy dòng cuối, giấu mất cuộc gọi kém

`LegQualityCalculator` chỉ đọc **dòng periodic stats cuối cùng** — mẫu ít đại diện nhất, vì lúc đó
stream đã tắt nên mọi chỉ số về 0.

| `271D1FAF` | Dòng cuối (cũ) | Tệ nhất cả cuộc (mới) |
| --- | --- | --- |
| Packet loss caller | 0,000 % | **11,32 %** |
| Packet loss callee | 0,000 % | **7,55 %** |
| Số mẫu vượt ngưỡng 5% | — | **18 / 80** |

**Đã sửa:** lấy giá trị tệ nhất cả cuộc (MOS nhỏ nhất, loss/RTT/jitter lớn nhất); tên dòng chỉ số đổi
theo cho rõ (`Packet loss cao nhất (caller)`).

### 3.5. Không phát hiện được lỗi TURN

`TURN_FAILURE` trước đây là Known Limitation vì bản đầu so khớp từ khoá tự do và báo nhầm hàng loạt.
Lý do rõ khi nhìn data: **mọi cuộc gọi thành công đều sẵn có** 20 dòng `TURN probe error response` và
4 dòng `Received TURN allocate error response` — nhiễu giao thức bình thường.

**Tín hiệu thật không phải "có lỗi hay không", mà là client có đi hết vòng request/response hay không:**

| Socket lỗi | Request gửi đi | Phản hồi nhận về | Kết luận |
| --- | --- | --- | --- |
| có | 0 | 0 | `TURN_FAILURE` — không tạo được socket |
| không | > 0 | 0 | `TURN_FAILURE` — gửi đi nhưng không ai trả lời |
| không | > 0 | > 0 | TURN hoạt động — **không** phải lỗi |

Đối chiếu trên cả 28 file webrtc: **13 file của cuộc gọi thành công → 0 ca bị gắn `TURN_FAILURE`**.

**Đã sửa:** `WebRtcLogParser` phân loại 3 sự kiện TURN; `TurnFailureDetector` ghép thành chẩn đoán;
`FailureCauseClassifier` (dùng chung cho 2 rule FAIL) chọn category theo thứ tự *nguyên nhân gốc
trước triệu chứng*: **TURN → ICE → SIGNALING**.

Nhờ thứ tự này, `0A6C2821` ra `TURN_FAILURE` thay vì `SIGNALING_FAILURE`: caller bấm `CANCEL` **vì**
app không gom nổi candidate do TURN hỏng — `CANCEL` là hệ quả, TURN mới là nguyên nhân.

Có **4 test chống hồi quy**, trong đó một test quét mọi cuộc gọi thành công để chắc chắn không tái
diễn false positive.

### 3.6. Chuỗi rule sau khi sửa

```text
10  MissingSignalingDataRule        không có signaling              -> UNKNOWN
15  ExplicitTerminationRule   (mới) CANCEL/FAIL_HARD trước OK_ACK_OK -> FAIL
20  MissingClientLogsRule     (sửa) không có log client nào         -> UNKNOWN
30  FailedToConnectRule             chưa từng CONFIRMED             -> FAIL
40  ConnectedWithoutByeRule         kết nối mà không thấy kết thúc   -> UNKNOWN
45  MediaFailedAfterConnectRule (mới) ICE một bên failed            -> FAIL / ICE_FAILURE
50  ConnectedSuccessfullyRule       kết nối và kết thúc bình thường  -> SUCCESS

Rule 15 và 30 cùng hỏi FailureCauseClassifier để chọn category:
   TURN_FAILURE  ->  ICE_FAILURE  ->  SIGNALING_FAILURE (fallback)
```

---

## 4. Issue Category trên cả 20 cuộc gọi

**Mọi case đều nêu một category — không case nào để trống.** Sau 5 lần sửa, **5 trên 6** category của
mục 4.2 đã được data mẫu chạm tới:

| Issue Category | Số ca | Ghi chú |
| --- | --- | --- |
| `NONE` | **7** | Cuộc gọi thành công, không phát hiện vấn đề — xem 4.1 |
| `TURN_FAILURE` | **6** | 3 ca không tạo được socket, 3 ca gửi request không phản hồi |
| `SIGNALING_FAILURE` | **5** | `FAIL_HARD` ×1, `CANCEL` ×1, vắng `OK_ACK_OK` ×3 |
| `ICE_FAILURE` | **1** | `2D9057AA` |
| `NETWORK_PACKET_LOSS` | **1** | `271D1FAF` |
| `NETWORK_DELAY_JITTER` | 0 | Không ca nào vượt ngưỡng — xem 4.3 |
| `UNKNOWN` | 0 | Không còn ca nào không quy được nguyên nhân |

### 4.1. `NONE` — category cho cuộc gọi không có vấn đề

Trước đây cuộc gọi sạch để **trống** mục *"Vấn đề chất lượng / nguyên nhân khả dĩ"*, nên không phân
biệt được "đã kiểm tra, không có vấn đề" với "chưa kiểm tra". Nay:

```text
- Chính: NONE - không phát hiện vấn đề chất lượng nào
```

`NONE` khác `UNKNOWN`: `NONE` là kết luận khẳng định, `UNKNOWN` là thừa nhận không quy được nguyên nhân.

> ⚠️ **Cần nêu với mentor:** mục 4.2 mô tả category là "áp dụng cho `FAIL` và `SUCCESS` có cờ chất
> lượng kém", và bảng 6 category không có `NONE`. Việc luôn nêu category là **mở rộng so với spec**.

### 4.2. `SIGNALING_FAILURE` còn gộp 3 bản chất khác nhau

Sau khi tách được `TURN_FAILURE`, nhóm này giảm từ 11 xuống 5 ca, nhưng vẫn gộp:

| Bản chất thật | Số ca | Call-ID | Có phải lỗi hệ thống? |
| --- | --- | --- | --- |
| Server **từ chối cứng** (`FAIL_HARD`) | 1 | `0EC7B700` | ✅ Có |
| Người dùng **chủ động huỷ** (`CANCEL`), không lỗi media | 1 | `311A9B6A` | ❌ **Không** — hành vi người dùng |
| **Vắng** `OK_ACK_OK`, không tín hiệu chấm dứt | 3 | `1B009D42`, `D114749E`, `9B556E56` | ⚠️ Không xác định được |

Mục 4.2 không có nhãn cho hành vi người dùng → **câu hỏi 1 cho mentor**.

### 4.3. Category chưa dùng: `NETWORK_DELAY_JITTER`

Jitter cao nhất trong cả data mẫu là **0,022 ms** so với trần 30 ms — cách ngưỡng hơn 1000 lần; RTT
cao nhất 115 ms so với trần 300 ms. Đây là **lỗ hổng độ phủ cần khai báo**, không phải bug: logic và
ngưỡng đã có nhưng chưa có cuộc gọi thật nào chứng minh nó chạy đúng.

---

## 5. Những điểm còn yếu

- **Độ tin cậy mới chỉ dựa vào độ đầy đủ file.** `DataCompletenessConfidencePolicy` trả `MEDIUM` cho
  mọi cuộc gọi thiếu file, bất kể evidence mạnh hay yếu. Mục 7.2 của spec yêu cầu derive từ *số lượng
  và độ mạnh evidence* — đó là việc của Sprint 3 T3. Hệ quả thấy ngay trong bảng: `0A6C2821` (4
  evidence, lỗi TURN dứt khoát) và `9B556E56` (2 evidence, suy từ sự vắng mặt) cùng là `MEDIUM`.
- **`9B556E56` nên là `LOW`** — kết luận chỉ dựa trên sự vắng mặt của `OK_ACK_OK`.
- **`271D1FAF` gắn cờ dựa trên đỉnh, không phải trung bình.** Trung bình cả cuộc là 1,77% (dưới
  ngưỡng), đỉnh 11,32% (trên ngưỡng) — hai cách tổng hợp cho hai kết luận trái ngược về cùng cuộc gọi.
- **Chưa phân định được lỗi TURN nằm ở server hay ở mạng thiết bị** — ngoài phạm vi theo mục 9.

---

## 6. Câu hỏi cho mentor

1. **Cuộc gọi bị caller chủ động `CANCEL` (không kèm lỗi media) gắn issue category nào?** Mục 4.2
   không có nhãn cho hành vi người dùng, nên rule engine đang phải dùng `SIGNALING_FAILURE` — ngụ ý
   lỗi hệ thống.
2. **Chỉ số chất lượng nên tổng hợp "tệ nhất cả cuộc" hay "trung bình cả cuộc"?** Ảnh hưởng trực tiếp
   tới `271D1FAF` (mục 5).
3. **`FAIL_HARD` và `CANCEL` có ngữ nghĩa chính xác là gì?** Xác nhận để chắc chắn việc dùng chúng làm
   bằng chứng trực tiếp là đúng.
4. **Việc luôn nêu category (thêm `NONE`) có chấp nhận được không?** Đây là mở rộng so với mục 4.2.
5. **Ba cuộc gọi `TURN_FAILURE` dạng "không tạo được socket" đều từ một máy có VPN `tun0`**
   (`0A6C2821`, `45AA3011`, `703100CF`) — mentor có dữ liệu nào xác nhận VPN là nguyên nhân không?
6. **Khi nào có ground truth?** Chưa có nhãn nên chưa đo được Verdict Accuracy (mục 6.5) — hiện chỉ
   kiểm được "kết luận có bằng chứng chống lưng hay không".

---

## Phụ lục A — Output thô tập `for_test`

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
Tóm tắt: Cuộc gọi không thiết lập được: Lỗi TURN phía CALLER: không tạo được socket tới TURN server nên chưa gửi đi được request nào. Signaling ghi nhận CANCEL - đó là hệ quả, không phải nguyên nhân gốc.

## Evidence chính
1. [EV001][signaling 2026-09-12 19:40:59.592] INIT_CALL: bắt đầu thiết lập cuộc gọi
2. [EV002][signaling 2026-09-12 19:41:05.816] CANCEL: cuộc gọi bị chấm dứt trước khi kịp được xác nhận
3. [EV003][caller_endcall.log 2026-09-12 19:41:04.609] Client state timeline của CALLER dừng ở INVITE_SENT - chưa bao giờ đạt CONFIRMED
4. [EV004][caller_webrtc.log 2026-09-12 19:40:58.661] Lỗi TURN phía CALLER: không tạo được socket tới TURN server nên chưa gửi đi được request nào

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (không tìm thấy sự kiện OK_ACK_OK nào sau INIT_CALL để tính thời gian thiết lập) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (không tìm thấy sự kiện INVITE) | Signaling |
| Số lần gửi lại INVITE | N/A (không có lệnh INVITE nào trong signaling) | Signaling |
| Số lần "No sessions found" | 0 lần | Signaling/End Call log |
| Thời gian đổ chuông | N/A (không tìm thấy sự kiện RINGING) | Signaling |
| Thời lượng kết nối | N/A (không tìm thấy sự kiện OK_ACK_OK) | Signaling |
| Bên kết thúc | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| Số lần gửi lại BYE | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| MOS thấp nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Packet loss cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| RTT cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Jitter cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Sự kiện ICE/TURN (caller) | TURN_SOCKET_ERROR=8 | WebRTC log |
| MOS thấp nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Packet loss cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| RTT cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Jitter cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (không có webrtc log của CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: TURN_FAILURE - client không đi hết được vòng request/response với TURN
- Khả dĩ khác: Theo mục 9 của spec, chẩn đoán nghẽn hay quá tải TURN server nằm ngoài phạm vi - ở đây chỉ kết luận được là client không đi hết được vòng request/response với TURN, chứ không kết luận được lỗi nằm ở phía server hay phía mạng của thiết bị

## Đề xuất
- Đọc phần tóm tắt để biết TURN hỏng theo kiểu nào: không tạo được socket, hay gửi được request nhưng không có phản hồi.
- Nếu không tạo được socket: kiểm tra mạng/định tuyến cục bộ của thiết bị (VPN, firewall, quyền truy cập mạng) - lỗi xảy ra trước khi có gói nào rời máy.
- Nếu đã gửi request mà không có phản hồi: kiểm tra đường tới TURN server và khả năng gói UDP bị chặn trên đường đi.

## Giới hạn dữ liệu
- Thiếu callee_endcall.log

================================================================================
Call folder: sample-data\for_test\0EC7B700-6B5D-45A3-9BB6-1463B56C9634
# Báo cáo phân tích cuộc gọi
Call-ID: 0EC7B700-6B5D-45A3-9BB6-1463B56C9634
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Cuộc gọi không thiết lập được: signaling ghi rõ FAIL_HARD trước khi cuộc gọi kịp được xác nhận (không quan sát được OK_ACK_OK), và log client không chỉ ra nguyên nhân tầng media nào.

## Evidence chính
1. [EV001][signaling 2026-09-18 05:30:29.450] INIT_CALL: bắt đầu thiết lập cuộc gọi
2. [EV002][signaling 2026-09-18 05:31:02.861] FAIL_HARD: cuộc gọi bị chấm dứt trước khi kịp được xác nhận
3. [EV003][callee_endcall.log 2026-09-18 05:31:02.752] Client state timeline của CALLEE dừng ở ANSWERED - chưa bao giờ đạt CONFIRMED

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (không tìm thấy sự kiện OK_ACK_OK nào sau INIT_CALL để tính thời gian thiết lập) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | 9.402 s | Signaling |
| Số lần gửi lại INVITE | 4 lần | Signaling |
| Số lần "No sessions found" | 0 lần | Signaling/End Call log |
| Thời gian đổ chuông | N/A (không tìm thấy sự kiện OK nào sau RINGING để tính thời gian đổ chuông) | Signaling |
| Thời lượng kết nối | N/A (không tìm thấy sự kiện OK_ACK_OK) | Signaling |
| Bên kết thúc | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| Số lần gửi lại BYE | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| MOS thấp nhất (caller) | N/A (không có end-call log của CALLER) | End Call log |
| Packet loss cao nhất (caller) | N/A (không có end-call log của CALLER) | End Call log |
| RTT cao nhất (caller) | N/A (không có end-call log của CALLER) | End Call log |
| Jitter cao nhất (caller) | N/A (không có end-call log của CALLER) | End Call log |
| Sự kiện ICE/TURN (caller) | TURN_ALLOCATE_REQUEST=8, TURN_ALLOCATE_RESPONSE=4 | WebRTC log |
| MOS thấp nhất (callee) | N/A (end-call log của CALLEE có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Packet loss cao nhất (callee) | N/A (end-call log của CALLEE có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| RTT cao nhất (callee) | N/A (end-call log của CALLEE có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Jitter cao nhất (callee) | N/A (end-call log của CALLEE có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Sự kiện ICE/TURN (callee) | ICE_CANDIDATE=4, TURN_ALLOCATE_REQUEST=13, TURN_ALLOCATE_RESPONSE=3 | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: SIGNALING_FAILURE - lỗi ở tầng signaling
- Khả dĩ khác: Cuộc gọi bị người dùng chủ động huỷ (CANCEL) cũng rơi vào category này, nhưng bản chất không phải lỗi hệ thống - mục 4.2 chưa có nhãn cho hành vi người dùng, đang chờ mentor xác nhận

## Đề xuất
- Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.

## Giới hạn dữ liệu
- Thiếu caller_endcall.log

================================================================================
Call folder: sample-data\for_test\271D1FAF-26D4-4150-AC3F-9204505BD84B
# Báo cáo phân tích cuộc gọi
Call-ID: 271D1FAF-26D4-4150-AC3F-9204505BD84B
Kết luận: SUCCESS
Cờ chất lượng: Có - NETWORK_PACKET_LOSS
Độ tin cậy: HIGH
Tóm tắt: Cuộc gọi thiết lập được và kết thúc bình thường, nhưng chất lượng bị giảm (NETWORK_PACKET_LOSS).

## Evidence chính
1. [EV001][signaling 2026-09-18 15:15:17.755] INIT_CALL: bắt đầu thiết lập cuộc gọi
2. [EV002][signaling 2026-09-18 15:15:21.596] OK_ACK_OK: cuộc gọi đã được xác nhận, cả hai bên đã kết nối
3. [EV003][signaling 2026-09-18 15:16:41.954] BYE từ CALLER

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | 3.840 s | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (không tìm thấy sự kiện TRYING nào sau INVITE để tính thời gian với tới callee) | Signaling |
| Số lần gửi lại INVITE | 1 lần | Signaling |
| Số lần "No sessions found" | 0 lần | Signaling/End Call log |
| Thời gian đổ chuông | 3.032 s | Signaling |
| Thời lượng kết nối | 80.358 s | Signaling |
| Bên kết thúc | CALLER | Signaling |
| Số lần gửi lại BYE | 1 lần | Signaling |
| MOS thấp nhất (caller) | 4.10477 | End Call log |
| Packet loss cao nhất (caller) | 11.3208 % | End Call log |
| RTT cao nhất (caller) | 115.0 ms | End Call log |
| Jitter cao nhất (caller) | 0.022 ms | End Call log |
| Sự kiện ICE/TURN (caller) | ICE_CONNECTION_STATE_CHANGE=4, TURN_ALLOCATE_REQUEST=15, TURN_ALLOCATE_RESPONSE=3 | WebRTC log |
| MOS thấp nhất (callee) | 4.34143 | End Call log |
| Packet loss cao nhất (callee) | 7.54717 % | End Call log |
| RTT cao nhất (callee) | 82.0 ms | End Call log |
| Jitter cao nhất (callee) | 0.012 ms | End Call log |
| Sự kiện ICE/TURN (callee) | ICE_CONNECTION_STATE_CHANGE=4, TURN_ALLOCATE_REQUEST=15, TURN_ALLOCATE_RESPONSE=3 | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: NETWORK_PACKET_LOSS - mất gói trên đường truyền
- Khả dĩ khác: NETWORK_DELAY_JITTER - mất gói cao và jitter/RTT cao thường đi cùng nhau; nếu log thiếu jitter/RTT thì không phân định được cái nào là nguyên nhân chính

## Đề xuất
- Kiểm tra chất lượng mạng của bên bị ảnh hưởng trong thời điểm cuộc gọi.

## Giới hạn dữ liệu
- Không có.

================================================================================
Call folder: sample-data\for_test\311A9B6A-0D30-4C30-9E01-9A42E4EF11E6
# Báo cáo phân tích cuộc gọi
Call-ID: 311A9B6A-0D30-4C30-9E01-9A42E4EF11E6
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Cuộc gọi không thiết lập được: signaling ghi rõ CANCEL trước khi cuộc gọi kịp được xác nhận (không quan sát được OK_ACK_OK), và log client không chỉ ra nguyên nhân tầng media nào.

## Evidence chính
1. [EV001][signaling 2026-09-19 08:01:00.142] INIT_CALL: bắt đầu thiết lập cuộc gọi
2. [EV002][signaling 2026-09-19 08:01:06.802] CANCEL: cuộc gọi bị chấm dứt trước khi kịp được xác nhận
3. [EV003][caller_endcall.log 2026-09-19 08:01:06.725] Client state timeline của CALLER dừng ở INVITE_SENT - chưa bao giờ đạt CONFIRMED

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (không tìm thấy sự kiện OK_ACK_OK nào sau INIT_CALL để tính thời gian thiết lập) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (không tìm thấy sự kiện INVITE) | Signaling |
| Số lần gửi lại INVITE | N/A (không có lệnh INVITE nào trong signaling) | Signaling |
| Số lần "No sessions found" | 0 lần | Signaling/End Call log |
| Thời gian đổ chuông | N/A (không tìm thấy sự kiện RINGING) | Signaling |
| Thời lượng kết nối | N/A (không tìm thấy sự kiện OK_ACK_OK) | Signaling |
| Bên kết thúc | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| Số lần gửi lại BYE | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| MOS thấp nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Packet loss cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| RTT cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Jitter cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Sự kiện ICE/TURN (caller) | TURN_ALLOCATE_REQUEST=8, TURN_ALLOCATE_RESPONSE=4 | WebRTC log |
| MOS thấp nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Packet loss cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| RTT cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Jitter cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (không có webrtc log của CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: SIGNALING_FAILURE - lỗi ở tầng signaling
- Khả dĩ khác: Cuộc gọi bị người dùng chủ động huỷ (CANCEL) cũng rơi vào category này, nhưng bản chất không phải lỗi hệ thống - mục 4.2 chưa có nhãn cho hành vi người dùng, đang chờ mentor xác nhận

## Đề xuất
- Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.

## Giới hạn dữ liệu
- Thiếu callee_endcall.log

================================================================================
Call folder: sample-data\for_test\45AA3011-1034-4D13-82A4-634A72D432B6
# Báo cáo phân tích cuộc gọi
Call-ID: 45AA3011-1034-4D13-82A4-634A72D432B6
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Cuộc gọi không thiết lập được: Lỗi TURN phía CALLER: không tạo được socket tới TURN server nên chưa gửi đi được request nào. Signaling ghi nhận CANCEL - đó là hệ quả, không phải nguyên nhân gốc.

## Evidence chính
1. [EV001][signaling 2026-09-12 04:51:11.274] INIT_CALL: bắt đầu thiết lập cuộc gọi
2. [EV002][signaling 2026-09-12 04:51:17.602] CANCEL: cuộc gọi bị chấm dứt trước khi kịp được xác nhận
3. [EV003][caller_endcall.log 2026-09-12 04:51:16.405] Client state timeline của CALLER dừng ở INVITE_SENT - chưa bao giờ đạt CONFIRMED
4. [EV004][caller_webrtc.log 2026-09-12 04:51:10.275] Lỗi TURN phía CALLER: không tạo được socket tới TURN server nên chưa gửi đi được request nào

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (không tìm thấy sự kiện OK_ACK_OK nào sau INIT_CALL để tính thời gian thiết lập) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (không tìm thấy sự kiện INVITE) | Signaling |
| Số lần gửi lại INVITE | N/A (không có lệnh INVITE nào trong signaling) | Signaling |
| Số lần "No sessions found" | 0 lần | Signaling/End Call log |
| Thời gian đổ chuông | N/A (không tìm thấy sự kiện RINGING) | Signaling |
| Thời lượng kết nối | N/A (không tìm thấy sự kiện OK_ACK_OK) | Signaling |
| Bên kết thúc | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| Số lần gửi lại BYE | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| MOS thấp nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Packet loss cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| RTT cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Jitter cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Sự kiện ICE/TURN (caller) | TURN_SOCKET_ERROR=8 | WebRTC log |
| MOS thấp nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Packet loss cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| RTT cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Jitter cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (không có webrtc log của CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: TURN_FAILURE - client không đi hết được vòng request/response với TURN
- Khả dĩ khác: Theo mục 9 của spec, chẩn đoán nghẽn hay quá tải TURN server nằm ngoài phạm vi - ở đây chỉ kết luận được là client không đi hết được vòng request/response với TURN, chứ không kết luận được lỗi nằm ở phía server hay phía mạng của thiết bị

## Đề xuất
- Đọc phần tóm tắt để biết TURN hỏng theo kiểu nào: không tạo được socket, hay gửi được request nhưng không có phản hồi.
- Nếu không tạo được socket: kiểm tra mạng/định tuyến cục bộ của thiết bị (VPN, firewall, quyền truy cập mạng) - lỗi xảy ra trước khi có gói nào rời máy.
- Nếu đã gửi request mà không có phản hồi: kiểm tra đường tới TURN server và khả năng gói UDP bị chặn trên đường đi.

## Giới hạn dữ liệu
- Thiếu callee_endcall.log

================================================================================
Call folder: sample-data\for_test\9B556E56-24D3-43BA-853D-7972AD009865
# Báo cáo phân tích cuộc gọi
Call-ID: 9B556E56-24D3-43BA-853D-7972AD009865
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Cuộc gọi không thiết lập được: không quan sát được sự kiện OK_ACK_OK (đã xác nhận) ở tầng signaling, và cũng không có evidence nào về lỗi ICE, nên quy về lỗi tầng signaling.

## Evidence chính
1. [EV001][signaling 2026-09-19 08:01:55.680] INIT_CALL: bắt đầu thiết lập cuộc gọi
2. [EV002][caller_endcall.log 2026-09-19 08:01:55.394] Client state timeline của CALLER dừng ở WAITING_INIT_CALL - chưa bao giờ đạt CONFIRMED

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (không tìm thấy sự kiện OK_ACK_OK nào sau INIT_CALL để tính thời gian thiết lập) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (không tìm thấy sự kiện INVITE) | Signaling |
| Số lần gửi lại INVITE | N/A (không có lệnh INVITE nào trong signaling) | Signaling |
| Số lần "No sessions found" | 0 lần | Signaling/End Call log |
| Thời gian đổ chuông | N/A (không tìm thấy sự kiện RINGING) | Signaling |
| Thời lượng kết nối | N/A (không tìm thấy sự kiện OK_ACK_OK) | Signaling |
| Bên kết thúc | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| Số lần gửi lại BYE | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| MOS thấp nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Packet loss cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| RTT cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Jitter cao nhất (caller) | N/A (end-call log của CALLER có nhưng không chứa dòng periodic stats nào (cuộc gọi chưa vào tầng media)) | End Call log |
| Sự kiện ICE/TURN (caller) | N/A (không có webrtc log của CALLER) | WebRTC log |
| MOS thấp nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Packet loss cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| RTT cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Jitter cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (không có webrtc log của CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: SIGNALING_FAILURE - lỗi ở tầng signaling
- Khả dĩ khác: Cuộc gọi bị người dùng chủ động huỷ (CANCEL) cũng rơi vào category này, nhưng bản chất không phải lỗi hệ thống - mục 4.2 chưa có nhãn cho hành vi người dùng, đang chờ mentor xác nhận

## Đề xuất
- Kiểm tra log signaling phía server quanh thời điểm INIT_CALL/INVITE để xác định nguyên nhân.

## Giới hạn dữ liệu
- Thiếu callee_endcall.log

================================================================================
Call folder: sample-data\for_test\AA9791CE-13D8-4DD7-942D-4D78304FD458
# Báo cáo phân tích cuộc gọi
Call-ID: AA9791CE-13D8-4DD7-942D-4D78304FD458
Kết luận: FAIL
Cờ chất lượng: Không
Độ tin cậy: MEDIUM
Tóm tắt: Cuộc gọi không thiết lập được: Lỗi TURN phía CALLER: đã gửi 60 request tới TURN server nhưng không nhận được phản hồi nào. Signaling ghi nhận CANCEL - đó là hệ quả, không phải nguyên nhân gốc.

## Evidence chính
1. [EV001][signaling 2026-09-11 09:49:32.045] INIT_CALL: bắt đầu thiết lập cuộc gọi
2. [EV002][signaling 2026-09-11 09:49:40.399] CANCEL: cuộc gọi bị chấm dứt trước khi kịp được xác nhận
3. [EV003][caller_webrtc.log 2026-09-11 09:49:32.157] Lỗi TURN phía CALLER: đã gửi 60 request tới TURN server nhưng không nhận được phản hồi nào

## Chỉ số cuộc gọi
| Chỉ số | Giá trị | Nguồn |
| Thời gian thiết lập | N/A (không tìm thấy sự kiện OK_ACK_OK nào sau INIT_CALL để tính thời gian thiết lập) | Signaling |
| Thời gian với tới callee (INVITE→TRYING) | N/A (không tìm thấy sự kiện INVITE) | Signaling |
| Số lần gửi lại INVITE | N/A (không có lệnh INVITE nào trong signaling) | Signaling |
| Số lần "No sessions found" | 0 lần | Signaling/End Call log |
| Thời gian đổ chuông | N/A (không tìm thấy sự kiện RINGING) | Signaling |
| Thời lượng kết nối | N/A (không tìm thấy sự kiện OK_ACK_OK) | Signaling |
| Bên kết thúc | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| Số lần gửi lại BYE | N/A (không có lệnh BYE nào trong signaling) | Signaling |
| MOS thấp nhất (caller) | N/A (không có end-call log của CALLER) | End Call log |
| Packet loss cao nhất (caller) | N/A (không có end-call log của CALLER) | End Call log |
| RTT cao nhất (caller) | N/A (không có end-call log của CALLER) | End Call log |
| Jitter cao nhất (caller) | N/A (không có end-call log của CALLER) | End Call log |
| Sự kiện ICE/TURN (caller) | TURN_ALLOCATE_REQUEST=60 | WebRTC log |
| MOS thấp nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Packet loss cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| RTT cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Jitter cao nhất (callee) | N/A (không có end-call log của CALLEE) | End Call log |
| Sự kiện ICE/TURN (callee) | N/A (không có webrtc log của CALLEE) | WebRTC log |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: TURN_FAILURE - client không đi hết được vòng request/response với TURN
- Khả dĩ khác: Theo mục 9 của spec, chẩn đoán nghẽn hay quá tải TURN server nằm ngoài phạm vi - ở đây chỉ kết luận được là client không đi hết được vòng request/response với TURN, chứ không kết luận được lỗi nằm ở phía server hay phía mạng của thiết bị

## Đề xuất
- Đọc phần tóm tắt để biết TURN hỏng theo kiểu nào: không tạo được socket, hay gửi được request nhưng không có phản hồi.
- Nếu không tạo được socket: kiểm tra mạng/định tuyến cục bộ của thiết bị (VPN, firewall, quyền truy cập mạng) - lỗi xảy ra trước khi có gói nào rời máy.
- Nếu đã gửi request mà không có phản hồi: kiểm tra đường tới TURN server và khả năng gói UDP bị chặn trên đường đi.

## Giới hạn dữ liệu
- Thiếu caller_endcall.log
- Thiếu callee_endcall.log

```

---

*Bản trình bày trực quan (HTML): [Kết quả chạy data mẫu](https://claude.ai/code/artifact/f8665a78-c5ba-4887-9bf6-7ba4373fe8d6)*
