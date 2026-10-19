# Peer Evaluation Report (Sprint 3 T7)

| Mục | Nội dung |
| --- | --- |
| Người đánh giá | Trần Anh Tú |
| Sản phẩm được đánh giá | `hason0510/call-analysis-assistant` (Bế Nguyễn Hà Sơn), commit `a905256` (2026-10-19) |
| Ngày chạy | 2026-10-20 |
| Môi trường | Windows 11, PowerShell 5.1 / Git Bash, OpenJDK 21.0.1, Docker Desktop (engine 28), Python 3.11, OpenAI `gpt-4o-mini` |
| Dữ liệu | Data mẫu của Mentor (`fail/`, `success/`, `for_test/`), đặt ở `../ai20k_sample` đúng như Runbook yêu cầu |
| Nguyên tắc | Chỉ chạy và gọi API, **không sửa code** của bạn ấy (mục 7.3). File log dùng để thử là bản sao tạo trong thư mục tạm |

## Tóm tắt

- **Runbook: đủ để dựng và chạy lại.** Làm đúng từng bước trên máy chưa từng chạy repo này, mọi kết quả khớp với
  kết quả Runbook ghi (import 20 file / 1059 document, `--analyze-all` 13/13, 590 test 0 lỗi, benchmark `dev`
  100%). Chỉ có hai chỗ vướng nhỏ, đều có hướng dẫn xử lý trong Runbook hoặc dễ tự đoán.
- **Hai issue có tái hiện**, cả hai đều làm sai kết luận chứ không chỉ sai hiển thị: (1) Call-ID ghi trong câu hỏi
  khác cuộc gọi của file đính kèm cho ra verdict sai với độ tin cậy MEDIUM; (2) end call log có BOM UTF-8 hoặc lưu
  dạng UTF-16 bị loại như file hỏng, cả report thành `UNKNOWN`.
- **Điểm thiết kế học được:** AI nhắc chỉ số bằng **mã** và code điền giá trị (`MetricCodes`); cổng fail-closed độc
  lập với Sanitizer (`OutboundGate`); báo cáo benchmark ghi **dấu vân tay của prompt**.

## 1. Runbook có đủ để tự dựng và chạy được không?

| Bước (Runbook) | Runbook ghi | Kết quả thực tế | Ngoài Runbook phải làm gì |
| --- | --- | --- | --- |
| 1. Chuẩn bị | JDK 21, Docker, data ở `../ai20k_sample`, Python tuỳ chọn | Đủ | Chép data mẫu ra cạnh repo theo đúng cây thư mục mẫu |
| 2.2 `.env` | Chép `.env.example`, điền 2 khoá | Được | — |
| 2.3 Build `.\build.ps1 -q clean compile` | Tự tìm JDK 21 ở 4 vị trí | Script không tìm thấy JDK 21 của máy (`~/.jdks/openjdk-21.0.1`, nơi IntelliJ tải JDK về) | Đặt `$env:JAVA_HOME_21` **đúng như Runbook hướng dẫn** → build thành công, không `ERROR` |
| 2.4 Elasticsearch | `docker compose up -d elasticsearch`, chờ green | Green sau khoảng 1 phút | Dừng ES của dự án khác đang giữ cổng 9200 (Runbook có ghi trong bảng xử lý sự cố) |
| 2.5 Nạp signaling | 20 file, 1059 document, không vấn đề | **Khớp** | — |
| 2.6 `--analyze-all` | 13/13, 20/20 schema, 7 không nhãn | **Khớp** | — |
| 3.1 Web UI + Chat API (`:8081`) | Chạy profile `web` | Chạy được; 11 request thử đều trả HTTP 200 | — |
| 4.1 `.\build.ps1 test` | 590 test, 0 lỗi | **590 test, 0 lỗi, 0 bỏ qua** (Testcontainers chạy ES thật) | — |
| 4.2 Benchmark `dev` | `make_variants.py` rồi `--evaluate`, khoảng 3 phút, khoảng 0,065 USD | Chạy được, **4,5 phút**; số liệu ở mục 4 | — |
| 5. Kibana | `setup_views.py`, `setup_dashboard.py` | 4 saved search, 17 biểu đồ, 2 dashboard tạo được | Cần Python, trong khi mục 1 ghi Python chỉ cần cho bước 4.2 |

