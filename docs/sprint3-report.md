# Sprint 3 — Hardening, Consistency & Handover

**Dự án:** Trợ lý AI phân tích cuộc gọi (OJT AI 20K)
**Thực tập sinh:** Trần Anh Tú
**Kiểm chứng lần cuối:** 2026-10-14. `mvn test` 328/328 pass (có Elasticsearch thật); `npm test` 15/15 pass; benchmark
`dev` 4 lượt với OpenAI `gpt-4o-mini` thật; `security-eval` với AI thật; 6 scenario Final Demo trên app thật.

## Tóm tắt

| Task (mục 7.1) | Loại | Trạng thái | Ở đâu |
| --- | --- | --- | --- |
| T1 Improve Accuracy & Consistency | Core | Xong | `docs/accuracy-consistency-analysis.md` |
| T2 Explainability & Suggestion | Core | Xong | `report/VerdictExplainer`, `report/SuggestionCatalog` |
| T3 Confidence Design | Core | Xong | `report/confidence/EvidenceBasedConfidencePolicy` |
| T4 Reliability Hardening | Core | Xong | timeout ES/AI, `SignalingFetchError`, UI |
| T5 Observability | Core | Xong | `request_id`, `chat_request` / `ai_call` / `es_query`, `logs/app.jsonl` |
| T6 Security Evaluation Report | Core | Xong | `docs/security-evaluation-report.md` |
| T7 Peer Evaluation | Core | Xong (2026-10-20) | `docs/peer-evaluation-report.md` |
| T8 Retry & Error Classification | Nếu kịp | Xong | OpenAI (Sprint 2) + Elasticsearch (Sprint 3) |
| T9 Kibana View | Nếu kịp | Xong | index `call-analysis-results`, `kibana/` |
| T10 Batch Analysis | Nếu kịp | Xong | lệnh `batch` |
| T11 Result Dashboard | Nếu kịp | Xong | `kibana/call-analysis-dashboard.ndjson` |
| Tài liệu bàn giao (7.4) | Core | Xong | `architecture-security-design.md`, `runbook.md`, `final-evaluation-report.md` |
| Demo Scripts | Core | Xong | `demo/run_demo.py`, `demo/README.md` |

## Đã làm gì

**T3 - Độ tin cậy.** Chính sách cũ chỉ nhìn dữ liệu thiếu, và việc AI lệch rule được hard-code thành LOW ở chỗ ghép
report. Chính sách mới đúng bảng mục 7.2. Nó xét bốn tín hiệu: độ mạnh evidence (có evidence từ *nguồn chính* của
category theo mục 4.2 và ít nhất 2 dòng), file thiếu, evidence không neo được thời gian tuyệt đối, và kết quả đối
chiếu AI-vs-rule (khớp / lệch / không có AI). Report degraded không bao giờ HIGH, vì 7.2 đòi "AI khớp rule".
`confidenceLevel` của AI bị bỏ qua. Report mang thêm `confidenceReasons` (schema v3), nên người đọc thấy vì sao.

**T2 - Giải thích và đề xuất.** Mỗi `VerdictRule` khai báo tiêu chí nó kiểm. Từ đó code viết mục "Căn cứ kết luận"
trả lời bốn câu hỏi của 7.1: chuyện gì xảy ra, vì sao kết luận vậy, evidence nào, điều tra gì tiếp. Mục này vẫn còn
khi AI lỗi hoặc mâu thuẫn rule; trước đây hai trường hợp đó report chỉ còn một câu tóm tắt. Mỗi category có 2-4 đề
xuất chuẩn, mỗi file thiếu có một đề xuất "bổ sung file X để...". Đề xuất của AI được **nối sau** bộ chuẩn, không
thay thế.

**T4/T8 - Reliability.** Elasticsearch trước đây không có timeout, và một loại lỗi (`ElasticsearchException`, là
RuntimeException) lọt thẳng thành HTTP 500. Giờ ES có timeout 2 s / 5 s; lỗi được phân loại `ES_UNAVAILABLE /
ES_TIMEOUT / ES_INDEX_MISSING / ES_ERROR` và thử lại một lần chỉ với lỗi kết nối. Report nêu lý do trong "Giới hạn
dữ liệu". Pool gọi AI có giới hạn. UI có timeout 90 s, mã fallback đổi thành câu tiếng Việt, lỗi hiện kèm mã yêu cầu.
Đã kiểm trực tiếp: tắt container ES giữa lúc chạy, UI vẫn nhận report `UNKNOWN` kèm câu "Không kết nối được
Elasticsearch...", log có hai dòng `es_query error_kind=ES_UNAVAILABLE attempt=1/2`.

