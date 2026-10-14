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
| JDK | 17+ | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js + npm | Node 18+ | chỉ cần cho Web UI |
| Docker Desktop | 4.x, engine 28 | Elasticsearch + Kibana; cần khoảng 4-6 GB RAM trống |
| curl | bất kỳ | kiểm tra nhanh API |

**Data mẫu không nằm trong git** (`sample-data/` bị ignore vì có thể chứa dữ liệu nhạy cảm). Đặt data Mentor cung
cấp vào:

```
sample-data/
  success/<Call-ID>/{signaling.json, caller_endcall.log, callee_endcall.log, caller_webrtc.log, callee_webrtc.log}
  fail/<Call-ID>/...
  for_test/<Call-ID>/...
  new_dataset/<Call-ID>/...      (tuỳ chọn)
```

Test backend (`mvn test`) và benchmark đọc thẳng các thư mục này.

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
mvn clean package                    # build + chạy toàn bộ test; thêm -DskipTests nếu chỉ cần jar
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar import sample-data/success sample-data/fail sample-data/for_test sample-data/new_dataset
curl -s "http://localhost:9200/call-signaling-events/_count"
```

`import` nhận một hoặc nhiều thư mục gốc (đường dẫn tính từ thư mục đang đứng), đọc mọi `signaling.json` (cả hai dạng export của Mentor) và index
vào `call-signaling-events`. Chạy lại an toàn: ID document cố định nên không nhân đôi dữ liệu.

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

Test cần `sample-data/` (mục 1). Test bảo mật S01-S08 (`SecurityEvaluationTest`) chạy lại toàn bộ bộ case bảo
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
`results.json`, `samples/<case>.md`. Một lần chạy đầy đủ `dev` (29 case, khoảng 460 request) với `gpt-4o-mini` mất
khoảng 20-30 phút và khoảng 1,6 triệu token.

### Mentor chạy tập `held-out`

1. Viết file case theo đúng định dạng mục 6.3 (xem `benchmark/dev/cases.yaml` làm mẫu; khoá `cases:` hoặc một
   danh sách). Field tuỳ chọn: `expected_intent: OUT_OF_SCOPE`, `files` dạng `{from, as, append}`,
   `sensitive_values`, `label_source`.
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
Guardrails. Diễn giải: `docs/security-evaluation-report.md`.

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
| `/api/health` báo `ai.configured: false` | Thiếu `CALL_ANALYSIS_AI_PROVIDER=openai` hoặc thiếu key | Mục 4 |
| Câu hỏi tiếng Việt gửi bằng curl bị coi là "ngoài phạm vi" | `curl.exe` trên Windows đổi codepage, mất dấu | Gửi câu hỏi từ file UTF-8: `-F "message=<q.txt"` |
| Upload báo "vượt giới hạn kích thước (10 MB)" | File > 10 MB | Cắt log quanh thời điểm cuộc gọi |
| Chỉ đính kèm WebRTC log và bị báo thiếu Call-ID | WebRTC log không chứa Call-ID | Điền ô Call-ID trên UI |
| `mvn package` báo không ghi được jar | Jar đang được một tiến trình java khác dùng (ví dụ `evaluate` đang chạy) | Chờ tiến trình đó xong hoặc dừng nó |
| `mvn test` lỗi "sample-data not found" | Chưa đặt data mẫu | Mục 1 |
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
