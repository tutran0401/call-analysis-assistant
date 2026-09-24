# OJT AI 20K - AI Call Analysis Assistant

> Tài liệu dành cho Thực tập sinh. Phiên bản 3 (2026-09-22).

# 1. Tổng quan

| Hạng mục | Nội dung |
| --- | --- |
| Thời gian | 6 tuần, 3 Sprint × 2 tuần |
| Thực tập sinh | Trần Anh Tú, Bế Nguyễn Hà Sơn |
| Mô hình làm việc | Chung đề bài và kế hoạch; **mỗi bạn tự xây dựng hoàn chỉnh, độc lập toàn bộ sản phẩm** trên repo riêng |

## 1.1. Bài toán

Xây dựng **trợ lý AI phân tích cuộc gọi**: người dùng mô tả yêu cầu bằng tiếng Việt trên web UI, đính kèm log cuộc gọi; hệ thống trả về **report chuẩn, nhất quán** gồm:

1. **Kết luận** cuộc gọi: `SUCCESS` / `FAIL` / `UNKNOWN` (kèm cờ chất lượng kém nếu có).
2. **Phân tích ngắn gọn các evidence** chính, trích dẫn được về dòng log gốc.
3. **Các chỉ số cuộc gọi** liên quan.
4. **Đề xuất** hướng xử lý / điều tra tiếp.
5. **Giới hạn dữ liệu** (thiếu file, thiếu chỉ số) ảnh hưởng đến kết luận.

Ví dụ yêu cầu của người dùng:

```text
Phân tích giúp cuộc gọi này, vì sao bên nhận không nghe được? (đính kèm caller_endcall.log, callee_webrtc.log)
```

Bạn **xây dựng toàn bộ từ đầu**.

## 1.2. Dữ liệu

| Nguồn | Góc nhìn | Cách đưa vào hệ thống | Nội dung chính |
| --- | --- | --- | --- |
| Signaling logs | Server | Mentor cung cấp bản export; bạn tự index vào **Elasticsearch local**; hệ thống tự truy vấn theo Call-ID | Lệnh signaling theo từng leg, dialog state (`INIT → CALLING → EARLY → CONFIRMED → TERMINATED`), ISP/ASN, cảnh báo/lỗi phía server |
| End Call log (`*_endcall.log`, TSV) | Client (caller / callee) | Người dùng đính kèm | State timeline phía client, lệnh signaling gửi/nhận, call summary (duration, MOS, packet loss...), periodic stats |
| WebRTC log (`*_webrtc.log`) | Client (caller / callee) | Người dùng đính kèm | Sự kiện ICE / TURN / PeerConnection, lỗi media transport |

## 1.3. Môi trường

- Toàn bộ hệ thống chạy trên **máy cá nhân** (Elasticsearch + Kibana local qua Docker, cần khoảng 4-6 GB RAM).
- **Không truy cập, không deploy, không ghi** vào bất kỳ hệ thống nào của team.
- Data mẫu Mentor cung cấp đã được xử lý PII.
- AI provider: mỗi bạn tự đề xuất (cloud API hoặc local model chạy được từ máy cá nhân), Mentor duyệt trước khi dùng (xem Sprint 1 - T10).

## 1.4. Mentor cung cấp

| Thời điểm | Nội dung |
| --- | --- |
| Trước Sprint 1 | Architecture overview, call flow cơ bản, mô tả schema 3 nguồn log, 5-10 known scenarios, danh sách sensitive fields ban đầu |
| Trước Sprint 1 | Data mẫu tập `dev` (20-30 cuộc gọi tổng cộng, tập `dev` chiếm khoảng 2/3) kèm ground truth: verdict, evidence chính, issue category |
| Cuối Sprint 1 | Phản hồi / duyệt AI Provider Proposal |
| Cuối Sprint 2, cuối Sprint 3 | Chạy tập `held-out` (bạn không được xem) bằng Evaluation Runner của bạn và gửi lại kết quả |
| Mỗi Sprint | Design review chung, review PR |

Mentor **không cung cấp sẵn implementation rule** — bạn tự phân tích data và thiết kế.

## 1.5. Lộ trình

| Sprint   | Tuần | Trọng tâm                               | Kết quả cuối sprint                                                                                            |
| -------- | ---- | --------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Sprint 1 | 1-2  | Domain, Data Foundation & Rule Baseline | Với log của một cuộc gọi: tính được chỉ số, dựng được timeline, ra verdict theo rule kèm evidence (chưa có AI) |
| Sprint 2 | 3-4  | AI Analysis, Web UI & Sanitizer         | End-to-end MVP dùng được trên web UI                                                                           |
| Sprint 3 | 5-6  | Hardening, Consistency & Handover       | Sản phẩm ổn định, đo lường đầy đủ, tài liệu bàn giao, demo                                                     |

---

# 2. Nguyên tắc làm việc