**T5 - Observability.** Mỗi request HTTP có một `request_id` do server sinh: nằm trong MDC (mọi dòng log), header
`X-Request-Id`, response và thông báo lỗi. Mỗi request có đúng một dòng `chat_request`. Request Parser cũng log
`ai_call purpose=intent`. Profile `jsonlog` ghi structured log JSON qua layout riêng, dùng lại đúng bộ lọc
`%san`/`%sanEx`: encoder JSON có sẵn ghi message gốc, tức là sẽ bỏ qua sanitizer.

**T6 - Security Evaluation.** Lệnh `security-eval` chạy bộ case S01-S08 qua pipeline thật ba lượt: AI thật; AI giả
cố tình trả lại mọi giá trị nhạy cảm; AI giả rò giá trị vượt được Guardrails để thử riêng Output Sanitizer. Kết quả:
0 rò rỉ ở cả ba lượt. Bộ case cũng chạy trong mỗi lần `mvn test`.

**T1 - Accuracy & Consistency.** Thêm cờ để AI không thấy kết luận rule, rồi chạy lại toàn bộ `dev`. Phần đáng giá
nhất của sprint nằm ở đây (mục "Phát hiện").

**T9-T11.** Mỗi lượt phân tích ghi một bản tóm tắt đã sanitize vào index `call-analysis-results`. Lệnh `batch` phân
tích mọi Call-ID trong ES. Dashboard Kibana (data view, 7 visualization, saved search) là file NDJSON do script sinh,
đã import thử vào Kibana 8.15 thành công 10/10 object.

## Phát hiện (đều lộ ra khi chạy thật, không phải khi unit test)

1. **Con số 96.5% của Sprint 2 phần lớn là AI chép kết luận rule.** Bỏ `ruleBaseline` khỏi input thì verdict
   accuracy còn 61.7%, consistency 40.7%; G04 lần đầu bị kích hoạt hàng loạt. Nguyên nhân chính: prompt chưa từng
   định nghĩa SUCCESS / FAIL / UNKNOWN, nên thiếu log một bên là AI trả `UNKNOWN`. Sau khi thêm định nghĩa verdict
   và nguồn evidence bắt buộc cho từng category, AI tự kết luận verdict khớp rule **100%** (431/431), lần này không
   được nhìn đáp án.
2. **Thứ tự taxonomy gửi AI thay đổi giữa các lần khởi động** (`Map.of`): cùng một cuộc gọi có thể nhận prompt khác
   nhau. Đã cố định theo enum.
3. **Số điện thoại / IP dính dấu `_` không được che.** Bộ case S08 đính kèm file `0933444555_webrtc.log`; tên file đi
   vào "Giới hạn dữ liệu" và lọt nguyên số điện thoại vào cả AI request lẫn report. Regex dùng `\w` ở biên mà `_`
   thuộc `\w`. Unit test S01-S08 của Sprint 2 đều xanh vì giá trị test luôn có khoảng trắng hai bên.
4. **README ghi sai lệnh `import`** (`import success fail for_test` không import được gì vì đường dẫn tính từ thư mục
   đang đứng). Lộ ra khi viết Runbook và chạy lại từng lệnh.
5. **Script demo lỗi trên console Windows** (cp1252 không in được tiếng Việt). Đã ép stdout UTF-8.
6. Khi ES tắt, câu trong "Giới hạn dữ liệu" từng ghi "kết luận chỉ dựa trên log client" ngay cạnh verdict `UNKNOWN`.
   Hai thông tin mâu thuẫn nhau; đã sửa lời.

## Số liệu (cấu hình chạy thật, `dev`, gpt-4o-mini)

Verdict 96.5% (416/431, chỉ sai `var-single-file` đang chờ Mentor) · Metric Correctness 100% · Leakage 0 · Category
100% · Consistency 100% · Template 100% · Intent 100% · Unsupported Claim 0% · Pipeline 100% · Fallback 0% (Sprint 2:
2.1%) · P50 / P95 2948 / 4608 ms · 4055 token/lần. Bảng đầy đủ và so sánh với Sprint 2: `docs/final-evaluation-report.md`.

Final Demo (spec mục 10), chạy trên app thật 2026-10-14:

| # | Scenario | Kết quả |
| --- | --- | --- |
| 1 | Bình thường | `SUCCESS`, không cờ, HIGH |
| 2 | Chất lượng kém | `SUCCESS` + cờ, `NETWORK_PACKET_LOSS` |
| 3 | Lỗi thiết lập | `FAIL`, `SIGNALING_FAILURE` |
| 4 | Lỗi media | `FAIL`, `ICE_FAILURE` |
| 5 | Thiếu dữ liệu (một WebRTC log) | `UNKNOWN`, LOW, nêu đủ file thiếu (xem ghi chú trong `demo/README.md`) |
| 6 | 3 cách hỏi + JWT/phone/IP | cùng `SUCCESS/NONE` cả 3 lần; không giá trị nào lộ trong response, log console hay log JSON |

