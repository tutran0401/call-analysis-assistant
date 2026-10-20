# Security Evaluation Report (Sprint 3 T6)

> Ngày chạy: 2026-10-14. Lệnh: `security-eval --data-root sample-data` (bộ case `benchmark/security/cases.yaml`),
> AI thật `gpt-4o-mini` ở lượt 1. Kết quả gốc: `reports/security-s3/summary.md` (không commit, `reports/` nằm trong
> `.gitignore`). Mọi giá trị nhạy cảm trong bộ case là **tổng hợp**, không phải dữ liệu thật.

## Kết luận

| Đích (PROJECT_SPEC.md mục 6.5) | Lượt 1: AI thật | Lượt 2: AI rò mọi giá trị (S07) | Lượt 3: AI rò giá trị vượt qua Guardrails (S07) | Target |
| --- | --- | --- | --- | --- |
| Security Leakage (input): giá trị nhạy cảm tới AI | **0** | 0 | 0 | 0 |
| Security Leakage (output): report / response | **0** | 0 | 0 | 0 |
| Security Leakage (output): application log, text + JSON | **0** | 0 | 0 | 0 |

**Đạt** cả 3 lượt, 7 case × 15 giá trị nhạy cảm đã chèn. Trong lúc làm, bộ case này đã **tìm ra và sửa một lỗ
thật** (xem "Lỗi tìm được").

### Phép đo độc lập với Sanitizer (thêm 2026-10-20, theo nhận xét của Mentor sau Sprint 2)

Mentor nhận xét: phép đo ở bảng trên dùng chính bộ dò của Sanitizer cộng vài giá trị mồi, nên "0 rò rỉ" chưa được
chứng minh độc lập. Một giá trị Sanitizer không nhận ra thì phép đo đó cũng không nhận ra.

Phép đo mới (`evaluation/RawValueOracle`) đi theo hướng ngược lại. Nó **đọc giá trị gốc từ chính file dữ liệu** của
case theo cấu trúc file, không theo mẫu văn bản: cột theo schema `#Hn` của end-call log, cây JSON của signaling
export và các ô JSON, dòng SDP (`a=ice-ufrag`, `a=ice-pwd`, `a=fingerprint`), khối `Cand[...]` của WebRTC log. Danh
sách field lấy từ `docs/sensitive-data-inventory.md`. Sau đó nó tìm **nguyên văn** từng giá trị ở ba đích. Lớp này
không gọi gì trong gói `sanitize`. Call-ID được xét riêng: được phép hiện cho người dùng (report phải hiện nó), không
được tới AI.

| Lần chạy | Giá trị gốc đọc được | AI request | Report / response | Log | Kết quả |
| --- | --- | --- | --- | --- | --- |
| `dev` (29 case, AI thật), trước khi sửa | 164 | **3** | **3** | 0 | Không đạt - tìm ra lỗ Call-ID (dưới) |
| `dev` (29 case, AI thật), sau khi sửa | 151 | 0 | 0 | 0 | **Đạt** |
| `security-eval` S01-S08, cả 3 lượt | 36 | 0 | 0 | 0 | **Đạt** |

Số giá trị giảm từ 164 xuống 151 vì Call-ID được tách khỏi danh sách chung (chỉ còn tính ở đích AI request).
Kết quả gốc: `reports/eval-dev-independent-leak/`, `reports/eval-dev-callid-masked/`,
`reports/security-20261020-034215/` (không commit, `reports/` nằm trong `.gitignore`).

## Cách đo

`security-eval` chạy từng case qua **đúng pipeline của Web UI** (`ChatAnalysisService`: File Validator → rule →
Input Sanitizer → AI → Guardrails → Output Sanitizer), rồi quét ba đích:

1. **AI request**: chuỗi đúng như gửi sang provider (bọc bằng `RecordingAiProvider`).
2. **Report / response**: toàn bộ JSON report, `renderedText`, `message` và `warnings` trả về Web UI.
3. **Application log**: mọi event log trong lúc chạy, sau đúng bộ lọc sẽ ghi ra console (`%san`, `%sanEx`) và file
   JSON (`SanitizingJsonLayout`).

Mỗi đích có hai số: số **giá trị gốc** đã chèn còn thấy được, và số giá trị nhạy cảm mà **detector vẫn nhận ra**
trong văn bản đã xử lý (bắt cả trường hợp chuỗi bị biến dạng nhẹ).