- Được trao đổi ý tưởng, đặt câu hỏi cho nhau; **không dùng chung code**, không copy thiết kế chi tiết của nhau.
- Mỗi bạn một repo riêng; Mentor có quyền đọc cả hai.
- **Peer review:** PR quan trọng được bạn còn lại review ở chế độ chỉ đọc (góp ý, không sửa code của nhau).
- Code do AI coding assistant sinh ra phải có test, và bạn phải tự giải thích được trong PR.
- Task chia **Core** (bắt buộc) và **Nếu kịp**. Thứ tự ưu tiên:
 1. Đạt **mức tối thiểu** (mục 8.1).
 2. Hoàn thiện các task Core còn lại.
 3. Task "Nếu kịp", cuối cùng là Stretch Goals.
- Phần Core chưa hoàn thiện ghi vào **Known Limitations** kèm lý do — không bỏ qua im lặng.

---

# 3. Thiết kế hệ thống

## 3.1. Kiến trúc mục tiêu

```text
┌──────────────────────────────────────────┐
│ Web UI tối giản                          │
│ (ô nhập câu hỏi + đính kèm file log)     │
└────────────────────┬─────────────────────┘
                     ▼
          ┌──────────────────────┐
          │ Chat API             │
          └──────────┬───────────┘
          ┌──────────┴───────────┐
          ▼                      ▼
┌──────────────────┐   ┌──────────────────┐
│ Request Parser   │   │ File Validator   │
│ (intent, focus)  │   │ (loại file,      │
└────────┬─────────┘   │  Call-ID, size)  │
         │             └────────┬─────────┘
         │                      ▼
         │             ┌──────────────────┐     ┌──────────────────┐
         │             │ Data Loader      │◄────│ Elasticsearch    │
         │             │ (file + ES)      │     │ local (signaling)│
         │             └────────┬─────────┘     └──────────────────┘
         │                      ▼
         │             ┌──────────────────┐
         │             │ Log Normalizer   │
         │             └────────┬─────────┘
         │                      ▼
         │             ┌──────────────────┐
         │             │ Timeline Builder │
         │             └────────┬─────────┘
         │          ┌───────────┴───────────┐
         │          ▼                       ▼
         │ ┌──────────────────┐   ┌──────────────────┐
         │ │ Evidence Engine  │   │ Metrics          │
         │ │ + Rule Verdict   │   │ Calculator       │
         │ └────────┬─────────┘   └────────┬─────────┘
         │          └───────────┬───────────┘
         └──────────────────────┤
                                ▼
                     ┌──────────────────────┐
                     │ Input Sanitizer      │
                     └──────────┬───────────┘
                                ▼
                     ┌──────────────────────┐
                     │ AI Analysis Engine   │
                     └──────────┬───────────┘
                                ▼
                     ┌──────────────────────┐
                     │ Guardrails           │
                     │ (schema, evidence,   │
                     │  verdict vs rule)    │
                     └──────────┬───────────┘
                                ▼
                     ┌──────────────────────┐
                     │ Output Sanitizer     │
                     └──────────┬───────────┘
                                ▼
                     ┌──────────────────────┐
                     │ Report Renderer      │
                     └──────────┬───────────┘
                                ▼
                            Web UI
```

## 3.2. Phân vai Code và AI

Để report **nhất quán** (cùng log → cùng kết luận, cùng số liệu), phần nào tính được bằng code thì không giao cho AI.

| Thành phần | Thực hiện bởi | Lý do |
| --- | --- | --- |
| Chỉ số cuộc gọi (mục 4.3) | Code | Số liệu phải đúng 100% và giống nhau mọi lần chạy |
| Tín hiệu verdict (đạt CONFIRMED hay không, kết thúc bình thường hay bất thường, lỗi ICE/TURN...) | Code (rule) | Làm baseline và căn cứ đối chiếu |
| Verdict cuối cùng | AI đề xuất, Guardrails đối chiếu với rule | Lệch với rule → `UNKNOWN` hoặc gắn cờ cần kiểm tra |
| Hiểu câu hỏi của người dùng | AI (Request Parser) | Ngôn ngữ tự nhiên |
| Phân tích evidence ngắn gọn, đề xuất | AI | Giá trị chính của AI |
| Bố cục report | Code (template) | AI chỉ điền field theo JSON schema, không tự dựng bố cục |

## 3.3. Nguyên tắc thiết kế bắt buộc

- LLM **không trực tiếp xử lý toàn bộ raw logs**; chỉ nhận timeline, evidence và chỉ số đã chuẩn hoá.
- Raw log có thể malformed, thiếu field, trùng lặp, lệch thứ tự, sai tên file; parser không được crash.
- AI chỉ nhận **minimum necessary context**.
- Sensitive / secret data không được gửi sang model.
- AI response, report và application log không được chứa sensitive / secret data dạng gốc.
- AI không được tự tạo evidence hay tự tạo số liệu.
- Mọi kết luận phải trace được về evidence.
- Không đủ dữ liệu phải trả `UNKNOWN` và nêu rõ ở mục "Giới hạn dữ liệu".
- AI failure không được làm hỏng pipeline: fallback về rule-based verdict.
- Câu hỏi ngoài phạm vi phân tích cuộc gọi được từ chối theo mẫu cố định.

