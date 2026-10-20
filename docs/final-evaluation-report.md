# Final Evaluation Report & Known Limitations

Tài liệu bàn giao số 3 (PROJECT_SPEC.md mục 7.4). Nội dung: accuracy, consistency, AI vs Rule, latency trên tập
`dev`; kết quả `held-out` do Mentor gửi lại; Known Limitations.

- **Sản phẩm:** AI Call Analysis Assistant, Trần Anh Tú
- **Ngày đo:** 2026-10-14
- **Cấu hình đo:** OpenAI `gpt-4o-mini` thật, context `structured`, có `ruleBaseline` (cấu hình chạy thật);
  `--repeat 5`; signaling từ `sample-data/`
- **Kết quả gốc:** `reports/eval-s3-v2-with-baseline/` (không commit). Các lượt so sánh khác:
  `docs/accuracy-consistency-analysis.md`

## 1. Mức đạt tối thiểu (mục 8.1)

| Điều kiện | Trạng thái | Căn cứ |
| --- | --- | --- |
| End-to-end trên web UI: hỏi tiếng Việt + đính kèm log → report mẫu 4.5; signaling tự lấy từ ES; có fallback | **Đạt** | 6 scenario Final Demo chạy trên app thật ngày 2026-10-14 (`demo/README.md`); fallback khi AI lỗi và khi ES tắt đã kiểm trực tiếp |
| Verdict Accuracy ≥ 85% trên `held-out` | **Chờ Mentor** | Trên `dev`: 96.5%. Mục 4 để trống cho kết quả Mentor gửi lại |
| Metric Correctness = 100% | **Đạt** (`dev`) | 431/431 report; chỉ số khớp giá trị tính tay ở `docs/metrics-hand-calculation.md` |
| Security Leakage = 0, S01-S08 pass | **Đạt** | `docs/security-evaluation-report.md`: 0 ở input, output, log, cả khi AI cố tình rò rỉ |
| Metric "Báo cáo" ở 6.5 đã đo | **Đạt** | Mục 2 |
| Runbook đủ để người khác dựng lại | **Đạt** (chưa kiểm chéo) | `docs/runbook.md`; chưa có người khác chạy thử theo Runbook - xem Known Limitations |
| Phần Core chưa xong có trong Known Limitations | **Đạt** | Mục 5 |

## 2. Metric trên tập `dev` (mục 6.5)

29 case (27 trong phạm vi + 2 case ngoài phạm vi), 461 request, mỗi câu hỏi trong 3 câu đầu chạy 5 lần.

| Metric | Sprint 2 (2026-10-10) | **Sprint 3 (2026-10-14)** | Target | Loại |
| --- | --- | --- | --- | --- |
| Verdict Accuracy | 96.5% (416/431) | **96.5% (416/431)** | ≥ 85% (held-out) | Bắt buộc |
| Verdict Accuracy - rule baseline | 96.3% (26/27) | 96.3% (26/27) | — | đối chứng |
| Metric Correctness | 100% | **100% (431/431)** | 100% | Bắt buộc |
| Security Leakage input / output / log | 0 / 0 / 0 | **0 / 0 / 0** | 0 | Bắt buộc |
| Security Leakage, đo độc lập với Sanitizer (2026-10-20, xem dưới) | — | **0 / 0 / 0** (151 giá trị gốc) | 0 | Bắt buộc |
| Issue Category Accuracy (chỉ nhãn Mentor, từ 2026-10-20) | 100% (227/227)\* | **chưa đo** - chưa có nhãn Mentor | ≥ 70% | Báo cáo |
| Category khớp nhãn tạm lấy từ rule (không phải accuracy) | — | 100% (59/59, lượt 2026-10-20) | — | Báo cáo |
| Consistency (case: 3 cách hỏi × 5 lần) | 100% (27/27) | **100% (27/27)** | ≥ 95% | Báo cáo |
| Template Compliance | 100% | **100%** | 100% | Báo cáo |
| Intent Accuracy (gồm OUT_OF_SCOPE) | 100% (461/461) | **100% (461/461)** | ≥ 90% | Báo cáo |
| Unsupported Claim Rate (report giao cho người dùng) | 0% | **0% (0/431)** | 0% | Báo cáo |
| Phản hồi AI thô bị G01/G05 bắt | 2.1% (9/431) | **0% (0/431)** | — | Báo cáo |
| Pipeline Success Rate | 100% | **100% (461/461)** | ≥ 95% | Báo cáo |
| Tỉ lệ fallback (degraded) | 2.1% | **0%** | — | Báo cáo |
| Latency P50 / P95 (end-to-end, máy cá nhân) | 2532 / 3123 ms | **2948 / 4608 ms** | — | Báo cáo |
| Token AI trung bình / lần gọi | 3437 | **4055** | — | Báo cáo |

