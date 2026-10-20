# Runbook - AI Call Analysis Assistant

Tài liệu bàn giao số 2 (PROJECT_SPEC.md mục 7.4). Mục tiêu: một người chưa từng thấy repo dựng và chạy lại được
toàn bộ sản phẩm **chỉ bằng tài liệu này**: Elasticsearch local, import data, Web UI, test, benchmark, kiểm tra
bảo mật, batch và Kibana.

Mọi lệnh chạy từ **thư mục gốc repo**. Ví dụ dùng cú pháp bash (Git Bash trên Windows); chỗ nào PowerShell khác
thì có ghi chú.

---

## 1. Yêu cầu

| Thành phần | Phiên bản đã kiểm | Ghi chú |
| --- | --- | --- |
| JDK | **17 - 21** (đã kiểm 17 và 21) | `java -version`. JDK mới hơn (ví dụ 26) build được nhưng test Mockito lỗi "cannot mock this class" (Mockito / Byte Buddy của Spring Boot 3.3 chưa hỗ trợ) - dùng JDK 21 |
| Maven | 3.9+ | `mvn -v` |
| Node.js + npm | Node 18+ | chỉ cần cho Web UI |
| Docker Desktop | 4.x, engine 28 | Elasticsearch + Kibana; cần khoảng 4-6 GB RAM trống |
| curl | bất kỳ | kiểm tra nhanh API |

### Dữ liệu mẫu

**Data mẫu không nằm trong git** (`sample-data/` bị ignore vì có thể chứa dữ liệu nhạy cảm). Xin bộ data từ Mentor
(gói zip gửi riêng, không qua repo), giải nén ở thư mục gốc repo sao cho có đúng cấu trúc:

```
sample-data/
  success/<Call-ID>/{signaling.json, caller_endcall.log, callee_endcall.log, caller_webrtc.log, callee_webrtc.log}
  fail/<Call-ID>/...
  for_test/<Call-ID>/...
  new_dataset/<Call-ID>/...      (tuỳ chọn)
```

Test backend (`mvn test`) và benchmark đọc thẳng các thư mục này. Kiểm nhanh đã đặt đúng chỗ:

```bash
ls sample-data/success/DE7DD314-F432-45CB-BCB4-AE9103CC0919/signaling.json
```

Thiếu data thì `mvn test` **không fail**: các test cần data được bỏ qua (Skipped) kèm lý do "Thiếu dữ liệu mẫu ...
xem docs/runbook.md mục Dữ liệu mẫu"; các test còn lại (dùng event dựng sẵn trong code) vẫn chạy. Bản clone sạch
đã kiểm: 304 test chạy, 81 bỏ qua, 0 lỗi. Có đủ data: 330 test, 0 bỏ qua.

## 2. Dựng Elasticsearch + Kibana

```bash
docker compose up -d elasticsearch kibana
curl -s http://localhost:9200/_cluster/health        # chờ tới khi có "status"
```

- ES ở `127.0.0.1:9200`, Kibana ở `http://localhost:5601`. Cả hai chỉ bind localhost; ES tắt security (chỉ dùng
  local, đúng mục 1.3).
- Dữ liệu ES nằm trong volume `es-data`, nên khởi động lại máy **không mất** data đã import.
- Sau khi khởi động lại máy, **Docker Desktop phải được mở lại bằng tay** trước khi chạy `docker compose`.

## 3. Build và import signaling

```bash
mvn clean package -DskipTests        # 1. build jar trước - ES lúc này còn rỗng
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar import sample-data/success sample-data/fail sample-data/for_test sample-data/new_dataset
curl -s "http://localhost:9200/call-signaling-events/_count"   # 2. import xong mới có dữ liệu
mvn test                             # 3. chạy test sau khi import (test tích hợp ES cần dữ liệu đã import)
```

Thứ tự này có lý do: test tích hợp Elasticsearch cần signaling đã import. Nếu chạy `mvn clean package` (kèm test)
khi ES đang bật mà chưa import, test đó tự **bỏ qua** kèm lý do, nhưng bản code trước 2026-10-20 thì đỏ và không ra
jar. Index tạo từ bản code trước 2026-10-20 cần import lại một lần để có trường `exportTruncated` (bản export bị cắt
bớt hiện trong "Giới hạn dữ liệu").

`import` nhận một hoặc nhiều thư mục gốc (đường dẫn tính từ thư mục đang đứng), đọc mọi `signaling.json` (cả hai dạng export của Mentor) và index
vào `call-signaling-events`. Chạy lại an toàn: ID document cố định nên không nhân đôi dữ liệu.

