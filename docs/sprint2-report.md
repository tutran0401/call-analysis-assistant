# Sprint 2 — AI Analysis, Web UI & Sanitizer

**Dự án:** Trợ lý AI phân tích cuộc gọi (OJT AI 20K)
**Thực tập sinh:** Trần Anh Tú
**Stack:** Java 17 · Spring Boot 3.3.5 · Elasticsearch 8.15 · React 18 + Vite + TypeScript
**Kiểm chứng lần cuối:** 2026-10-10 — `mvn test` 269/269 pass (gồm 2 test trên Elasticsearch thật), `npm test` (web)
12/12 pass; benchmark `dev` chạy với **OpenAI `gpt-4o-mini` thật** (461 request).

> **Đọc trước:** benchmark đã chạy với AI thật, nhưng số liệu cần đọc kèm hai giới hạn ở mục "Điều cần đọc kỹ trước
> khi diễn giải": nhãn issue category là kết quả rule đã kiểm tay (chưa phải ground truth của Mentor), và AI được thấy
> kết luận của rule trong input nên việc AI đồng ý với rule **không phải bằng chứng độc lập**. Verdict Accuracy ≥ 85%
> **trên tập `held-out` vẫn là việc của Mentor** - số 96.5% dưới đây là trên tập `dev`, chưa phải điều kiện đạt.

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
  trong chỉ số/evidence, có chuẩn hoá định dạng (`6.20` = `6.2`, `2,9` = `2.9`), dung sai cho số đếm một chữ số, và
  chấp nhận **làm tròn trung thực** (4.4 cho chỉ số 4.40337) nhưng không chấp nhận số lệch hay số tự tính.
- **Request Parser (T4).** Rule từ khoá chạy trước và là kết quả cuối với mọi câu hỏi trong phạm vi (xác định, không
  tốn lượt gọi AI); AI chỉ được hỏi khi rule định từ chối, và chỉ có thể *cứu* câu đó thành trong phạm vi. Thử với OpenAI
  thật: câu diễn đạt lạ được cứu đúng, câu về nấu phở / chọn quà vẫn bị từ chối.
- **AI Provider (T5).** Một cổng `AiProvider` với ba bản: `OpenAiProvider` (gpt-4o-mini, structured output
  strict, temperature 0), `FakeAiProvider` (xác định, có 9 chế độ lỗi để test Guardrails và Fallback), `NoAiProvider`
  (mặc định). Chọn bằng `call-analysis.ai.provider`; chọn `openai` mà thiếu key thì tự rơi về `none`, không lỗi
  lúc khởi động. Mọi lời gọi bọc thêm hạn chót cứng 30 s độc lập với timeout của provider. Retry có kiểm soát: thử
  lại **một** lần, chỉ với lỗi tạm thời (timeout, mất kết nối, 408/429/5xx); không bao giờ retry 4xx hay phản hồi hỏng.
- **Web UI + Chat API (T1, T2).** `POST /api/chat` (multipart), `GET /api/health`; UI React một trang, báo trước
  nếu AI/Elasticsearch chưa sẵn sàng, banner khi degraded, render report **từ JSON** (test xác nhận không chèn
  HTML từ nội dung report).
- **Evaluation Runner (T9) + Benchmark `dev` (T10).** `evaluate --cases <yaml> --data-root <dir>` chạy bộ case
  qua đúng đường đi của một request thật, đo các metric ở mục 6.5, ghi `summary.md` + `results.json` +
  `samples/`. Nhận bộ case từ file (đúng format mục 6.3) nên Mentor chạy được tập `held-out`.

## Kết quả đo (benchmark `dev`, `gpt-4o-mini` thật)

29 case (20 cuộc gọi thật + 7 biến thể file + 2 case ngoài phạm vi), 461 request (câu hỏi 1-3 của mỗi case chạy 5
lần). Toàn văn: `docs/evaluation-run-dev-gpt-4o-mini.md`.