Ba lượt:

| Lượt | AI provider | Mục đích |
| --- | --- | --- |
| 1 | `gpt-4o-mini` thật | S01-S06, S08 trên đường chạy thật |
| 2 | `LeakyAiProvider`: AI giả cố tình chèn **mọi** giá trị nhạy cảm vào summary / analysis / suggestions | S07. Kết quả: Guardrails G05 từ chối hết (giá trị có chữ số = số liệu lạ) → fallback rule-based |
| 3 | `LeakyAiProvider` chỉ chèn giá trị **không có chữ số** (email) | S07 khi Guardrails **không** chặn: report AI được giao, chỉ còn Output Sanitizer, và nó vẫn che hết |

Lượt 3 được thêm vì lượt 2 chỉ chứng minh Guardrails chặn, chưa chứng minh Output Sanitizer chặn.

## Kết quả theo mã (mục 6.4)

| Mã | Case / test | Giá trị đã chèn | Lọt (3 lượt) | Kiểm thêm bằng unit test |
| --- | --- | --- | --- | --- |
| S01 - JWT trong log | `sec-s01-jwt` | JWT trong WebRTC log | 0 | `InputSanitizerTest.s01_*` |
| S02 - Authorization header | `sec-s02-authorization` | Bearer token, Basic credential | 0 | `InputSanitizerTest.s02_*` |
| S03 - API key trong exception | `sec-s03-api-key-exception` | `sk-proj-…`, `AKIA…` trong stack trace | 0 | `InputSanitizerTest.s03_*`, `ChatAnalysisServiceTest.s03_apiKeyInAnAiExceptionNeverReachesTheResponse` |
| S04 - Phone / email | `sec-s04-phone-email` | phone, email, `+84…` trong log **và số điện thoại trong câu hỏi** | 0 | `InputSanitizerTest.s04_*` |
| S05 - Client IP / device ID | `sec-s05-ip-device` | 2 IP client, device ID, user ID | 0 | `InputSanitizerTest.s05_*` |
| S06 - Nested JSON | `sec-s06-nested-json` | phone, email, access token trong JSON lồng 3 cấp | 0 | `InputSanitizerTest.s06_*` |
| S07 - AI response chứa lại giá trị nhạy cảm | lượt 2 và 3 | mọi giá trị ở trên | 0 | `OutputSanitizerTest` (S07), `SecurityEvaluationTest.s07_*` |
| S08 - Error message / application log | `sec-s08-error-message` | số điện thoại trong **tên file** (đi vào "Giới hạn dữ liệu", ghi chú xử lý, log) | 0 | `OutputSanitizerTest` (S08), `InputSanitizerTest.s08_*`, `SanitizingJsonLayoutTest` |

`SecurityEvaluationTest` chạy lại toàn bộ bộ case này ở mỗi lần `mvn test` (với AI giả), nên một thay đổi làm lọt
giá trị sẽ làm build đỏ.

## Lỗi tìm được

**Số điện thoại / IP dính liền với dấu `_` không được che.** Case S08 đính kèm file tên `0933444555_webrtc.log`.
Validator đưa tên file vào mục "Giới hạn dữ liệu" (tên gợi ý loại khác nội dung), và **số điện thoại lọt nguyên vào
cả AI request lẫn report**. Nguyên nhân: regex số điện thoại dùng biên `(?![\w])`, mà `_` thuộc `\w`. Regex IPv4 có
cùng lỗi ở lookbehind (`ip_113.161.45.67`). Đã sửa: `_` được coi là dấu phân cách (`[A-Za-z0-9]` thay cho `\w` ở
biên), kèm test hồi quy `InputSanitizerTest.s08_phoneAndIpGluedToUnderscoreAreStillMasked`.

Bài học: các unit test S01-S08 của Sprint 2 đều xanh vì chúng chỉ dùng giá trị có khoảng trắng hoặc `=` ở hai bên.
Lỗ này chỉ lộ ra khi đưa giá trị vào **đúng chỗ dữ liệu thật đi qua** (tên file → thông báo validator).

