# Sprint 2 — AI Analysis, Web UI & Sanitizer

**Dự án:** Trợ lý AI phân tích cuộc gọi (OJT AI 20K)
**Thực tập sinh:** Trần Anh Tú
**Stack:** Java 17 · Spring Boot 3.3.5 · Elasticsearch 8.15 · React 18 + Vite + TypeScript
**Kiểm chứng lần cuối:** 2026-10-05 — `mvn test` 246/246 pass, `npm test` (web) 12/12 pass.

> **Đọc trước:** các metric AI ở mục "Kết quả đo" được đo bằng **AI giả lập** (`FakeAiProvider`, luôn đồng ý với
> rule), vì chưa có API key. Chúng chứng minh bộ đo chạy đúng, **không** nói gì về độ chính xác của
> `gpt-4o-mini`. Bộ adapter OpenAI đã viết và test bằng HTTP giả lập, chưa gọi mạng thật lần nào.

## Đã làm gì và làm như nào

Sprint 1 để lại một pipeline rule-based đáng tin (13/13 trên tập có nhãn). Sprint 2 **không thay nó mà đặt AI
lên trên nó**: rule vẫn tính chỉ số, evidence, verdict baseline; AI chỉ đề xuất lời văn và một verdict để đối
chiếu, và mọi thứ AI nói phải qua code kiểm trước khi người dùng thấy.

Một lượt hỏi trên web đi qua: **Request Parser** (intent, focus, Call-ID) → **File Validator** (loại file theo
nội dung, đúng Call-ID, giới hạn cỡ, báo thiếu file) → pipeline Sprint 1 → **Input Sanitizer** → **AI** →
**Guardrails** → **Output Sanitizer** → kiểm schema → report. Mọi nhánh lỗi của AI (chưa có key, timeout, trả lời
hỏng, bị Guardrails từ chối, context còn giá trị nhạy cảm) rơi về cùng một chỗ: report rule-based đánh dấu
`degraded`. Sơ đồ: `docs/architecture-diagram-v2.md`; prompt và Guardrails: `docs/prompt-strategy.md`.

- **Sanitizer (T7).** Một engine dùng chung cho input lẫn output: regex theo `docs/sensitive-data-inventory.md`
  (JWT, Authorization, API key, ICE ufrag/pwd, DTLS fingerprint, SDP, TURN URL, phone, email, IP, ID người
  dùng/thiết bị/session) cộng xử lý theo **tên field** trong JSON lồng nhau. Chính sách mỗi loại (Drop / Mask /
  Pseudonymize / Minimize) nằm ở một chỗ duy nhất, `SensitiveKind`. Pseudonym là HMAC nên cùng ID cho cùng giá
  trị giả (vẫn correlate được). Engine **idempotent**: chạy lại trên kết quả đã sạch không đổi gì, nên Output
  Sanitizer chạy được lên dữ liệu đã qua Input Sanitizer. Có cả bộ lọc logback cho mọi log message và stack trace.
- **Guardrails (T6).** G01–G05 bằng code. Điểm đáng nói là G05: so mọi con số trong lời văn AI với tập số có
  trong chỉ số/evidence, có chuẩn hoá định dạng (`6.20` = `6.2`, `2,9` = `2.9`) và dung sai cho số đếm một chữ số.
- **AI Provider (T5).** Một cổng `AiProvider` với ba bản: `OpenAiProvider` (gpt-4o-mini, structured output
  strict, temperature 0), `FakeAiProvider` (xác định, có 9 chế độ lỗi để test Guardrails và Fallback), `NoAiProvider`
  (mặc định). Chọn bằng `call-analysis.ai.provider`; chọn `openai` mà thiếu key thì tự rơi về `none`, không lỗi
  lúc khởi động. Mọi lời gọi bọc thêm hạn chót cứng 20 s độc lập với timeout của provider.
- **Web UI + Chat API (T1, T2).** `POST /api/chat` (multipart), `GET /api/health`; UI React một trang, báo trước
  nếu AI/Elasticsearch chưa sẵn sàng, banner khi degraded, render report **từ JSON** (test xác nhận không chèn
  HTML từ nội dung report).
- **Evaluation Runner (T9) + Benchmark `dev` (T10).** `evaluate --cases <yaml> --data-root <dir>` chạy bộ case
  qua đúng đường đi của một request thật, đo các metric ở mục 6.5, ghi `summary.md` + `results.json` +
  `samples/`. Nhận bộ case từ file (đúng format mục 6.3) nên Mentor chạy được tập `held-out`.