---

# 4. Đặc tả phân tích

## 4.1. Verdict

| Verdict | Định nghĩa |
| --- | --- |
| `SUCCESS` | Cuộc gọi thiết lập được và kết thúc bình thường. Nếu phát hiện vấn đề chất lượng → gắn cờ **"Chất lượng kém"** kèm issue category |
| `FAIL` | Cuộc gọi không thiết lập được hoặc bị ngắt bất thường |
| `UNKNOWN` | Không đủ evidence để kết luận (thiếu file, thiếu dữ liệu, evidence mâu thuẫn) |

## 4.2. Issue Category

Áp dụng cho `FAIL` và `SUCCESS` có cờ chất lượng kém.

| Category | Nguồn evidence chính |
| --- | --- |
| `NETWORK_PACKET_LOSS` | End Call log (call summary, periodic stats) |
| `NETWORK_DELAY_JITTER` | End Call log (RTT, jitter nếu có trong schema) |
| `ICE_FAILURE` | WebRTC log, End Call state timeline |
| `TURN_FAILURE` | WebRTC log (lỗi TURN socket / allocation / relay) |
| `SIGNALING_FAILURE` | Signaling logs (timeout, gửi lại, không tìm thấy session), End Call state timeline |
| `UNKNOWN` | Không xác định được |

## 4.3. Call Metrics Catalog

Chỉ số không có trong dữ liệu phải hiển thị `N/A` kèm lý do, **không được mặc định về 0**.

| Nhóm | Chỉ số | Nguồn | Loại | Ghi chú |
| --- | --- | --- | --- | --- |
| Thiết lập | Thời gian thiết lập (INIT_CALL → OK_ACK) | Signaling | Core | |
| Thiết lập | Thời gian với tới callee (INVITE đầu tiên → TRYING), số lần gửi lại INVITE, số lần "No sessions found" | Signaling | Core | |
| Thiết lập | Thời gian đổ chuông (RINGING → OK) | Signaling | Core | |
| Trong cuộc gọi | Thời lượng kết nối (OK_ACK → BYE) | Signaling | Core | |
| Trong cuộc gọi | Khoảng trống lớn nhất giữa các PAIR_PING theo từng leg | Signaling | Nếu kịp | **Proxy**, ghi rõ trong report |
| Kết thúc | Bên kết thúc (ai gửi BYE), số lần gửi lại BYE | Signaling | Core | |
| Server | Latency các API nội bộ lúc INIT_CALL | Signaling | Nếu kịp | |
| Server | Số WARN / ERROR theo service | Signaling | Nếu kịp | WARN không đồng nghĩa với lỗi |
| Ngữ cảnh | ISP, ASN, country theo từng leg | Signaling | Nếu kịp | |
| Chất lượng | MOS, packet loss, RTT, jitter | End Call log | Core | |
| Media | Sự kiện ICE / TURN / PeerConnection chính | WebRTC log | Core | |

## 4.4. Intent

| Intent | Ví dụ | Xử lý |
| --- | --- | --- |
| `ANALYZE_CALL` | "Phân tích cuộc gọi này giúp mình" | Report đầy đủ |
| `ANALYZE_WITH_FOCUS` | "Vì sao bên nhận không nghe được?" | Report đầy đủ + phần phân tích ưu tiên khía cạnh được hỏi |
| `OUT_OF_SCOPE` | "Viết giúp mình một email" | Từ chối theo mẫu cố định |

## 4.5. Mẫu report

Report luôn theo đúng bố cục sau, bằng tiếng Việt. Số liệu dưới đây là **giả lập**, chỉ để minh hoạ bố cục.