**Call-ID tới AI qua câu "Giới hạn dữ liệu" (tìm bằng phép đo độc lập, 2026-10-20).** Case `var-foreign-call-file`
đính kèm nhầm một file của cuộc gọi khác. Câu giới hạn "Bỏ qua file ...: thuộc Call-ID X, khác Call-ID Y" được gửi
nguyên văn sang AI, tức là AI nhận cả hai Call-ID. Thiết kế thì không bao giờ đưa Call-ID vào context như một
field, nên lỗ chỉ tồn tại trong văn bản tự do. Phép đo cũ không thấy vì Call-ID không nằm trong inventory nhạy cảm
của Sanitizer. Đã sửa: `AiContextBuilder` thay mọi Call-ID dạng UUID trong chuỗi gửi đi bằng `<CALL_ID>` /
`<CALL_ID_KHAC>` (không có chữ số nên không ảnh hưởng G05), ở cả hai context mode. Cách này che luôn Call-ID trong
mô tả evidence lấy từ message của server ("No sessions found for Call-ID: ..."). Test hồi quy:
`AiContextBuilderTest.callIdsInLimitationTextNeverReachTheAi`.

## Guardrails và File Validation (chạy lại trước khi nộp, mục 7.1)

Toàn bộ chạy trong `mvn test` (327 test backend, xanh ngày 2026-10-14, có Elasticsearch chạy):

| Mã | Nội dung | Test |
| --- | --- | --- |
| G01 | Evidence ID không tồn tại | `AiResponseGuardTest` |
| G02 | AI response sai format, **thiếu field**, field lạ | `AiResponseGuardTest` |
| G03 | verdict / issueCategory / confidence **ngoài enum** | `AiResponseGuardTest` |
| G04 | Verdict AI mâu thuẫn rule → `UNKNOWN` + cờ | `AiResponseGuardTest`, `ChatAnalysisServiceTest`, `AnalysisReportComposerTest` |
| G05 | Số liệu ngoài bộ chỉ số đã tính | `AiResponseGuardTest` |
| Oversized | AI response quá lớn; câu hỏi > 2000 ký tự → 400; file > 10 MB → 413 (Tomcat thật) và bị File Validator loại; timeline gửi AI bị cắt ở 80 dòng | `AiResponseGuardTest`, `ChatControllerIntegrationTest`, `UploadSizeLimitTest`, `AiContextBuilderTest` |
| F01 | Thiếu file của một bên | `ContentUploadValidatorTest` |
| F02 | Tên file không khớp nội dung | `ContentUploadValidatorTest`, case `sec-s08-error-message` |
| F03 | Các file thuộc Call-ID khác nhau | `ContentUploadValidatorTest` |
| F04 | File hỏng / vượt giới hạn kích thước | `ContentUploadValidatorTest`, `ChatControllerIntegrationTest`, `UploadSizeLimitTest` |

## Giới hạn

- **Sanitizer dựa trên mẫu đã biết** (regex + tên field). Một định dạng bí mật chưa có trong inventory sẽ lọt.
  Giảm thiểu bằng thiết kế: AI không nhận log gốc, không nhận câu hỏi gốc, và một context còn sót dù một giá trị
  thì không được gửi đi (`INPUT_NOT_SANITIZED`). Phép đo độc lập kiểm lại từ phía dữ liệu gốc, nhưng nó cũng chỉ
  biết các field có trong inventory: một loại bí mật mà cả inventory lẫn Sanitizer đều chưa biết thì cả hai cùng
  bỏ sót.
- **Số điện thoại chỉ nhận dạng VN** (`0…`, `84…`, `+84…`).
- Trong `security-eval`, cột "sự kiện log chứa giá trị gốc trước bộ lọc" bằng 0: pipeline không tự ghi giá trị nhạy
  cảm vào log (nó chỉ log field tóm tắt). Bộ lọc log vì thế được chứng minh bằng unit test (`OutputSanitizerTest` S08,
  `SanitizingJsonLayoutTest`) chứ không bằng lượt chạy này.
- Giá trị trong bộ case là tổng hợp; chưa chạy trên log thật chứa PII (data mẫu của Mentor đã xử lý PII).

## Chạy lại

```bash
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar security-eval --data-root sample-data
# -> reports/security-<thời điểm>/summary.md
mvn test -Dtest=SecurityEvaluationTest,InputSanitizerTest,OutputSanitizerTest,SanitizingJsonLayoutTest
```
