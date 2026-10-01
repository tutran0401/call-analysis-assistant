# AI Call Analysis Assistant — Sprint 1

Bản rule-based baseline cho trợ lý phân tích cuộc gọi OJT AI 20K (xem `docs/PROJECT_SPEC.md`).
Phạm vi Sprint 1: **với log của một cuộc gọi, tính được chỉ số, dựng được timeline, và
ra verdict theo rule kèm evidence — chưa có AI.**

## Công nghệ sử dụng

Java 17, Spring Boot 3.3.5 (chỉ chạy CLI, không có web server), Maven. Elasticsearch 8.15
qua Docker Compose để lưu signaling log.

## Cài đặt

Yêu cầu: JDK 17+, Maven, Docker Desktop.

```bash
# 1. Bật Elasticsearch (+ Kibana) ở local
docker compose up -d

# 2. Build project
mvn clean package

# 3. Import data mẫu (signaling log) mentor cung cấp vào Elasticsearch
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar import success fail for_test
```

Elasticsearch cần khoảng 1-2 GB heap (đã cấu hình trong `docker-compose.yml`); Kibana
truy cập được tại http://localhost:5601 khi cả 2 container đã chạy. Cả Elasticsearch (9200) và
Kibana (5601) chỉ bind vào `127.0.0.1`, không mở ra mạng ngoài (ES chạy với security tắt).

**Data mẫu không nằm trong git** (`sample-data/` được ignore vì có thể chứa dữ liệu nhạy cảm): cần tự đặt
data mentor cung cấp vào `sample-data/{success,fail,for_test}/` trước khi chạy `import`, `demo` hay `mvn test`
(test đọc trực tiếp các cuộc gọi trong thư mục này).

## Chạy demo

```bash
# Phân tích 1 cuộc gọi, lấy signaling từ Elasticsearch
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar analyze sample-data/success/DE7DD314-F432-45CB-BCB4-AE9103CC0919

# Phân tích 1 cuộc gọi, đọc trực tiếp signaling.json thay vì query ES
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar analyze sample-data/fail/1B009D42-49CD-479E-B26C-3A2994AEB720 --from-file

# Phân tích toàn bộ cuộc gọi trong 1 hoặc nhiều thư mục gốc (đáp ứng yêu cầu "demo tối thiểu 5 cuộc gọi" của Sprint 1)
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar demo sample-data/success sample-data/fail sample-data/for_test
```

Mỗi cuộc gọi in ra report theo đúng bố cục cố định ở `PROJECT_SPEC.md` mục 4.5, kèm theo
các cảnh báo của parser (thiếu file, dòng log lỗi định dạng...) — phần này không thuộc
report chính thức nhưng hữu ích khi debug.

## Chạy test

```bash
mvn test
```

Toàn bộ test cho parser, timeline, metrics, evidence/rule-verdict và report-schema đều
chạy trên data mẫu thật trong `sample-data/fail/`, `sample-data/success/`, `sample-data/for_test/` — nhiều giá trị kỳ vọng
(thời lượng, số lần gửi lại, MOS/packet-loss/RTT/jitter) được **tính tay** trực tiếp từ
8 cuộc gọi thật khác nhau (yêu cầu ở mục 5.1 là tối thiểu 5), chứ không chỉ so khớp với chính
output của code. Mốc gốc và phép tính của từng giá trị: `docs/metrics-hand-calculation.md`.

**Đã kiểm chứng trên toàn bộ data mẫu** (`demo sample-data/success sample-data/fail sample-data/for_test`, cả 20 cuộc gọi,
qua pipeline có ES thật): 0 cảnh báo parser trên mọi file — **parse sạch 100%** (vượt xa
mục tiêu ≥90%) — và không có exception nào trong suốt quá trình chạy. Kết quả giống hệt
nhau trên cả hai đường lấy signaling (qua Elasticsearch và `--from-file`).