## Kết quả đo (benchmark `dev`, AI giả lập)

29 case (20 cuộc gọi thật + 7 biến thể file + 2 case ngoài phạm vi), 461 lần chạy, repeat 5. Toàn văn:
`docs/evaluation-run-dev-fake-ai.md`.

| Metric | Giá trị | Target | Ghi chú |
| --- | --- | --- | --- |
| Verdict Accuracy | 96.5% (416/431) | ≥ 85% | 15 lần sai đều là `var-single-file` (xem câu hỏi 1) |
| Verdict Accuracy — rule | 96.3% (26/27) | — | cùng ca duy nhất |
| Metric Correctness | 100% | 100% | |
| Security Leakage input / output / log | 0 / 0 / 0 | 0 | 5 giá trị nhạy cảm đã chèn làm probe |
| Issue Category Accuracy | 100% | ≥ 70% | **vô nghĩa về độ chính xác**: nhãn category là kết quả của chính rule (xem "Nguồn nhãn") |
| Consistency / Template / Intent / Pipeline | 100% | ≥ 95 / 100 / 90 / 95% | Consistency chưa phản ánh AI thật; Intent đo trên câu hỏi do chính tôi viết |

**Các con số đáng tin:** Metric Correctness, Template Compliance, Pipeline Success, và 3 con số Leakage (đo ở
cửa ra - request thật gửi AI, response thật trả người dùng, log sau bộ lọc - không dựa vào việc sanitizer tự báo).
**Các con số chưa đáng tin:** mọi thứ phụ thuộc hành vi của AI (Consistency, Unsupported Claim, Issue Category
của AI, tỉ lệ degraded), và Intent Accuracy (bộ câu hỏi tự viết, parser dùng từ khoá - dễ overfit).

## Điều tìm ra khi chạy (và đã sửa)

Bốn lỗi, cả bốn đều lộ ra khi **chạy thật** chứ không phải khi test đơn vị đã xanh:

1. **Chỉ đính kèm WebRTC log → luôn `UNKNOWN`** (benchmark lộ ra: rule baseline tụt từ 13/13 xuống 20/27).
   Log WebRTC không chứa Call-ID; Sprint 1 không gặp vì tên thư mục chính là Call-ID. Qua web, không có Call-ID
   thì không tra được signaling. Sửa: thêm ô Call-ID tuỳ chọn trên UI / tham số `callId` của API / `call_id`
   của case benchmark; Call-ID viết trong câu hỏi vẫn được ưu tiên. Test hồi quy ở `ChatAnalysisServiceTest`.
2. **Ghi chú xử lý để lộ đường dẫn thư mục tạm của máy chủ** (`C:\Users\<tên user>\AppData\...`) ra UI — thấy
   khi gọi API bằng curl. Sửa: thay bằng `<upload>`; có test.
3. **Bộ lọc log che cả `127.0.0.1` của chính Tomcat.** Loopback/0.0.0.0 không phải IP client nên không che nữa.
4. **Logback in cả trạng thái nội bộ ra CLI** vì dùng thuộc tính `converterClass` đã lỗi thời. Sửa sang `class`.

## Nguồn nhãn — điều cần nói thật

Tập `dev` hiện có: verdict của `success/` và `fail/` là nhãn Mentor (theo tên thư mục); verdict của `for_test/`
và **mọi issue category** là kết quả rule đã được kiểm tay với log thô (`docs/sample-run-report.md`); **Evidence
ID kỳ vọng bỏ trống** vì chưa có ground truth. Mỗi case ghi `label_source` và `summary.md` liệt kê số case theo
từng nguồn. Hệ quả: Issue Category Accuracy của rule trên tập này là 100% theo cách xây — chỉ dùng để so AI với
rule, không phải độ chính xác độc lập.

## Câu hỏi cần xác minh với Mentor