```text
# Báo cáo phân tích cuộc gọi
Call-ID: CALL-EXAMPLE-001
Kết luận: SUCCESS            (SUCCESS | FAIL | UNKNOWN)
Cờ chất lượng: Có - NETWORK_PACKET_LOSS
Độ tin cậy: MEDIUM
Tóm tắt: Cuộc gọi thiết lập thành công, kết thúc bình thường do caller cúp máy; phía callee có packet loss cao.

## Evidence chính
1. [EV02][signaling 10:00:06.320] OK_ACK: LegA CONFIRMED, LegB CONFIRMED
2. [EV05][callee_endcall.log 10:00:41.000] Packet loss phía callee = 9.8%
3. [EV09][signaling 10:01:41.610] BYE từ CALLER

## Chỉ số cuộc gọi
| Chỉ số                 | Giá trị                          | Nguồn     |
| Thời gian thiết lập    | 6.2 s                            | Signaling |
| Thời gian đổ chuông    | 4.8 s                            | Signaling |
| Thời lượng kết nối     | 95.3 s                           | Signaling |
| Bên kết thúc           | Caller                           | Signaling |
| MOS (callee)           | 2.9                              | End Call  |
| Packet loss (callee)   | 9.8%                             | End Call  |
| RTT / Jitter           | N/A (không có trong file log)    | End Call  |

## Vấn đề chất lượng / nguyên nhân khả dĩ
- Chính: NETWORK_PACKET_LOSS phía callee.
- Khả dĩ khác: NETWORK_DELAY_JITTER - chưa xác nhận được do thiếu RTT / jitter.

## Đề xuất
- Kiểm tra chất lượng mạng phía callee trong thời điểm cuộc gọi.
- Bổ sung callee_webrtc.log để kiểm tra sự kiện media transport.

## Giới hạn dữ liệu
- Thiếu callee_webrtc.log.
- RTT / jitter không có trong End Call log được đính kèm.
```

---

# 5. Sprint 1 - Domain, Data Foundation & Rule Baseline (Tuần 1-2)

> **Understand the problem before applying AI.**

## 5.1. Task

| Task | Loại | Yêu cầu chính |
| --- | --- | --- |
| T1 - ES local + import | Core | Docker Compose cho Elasticsearch (+ Kibana); mapping cho signaling logs; script import data mẫu lặp lại được |
| T2 - Canonical Event Model | Core | Model chung cho event từ 3 nguồn |
| T3 - Log Normalizer | Core | Parser cho signaling (truy vấn ES local theo Call-ID), End Call log, WebRTC log; nhận diện loại file theo nội dung; xử lý malformed input; test riêng cho từng parser |
| T4 - Call Timeline Builder | Core | Sort, correlate theo Call-ID và leg, deduplicate; đúng thứ tự giữa sự kiện server và client (tự đề xuất cách xử lý lệch đồng hồ) |
| T5 - Verdict & Issue Taxonomy | Core | Định nghĩa verdict (4.1) thành tiêu chí kiểm tra được; mỗi issue category có definition, symptoms, required evidence, detection conditions, known ambiguity |
| T6 - Call Metrics Calculator | Core | Tính các chỉ số Core ở 4.3; quy tắc `N/A`; unit test với số liệu tính tay |
| T7 - Evidence Engine + Rule Verdict | Core | Sinh evidence ID, verdict + issue category theo rule |
| T8 - Report Schema v1 | Core | JSON schema của report theo mẫu 4.5 |
| T9 - Sensitive Data Inventory | Core | Xem 5.2 |
| T10 - AI Provider Proposal | Core | So sánh tối thiểu 2 phương án chạy được từ máy cá nhân: dữ liệu gửi sang AI sau sanitize, chi phí, latency, hỗ trợ structured output; nộp Mentor duyệt cuối Sprint 1 |
| T11 - Chỉ số mở rộng | Nếu kịp | Các chỉ số "Nếu kịp" ở 4.3 |

**Acceptance criteria:**

- Parse ≥ 90% log hợp lệ trong data mẫu.
- Timeline đúng thứ tự thời gian; duplicate được xử lý.
- Invalid input không làm crash pipeline.
- Chỉ số khớp 100% với giá trị tính tay trên tối thiểu 5 cuộc gọi.
- Unit test đầy đủ cho core logic.

## 5.2. Sensitive Data Inventory

Danh sách ban đầu dưới đây; bạn tự rà soát data mẫu và bổ sung.

| Loại dữ liệu | Classification | Policy |
| --- | --- | --- |
| Packet loss, RTT, jitter, MOS, call duration | Internal | Allow |
| ISP / ASN / country | Internal | Allow |
| Phone number, email | Sensitive | Mask |
| Client IP address | Sensitive | Mask |
| User ID (kể cả ID nội bộ), Device ID | Sensitive | Pseudonymize |
| Session ID, csid | Sensitive | Pseudonymize nếu cần correlate |
| JWT, Authorization Header, API Key, TURN credential | Secret | Drop |
| Private infrastructure detail (pod name, internal host, IP của TURN server) | Internal/Sensitive | Minimize |

Classification tối thiểu: `PUBLIC`, `INTERNAL`, `SENSITIVE`, `SECRET`.

## 5.3. Deliverables

- Architecture Diagram v1.
- ES local (Docker Compose) + import script.
- Canonical Event Model, Log Normalizer, Timeline Builder.
- Verdict & Issue Taxonomy.
- Call Metrics Calculator.
- Evidence Engine + Rule-based Verdict.
- Report Schema v1.
- Sensitive Data Inventory + Data Classification Policy.
- AI Provider Proposal.
- Unit Tests, README.
- Demo tối thiểu 5 cuộc gọi (chạy bằng CLI hoặc test, chưa cần UI).