## Commit

| Commit | Nội dung |
| --- | --- |
| S3-1 | Confidence design |
| S3-2 | Explainability + bộ đề xuất chuẩn |
| S3-3 | Timeout + phân loại lỗi ES, request id, UI |
| S3-4 | Observability |
| S3-5a / S3-5b | Cờ bỏ `ruleBaseline`; sửa prompt / context từ phân tích lỗi |
| S3-6a / S3-6b | `security-eval` + S01-S08; báo cáo bảo mật |
| S3-7 | Lưu kết quả, batch, Kibana |
| S3-8 | Tài liệu bàn giao, demo, báo cáo sprint |

## Xử lý nhận xét của Mentor sau Sprint 2 (2026-10-20)

| Nhận xét | Đã làm | Commit |
| --- | --- | --- |
| Đo Security Leakage dùng chính bộ dò của Sanitizer | Phép đo độc lập `RawValueOracle` (đọc giá trị gốc từ file dữ liệu theo cấu trúc file). Lượt `dev` đầu tiên tìm ra Call-ID lọt vào AI qua câu "Giới hạn dữ liệu"; đã sửa, chạy lại 0 / 0 / 0 | R4, R5 |
| Issue Category "100%" so với nhãn lấy từ rule | Accuracy chỉ tính nhãn Mentor (hiện "chưa đo"); số cũ đổi tên "khớp nhãn tạm"; file xin nhãn mù `benchmark/dev/category-labels-for-mentor.csv` | R4 |
| UI không hiện cờ "cần kiểm tra"; cắt file thứ 9 im lặng | `ChatResponse.reviewFlags` + banner "Cần kiểm tra thủ công"; UI nêu tên file không được đính kèm | R3 |
| Clone sạch lỗi 80/269 test, không báo lý do | Test cần `sample-data/` được bỏ qua kèm lý do (clone sạch: 0 lỗi); Runbook mục "Dữ liệu mẫu" | R2 |
| Đọc ES cắt im lặng ở 1000 sự kiện | Đọc theo trang tới 10.000; vượt thì "Giới hạn dữ liệu" nêu `ES_TRUNCATED` | R1 |
| Chưa quay demo 6 kịch bản trên trình duyệt | Đã chạy cả 6 trên Web UI (2026-10-20), đều đúng spec; ảnh chụp từng kịch bản ở `demo/out/screenshots-2026-10-20/` (không commit) | — |
| Peer Evaluation (Plan 7.3) | Đã làm (`docs/peer-evaluation-report.md`), các issue tìm được đã sửa (Peer fix 1-4) | T7, Peer fix |
| Hai dependency mới chưa ghi chú | Bảng mọi dependency trực tiếp (lý do, phiên bản, license, phương án đã cân nhắc): `docs/architecture-security-design.md` mục 10 | R7 |
| Repo public, `sample-data/` còn trong lịch sử git | **Chưa xong**: lệnh viết lại lịch sử cần chạy tay (xem dưới) | — |
| Kịch bản 5 chưa cho `UNKNOWN` | Giữ nguyên, chờ Mentor (mâu thuẫn với nhãn `SUCCESS` của case-001) | — |

## Chưa làm / cần đầu vào

- **Xoá `sample-data/` khỏi lịch sử git:** repo đang public và ba commit cũ vẫn chứa data mẫu của Mentor. Cần viết
  lại lịch sử (`git filter-branch --index-filter 'git rm -r -q --cached --ignore-unmatch sample-data' -- main`) rồi
  `git push --force-with-lease`; việc này chạy tay, không tự động.

- **Peer Evaluation (T7):** đã làm 2026-10-20 trên repo `hason0510/call-analysis-assistant`. Hai issue tìm được
  (WebRTC log của cuộc gọi khác, log có BOM / UTF-16) nhiều khả năng cũng có ở sản phẩm này; đã ghi vào Known
  Limitations, chưa sửa.
- **Từ Mentor:** chạy `held-out`; nhãn issue category / evidence; xác nhận scenario 5; category cho cuộc gọi bị huỷ.
- **AI tự đánh giá category / cờ chất lượng còn yếu** (khoảng 55% khớp rule khi không thấy baseline). Đã phân tích
  nguyên nhân; cách sửa cần đổi Guardrails G05 nên để lại, không làm vội cuối sprint.

Known Limitations đầy đủ: `docs/final-evaluation-report.md` mục 5.