\* Con số 100% trước đây so với nhãn category **lấy từ output của rule engine** (đã kiểm tay), nên không đo được
gì về độ chính xác (Mentor nhận xét sau Sprint 2). Từ 2026-10-20 Issue Category Accuracy chỉ tính case có nhãn
`category=mentor`; con số cũ được báo riêng dưới tên "khớp nhãn tạm". Nhãn đã xin Mentor (gán mù, không kèm kết quả
rule): `benchmark/dev/category-labels-for-mentor.csv`.

**Đo rò rỉ độc lập** (`evaluation/RawValueOracle`): đọc giá trị nhạy cảm gốc từ file dữ liệu theo cấu trúc file
(không dùng Sanitizer), rồi tìm nguyên văn ở AI request, response và log. Lượt `dev` đầu tiên (2026-10-20, AI thật,
`--repeat 1`) ra **6 lần lọt** (3 vào AI request, 3 vào response). Cả 6 đều là Call-ID nằm trong câu "Giới hạn dữ
liệu" của case `var-foreign-call-file`. Đã sửa ở `AiContextBuilder`; lượt chạy lại: 0 / 0 / 0, verdict 97.2%
(104/107), fallback 0%. Chi tiết: `docs/security-evaluation-report.md`.

Tất cả metric "Báo cáo" đều đạt target. Token tăng 18% so với Sprint 2 (prompt có định nghĩa verdict, taxonomy kèm
`requiredEvidence`). Latency P95 tăng theo.

### Phân bố độ tin cậy (thiết kế mới của Sprint 3, mục 7.2)

| Verdict cuối | HIGH | MEDIUM | LOW |
| --- | --- | --- | --- |
| SUCCESS | 31 | 174 | — |
| FAIL | — | 211 | — |
| UNKNOWN | — | — | 15 |

Không có FAIL nào được HIGH: không case FAIL nào trong data mẫu có đủ cả 4 file log, và mục 7.2 quy định thiếu
một phần file thì MEDIUM. Đây là đặc điểm của data mẫu, không phải lỗi.

## 3. AI vs Rule vs Ground truth

Bảng từng case (câu hỏi đầu, lần chạy đầu): `reports/eval-s3-v2-with-baseline/summary.md`. Tóm tắt:

| | Số case |
| --- | --- |
| Rule = AI = Ground truth | 26 / 27 |
| Rule = AI ≠ Ground truth | 1 (`var-single-file`: rule và AI `SUCCESS`, nhãn `UNKNOWN` - câu hỏi scenario 5 đang chờ Mentor) |
| Rule ≠ AI | 0 |

**Đo AI độc lập với rule** (Sprint 2 chưa làm được): chạy lại toàn bộ `dev` với AI không được nhìn kết luận rule.

| | AI tự kết luận verdict khớp rule | AI khớp category | AI khớp cờ chất lượng | Verdict cuối |
| --- | --- | --- | --- | --- |
| Prompt Sprint 2 | 64.5% | 49.5% | 42.7% | 61.7% |
| Prompt Sprint 3 (sau phân tích lỗi T1) | **100%** | 54.3% | 56.1% | 96.5% |

Kết luận: verdict do AI tự suy ra giờ khớp rule hoàn toàn; category và cờ chất lượng AI tự đánh giá còn yếu, nên vẫn
do rule quyết định. Chi tiết và phân tích lỗi: `docs/accuracy-consistency-analysis.md`.

**Nguồn nhãn** (đọc trước khi diễn giải accuracy): 13 case dùng verdict theo thư mục của Mentor với category là
output rule đã kiểm tay; 7 case kiểm tay với log thô; 5 biến thể của cuộc gọi có nhãn; 4 case suy từ spec. Nhãn
issue category **chưa phải ground truth độc lập từ Mentor**, nên Issue Category Accuracy 100% chỉ có nghĩa là report
giữ đúng category của rule.

## 4. Kết quả `held-out` (Mentor chạy)

> Chưa có. Mentor chạy theo `docs/runbook.md` mục 7, rồi điền vào đây. Không dùng kết quả này để tinh chỉnh (mục 6.3).