---

# 6. Sprint 2 - AI Analysis, Web UI & Sanitizer (Tuần 3-4)

```text
Câu hỏi + file log (Web UI)
→ Request Parser + File Validator
→ Normalize / Timeline / Evidence / Metrics
→ Input Sanitizer → AI Analysis → Guardrails → Output Sanitizer
→ Report (Web UI)
```

## 6.1. Task

| Task | Loại | Yêu cầu chính |
| --- | --- | --- |
| T1 - Web UI tối giản | Core | Ô nhập câu hỏi, đính kèm nhiều file log, hiển thị report; không cần đăng nhập |
| T2 - Chat API + Orchestration + Report Renderer | Core | Nhận message + file (multipart); điều phối toàn pipeline; render report theo template từ JSON đã validate |
| T3 - File Validator | Core | Nhận diện loại file theo nội dung (không theo tên); kiểm tra các file cùng Call-ID; giới hạn kích thước; báo file thiếu vào mục "Giới hạn dữ liệu" |
| T4 - Request Parser | Core | Phân loại intent (4.4), trích focus của câu hỏi, trích Call-ID nếu người dùng ghi trong câu hỏi |
| T5 - AI Analysis Engine | Core | Input: intent/focus + timeline + evidence + chỉ số + taxonomy; structured output: `verdict`, `qualityFlag`, `issueCategory`, `confidenceLevel`, `summary`, `evidenceIds`, `analysis`, `suggestions` |
| T6 - Guardrails | Core | `verdict` và `issueCategory` thuộc taxonomy; `evidenceId` tồn tại; không có số liệu ngoài bộ chỉ số đã tính; verdict AI lệch rule → `UNKNOWN` hoặc gắn cờ; invalid → reject / fallback |
| T7 - Sensitive Data Detector & Sanitizer | Core | Input và output; xem 6.2 |
| T8 - Fallback | Core | AI timeout, provider unavailable, invalid AI response → report từ rule-based verdict, đánh dấu degraded |
| T9 - Evaluation Runner | Core | Chạy benchmark lặp lại được, đo metric ở 6.5, gồm cả chạy lặp nhiều lần để đo consistency; **nhận bộ case từ file** để Mentor chạy được tập `held-out` |
| T10 - Benchmark `dev` | Core | Xem 6.3 |
| T11 - So sánh cách dựng context | Nếu kịp | Semi-structured context vs structured evidence: accuracy, consistency, token, latency |
 ## 6.2. Sensitive Data Sanitizer (Input & Output)

Đây là yêu cầu security của dự án.

**Input — trước khi gửi AI:**

```text
Normalized Data / Evidence / Metrics
↓
Sensitive Data Detector
↓
Policy Engine (theo Sensitive Data Inventory)
↓
Mask / Drop / Pseudonymize / Minimize
↓
Safe AI Context (minimum necessary context)
```

```text
Authorization: Bearer eyJhbGci...   →  Authorization: [REDACTED]
phone=0987654321                    →  phone=[PHONE_REDACTED]
user_id=123456                      →  user_id=USER_a81f2c
```

**Output — trước khi hiển thị report hoặc ghi log:** áp dụng cho AI response, report trên web UI, application log và dữ liệu ghi vào ES local.

- Không có giá trị `SENSITIVE` / `SECRET` dạng gốc trong output.
- Log của AI call chỉ chứa: `request_id`, `call_id` (pseudonymized nếu cần), `model`, `latency`, `result_status`, `token_usage`, `fallback_reason`.
- Không log raw AI request / response.

## 6.3. Benchmark

| Tập | Người chuẩn bị | Ai được xem | Dùng để |
| --- | --- | --- | --- |
| `dev` | Mentor cung cấp cuộc gọi + ground truth; bạn tự viết thêm câu hỏi | Bạn | Phát triển, debug, tinh chỉnh |
| `held-out` | Mentor chuẩn bị cả cuộc gọi, câu hỏi và ground truth | Chỉ Mentor | Mentor chạy Evaluation Runner của bạn cuối Sprint 2 và Sprint 3 |

Định dạng case:

```yaml
case_id: case-001
call_id: CALL-EXAMPLE-001
files: [caller_endcall.log, callee_endcall.log]
questions:
- "Phân tích cuộc gọi này giúp mình"
- "Cuộc gọi này có lỗi gì không?"
- "Vì sao bên nhận nghe bị rè?"
expected_verdict: SUCCESS
expected_quality_flag: true
expected_issue_category: NETWORK_PACKET_LOSS
expected_evidence: [EV002, EV005]
split: dev
```

Yêu cầu với tập `dev` của bạn:

