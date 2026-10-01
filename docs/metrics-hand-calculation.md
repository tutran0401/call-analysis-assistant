# Call Metrics — đối chiếu tính tay (T6)

Acceptance criteria Sprint 1 (PROJECT_SPEC.md mục 5.1): *"Chỉ số khớp 100% với giá trị tính tay trên
tối thiểu 5 cuộc gọi"*. Tài liệu này ghi lại **mốc gốc và phép tính** cho từng giá trị, để người review
tự trừ lại được mà không cần đọc code. Mọi con số dưới đây được khẳng định trong
`CallMetricsCalculatorTest` (`signalingMetricsMatchHandCalculation`, `qualityMetricsMatchHandCalculation`).

**Kết quả: 8 cuộc gọi có số liệu (setup, reach, ring, connected, retransmit, bên kết thúc) + 8 bộ chỉ
số chất lượng trên 5 cuộc gọi — khớp 100%.** Ngoài ra 3 cuộc gọi `for_test/` khác được kiểm chứng
phải ra N/A (không được mặc định về 0).

## 1. Quy ước tính

| Chỉ số | Định nghĩa (mục 4.3) | Cách tính |
| --- | --- | --- |
| Thời gian thiết lập | INIT_CALL → OK_ACK | `INIT_CALL` sớm nhất → `OK_ACK_OK` sớm nhất sau đó. Signaling không có lệnh tên đúng `OK_ACK`; `OK_ACK_OK` là bản ghi tương đương (xem `SignalingCommands`) |
| Thời gian với tới callee | INVITE đầu tiên → TRYING | `INVITE` sớm nhất → `TRYING` sớm nhất sau đó |
| Thời gian đổ chuông | RINGING → OK | `RINGING` sớm nhất → `OK` sớm nhất sau đó |
| Thời lượng kết nối | OK_ACK → BYE | `OK_ACK_OK` sớm nhất → `BYE` sớm nhất sau đó |
| Số lần gửi lại INVITE / BYE | | Số **đợt gửi** − 1, cộng theo từng người gửi (`appUserId`). Các dòng cách nhau ≤ 250ms là một đợt (xem mục 2) |
| Bên kết thúc | Ai gửi BYE | `appUserId` của `BYE` sớm nhất so với caller = người gửi `INIT_CALL`, callee = người gửi `TRYING`/`RINGING`/`OK`. Chỉ khi signaling thiếu danh tính mới dùng `CALL_SUMMARY` của end-call log |
| MOS, packet loss, RTT, jitter | | Giá trị **tệ nhất** trong cả cuộc gọi (MOS thấp nhất; packet loss, RTT, jitter cao nhất), chỉ xét các dòng `PERIODIC_STATS` **có media** (`audio.bytesReceived > 0`) của end-call log từng bên. MOS ≤ 0 bị loại (thang hợp lệ 1–5). Field: `audio.audioMos`, `audio.packetLostPercent`, `transport.currentRttMs`, `audio.jitter` |

Thời lượng là hiệu hai timestamp nano-giây, **cắt** về mili-giây (`Duration.toMillis()`), nên có thể
kém 1ms so với làm tròn.

## 2. Vì sao đếm gửi lại theo "đợt" chứ không theo requestId

Soát toàn bộ 20 signaling export mẫu:

- Một lần gửi được **nhiều service ghi lại** (service `SCYZ…` và `S46T…`), mỗi service có thể ghi 2–3
  dòng; các dòng của một lần gửi nằm trong khoảng **≤ 30ms**.
- Lần gửi lại **giữ nguyên requestId**; còn service `SCYZ…` dùng **một requestId cho mọi lệnh của cả
  phiên** (INIT_CALL, INVITE, BYE, PAIR_PING… đều chung `226c9e1c` ở cuộc gọi `271D1FAF`).
- Các lần gửi lại cách nhau theo backoff **~0.5s → 1s → 2s → 4s** (kiểu timer SIP).

Ví dụ `0EC7B700` — 15 dòng INVITE, **1 requestId** (`795d0929`), độ lệch so với dòng đầu:

```text
+0.000 +0.005 +0.014 | +0.522 +0.528 +0.535 | +1.546 +1.551 +1.558 | +3.567 +3.574 +3.581 | +7.586 +7.593 +7.600
      đợt 1          |       đợt 2          |       đợt 3          |        đợt 4         |        đợt 5
```

→ 5 đợt = **4 lần gửi lại**. Cách đếm cũ (số requestId khác nhau − 1) ra **0**. Ở `271D1FAF` cách cũ
ra đúng 1 chỉ là tình cờ (2 requestId đến từ 2 service, không phải 2 lần gửi).