| Metric | Cuối Sprint 2 | Cuối Sprint 3 |
| --- | --- | --- |
| Số case / số request | | |
| Verdict Accuracy | | |
| Issue Category Accuracy | | |
| Consistency | | |
| Pipeline Success Rate | | |
| Latency P50 / P95 | | |

## 5. Known Limitations

### Core chưa hoàn thiện hoặc chưa kiểm chứng

| Giới hạn | Lý do | Ảnh hưởng / giảm thiểu |
| --- | --- | --- |
| **Peer Evaluation (T7): đã làm (2026-10-20)** | — | `docs/peer-evaluation-report.md`: Runbook của bạn còn lại dựng lại được hoàn toàn; 2 issue có tái hiện; 3 điểm thiết kế học được. Hai issue đó có khả năng cũng có ở sản phẩm này (xem "Thiết kế / dữ liệu") |
| **Kết quả `held-out` chưa có** | Mentor chạy cuối sprint | Mục 4 để trống |
| **Runbook: đã được người khác chạy thử (2026-10-19)** | — | Bạn còn lại dựng và chạy lại được toàn bộ theo Runbook, tái hiện đúng mọi metric `dev` (báo cáo của bạn ấy, nhận 2026-10-19). Ba chỗ vướng (JDK > 21, `mvn package` khi ES rỗng, cổng 8080) đã sửa ngày 2026-10-20 |
| **Nhãn issue category và evidence ID chưa phải ground truth của Mentor** | Mentor chưa cung cấp | Issue Category Accuracy hiện "chưa đo" (chỉ tính nhãn Mentor). Đã gửi file xin nhãn `benchmark/dev/category-labels-for-mentor.csv` (20 cuộc gọi); `expected_evidence` để trống |
| **Scenario 5 ("chỉ một file" → `UNKNOWN`) chưa thống nhất** | Data có nhãn của Mentor (`5E0800AE`, một file, `SUCCESS`) mâu thuẫn với scenario 5 | Đính kèm đúng một WebRTC log không kèm Call-ID → `UNKNOWN` (đúng kỳ vọng). Có Call-ID và signaling → `SUCCESS`, độ tin cậy `MEDIUM`, nêu đủ file thiếu. Chờ Mentor |
| **AI tự đánh giá category / cờ chất lượng còn yếu** | AI không biết ngưỡng chất lượng (đưa ngưỡng vào sẽ đụng G05); khoảng 55% khớp rule khi không thấy baseline | Cấu hình chạy thật giữ `ruleBaseline`; category trong report luôn của rule; lệch thì độ tin cậy LOW |
| **Chất lượng lời văn AI chưa được chấm** | Metric 6.5 không đo; cần rubric hoặc người chấm | Lời văn bị Guardrails kiểm số liệu và evidence; phần "Căn cứ kết luận" do code viết nên không phụ thuộc AI |
| **Chỉ số "Nếu kịp" ở 4.3 chưa đưa vào report** (khoảng trống PAIR_PING, latency API nội bộ, WARN/ERROR theo service, ISP/ASN theo leg) | Ưu tiên Core và hardening | Không ảnh hưởng mức tối thiểu |

### Issue từ Peer Evaluation của bạn còn lại (nhận 2026-10-19)