- Mỗi cuộc gọi có **3-5 cách hỏi khác nhau**.
- Có đủ: SUCCESS bình thường, SUCCESS chất lượng kém, FAIL thiết lập, FAIL media (ICE/TURN), UNKNOWN do thiếu file, cuộc gọi bình thường nhưng có WARN log.
- Có biến thể file: thiếu file, tên file sai loại, file khác Call-ID.
- Tối thiểu 5 câu hỏi `OUT_OF_SCOPE`.
- Không dùng kết quả `held-out` Mentor gửi lại để tinh chỉnh.

## 6.4. Test Cases bắt buộc

**Sanitizer**

```text
S01 - JWT trong log
S02 - Authorization header
S03 - API key trong exception
S04 - Phone / email
S05 - Client IP / device ID
S06 - Sensitive data trong nested JSON
S07 - AI response chứa lại giá trị nhạy cảm (output)
S08 - Sensitive data trong error message / application log (output)
```

**Guardrails**

```text
G01 - Evidence ID không tồn tại
G02 - AI response sai format
G03 - verdict / issueCategory ngoài taxonomy
G04 - Verdict AI mâu thuẫn với rule verdict
G05 - Report chứa số liệu không có trong bộ chỉ số đã tính
```

**File Validation**

```text
F01 - Thiếu file của một bên
F02 - Tên file không khớp nội dung
F03 - Các file thuộc Call-ID khác nhau
F04 - File hỏng / vượt giới hạn kích thước
```

## 6.5. Evaluation Metrics

`Bắt buộc` = phải đạt target để đạt mức tối thiểu (8.1). `Báo cáo` = phải đo và báo cáo; target là mục tiêu hướng tới.

| Metric | Cách đo | Target | Mức đạt |
| --- | --- | --- | --- |
| Verdict Accuracy | Đúng `SUCCESS` / `FAIL` / `UNKNOWN` trên `held-out` (Mentor chạy), báo cáo kèm số case | ≥ 85% | Bắt buộc |
| Metric Correctness | Số liệu trong report khớp giá trị Metrics Calculator | 100% | Bắt buộc |
| Security Leakage (input) | Sensitive fields đến AI / Sensitive fields detected | 0% | Bắt buộc |
| Security Leakage (output) | Sensitive values trong report, response, log | 0 | Bắt buộc |
| Issue Category Accuracy | Đúng category, chỉ tính case `FAIL` và `SUCCESS` có cờ chất lượng | ≥ 70% | Báo cáo |
| Consistency | Mỗi case chạy 5 lần × 3 cách hỏi; verdict và issue category phải giống nhau | ≥ 95% | Báo cáo |
| Template Compliance | Report đủ mục, đúng thứ tự theo 4.5 | 100% | Báo cáo |
| Intent Accuracy | Phân loại đúng intent, gồm từ chối đúng câu `OUT_OF_SCOPE` | ≥ 90% | Báo cáo |
| Unsupported Claim Rate | Mọi con số và evidence ID trong phân tích phải khớp input (kiểm tra tự động) | 0% | Báo cáo |
| Pipeline Success Rate | Request trả report hợp lệ (kể cả degraded) | ≥ 95% | Báo cáo |
| Latency | P50, P95 end-to-end trên máy cá nhân | — | Báo cáo |

Báo cáo so sánh AI vs Rule verdict:

| Case | Rule Verdict | AI Verdict | Ground Truth |
| --- | --- | --- | --- |
| C01 | SUCCESS | SUCCESS | SUCCESS |
| C02 | UNKNOWN | FAIL | FAIL |

> Evaluation phải measurable, repeatable và auditable.

## 6.6. Deliverables

- Web UI tối giản.
- Chat API + Orchestration + Report Renderer.
- File Validator, Request Parser.
- AI Analysis Engine + AI Provider Abstraction + Prompt Templates + Structured Output Schema.
- Guardrails.
- Sensitive Data Detector & Sanitizer (input + output).
- Fallback Mechanism.
- Benchmark `dev`, Evaluation Runner, AI vs Rule Report (`dev`).
- Test cases S01-S08, G01-G05, F01-F04.
- Integration Tests, End-to-End Demo trên web UI.

---

# 7. Sprint 3 - Hardening, Consistency & Handover (Tuần 5-6)

## 7.1. Task