| Metric | Giá trị | Target | Ghi chú |
| --- | --- | --- | --- |
| Verdict Accuracy | 96.5% (416/431) | ≥ 85% (Bắt buộc, trên `held-out`) | 15 lần sai đều là `var-single-file` (xem câu hỏi 1) |
| Verdict Accuracy - rule | 96.3% (26/27) | — | cùng case duy nhất |
| Metric Correctness | 100% (431/431) | 100% | |
| Security Leakage input / output / log | 0 / 0 / 0 | 0 | 5 giá trị nhạy cảm tổng hợp đã chèn làm probe, đo ở cửa ra |
| Unsupported Claim Rate (report giao cho người dùng) | **0%** (0/422) | 0% | kiểm lại độc lập trên report cuối |
| Phản hồi AI thô bị Guardrails bắt (G01/G05) | 2.1% (9/431) | — | cả 9 lần là `case-015`: AI tự trừ hai mốc giờ ra "32/33 giây"; đã fallback, không tới người dùng |
| Issue Category Accuracy | 100% (227/227) | ≥ 70% | **không độc lập**: nhãn là kết quả của chính rule |
| Consistency (5 lần × 3 cách hỏi) | 100% (27/27 case) | ≥ 95% | verdict + category do rule quyết định nên ít dao động |
| Template Compliance / Pipeline Success | 100% / 100% | 100% / ≥ 95% | |
| Intent Accuracy | 100% | ≥ 90% | bộ câu hỏi do chính tôi viết |
| Tỉ lệ fallback (degraded) | 2.1% | — | cùng 9 lần ở trên |
| Latency P50 / P95 | 2.5 s / 3.1 s | — | máy cá nhân, gồm cả pipeline và 1 lần gọi OpenAI |
| Token trung bình / lần gọi phân tích | 3437 | — | |

**Các con số đáng tin:** Metric Correctness, Template Compliance, Pipeline Success, Unsupported Claim (đã kiểm lại
độc lập), 3 con số Leakage (đo ở cửa ra: request thật gửi AI, response thật trả người dùng, log sau bộ lọc), và
latency/token.

### Điều cần đọc kỹ trước khi diễn giải

1. **AI được thấy kết luận của rule** (`ruleBaseline` nằm trong input theo đúng thiết kế mục 3.2), nên việc AI cho
   cùng verdict với rule trong 461/461 request (không lần nào xảy ra mâu thuẫn G04) **không** chứng minh AI tự suy luận
   đúng; nó chủ yếu chứng minh AI không phá kết luận của rule. Bảng "AI vs Rule vs Ground truth" vì vậy gần như trùng
   cột Rule. Muốn đo AI độc lập cần một biến thể không đưa `ruleBaseline` vào input - **chưa làm**, đề xuất cho Sprint 3.
   Hệ quả: chính sách G04 (mâu thuẫn → `UNKNOWN`) **chưa từng được kích hoạt** trên AI thật, nên chưa có số liệu để quyết.
2. **Nhãn issue category là kết quả của rule** (xem "Nguồn nhãn"), nên Issue Category Accuracy 100% chỉ có nghĩa là AI
   không đổi category của rule.
3. **Cỡ mẫu nhỏ**: 27 cuộc gọi, 3 cách hỏi/cuộc, câu hỏi do một người viết. Chênh lệch dưới vài điểm phần trăm nằm trong nhiễu.
4. **Consistency 100% phần lớn do thiết kế** (rule quyết định verdict/category), không phải do AI ổn định tuyệt đối.
   Phần AI viết (lời văn) có thể khác nhau giữa các lần chạy; metric này không đo điều đó.

### So sánh cách dựng context (T11)

`structured` vs `semi` trên cùng điều kiện (174 request mỗi bên): accuracy và consistency giống hệt nhau, **`semi` dùng
ít hơn 27% token (2495 vs 3436)**, latency như nhau. Chưa đủ dữ liệu để nói `semi` ít bịa số hơn. Chi tiết và giới hạn
ở `docs/context-mode-comparison.md`.

### Độ bền khi mạng hỏng (quan sát được)

Một lần chạy benchmark bị mất DNS của máy trong vài phút (56 request liền nhau gặp `UnknownHostException`). Hệ thống
xử lý đúng thiết kế: cả 56 request vẫn trả report hợp lệ từ rule, đánh dấu `degraded`, đúng verdict, Pipeline Success 100%.
Lần chạy đó bị loại khỏi số liệu ở trên vì không đại diện cho AI; số liệu trên là lần chạy lại sạch. Retry 1 lần không
cứu được sự cố kéo dài vài phút (đúng dự kiến - đó là việc của fallback).

## Điều tìm ra khi chạy (và đã sửa)