**Lưu ý về data mẫu: chưa có ground truth.** `sample-data/` chỉ có log và `sample.md` (đặc tả định
dạng), không kèm file nhãn nào; tên ba thư mục `success/`, `fail/`, `for_test/` là **cách tổ chức
data để chạy thử**, không phải nhãn verdict. Theo mục 1.4 của spec, ground truth sẽ do Mentor cung
cấp riêng — nên **chưa đo được Verdict Accuracy** (mục 6.5), hiện chỉ kiểm được từng kết luận có
bằng chứng trong log chống lưng hay không.

**Kết quả chạy thử 20 cuộc gọi:** 12 `FAIL`, 7 `SUCCESS`, 1 `SUCCESS` có cờ chất lượng kém, 0
`UNKNOWN`. **5 trên 6 issue category** của mục 4.2 đã được data mẫu chạm tới (`TURN_FAILURE` ×6,
`SIGNALING_FAILURE` ×5, `ICE_FAILURE` ×1, `NETWORK_PACKET_LOSS` ×1, cộng `NONE` ×7); chỉ
`NETWORK_DELAY_JITTER` chưa có ca nào. Toàn bộ kết quả từng cuộc, phần đối chiếu độc lập với log thô,
5 lỗi tìm ra khi chạy, và output nguyên văn: `docs/sample-run-report.md`.

**Lệch đồng hồ server–thiết bị (T4):** `ClockSkewAnchor` so mốc đầu tiên của cùng một lệnh signaling ở
server và ở end-call log của từng leg, lấy trung vị; chỉ dịch timestamp khi lệch vượt 2 giây (dưới đó coi là
trễ mạng). Data mẫu hiện không có ca lệch thật nên chưa kích hoạt trên data thật, chỉ được kiểm bằng test tổng hợp.

## Cấu trúc project

Code chia thành 4 tầng, phụ thuộc **một chiều** từ ngoài vào trong (`cli`/`ingest`/`report`
→ `application` → `analysis` → `domain`). Chi tiết và sơ đồ ở
`docs/architecture-diagram-v1.md`.

```
src/main/java/com/tutran/callassistant/
  domain/         Model thuần - không Spring, không IO, không phụ thuộc gì bên ngoài
    event/        Canonical Event Model (T2) + NormalizedEvents
    timeline/     CallTimeline (+ các truy vấn dùng chung cho metrics và rule)
    metrics/      CallMetrics, MetricResult (giá trị hoặc N/A kèm lý do)
    verdict/      Verdict, IssueCategory, Evidence, RuleVerdictResult
    report/       Report Schema v1 dạng record + ConfidenceLevel
  application/    Use case + port (chỉ interface, không biết công nghệ nào)
    port/in/      AnalyzeCallUseCase, ImportSignalingUseCase
    port/out/     SignalingEventSource, ClientLogSource, SignalingEventArchive,
                  CallDirectoryScanner, ReportPresenter, ImportProgressListener
    CallAnalysisPipeline    <- đọc class này là thấy toàn bộ hệ thống (8 dòng)
  analysis/       Logic nghiệp vụ
    timeline/     Timeline Builder (T4): loại trùng + neo thời gian + sắp thứ tự
    metrics/      Call Metrics Calculator (T6): facade gom 4 calculator chuyên trách
    verdict/      Evidence Engine + Rule Verdict (T7) + taxonomy (T5)
      rule/       5 VerdictRule - thứ tự quyết định verdict đọc được ngay ở danh sách
      quality/    3 QualityCheck - tiêu chí gắn cờ chất lượng kém
  ingest/         Adapter đầu vào
    file/         Log Normalizer (T3): 3 parser + nhận diện loại file theo nội dung
    es/           Import + query Elasticsearch (T1)
  report/         Report Schema v1 + assembler + renderer + guard (T8)
  cli/            Entrypoint: mỗi lệnh (import/analyze/demo) là một class riêng
docs/
  architecture-diagram-v1.md
  verdict-issue-taxonomy.md
  sensitive-data-inventory.md
  ai-provider-proposal.md
sample-data/    Data mẫu do mentor cung cấp (không chỉnh sửa nội dung), KHÔNG kèm nhãn verdict
  sample.md     Đặc tả định dạng log (WebRTC + 9 schema #H1-#H9 của end-call log)
  success/      7 cuộc gọi — tên thư mục chỉ là cách tổ chức data, không phải nhãn
  fail/         6 cuộc gọi — như trên
  for_test/     7 cuộc gọi — tập chính dùng để chạy thử và đối chiếu với log thô
```