**Sau thay đổi sanitizer 2026-10-20:** nạp lại toàn bộ các thư mục signaling đã từng import. Lệnh `import` giữ
document ID cũ và ghi đè mỗi document bằng bản đã mask/pseudonymize, kể cả `rawEvent`. Nếu index còn dữ liệu từ
những export không còn trên đĩa, các document đó không được ghi đè; cần kiểm tra chúng riêng trước khi dùng index
cho đánh giá bảo mật. `appUserId` và `csid` được giả danh ổn định trong một lần chạy ứng dụng. Nếu cần giữ cùng
định danh qua các lần import, đặt `CALL_ANALYSIS_PSEUDONYM_SALT` cố định như mục 4.

## 4. Cấu hình AI

```bash
cp .env.example .env      # rồi điền OPENAI_API_KEY và CALL_ANALYSIS_AI_PROVIDER=openai
```

`.env` nằm trong `.gitignore`; ứng dụng tự đọc nó khi chạy từ thư mục gốc. **Không bao giờ commit key.**

| Biến | Giá trị | Ý nghĩa |
| --- | --- | --- |
| `CALL_ANALYSIS_AI_PROVIDER` | `none` (mặc định) / `fake` / `openai` | `none` = chỉ rule; `fake` = AI giả offline |
| `OPENAI_API_KEY` | key | thiếu key thì `openai` tự rơi về `none` |
| `CALL_ANALYSIS_PSEUDONYM_SALT` | chuỗi bất kỳ | nên đặt cố định khi chạy `evaluate` để pseudonym ổn định |

Tham số khác (`src/main/resources/application.yml`):

| Khoá | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `call-analysis.ai.timeout-ms` | 30000 | hạn chót cứng cho một lượt gọi AI (gồm retry) |
| `call-analysis.ai.openai.timeout-ms` / `max-attempts` | 12000 / 2 | timeout mỗi lần gọi, số lần thử |
| `call-analysis.ai.context-mode` | `structured` | `semi` = evidence dạng văn bản, ít token hơn |
| `call-analysis.ai.include-rule-baseline` | `true` | `false` = AI không thấy kết luận rule (đo AI độc lập) |
| `elasticsearch.connect-timeout-ms` / `socket-timeout-ms` | 2000 / 5000 | timeout mọi lời gọi ES |
| `call-analysis.results.archive` | `none` (CLI), `es` (profile web) | ghi kết quả vào `call-analysis-results` |

## 5. Chạy ứng dụng (Web UI)

```bash
# Terminal 1 - backend (Chat API ở 127.0.0.1:8080)
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar --spring.profiles.active=web
#   thêm profile jsonlog để ghi structured log: --spring.profiles.active=web,jsonlog  -> logs/app.jsonl

# Terminal 2 - frontend
cd web && npm install && npm run dev       # mở http://localhost:5173
```

Cổng 8080 bận ("Port 8080 was already in use"): chạy backend với `--server.port=8090` và báo cho proxy của Vite biết:
`BACKEND_URL=http://127.0.0.1:8090 npm run dev` (PowerShell: `$env:BACKEND_URL="http://127.0.0.1:8090"; npm run dev`).

Trên UI: gõ câu hỏi tiếng Việt, kéo thả `*_endcall.log` / `*_webrtc.log`, bấm **Phân tích**. Banner đầu trang báo
trước nếu AI chưa cấu hình hoặc ES chưa chạy.

Kiểm tra nhanh:

```bash
curl -s http://127.0.0.1:8080/api/health
# {"status":"UP","ai":{"model":"gpt-4o-mini","configured":true},"elasticsearch":"UP"}
```

Gọi API bằng curl: lưu câu hỏi vào file UTF-8 (xem mục 10, "mất dấu tiếng Việt"):

```bash
printf 'Phân tích cuộc gọi này giúp mình' > q.txt
curl -s -F "message=<q.txt" -F "files=@sample-data/success/<Call-ID>/caller_endcall.log" http://127.0.0.1:8080/api/chat
```

Mỗi phản hồi có `requestId`, và header `X-Request-Id` mang cùng giá trị. Mã này xuất hiện trong mọi dòng log của
request, nên từ một lỗi người dùng báo lại tìm được đúng các dòng log liên quan.

### CLI (không cần Web UI)

```bash
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar analyze sample-data/success/<Call-ID>
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar analyze sample-data/fail/<Call-ID> --from-file   # không cần ES
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar demo sample-data/success sample-data/fail sample-data/for_test
```

## 6. Chạy test

```bash
mvn test                              # backend; test tích hợp ES tự bỏ qua nếu ES không chạy
cd web && npm install && npm test     # Web UI
```