Ngưỡng 250ms nằm giữa độ trải rộng của một đợt (≤ 30ms) và khoảng cách gửi lại ngắn nhất (~520ms).

**Cần mentor xác nhận:** mọi cuộc gọi có BYE đều có đúng 1 lần gửi lại BYE ở ~+0.54s, kể cả khi
`BYE_ACK_BYE` đã về trước đó (ví dụ `271D1FAF`: ACK lúc 42.059, dòng BYE lặp lúc 42.498). Có thể đây là
server ghi log khi timer gửi lại kích hoạt chứ không phải gửi lại thật. Code đang báo đúng những gì log
ghi lại.

## 3. Chỉ số signaling — 8 cuộc gọi

Timestamp lấy nguyên từ `@timestamp` của `signaling.json` (UTC).

### for_test/271D1FAF-26D4-4150-AC3F-9204505BD84B

| Chỉ số | Mốc đầu | Mốc cuối | Kết quả |
| --- | --- | --- | --- |
| Thiết lập | INIT_CALL 15:15:17.755630862 | OK_ACK_OK 15:15:21.596429013 | **3840 ms** |
| Với tới callee | INVITE 15:15:18.285 | không có TRYING | **N/A** |
| Đổ chuông | RINGING 15:15:18.415535112 | OK 15:15:21.448368324 | **3032 ms** |
| Kết nối | OK_ACK_OK 15:15:21.596429013 | BYE 15:16:41.954584182 | **80358 ms** |
| Gửi lại INVITE | đợt 18.285–18.315, đợt 18.844 | | **1** |
| Gửi lại BYE | đợt 41.954–41.965, đợt 42.498 | | **1** |
| Bên kết thúc | BYE từ `UYBS6Y7J2MK` = người gửi INIT_CALL | | **CALLER** |

### success/DE7DD314-F432-45CB-BCB4-AE9103CC0919

| Chỉ số | Mốc đầu | Mốc cuối | Kết quả |
| --- | --- | --- | --- |
| Thiết lập | INIT_CALL 03:07:21.453318103 | OK_ACK_OK 03:07:30.035184523 | **8581 ms** |
| Với tới callee | INVITE 03:07:22.000161733 | TRYING 03:07:24.992159953 | **2991 ms** |
| Đổ chuông | RINGING 03:07:25.110377980 | OK 03:07:29.859415853 | **4749 ms** |
| Kết nối | OK_ACK_OK 03:07:30.035184523 | BYE 03:13:31.346554475 | **361311 ms** |
| Gửi lại INVITE | đợt ở +0 / +0.522 / +1.547 / +3.570s | | **3** |
| Gửi lại BYE | đợt ở +0 / +0.541s | | **1** |
| Bên kết thúc | BYE từ `US43EGOIN7G` = người gửi TRYING | | **CALLEE** |

### success/EE129C8F-EAD0-4302-AB68-920D32F8B8B7

| Chỉ số | Mốc đầu | Mốc cuối | Kết quả |
| --- | --- | --- | --- |
| Thiết lập | INIT_CALL 08:21:28.768692800 | OK_ACK_OK 08:21:42.298932319 | **13530 ms** |
| Với tới callee | INVITE 08:21:34.443227404 | TRYING 08:21:35.948322579 | **1505 ms** |
| Đổ chuông | RINGING 08:21:36.088734252 | OK 08:21:42.076470307 | **5987 ms** |
| Kết nối | OK_ACK_OK 08:21:42.298932319 | BYE 08:22:18.606130118 | **36307 ms** |
| Gửi lại INVITE | đợt ở +0 / +0.529 / +1.554 / +3.590s | | **3** |
| Gửi lại BYE | đợt ở +0 / +0.537s | | **1** |
| Bên kết thúc | BYE từ `U5PJID6GY67` = người gửi TRYING | | **CALLEE** |

### success/C8CF631E-0C6B-46E4-92E7-280E7B6A5394

| Chỉ số | Mốc đầu | Mốc cuối | Kết quả |
| --- | --- | --- | --- |
| Thiết lập | INIT_CALL 08:39:26.775580452 | OK_ACK_OK 08:39:38.733682222 | **11958 ms** |
| Với tới callee | INVITE | không có TRYING | **N/A** |
| Đổ chuông | RINGING 08:39:27.649221375 | OK 08:39:38.592189820 | **10942 ms** |
| Kết nối | OK_ACK_OK 08:39:38.733682222 | BYE 08:39:54.542700255 | **15809 ms** |
| Gửi lại INVITE | đợt ở +0 / +0.536s | | **1** |
| Gửi lại BYE | đợt ở +0 / +0.535s | | **1** |
| Bên kết thúc | BYE từ `U3VMPERDDSC` = người gửi INIT_CALL | | **CALLER** |