### Nguyên tắc thiết kế

Những chỗ dễ thay đổi nhất đều được đặt sau một interface, nên **thêm tính năng là thêm
class, không phải sửa class đang chạy**:

| Muốn thêm gì | Phải sửa gì |
| --- | --- |
| Một nguồn log thứ 4 | Thêm 1 class implement `LogParser` (nó tự khai báo cách nhận diện và cách đọc) |
| Một tình huống verdict mới | Thêm 1 class implement `VerdictRule` |
| Một tiêu chí chất lượng mới | Thêm 1 class implement `QualityCheck` |
| Một lệnh CLI mới | Thêm 1 class implement `CliCommand` |
| Một cách trình bày report mới | Thêm 1 class implement `ReportRenderer` |
| Đổi ngưỡng cảnh báo | Sửa `application.yml`, không cần build lại |

Chỉ những đường biên thật sự sẽ thay đổi mới có interface — `TimelineBuilder` hay
`CallMetricsCalculator` vẫn là class thường, vì tạo interface cho mọi thứ cũng là một dạng
rối rắm không cần thiết.

## Giới hạn đã biết (Known Limitations) — Sprint 1

- **Chưa phân định được lỗi TURN nằm ở phía server hay phía mạng của thiết bị.** Rule engine phát
  hiện được lỗi TURN theo hai hình thái (không tạo được socket / gửi request mà không có phản hồi),
  nhưng theo mục 9 của spec thì chẩn đoán nghẽn hay quá tải TURN server nằm ngoài phạm vi. Ba cuộc
  gọi `TURN_FAILURE` dạng "không tạo được socket" đều đến từ một máy có VPN `tun0` — mới thấy trên
  một máy nên chưa kết luận VPN là nguyên nhân.
- **Cuộc gọi do người dùng chủ động huỷ chưa có issue category riêng.** 7/20 cuộc gọi trong
  data mẫu kết thúc bằng `CANCEL` trước khi kết nối. Verdict `FAIL` là đúng, nhưng mục 4.2
  của spec không có category nào cho hành vi người dùng nên chúng tạm mang
  `SIGNALING_FAILURE` — ngụ ý lỗi hệ thống. Đang chờ Mentor xác nhận nên gắn nhãn gì
  (xem `docs/sample-run-report.md` mục 6).
- **Chưa làm các chỉ số "Nếu kịp" (mục 4.3)**: proxy khoảng trống PAIR_PING, latency API
  nội bộ lúc INIT_CALL, số lượng WARN/ERROR phía server, ngữ cảnh ISP/ASN/country. Sprint 1
  ưu tiên làm xong các chỉ số Core trước.
- **Độ tin cậy (confidence level) mới chỉ là placeholder tạm thời.**
  `DataCompletenessConfidencePolicy` suy ra HIGH/MEDIUM/LOW chỉ dựa trên độ đầy đủ của dữ
  liệu và mức độ chắc chắn của issue category; thiết kế đầy đủ (đối chiếu AI vs rule) là
  việc của Sprint 3 T3 — khi đó chỉ cần thêm một implementation của `ConfidencePolicy`.
- **Chưa có sanitizer.** Các field nhạy cảm/bí mật đã được liệt kê ở
  `docs/sensitive-data-inventory.md`, nhưng việc mask/pseudonymize/drop là deliverable của
  Sprint 2 (Sprint 1 chưa gửi gì sang AI provider nên chưa có nguy cơ rò rỉ dữ liệu).
- **AI Provider Proposal** (`docs/ai-provider-proposal.md`) đã có bản nháp nhưng **chưa
  được Mentor duyệt**.