Test cần `sample-data/` (mục 1 "Dữ liệu mẫu"); thiếu thì chúng được bỏ qua kèm lý do chứ không fail. Kiểm số
`Skipped` ở cuối output: khác 0 nghĩa là data chưa đặt đủ. Test bảo mật S01-S08 (`SecurityEvaluationTest`) chạy lại toàn bộ bộ case bảo
mật ở mỗi lần `mvn test`.

## 7. Benchmark (Evaluation Runner)

```bash
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar evaluate \
    --cases benchmark/dev/cases.yaml --data-root sample-data --repeat 5 --out reports/eval-dev
#   --signaling es                                  lấy signaling từ ES thay vì signaling.json
#   --call-analysis.ai.context-mode=semi            đổi cách dựng context
#   --call-analysis.ai.include-rule-baseline=false  AI không thấy kết luận rule
```

Kết quả: `reports/eval-dev/summary.md` (metric mục 6.5, bảng AI vs Rule vs Ground truth, các lần chạy sai),
`results.json`, `samples/<case>.md`. Một lần chạy đầy đủ `dev` (30 case, khoảng 470 request) với `gpt-4o-mini` mất
khoảng 20-30 phút và khoảng 1,6 triệu token.

### Mentor chạy tập `held-out`

1. Viết file case theo đúng định dạng mục 6.3 (xem `benchmark/dev/cases.yaml` làm mẫu; khoá `cases:` hoặc một
   danh sách). Để đo Intent Accuracy theo đủ ba intent, thêm `expected_intents` cùng số phần tử với `questions`,
   mỗi phần tử là `ANALYZE_CALL`, `ANALYZE_WITH_FOCUS` hoặc `OUT_OF_SCOPE`. Case ngoài phạm vi có thể dùng
   `expected_intent: OUT_OF_SCOPE` cho mọi câu hỏi. Thiếu nhãn intent thì chỉ số này không có mẫu đo.
   Field tuỳ chọn khác: `files` dạng `{from, as, append}`, `sensitive_values`, `label_source`.
2. Đặt log của các cuộc gọi held-out dưới một thư mục, mỗi cuộc gọi một thư mục con tên là Call-ID (có
   `signaling.json`).
3. Chạy:

   ```bash
   java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar evaluate \
       --cases <held-out.yaml> --data-root <thư-mục-held-out> --repeat 5 --out reports/eval-held-out
   ```

4. Gửi lại `reports/eval-held-out/summary.md`. Lỗi định dạng case được báo rõ case nào, field nào.

## 8. Kiểm tra bảo mật (S01-S08)

```bash
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar security-eval --data-root sample-data
# -> reports/security-<thời điểm>/summary.md; kết luận PASS/FAIL ở cuối
```

Ba lượt: AI đang cấu hình; AI giả cố tình trả lại mọi giá trị nhạy cảm; AI giả rò giá trị vượt qua được
Guardrails. Mỗi lượt có thêm **phép đo độc lập** (đọc giá trị gốc từ file dữ liệu, không dùng Sanitizer); lệnh
`evaluate` cũng in phép đo này thành một dòng riêng trong bảng metric. Kết luận PASS chỉ khi cả hai phép đo bằng 0.
Diễn giải: `docs/security-evaluation-report.md`.

## 9. Batch analysis và Kibana

```bash
# Phân tích mọi Call-ID đang có trong ES (log client lấy từ --data-root); mặc định chỉ rule, không tốn AI
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar batch --data-root sample-data [--with-ai]
# -> in phân bố verdict / category / UNKNOWN / fallback; ghi reports/batch-<thời điểm>/summary.md

# Import dashboard Kibana (một lần, chạy lại được)
sh kibana/import-dashboard.sh
# mở http://localhost:5601/app/dashboards#/view/ca-dashboard
```

Dashboard đọc index `call-analysis-results`. Index này được ghi bởi `batch` và bởi Web UI (profile `web`). Mỗi
document chỉ là bản tóm tắt đã sanitize (Call-ID dạng pseudonym, không có văn bản report).

PowerShell không có `sh`: chạy lệnh `curl` bên trong `kibana/import-dashboard.sh` bằng `curl.exe`.