Sáu lỗi, tất cả đều lộ ra khi **chạy thật** chứ không phải khi test đơn vị đã xanh:

1. **Chỉ đính kèm WebRTC log → luôn `UNKNOWN`** (benchmark lộ ra: rule baseline tụt từ 13/13 xuống 20/27).
   Log WebRTC không chứa Call-ID; Sprint 1 không gặp vì tên thư mục chính là Call-ID. Qua web, không có Call-ID
   thì không tra được signaling. Sửa: thêm ô Call-ID tuỳ chọn trên UI / tham số `callId` của API / `call_id`
   của case benchmark; Call-ID viết trong câu hỏi vẫn được ưu tiên. Test hồi quy ở `ChatAnalysisServiceTest`.
2. **Ghi chú xử lý để lộ đường dẫn thư mục tạm của máy chủ** (`C:\Users\<tên user>\AppData\...`) ra UI — thấy
   khi gọi API bằng curl. Sửa: thay bằng `<upload>`; có test.
3. **Bộ lọc log che cả `127.0.0.1` của chính Tomcat.** Loopback/0.0.0.0 không phải IP client nên không che nữa.
4. **Logback in cả trạng thái nội bộ ra CLI** vì dùng thuộc tính `converterClass` đã lỗi thời. Sửa sang `class`.
5. **Guardrails G05 loại oan ~40% report đúng khi gọi OpenAI thật.** `gpt-4o-mini` thường viết "MOS 4.4" khi chỉ số là
   `4.40337`; bản G05 đầu khớp chuỗi tuyệt đối nên coi là số bịa. Test với `FakeAiProvider` không bao giờ lộ ra điều này.
   Sửa: chấp nhận làm tròn trung thực (ít chữ số hơn nguồn, lệch không quá nửa đơn vị ở chữ số cuối), vẫn bắt số lệch và
   số tự tính; prompt nêu rõ quy tắc. Sau sửa 12/12 lần chạy liên tiếp cùng một case đều được chấp nhận.
6. **OpenAI thỉnh thoảng treo quá hạn (~1/10 lần) rồi lần sau chạy bình thường**, và lỗi timeout khi *đọc body* bị
   Spring bọc thành loại exception khác nên không được phân loại là timeout. Sửa: phân loại đúng, thêm retry có kiểm soát
   (1 lần, chỉ lỗi tạm thời), có test mô phỏng đúng tình huống body treo.

## Nguồn nhãn — điều cần nói thật

Tập `dev` hiện có: verdict của `success/` và `fail/` là nhãn Mentor (theo tên thư mục); verdict của `for_test/`
và **mọi issue category** là kết quả rule đã được kiểm tay với log thô (`docs/sample-run-report.md`); **Evidence
ID kỳ vọng bỏ trống** vì chưa có ground truth. Mỗi case ghi `label_source` và `summary.md` liệt kê số case theo
từng nguồn. Hệ quả: Issue Category Accuracy của rule trên tập này là 100% theo cách xây — chỉ dùng để so AI với
rule, không phải độ chính xác độc lập.

## Câu hỏi cần xác minh với Mentor