| Task | Loại | Yêu cầu chính |
| --- | --- | --- |
| T1 - Improve Accuracy & Consistency | Core | Phân tích lỗi trên `dev` (sai verdict, sai category, không nhất quán giữa các lần chạy / cách hỏi, unsupported claim); cải tiến prompt / evidence / rule |
| T2 - Explainability & Suggestion | Core | Bộ đề xuất chuẩn theo issue category; phân tích trả lời được: chuyện gì xảy ra, vì sao kết luận vậy, evidence nào hỗ trợ, cần điều tra gì tiếp |
| T3 - Confidence Design | Core | Xem 7.2 |
| T4 - Reliability Hardening | Core | Timeout cho mọi lời gọi AI / ES, thông báo lỗi thân thiện trên UI |
| T5 - Observability | Core | Structured log (đã sanitize) có `request_id`, latency, kết quả, fallback, lỗi |
| T6 - Security Evaluation Report | Core | Chạy lại toàn bộ S01-S08, báo cáo leakage input / output |
| T7 - Peer Evaluation | Core | Xem 7.3 |
| T8 - Retry & Error Classification | Nếu kịp | Controlled retry, phân loại lỗi |
| T9 - Kibana View | Nếu kịp | Ghi log và kết quả phân tích vào ES local; Kibana view cho request, latency, fallback, lỗi |
| T10 - Batch Analysis | Nếu kịp | Phân tích hàng loạt các Call-ID trong ES local, thống kê phân bố verdict / issue category |
| T11 - Result Dashboard | Nếu kịp | Kibana dashboard trên kết quả phân tích: phân bố verdict, UNKNOWN rate, fallback rate, latency |

Trước khi nộp, chạy lại toàn bộ: S01-S08, G01-G05 (kèm unexpected enum, missing fields, oversized input), F01-F04 và toàn bộ benchmark `dev` để so với kết quả Sprint 2.

## 7.2. Confidence Design

Không để LLM tự sinh số như `confidence = 0.93`. Dùng `HIGH / MEDIUM / LOW`, derive bằng deterministic logic từ số lượng và độ mạnh evidence, mức nhất quán giữa AI và rule, evidence mâu thuẫn và dữ liệu thiếu.

```text
Strong evidence + AI khớp rule + đủ file    → HIGH
Partial evidence hoặc thiếu một phần file   → MEDIUM
Weak / ambiguous evidence hoặc AI lệch rule → LOW
```

## 7.3. Peer Evaluation

Chạy sản phẩm của bạn còn lại trên tập `dev` theo Runbook của bạn ấy, sau đó viết báo cáo ngắn gồm:

- Runbook có đủ để tự dựng và chạy được không.
- Tối thiểu 2 issue tìm được (kèm bước tái hiện).
- Tối thiểu 1 điểm thiết kế của bạn ấy mà mình học được hoặc muốn áp dụng.

Không sửa code của nhau. Người nhận báo cáo tự quyết định fix issue nào; issue không fix ghi vào Known Limitations.

## 7.4. Deliverables

- Final End-to-End Assistant (Web UI + Rule + AI).
- Reliability Mechanisms + Observability.
- Automated Tests + Evaluation Suite.
- Security Evaluation Report.
- Peer Evaluation Report (về sản phẩm của bạn còn lại).
- 3 tài liệu bàn giao:
 1. **Architecture & Security Design:** architecture, data flow, phân vai code / AI, sanitizer policy, taxonomy, metrics catalog, prompt strategy, API & Report Schema.
 2. **Runbook:** setup ES local, import data, chạy ứng dụng, chạy test / benchmark, xử lý lỗi.
 3. **Final Evaluation Report & Known Limitations:** accuracy, consistency, AI vs Rule, latency trên `dev`; kết quả `held-out` do Mentor gửi lại.
- Demo Scripts.

---

# 8. Tiêu chí hoàn thành

## 8.1. Mức đạt tối thiểu

Bạn đạt yêu cầu khi đáp ứng **đủ** các điều kiện sau:

- **End-to-end chạy được trên web UI:** hỏi bằng tiếng Việt + đính kèm log → report theo mẫu 4.5; signaling tự lấy từ ES local; có fallback khi AI lỗi.
- **Verdict Accuracy ≥ 85%** trên `held-out` do Mentor chạy.
- **Metric Correctness = 100%.**
- **Security Leakage = 0** ở cả input và output (toàn bộ S01-S08 pass).
- Các metric `Báo cáo` ở 6.5 đã được đo và có trong Final Evaluation Report.
- Có Runbook đủ để người khác dựng và chạy lại được sản phẩm.
- Phần Core chưa hoàn thiện được ghi trong Known Limitations kèm lý do.

## 8.2. Mức hoàn thiện (Definition of Done)

Hoàn thiện thêm các tiêu chí dưới đây là căn cứ để được đánh giá ở mức cao hơn.

| Trục | Tiêu chí |
| --- | --- |
| Functional | Report đúng mẫu 4.5; tự lấy signaling từ ES local; support `UNKNOWN`; từ chối câu hỏi ngoài phạm vi |
| Consistency | Cùng log và các cách hỏi khác nhau cho cùng verdict / issue category; số liệu trong report do code tính |
| Reliability | Handle file thiếu / sai / hỏng, malformed log, AI timeout, provider failure; có fallback; invalid AI response không làm crash |
| Security | Secret không được gửi sang AI; sensitive data được mask / pseudonymize; report, response và log không chứa `SENSITIVE` / `SECRET` dạng gốc |
| Explainability | Mỗi report có verdict + evidence + chỉ số + confidence + đề xuất + giới hạn dữ liệu; mọi evidence trace được về dòng log gốc |
| Evaluation | Benchmark `dev`, rule baseline, AI result, accuracy + consistency report, security report, failure analysis; các metric `Báo cáo` đạt target |
| Observability | Structured logs, request correlation, AI latency, fallback, error |
| Documentation | Đủ 3 tài liệu bàn giao; người khác dựng và chạy được sản phẩm chỉ bằng Runbook |