### success/70A1F889-9371-4179-8C27-125ED39BA415

| Chỉ số | Mốc đầu | Mốc cuối | Kết quả |
| --- | --- | --- | --- |
| Thiết lập | INIT_CALL 06:55:47.230050207 | OK_ACK_OK 06:56:19.824715943 | **32594 ms** |
| Với tới callee | INVITE 06:55:47.817142877 | TRYING 06:55:59.179671512 | **11362 ms** |
| Đổ chuông | RINGING 06:55:59.392358770 | OK 06:56:19.649460538 | **20257 ms** |
| Kết nối | OK_ACK_OK 06:56:19.824715943 | BYE 06:57:00.388726649 | **40564 ms** |
| Gửi lại INVITE | đợt ở +0 / +0.521 / +1.543 / +3.559 / +7.580s | | **4** |
| Gửi lại BYE | đợt ở +0 / +0.543s | | **1** |
| Bên kết thúc | BYE từ `UGCU5AF4MTE` = người gửi INIT_CALL | | **CALLER** |

### success/5E0800AE-F9D4-497D-9FA1-CD56C3E901E8 (không có end-call log nào)

| Chỉ số | Mốc đầu | Mốc cuối | Kết quả |
| --- | --- | --- | --- |
| Thiết lập | INIT_CALL 08:39:24.118600704 | OK_ACK_OK 08:39:38.876237373 | **14757 ms** |
| Với tới callee | INVITE 08:39:24.762891703 | TRYING 08:39:34.832366613 | **10069 ms** |
| Đổ chuông | RINGING 08:39:34.936964660 | OK 08:39:38.470616448 | **3533 ms** |
| Kết nối | OK_ACK_OK 08:39:38.876237373 | BYE 08:41:36.632927359 | **117756 ms** |
| Gửi lại INVITE | đợt ở +0 / +0.524 / +1.549 / +3.571 / +7.592s | | **4** |
| Gửi lại BYE | đợt ở +0 / +0.536s | | **1** |
| Bên kết thúc | BYE từ `UZFSCHDZTWR` = người gửi INIT_CALL | | **CALLER** (bản cũ: N/A vì thiếu end-call log) |

### success/6A7CE985-4A1A-44D1-84B1-DBB0B0B90448 (không có end-call log nào)

| Chỉ số | Mốc đầu | Mốc cuối | Kết quả |
| --- | --- | --- | --- |
| Thiết lập | INIT_CALL 08:41:37.594982903 | OK_ACK_OK 08:41:46.896524778 | **9301 ms** |
| Với tới callee | INVITE 08:41:38.195291236 | TRYING 08:41:41.880440514 | **3685 ms** |
| Đổ chuông | RINGING 08:41:41.923224098 | OK 08:41:46.741356295 | **4818 ms** |
| Kết nối | OK_ACK_OK 08:41:46.896524778 | BYE 08:42:19.714907628 | **32818 ms** |
| Gửi lại INVITE | đợt ở +0 / +0.525 / +1.551 / +3.578 / +7.613s | | **4** |
| Gửi lại BYE | đợt ở +0 / +0.542s | | **1** |
| Bên kết thúc | BYE từ `UL5EWF2DA5E` = người gửi TRYING | | **CALLEE** (bản cũ: N/A) |

### fail/2D9057AA-C496-48B2-946A-98FA2896D086 (kết nối rồi rớt media)

| Chỉ số | Mốc đầu | Mốc cuối | Kết quả |
| --- | --- | --- | --- |
| Thiết lập | INIT_CALL 23:43:01.013182693 | OK_ACK_OK 23:43:07.787631318 | **6774 ms** |
| Với tới callee | INVITE | không có TRYING | **N/A** |
| Đổ chuông | RINGING 23:43:01.926633579 | OK 23:43:07.491823658 | **5565 ms** |
| Kết nối | OK_ACK_OK 23:43:07.787631318 | BYE 23:43:41.679725739 | **33892 ms** |
| Gửi lại INVITE | đợt ở +0 / +0.537s | | **1** |
| Gửi lại BYE | đợt ở +0 / +0.537 / +1.557 / +3.575 / +7.588s | | **4** |
| Bên kết thúc | BYE từ `UKVIYF7A4DW` = người gửi RINGING | | **CALLEE** |