1. **"Chỉ đính kèm một file" có phải `UNKNOWN` không?** Spec mục 10 scenario 5 nói `UNKNOWN`. Hiện tại, nếu
   signaling vẫn lấy được từ Elasticsearch, rule vẫn kết luận được từ một file (độ tin cậy thấp hơn, kèm "Thiếu
   ...") nên trả `SUCCESS`. Case `var-single-file` ghi lại đúng sự lệch này. **Tôi chủ động không đổi rule**, vì dữ liệu
   của chính Mentor mâu thuẫn với scenario 5: cuộc gọi `success/5E0800AE` (nhãn SUCCESS) cũng chỉ có đúng một file
   client (`caller_webrtc.log`). Một quy tắc "một file → `UNKNOWN`" sẽ làm sai nhãn đó. Cần Mentor xác nhận scenario 5
   nghĩa là gì (ví dụ "không có signaling và chỉ một file", hay "thiếu cả hai end-call log").
2. **Ground truth issue category và Evidence ID** cho tập `dev` (và định dạng Evidence ID kỳ vọng) — hiện đang
   dùng nhãn tạm như trên.
3. **Khi AI mâu thuẫn rule thì `UNKNOWN` hay giữ verdict rule + gắn cờ?** Đang chọn `UNKNOWN`; phân tích đánh đổi
   ở `docs/prompt-strategy.md`. Trên AI thật chưa từng xảy ra mâu thuẫn (461/461 đồng ý, xem "Điều cần đọc kỹ" mục 1)
   nên chưa có số đo để quyết; cần biến thể đo không đưa `ruleBaseline` cho AI.
4. **Người dùng chỉ đính kèm WebRTC log có phải tự nhập Call-ID không?** Hiện là có (ô tuỳ chọn). Hay Mentor mong
   đợi cách khác lấy Call-ID?
5. **OpenAI `gpt-4o-mini` có được duyệt không?** (`docs/ai-provider-proposal.md`; Sprint 1 chưa được duyệt.)
6. Hai câu còn treo từ Sprint 1: category cho cuộc gọi bị `CANCEL`, và ngữ nghĩa `requestId` khi retransmit
   (`docs/sprint1-report.md`).

## Chưa làm / bỏ qua chờ tài liệu và đầu vào từ Mentor

| Việc | Lý do |
| --- | --- |
| Tập `held-out`, Verdict Accuracy ≥ 85% do Mentor chạy | Việc của Mentor; Runner nhận file case (`--cases`) |
| Ground truth issue category / Evidence ID | Chưa có tài liệu từ Mentor (câu hỏi 2) |
| Mentor duyệt OpenAI `gpt-4o-mini` | Chưa có phản hồi (câu hỏi 5) |
| Đo AI độc lập với rule (không đưa `ruleBaseline` vào input) | Chưa làm; cần để quyết chính sách G04; gợi ý Sprint 3 T1 |
| Chấm chất lượng lời văn AI (analysis, suggestions) | Các metric 6.5 không đo; cần rubric / người chấm |
| Demo 6 scenario trên trình duyệt thật (quay màn hình) | Tiện ích Chrome không kết nối được trong phiên làm việc; đã kiểm bằng test UI (12), test API qua HTTP và gọi qua Vite proxy với OpenAI + Elasticsearch thật. Cần bạn mở `http://localhost:5173` xem bằng mắt |
| Peer review PR bởi bạn còn lại | Ngoài tầm của một phiên làm việc đơn lẻ |

## Giới hạn đã biết

- **Chưa đo được AI độc lập với rule** (AI thấy kết luận của rule) và chưa chấm chất lượng lời văn; chính sách G04
  chưa từng được kích hoạt trên AI thật.
- **API key đã bị lộ trong khung chat** khi được cấp cho phiên làm việc này (không nằm trong repo - đã kiểm bằng
  `git grep`); nên thu hồi và tạo key mới.
- **Sanitizer dựa trên mẫu đã biết** (regex + tên field). Một định dạng bí mật chưa có trong inventory sẽ lọt.
  Giảm thiểu bằng thiết kế: AI không nhận log gốc, không nhận câu hỏi gốc, và còn sót dù một giá trị thì không gửi.
  Số điện thoại chỉ nhận dạng VN (`0…`, `84…`, `+84…`).
- **Không có xác thực / giới hạn tần suất** trên API (đúng phạm vi mục 9); server bind `127.0.0.1`.
- **Request Parser dựa trên từ khoá**: câu hỏi diễn đạt lạ có thể bị phân loại nhầm.
- **`FakeAiProvider` luôn đồng ý với rule** nên các test orchestration không kiểm được chất lượng lời văn AI; đó là lý
  do bài học 5 chỉ lộ ra khi gọi OpenAI thật.
- **Báo cáo trực quan HTML** như Sprint 1 chưa làm cho Sprint 2.

## Chạy lại

```bash
mvn test                                   # 269 test backend (cần sample-data/; test ES tự bỏ qua nếu ES không chạy)
cd web && npm install && npm test          # 12 test UI
# Web UI với OpenAI thật (đặt key trong biến môi trường, không ghi vào file):
export OPENAI_API_KEY=...; export CALL_ANALYSIS_AI_PROVIDER=openai
docker compose up -d elasticsearch         # rồi: java -jar ... import success fail for_test (lần đầu)
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar --spring.profiles.active=web
cd web && npm run dev                      # http://localhost:5173
# Benchmark (signaling lấy từ cây data mẫu, không cần ES):
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar evaluate \
    --cases benchmark/dev/cases.yaml --data-root sample-data --repeat 5
```