**Kết luận: Đủ.** Người chưa biết dự án dựng lại được hoàn toàn chỉ bằng Runbook. Hai góp ý nhỏ:

1. `build.ps1` có thể dò thêm `~/.jdks/*21*` (thư mục IntelliJ tải JDK về) trước khi báo lỗi. Hiện tại Runbook đã
   chỉ cách đặt `JAVA_HOME_21` nên không chặn được ai.
2. Mục 1 ghi "Python 3 (tuỳ chọn): chỉ cần nếu chạy benchmark", nhưng mục 5 (Kibana) cũng cần Python.

## 2. Issue tìm được

Cách thử: gọi `POST /api/analyze` trên server profile `web` của bạn ấy, bằng script Python (UTF-8, tránh lỗi mã hoá của
curl). Cuộc gọi dùng: `2D9057AA` (nhãn **FAIL**, ICE) và `DE7DD314` (nhãn **SUCCESS**).

### Issue 1: Call-ID trong câu hỏi khác cuộc gọi của file đính kèm → verdict sai với độ tin cậy MEDIUM

- **Mức độ:** cao. Kết luận sai, nhưng report trông đáng tin (MEDIUM, có evidence, không cảnh báo gì về WebRTC log).
- **Bước tái hiện:**
  1. Đính kèm `callee_endcall.log` và `callee_webrtc.log` của **2D9057AA** (cuộc gọi FAIL).
  2. Câu hỏi: `Phân tích cuộc gọi DE7DD314-F432-45CB-BCB4-AE9103CC0919 giúp mình` (Call-ID của một cuộc gọi
     **SUCCESS**, ví dụ người dùng dán nhầm Call-ID).
  3. Gửi.
- **Kết quả mong đợi:** hoặc phân tích 2D9057AA (cuộc gọi của file), hoặc báo rõ file đính kèm không thuộc DE7DD314
  và không dùng chúng; không được đưa ra kết luận về DE7DD314 dựa trên log của cuộc gọi khác.
- **Kết quả thực tế:** report cho **DE7DD314: `FAIL` / `ICE_FAILURE`, độ tin cậy MEDIUM**. Tóm tắt: "Cuộc gọi không
  thành công. ICE chuyển sang failed và không bao giờ đạt connected". Đối chứng: DE7DD314 với log của chính nó ra
  `SUCCESS`, đúng nhãn của Mentor.
  - End call log bị loại đúng ("Call-ID trong file (2D9057AA…) khác Call-ID đang phân tích (DE7DD314…), đã loại").
  - Nhưng **WebRTC log đi cùng vẫn được giữ** và quyết định verdict: ICE failed của 2D9057AA thành ICE failed của
    DE7DD314.
  - Biến thể: chỉ đính kèm `callee_webrtc.log` của 2D9057AA kèm Call-ID của DE7DD314 cũng ra `FAIL` / `ICE_FAILURE`
    MEDIUM (request `e9df8028-…`).
- **Bằng chứng:** request `40076832-29cd-4ff2-8c61-fd8b927493f4` và `e9df8028-6ae0-402b-a6d2-69ccaa933563`; response lưu ở thư mục tạm của phiên đánh
  giá.
- **Liên quan Known Limitations:** README có ghi "WebRTC log không mang Call-ID nên không kiểm được file WebRTC của cuộc
  gọi khác". Issue này cho thấy hậu quả cụ thể: không chỉ "không kiểm được", mà ra **kết luận sai trái nhãn**, và ở ca
  đầu hệ thống **đã có bằng chứng** file đính kèm thuộc cuộc gọi khác (end call log lệch Call-ID) nhưng chưa dùng.