## 4. Chỉ số chất lượng — giá trị tệ nhất trên các dòng có media

Vì sao không lấy dòng cuối: dòng periodic stats cuối cùng thường được ghi khi stream đã tắt nên các
chỉ số về 0. Ví dụ `271D1FAF`: dòng cuối báo packet loss 0% ở cả hai bên, trong khi giữa cuộc gọi có
đỉnh 11.32% (caller) và 7.55% (callee).

Vì sao phải lọc dòng chưa có media: client vẫn ghi stats trước khi media lên, hoặc khi media không
bao giờ lên, và mọi field ở các dòng đó đều bằng 0. Nếu để lọt vào phép lấy giá trị tệ nhất thì "MOS
thấp nhất" sẽ luôn là 0.

Ô dạng `giá trị (dòng N)`: N là số dòng trong `*_endcall.log` (tính từ 1, gồm cả header `#H…`) nơi giá
trị đó xuất hiện. "mọi dòng" nghĩa là mọi dòng có media đều bằng giá trị đó.

| Cuộc gọi | Bên | Số dòng có media | MOS thấp nhất | Packet loss cao nhất | RTT cao nhất | Jitter cao nhất |
| --- | --- | --- | --- | --- | --- | --- |
| for_test/271D1FAF | caller | 80/80 | 4.10477 (170) | 11.3208 % (108) | 115 ms (170) | 0.022 (98) |
| for_test/271D1FAF | callee | 80/80 | 4.34143 (97) | 7.54717 % (119) | 82 ms (166) | 0.012 (159) |
| success/DE7DD314 | caller | 359/359 | 4.41438 (595) | 2.04082 % (274) | 107 ms (209) | 0.02 (158) |
| success/DE7DD314 | callee | 359/359 | 4.4013 (568) | 2.0 % (455) | 152 ms (569) | 0.022 (215) |
| success/EE129C8F | caller | 36/36 | 4.40913 (101) | 0 % (mọi dòng) | 134 ms (101) | 0.02 (142) |
| success/EE129C8F | callee | 35/36 | 4.33531 (91) | 3.7037 % (91) | 181 ms (129) | 0.026 (126) |
| success/C8CF631E | caller | 15/15 | 4.40396 (82) | 0 % (mọi dòng) | 156 ms (82) | 0.012 (87) |
| success/70A1F889 | callee | 39/39 | 4.37638 (84) | 0 % (mọi dòng) | 266 ms (84) | 0.028 (96) |

Các dòng `END_CALL_SUMMARY` (có field `endCall.*`/`setup.*`/`answer.*`) cũng khai báo
`audio.audioMos` nhưng không phải periodic stats, nên không được xét.

## 5. Các ca phải ra N/A (không được mặc định về 0)

| Cuộc gọi | Tình huống | Chỉ số N/A |
| --- | --- | --- |
| fail/2D9057AA (callee) | 25 dòng periodic stats **toàn số 0**, kể cả `audio.bytesReceived` — callee chưa từng nhận media (ICE failed) | MOS, packet loss, RTT, jitter. Nếu không lọc thì report sẽ in "MOS 0, Packet loss 0 %, RTT 0 ms" |
| for_test/0EC7B700 | FAIL_HARD sau RINGING | thiết lập, đổ chuông, kết nối, gửi lại BYE, bên kết thúc; chất lượng cả hai bên. Vẫn có: với tới callee 9402 ms, gửi lại INVITE 4 |
| for_test/9B556E56 | chỉ có INIT_CALL | mọi chỉ số Core, kể cả "No sessions found" (không có field text tự do nào để tìm) |
| for_test/0A6C2821, 45AA3011 | caller huỷ trong INIT_CALL | mọi chỉ số signaling và chất lượng |

## 6. "No sessions found"

Mục 4.3 ghi nguồn là Signaling, nhưng signaling export mẫu chỉ có field có cấu trúc (`cmd`,
`service`, `csid`, `requestId`, `appUserId`, `isp`, `asn`, `countryCode`, `latencyMs`,
`callSessionId`, `level`), **không có field message nào**. Chuỗi "no session" không xuất hiện ở bất
kỳ file nào trong 20 cuộc gọi mẫu. Code tìm trong mọi field text tự do (`msg`, `message`) của **mọi
nguồn**: nếu signaling export thật có field message thì cũng được tìm. Không có field nào để tìm thì
trả N/A chứ không trả 0.