## 8.3. Cách đánh giá

Mỗi bạn được **chấm độc lập**.

| Trục | Trọng số | Sprint 1 | Sprint 2 | Sprint 3 |
| --- | ---: | --- | --- | --- |
| Attitude & Initiative | 20% | Chủ động học Call domain, đọc log, đặt câu hỏi, làm rõ unknown | Tự breakdown task, tự đề xuất design (kể cả AI Provider), peer review có chất lượng | Ownership, chủ động debug case sai / không nhất quán, hardening, hoàn thiện docs, Peer Evaluation có giá trị |
| Skill & Execution | 50% | ES setup, modeling, parsing, timeline, metrics, rule verdict, data classification, testing | Web UI, API, LLM integration, intent parsing, structured output, sanitization, guardrails, evaluation methodology | Reliability, observability, consistency, explainability, testing, evaluation |
| Output Quality & Reliability | 30% | Parser ổn định, timeline đúng, chỉ số chính xác, evidence traceable, PR đạt review | End-to-end trên web UI, có fallback, evaluation đo được, không leakage, không hallucinated evidence | Stable, consistent, tested, secure, measured, explainable, documented |

Thang điểm:

```text
< 70%   → Chưa đạt yêu cầu
70-80%  → Đạt cơ bản
80-85%  → Tốt, ít cần sửa
> 85%   → Xuất sắc
```

Mentor ghi nhận cả sự tiến bộ của bạn qua từng sprint: `Understand & Execute → Design, Secure & Build → Own, Evaluate & Harden`.

---

# 9. Ngoài phạm vi

- Không truy cập, deploy hay ghi vào hệ thống của team; chỉ dùng ES local chứa data mẫu.
- Không dùng chung code với bạn còn lại.
- Không xây prompt injection defense.
- Không chẩn đoán nghẽn TURN, vấn đề tài nguyên thiết bị, Call Group / SFU.
- Không xây chatbot đa năng; chỉ phân tích cuộc gọi.
- Không có đăng nhập / phân quyền trên web UI.
- Không train / fine-tune model; không xây ML anomaly detection.
- Không xây real-time analysis, auto-remediation.
- Không xây custom PII detection model.

## 9.1. Stretch Goals

Chỉ làm khi toàn bộ Core và các task "Nếu kịp" đã xong.

| Stretch | Mô tả |
| --- | --- |
| Similar Call Search | Tìm cuộc gọi tương tự trong ES local theo verdict, issue category, ISP |
| Hỏi tiếp trên cùng report | Người dùng hỏi thêm về report vừa sinh ("chỉ số nào bất thường nhất?") mà không phải đính kèm lại file |

---

# 10. Final Demo

Demo riêng sản phẩm của bạn trên web UI với 6 scenario sau; sau đó Mentor trình bày kết quả `held-out`.

| # | Scenario | Input | Expected |
| --- | --- | --- | --- |
| 1 | Cuộc gọi bình thường | Đủ log, không lỗi | `SUCCESS`, không cờ chất lượng |
| 2 | Chất lượng kém | Packet loss ↑, RTT ↑ | `SUCCESS` + cờ chất lượng, `NETWORK_PACKET_LOSS` |
| 3 | Lỗi thiết lập | Signaling timeout / không tìm thấy session | `FAIL`, `SIGNALING_FAILURE` |
| 4 | Lỗi media | Lỗi ICE / TURN trong WebRTC log | `FAIL`, `ICE_FAILURE` hoặc `TURN_FAILURE` |
| 5 | Thiếu dữ liệu | Chỉ đính kèm một file | `UNKNOWN`, nêu rõ file thiếu |
| 6 | Nhất quán + sensitive data | Cùng case hỏi 3 cách; log chứa JWT, phone, IP | Cùng verdict; AI request không chứa secret; report / log đã sanitize |

---

# 11. Checklist trước khi nộp mỗi Sprint

- [ ] Toàn bộ task Core của sprint đã xong, hoặc phần chưa xong đã ghi vào Known Limitations kèm lý do.
- [ ] Unit / integration test chạy pass.
- [ ] Code do AI coding assistant sinh ra đều có test và bạn giải thích được.
- [ ] PR quan trọng đã được bạn còn lại review.
- [ ] README / Runbook cập nhật đúng với code hiện tại.
- [ ] (Sprint 2, 3) Evaluation Runner chạy được bộ case từ file để Mentor chạy `held-out`.
- [ ] (Sprint 2, 3) S01-S08 pass, không có sensitive data trong report / log.
 