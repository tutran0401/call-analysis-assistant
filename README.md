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
truy cập được tại http://localhost:5601 khi cả 2 container đã chạy.

## Chạy demo

```bash
# Phân tích 1 cuộc gọi, lấy signaling từ Elasticsearch
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar analyze success/DE7DD314-F432-45CB-BCB4-AE9103CC0919

# Phân tích 1 cuộc gọi, đọc trực tiếp signaling.json thay vì query ES
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar analyze fail/1B009D42-49CD-479E-B26C-3A2994AEB720 --from-file

# Phân tích toàn bộ cuộc gọi trong 1 hoặc nhiều thư mục gốc (đáp ứng yêu cầu "demo tối thiểu 5 cuộc gọi" của Sprint 1)
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar demo success fail for_test
```

Mỗi cuộc gọi in ra report theo đúng bố cục cố định ở `PROJECT_SPEC.md` mục 4.5, kèm theo
các cảnh báo của parser (thiếu file, dòng log lỗi định dạng...) — phần này không thuộc
report chính thức nhưng hữu ích khi debug.

## Chạy test

```bash
mvn test
```

Toàn bộ test cho parser, timeline, metrics, evidence/rule-verdict và report-schema đều
chạy trên data mẫu thật trong `fail/`, `success/`, `for_test/` — nhiều giá trị kỳ vọng
(thời lượng, số lần gửi lại, MOS/packet-loss/RTT/jitter) được **tính tay** trực tiếp từ
5 cuộc gọi thật khác nhau (đúng yêu cầu ở mục 5.1), chứ không chỉ so khớp với chính output
của code.

**Đã kiểm chứng trên toàn bộ data mẫu** (`demo success fail for_test`, cả 20 cuộc gọi,
qua pipeline có ES thật): 0 cảnh báo parser trên mọi file — **parse sạch 100%** (vượt xa
mục tiêu ≥90%) — với 8 verdict `SUCCESS`, 6 `FAIL`, 6 `UNKNOWN`; mỗi `UNKNOWN` đều có lý
do thiếu dữ liệu cụ thể trong report, và không có exception nào trong suốt quá trình chạy.

## Cấu trúc project

```
src/main/java/com/tutran/callassistant/
  domain/     Canonical Event Model (T2)
  parser/     Log Normalizer: nhận diện loại file + 3 parser cho 3 nguồn log (T3)
  timeline/   Call Timeline Builder (T4)
  taxonomy/   Verdict & Issue Taxonomy (T5)
  metrics/    Call Metrics Calculator (T6)
  evidence/   Evidence Engine + Rule Verdict (T7)
  report/     Report Schema v1 + renderer (T8)
  es/         Import + query Elasticsearch (T1)
  cli/        Entrypoint chạy demo
docs/
  architecture-diagram-v1.md
  verdict-issue-taxonomy.md
  sensitive-data-inventory.md
  ai-provider-proposal.md
```

## Giới hạn đã biết (Known Limitations) — Sprint 1

- **Chưa phát hiện lỗi TURN riêng biệt một cách đáng tin cậy.** Phiên bản đầu tiên dùng
  cách so khớp từ khóa tự do ("turn"/"ice" + "error"/"fail"), nhưng cách này báo nhầm cả
  những phản hồi giao thức TURN bình thường, tự phục hồi được (per-candidate) thành lỗi —
  kể cả trên một cuộc gọi SUCCESS hoàn toàn sạch (xem Javadoc của package `evidence`/`parser`
  và lịch sử commit để biết số lượng false-positive cụ thể). `RuleVerdictEngine` hiện gộp
  lỗi tầng TURN vào `ICE_FAILURE` (dựa trên trạng thái kết thúc chính thức
  `onIceConnectionChange` của engine) hoặc `SIGNALING_FAILURE`. Một bộ phát hiện TURN
  riêng, đáng tin cậy hơn sẽ để lại cho sprint sau.
- **Chưa làm các chỉ số "Nếu kịp" (mục 4.3)**: proxy khoảng trống PAIR_PING, latency API
  nội bộ lúc INIT_CALL, số lượng WARN/ERROR phía server, ngữ cảnh ISP/ASN/country. Sprint 1
  ưu tiên làm xong các chỉ số Core trước.
- **Độ tin cậy (confidence level) mới chỉ là placeholder tạm thời.** `ConfidenceLevel` suy
  ra HIGH/MEDIUM/LOW chỉ dựa trên độ đầy đủ của dữ liệu và mức độ chắc chắn của issue
  category; thiết kế đầy đủ (đối chiếu AI vs rule) là việc của Sprint 3 T3.
- **Chưa có sanitizer.** Các field nhạy cảm/bí mật đã được liệt kê ở
  `docs/sensitive-data-inventory.md`, nhưng việc mask/pseudonymize/drop là deliverable của
  Sprint 2 (Sprint 1 chưa gửi gì sang AI provider nên chưa có nguy cơ rò rỉ dữ liệu).
- **AI Provider Proposal** (`docs/ai-provider-proposal.md`) đã có bản nháp nhưng **chưa
  được Mentor duyệt**.