| # | Issue | Xử lý |
| --- | --- | --- |
| 1 | "No sessions found" ra `0` thay vì `N/A` với export dạng gọn | **Đã sửa**: chỉ đếm `message` của signaling (mục 4.3); không có thì `N/A` kèm lý do |
| 2 | Cờ mất gói bật theo mẫu đỉnh của một khoảng đo; cờ không có evidence | **Đã sửa**: ngưỡng dùng mất gói cả cuộc (bộ đếm cộng dồn), RTT / jitter trung bình; cờ ghi dòng periodic stats làm evidence. `271D1FAF` hết cờ (cả cuộc 1,83 %); ca chất lượng kém của `dev` và demo scenario 2 chuyển sang `B5F19381` (8,48 %) |
| 3 | Export signaling bị cắt bớt nhưng report HIGH, "Giới hạn dữ liệu: Không có" | **Đã sửa**: cờ cắt bớt gắn vào từng event (cả đường ES), thành giới hạn dữ liệu, độ tin cậy tối đa MEDIUM |
| R1-R3 | Runbook: JDK > 21; `mvn package` hỏng khi ES rỗng; cổng 8080 ghi cứng | **Đã sửa**: Runbook ghi JDK 17-21 và thứ tự build → import → test; test ES tự bỏ qua khi chưa import; proxy Vite đọc `BACKEND_URL` |
| 4 | Evidence WebRTC mang mốc giờ suy ra, không có số dòng gốc | **Đã sửa** (mục 8.2): evidence từ log client mang số dòng (`callee_webrtc.log:902`); evidence WebRTC hiện mốc có thật trong file trước, mốc suy ra sau (`[020:195] ≈ 2026-09-17 23:43:22.031`). Không đổi Report Schema (chỉ đổi chuỗi `source` / `timestamp`) |
| 5 | Lượt chạy chỉ rule (`batch`, baseline của `evaluate`) bị ghi `AI_UNAVAILABLE`, làm lệch biểu đồ fallback | **Đã sửa**: chạy chỉ rule theo thiết kế ghi `RULE_ONLY` (biểu đồ fallback bỏ qua); chưa cấu hình AI ghi `AI_NOT_CONFIGURED`; `AI_UNAVAILABLE` chỉ còn cho lỗi AI thật. Dashboard Kibana cần import lại (`sh kibana/import-dashboard.sh`) |
| Quan sát | `rawEvent` trong `call-signaling-events` giữ nguyên IP client (419 document) | **Chưa sửa**: mục 6.2 chưa rõ "dữ liệu ghi vào ES local" gồm cả signaling nạp vào hay chỉ kết quả phân tích; đang hỏi Mentor. Index chỉ nằm trên máy cá nhân, bind `127.0.0.1` |

### Thiết kế / dữ liệu

- **WebRTC log của cuộc gọi khác được nhận theo loại** (phát hiện khi làm Peer Evaluation: sản phẩm của bạn còn lại
  ra verdict sai, MEDIUM, khi Call-ID trong câu hỏi khác cuộc gọi của file). `ContentUploadValidator` của sản phẩm
  này có cùng quy ước: WebRTC log không mang Call-ID nên "luôn được nhận theo loại". Rất có thể cùng lỗi, **chưa
  kiểm bằng chạy thật**. Hướng sửa: end call log trong cùng lượt upload lệch Call-ID thì coi WebRTC log đi kèm là
  nghi vấn.
- **Log có BOM UTF-8 hoặc lưu UTF-16 chưa được xử lý** (cùng nguồn phát hiện). Code không có chỗ nào bỏ BOM hay giải
  mã UTF-16, nên file kiểu này nhiều khả năng bị nhận diện sai loại. **Chưa kiểm bằng chạy thật.**

- **Sanitizer dựa trên mẫu đã biết** (regex + tên field); số điện thoại chỉ nhận dạng dạng VN. Giảm thiểu: AI không
  nhận log gốc hay câu hỏi gốc; context còn sót một giá trị thì không gửi. `security-eval` đã tìm và sửa một lỗ
  (số/IP dính dấu `_`); có thể còn mẫu chưa biết.
- **Cuộc gọi bị người dùng chủ động huỷ (`CANCEL`) mang category `SIGNALING_FAILURE`**: mục 4.2 không có category
  cho hành vi người dùng; chờ Mentor.
- **Cuộc gọi bị ngắt giữa chừng mang `ICE_FAILURE`**: 4.2 không có category riêng cho "bị ngắt bất thường"; log chỉ
  cho biết một bên mất mạng, không cho biết vì sao.
- **Chưa phân định lỗi TURN ở phía server hay phía mạng thiết bị** (ngoài phạm vi theo mục 9).
- **Batch analysis cần log client trên đĩa**: ES chỉ chứa signaling, nên `batch` lấy log client từ `--data-root`;
  Call-ID không có thư mục thì bị bỏ qua và được đếm riêng.
- **Report degraded không bao giờ HIGH** theo thiết kế (mục 7.2 đòi "AI khớp rule"). Chạy `batch` chỉ rule nên toàn
  bộ là MEDIUM / LOW và fallback 100%; summary của batch có ghi rõ.
- **Data mẫu nhỏ** (27 cuộc gọi, câu hỏi do một người viết): chênh lệch dưới vài điểm phần trăm nằm trong nhiễu.

### Vận hành

- Không có xác thực / giới hạn tần suất trên API (đúng phạm vi mục 9); server chỉ bind `127.0.0.1`.
- Docker Desktop phải mở bằng tay sau khi khởi động lại máy.
- API key OpenAI từng bị lộ trong khung chat ở Sprint 2 (không có trong repo); cần thu hồi và tạo key mới.