1. **"Chỉ đính kèm một file" có phải `UNKNOWN` không?** Spec mục 10 scenario 5 nói `UNKNOWN`. Hiện tại, nếu
   signaling vẫn lấy được từ Elasticsearch, rule vẫn kết luận được từ một file (độ tin cậy thấp hơn, kèm "Thiếu
   ...") nên trả `SUCCESS`. Case `var-single-file` ghi lại đúng sự lệch này. Cần quyết: đổi rule, hay hiểu scenario
   5 là "không có signaling và chỉ một file"?
2. **Ground truth issue category và Evidence ID** cho tập `dev` (và định dạng Evidence ID kỳ vọng) — hiện đang
   dùng nhãn tạm như trên.
3. **Khi AI mâu thuẫn rule thì `UNKNOWN` hay giữ verdict rule + gắn cờ?** Đang chọn `UNKNOWN`; phân tích đánh đổi
   ở `docs/prompt-strategy.md`. Cần số đo từ AI thật để quyết.
4. **Người dùng chỉ đính kèm WebRTC log có phải tự nhập Call-ID không?** Hiện là có (ô tuỳ chọn). Hay Mentor mong
   đợi cách khác lấy Call-ID?
5. **OpenAI `gpt-4o-mini` có được duyệt không?** (`docs/ai-provider-proposal.md`; Sprint 1 chưa được duyệt.)
6. Hai câu còn treo từ Sprint 1: category cho cuộc gọi bị `CANCEL`, và ngữ nghĩa `requestId` khi retransmit
   (`docs/sprint1-report.md`).

## Chưa làm / bỏ qua chờ tài liệu và đầu vào từ Mentor

| Việc | Lý do |
| --- | --- |
| Chạy benchmark với `gpt-4o-mini` thật; bảng AI vs Rule thật; đo Consistency / Unsupported Claim thật | Chưa có API key. Adapter + test (HTTP giả lập) đã xong; chỉ cần đặt `OPENAI_API_KEY` và chạy `evaluate` |
| Tập `held-out`, Verdict Accuracy ≥ 85% do Mentor chạy | Việc của Mentor; Runner đã nhận file case |
| Ground truth issue category / Evidence ID | Chưa có tài liệu từ Mentor (câu hỏi 2) |
| Request Parser bằng AI | Cần provider thật; hiện chỉ có bản từ khoá |
| T11 So sánh cách dựng context (Nếu kịp) | Ưu tiên Core; cần AI thật để so token/latency/accuracy |
| Chạy pipeline end-to-end với Elasticsearch thật qua web | Benchmark và demo kiểm bằng nguồn signaling từ cây thư mục (`call-analysis.signaling-root`); đường ES đã có test cũ ở Sprint 1 nhưng chưa được chạy lại trong phiên này |
| Peer review PR bởi bạn còn lại | Ngoài tầm của một phiên làm việc đơn lẻ |

## Giới hạn đã biết

- **Chưa đo được chất lượng AI thật** (xem đầu tài liệu). Mọi quyết định thiết kế phụ thuộc hành vi AI
  (chính sách G04, dung sai G05, prompt) là giả thuyết cho tới khi chạy `gpt-4o-mini`.
- **Sanitizer dựa trên mẫu đã biết** (regex + tên field). Một định dạng bí mật chưa có trong inventory sẽ lọt.
  Giảm thiểu bằng thiết kế: AI không nhận log gốc, không nhận câu hỏi gốc, và còn sót dù một giá trị thì không gửi.
  Số điện thoại chỉ nhận dạng VN (`0…`, `84…`, `+84…`).
- **Không có xác thực / giới hạn tần suất** trên API (đúng phạm vi mục 9); server bind `127.0.0.1`.
- **Request Parser dựa trên từ khoá**: câu hỏi diễn đạt lạ có thể bị phân loại nhầm.
- **`FakeAiProvider` luôn đồng ý với rule** nên các test orchestration không kiểm được chất lượng lời văn AI.
- **Báo cáo trực quan HTML** như Sprint 1 chưa làm cho Sprint 2.

## Chạy lại

```bash
mvn test                                   # 246 test backend (cần sample-data/)
cd web && npm install && npm test          # 12 test UI
# Web UI (AI giả lập, signaling từ cây data mẫu, không cần Elasticsearch):
CALL_ANALYSIS_AI_PROVIDER=fake java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar \
    --spring.profiles.active=web --call-analysis.signaling-root=sample-data
cd web && npm run dev                      # http://localhost:5173
# Benchmark:
CALL_ANALYSIS_AI_PROVIDER=fake java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar evaluate \
    --cases benchmark/dev/cases.yaml --data-root sample-data --repeat 5
```