## 10. Xử lý sự cố

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `docker compose` báo `open //./pipe/dockerDesktopLinuxEngine` | Docker Desktop chưa chạy | Mở Docker Desktop, chờ engine lên, chạy lại |
| `/api/health` báo `"elasticsearch":"DOWN"` | ES chưa lên hoặc sai host/port | `docker compose up -d elasticsearch`; kiểm `curl localhost:9200` |
| Report `UNKNOWN`, "Giới hạn dữ liệu" ghi "Elasticsearch chưa có index signaling" | Chưa chạy `import` | Mục 3 |
| "Giới hạn dữ liệu" ghi "Không kết nối được Elasticsearch" / "phản hồi quá chậm" | ES tắt / treo (`ES_UNAVAILABLE` / `ES_TIMEOUT` trong log) | Khởi động lại ES rồi hỏi lại; trong lúc ES tắt, report vẫn trả ra (thường là `UNKNOWN`) chứ không lỗi 500 |
| UI hiện "AI phản hồi quá lâu — đây là kết quả rule-based" | AI timeout (`AI_TIMEOUT`) | Thử lại; kiểm mạng; có thể tăng `call-analysis.ai.timeout-ms` |
| UI hiện "Không kết nối được dịch vụ AI" | Sai key, hết quota, mất mạng (`AI_UNAVAILABLE`) | Kiểm `OPENAI_API_KEY` trong `.env`; xem dòng `ai_call` trong log |
| UI hiện "Chưa cấu hình AI" | `CALL_ANALYSIS_AI_PROVIDER` không phải `openai` hoặc thiếu key (`AI_NOT_CONFIGURED`) | Mục 4. `RULE_ONLY` trong log / Kibana là lượt `batch` hoặc rule baseline chạy chỉ rule theo thiết kế, không phải lỗi |
| `/api/health` báo `ai.configured: false` | Thiếu `CALL_ANALYSIS_AI_PROVIDER=openai` hoặc thiếu key | Mục 4 |
| Câu hỏi tiếng Việt gửi bằng curl bị coi là "ngoài phạm vi" | `curl.exe` trên Windows đổi codepage, mất dấu | Gửi câu hỏi từ file UTF-8: `-F "message=<q.txt"` |
| Upload báo "vượt giới hạn kích thước (10 MB)" | File > 10 MB | Cắt log quanh thời điểm cuộc gọi |
| Chỉ đính kèm WebRTC log và bị báo thiếu Call-ID | WebRTC log không chứa Call-ID | Điền ô Call-ID trên UI |
| Backend báo "Port 8080 was already in use" | Một dịch vụ khác đang giữ cổng 8080 | `--server.port=8090` cho backend và `BACKEND_URL=http://127.0.0.1:8090` cho `npm run dev` (mục 5) |
| Test lỗi "Mockito cannot mock this class ... Java: 26" | Maven chạy bằng JDK mới hơn 21 | Đặt `JAVA_HOME` về JDK 21 (mục 1) |
| `mvn package` báo không ghi được jar | Jar đang được một tiến trình java khác dùng (ví dụ `evaluate` đang chạy) | Chờ tiến trình đó xong hoặc dừng nó |
| `mvn test` có nhiều test `Skipped` với lý do "Thiếu dữ liệu mẫu" | Chưa đặt data mẫu | Mục 1 "Dữ liệu mẫu" |
| `ElasticsearchPipelineIntegrationTest` fail, verdict `UNKNOWN` thay vì `FAIL` | ES chạy nhưng chưa import (ví dụ volume bị xoá) | Mục 3 |
| "Giới hạn dữ liệu" ghi "nhiều sự kiện signaling hơn trần đọc một lần" | Một Call-ID có hơn 10.000 sự kiện trong ES (`ES_TRUNCATED`); chỉ phần đầu được phân tích | Kiểm Call-ID có bị trùng giữa nhiều cuộc gọi không; nếu thật sự dài, phân tích theo khoảng thời gian |
| `import` in `Could not list success` và `Done: 0 calls` | Truyền tên thư mục thay vì đường dẫn | Dùng `sample-data/success`, không phải `success` |
| Cần tìm log của một request lỗi | — | Lấy `requestId` trên UI, rồi `grep req-xxxxxxxx` trong log console hoặc `logs/app.jsonl` |

## 11. Log và quan sát

- Console: `HH:mm:ss LEVEL [thread] [request_id] logger - message`, mọi message và stack trace đi qua Sanitizer.
- Profile `jsonlog`: thêm `logs/app.jsonl`, mỗi dòng một JSON `{ts, level, logger, thread, request_id, message, error}`.
- Các dòng chuẩn:
  - `chat_request`: kết quả mỗi request (verdict, độ tin cậy, degraded, fallback_reason, latency_ms)
  - `ai_call`: model, latency_ms, result_status, token_usage, fallback_reason; `purpose=intent` cho Request Parser
  - `es_query`: hits, latency_ms, error_kind
- Không bao giờ log raw AI request/response, câu hỏi gốc hay nội dung log người dùng (mục 6.2).