- **Gợi ý (người nhận tự quyết):** khi end call log trong cùng lượt upload lệch Call-ID, coi WebRTC log đi kèm là
  nghi vấn (loại, hoặc giữ nhưng hạ LOW và nêu trong "Giới hạn dữ liệu"). Khi chỉ có WebRTC log mà Call-ID lấy từ câu
  hỏi, nên nêu rõ "không kiểm được WebRTC log có thuộc cuộc gọi này" và không để HIGH/MEDIUM dựa riêng vào nó.

### Issue 2: End call log có BOM UTF-8 hoặc lưu dạng UTF-16 bị coi là file hỏng → toàn bộ report `UNKNOWN`

- **Mức độ:** trung bình. Nhiều công cụ Windows ghi file kiểu này: PowerShell 5.1 `Out-File` / `Set-Content
  -Encoding UTF8` thêm BOM; Notepad lưu "Unicode" là UTF-16. Người dùng mở log, sửa hoặc cắt bớt rồi lưu lại là gặp.
- **Bước tái hiện:**
  1. Lấy `callee_endcall.log` của 2D9057AA, thêm 3 byte `EF BB BF` ở đầu (BOM UTF-8). Nội dung không đổi.
  2. Đính kèm file đó cùng `callee_webrtc.log`, hỏi `Phân tích cuộc gọi này giúp mình`.
  3. Lặp lại với cùng file lưu dạng UTF-16.
- **Kết quả mong đợi:** như khi gửi file gốc: `FAIL` / `ICE_FAILURE` cho 2D9057AA. Trường hợp kém nhất là báo rõ
  "file có BOM / sai mã hoá" để người dùng biết cách sửa.
- **Kết quả thực tế:**
  - **BOM UTF-8** (request `877d22e0-e612-495d-97c8-b33e969ac340`): parser báo "dòng 1: chưa khai báo #H﻿#H1 trước dòng
    dữ liệu này" (BOM dính vào header `#H1`), end call log không đọc được → không có Call-ID → không lấy được
    signaling → `UNKNOWN`, LOW. "Giới hạn dữ liệu" còn ghi "Thiếu end call log của caller" trong khi file người dùng
    gửi là của **callee** và có gửi.
  - **UTF-16** (request `a74e85a2-5199-4295-8163-df9863317761`): "callee_endcall.log: file hỏng hoặc không phải văn bản (có ký tự nhị phân), đã loại" → `UNKNOWN`, LOW.
  - Đối chứng: cùng nội dung, lưu UTF-8 không BOM ra đúng `FAIL` / `ICE_FAILURE`; đổi xuống dòng sang CRLF cũng đúng.
- **Gợi ý:** bỏ BOM UTF-8 khi đọc; nhận diện BOM UTF-16 LE/BE và giải mã; nếu vẫn không đọc được thì thông báo nêu
  nguyên nhân mã hoá thay vì "file hỏng".

### Đã thử nhưng không thấy lỗi (ghi lại để người nhận biết phạm vi đã được kiểm)

| Thử | Kết quả |
| --- | --- |
| Số điện thoại trong **tên file** (`0933444555_webrtc.log`, nội dung là end call log) | Tên file được che thành `[CONTACT_REDACTED]` trong "Giới hạn dữ liệu"; không lộ ở response, log server hay index `analysis-requests`. (Sản phẩm của mình từng lộ đúng ca này, xem `docs/security-evaluation-report.md`.) |
| Giá trị nhạy cảm dính dấu `_` trong log (`user_0987654321`, `ip_113.161.45.67`) | Không lộ ở response và log server |
| Tên file có đường dẫn `../../evil_endcall.log` | Chỉ giữ tên file; phân tích theo nội dung, đúng |
| Câu hỏi chỉ có khoảng trắng + file | Phân tích bình thường |
| Một file đính kèm hai lần cùng tên | Phân tích đúng (`SUCCESS`) |
| Bản ghi request trong ES (`analysis-requests`) | Chỉ mã / số / cờ; không có Call-ID gốc hay giá trị đã chèn |

## 3. Điểm thiết kế học được / muốn áp dụng

| Điểm thiết kế | Vì sao hay | Áp dụng vào sản phẩm của mình thế nào |
| --- | --- | --- |
| **AI nhắc chỉ số bằng mã** (`{PACKET_LOSS_CALLER}`), code thay bằng giá trị kèm tên chỉ số (`MetricCodes`) | G05 của cả hai sản phẩm chỉ bắt được số **sai**. Kiểu lỗi nguy hiểm hơn là số **đúng nhưng gán sai nghĩa** (mẫu stats 11,32% bị gọi là mất gói cả cuộc). Khi tên và giá trị do code điền, kiểu lỗi đó biến mất, và chỉ số chọn nhầm sẽ hiện rõ tên sai chỗ | Bên mình đang cho AI chép số rồi để G05 so khớp, có dung sai làm tròn. Đổi sang mã chỉ số sẽ bỏ được phần dung sai, và loại được kiểu lỗi mà G05 không bắt được |
| **Cổng fail-closed độc lập** (`OutboundGate`) quét đúng chuỗi sắp gửi bằng bộ nhận diện **khác** regex của Sanitizer | Sanitizer bỏ sót một dạng thì một bộ nhận diện viết riêng vẫn có cơ hội bắt. Bên mình kiểm "còn sót" bằng **chính** engine của Sanitizer, nên một lỗ của regex là lỗ của cả hai lớp (đúng như lỗi dấu `_` mình gặp) | Thêm một bộ nhận diện độc lập, đơn giản (IP, JWT, email, SĐT) chạy trên chuỗi cuối cùng trong `ChatAnalysisService` trước khi gọi provider |
| **Báo cáo benchmark ghi dấu vân tay của prompt** (`Prompt AI: analyze-call.system.txt e717cfd0…`) | Hai lượt chạy khác prompt thì không so trực tiếp được. Bên mình phải tự ghi tay "trước / sau khi sửa prompt" trong `docs/accuracy-consistency-analysis.md` | Thêm hash của prompt + schema vào đầu `summary.md` của `evaluate` |

## 4. Số liệu chạy `dev` theo Runbook (`--repeat=1`, AI thật)

`target/evaluation/evaluation-20261020-030602.md`: 20 case, 69 lượt, `gpt-4o-mini`, signaling có cho 13/13 Call-ID.

| Metric | Giá trị | Ghi chú |
| --- | --- | --- |
| Verdict Accuracy | 100% (62/62) | 19 case có `expected_verdict` |
| Metric Correctness | 100% (62/62) | |
| Security Leakage input / output | 0/1651 · 0 | gồm application log 69/69 lượt |
| Issue Category Accuracy | N/A | bộ case không đặt nhãn category (đúng như README) |
| Consistency | 100% (62/62) | chỉ lặp 1 lần; README báo 100% với `--repeat=5` |
| Template Compliance | 100% | |
| Intent Accuracy | 100% (64/64) | |
| Unsupported Claim (AI trước Guardrails / report cuối) | 0% / 0% | |
| Pipeline Success Rate | 100% | |
| Fallback | 0% | |
| Latency P50 / P95 | 3276 / 4588 ms | max 7784 ms |
| Thời gian chạy | 4,5 phút | Runbook ghi khoảng 3 phút |

Số liệu khớp với những gì README và Final Evaluation Report của bạn ấy công bố.

---

Ghi chú quy trình (mục 7.3): **không sửa code của nhau**. Người nhận báo cáo tự quyết định fix issue nào; issue không
fix ghi vào Known Limitations của họ. Hai issue trên nên được kiểm lại trên sản phẩm của chính mình: Call-ID lệch giữa
câu hỏi và file, và log có BOM. Kết quả kiểm ghi ở Known Limitations của sản phẩm mình.
