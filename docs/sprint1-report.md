# Sprint 1 — Domain, Data Foundation & Rule Baseline

**Dự án:** Trợ lý AI phân tích cuộc gọi (OJT AI 20K)
**Thực tập sinh:** Trần Anh Tú
**Stack:** Java 17 · Spring Boot 3.3.5 · Elasticsearch 8.15 (Docker Compose)

Sprint 1 chỉ làm phần **rule-based, chưa có AI** (đúng tinh thần *"Understand the problem before
applying AI"* của `PROJECT_SPEC.md` mục 5): với log của một cuộc gọi, hệ thống phải tự tính chỉ số,
dựng timeline, và ra kết luận SUCCESS/FAIL/UNKNOWN kèm bằng chứng — bằng logic xác định, không đoán.
Đây sẽ là baseline để đối chiếu verdict của AI ở Sprint 2.

## Đã làm gì và làm như nào

Dữ liệu mẫu gồm 3 nguồn không đồng nhất cho mỗi cuộc gọi: `signaling.json` (server, JSON có cấu
trúc), `*_endcall.log` (client, TSV với 9 schema cột khác nhau đánh số không cố định giữa các file),
`*_webrtc.log` (client, text log 2 định dạng iOS/native khác nhau). Việc đầu tiên là viết 3 parser
**nhận diện loại file bằng nội dung chứ không bằng tên** — vì tên file trong data mẫu có thể sai
(`calleer_webrtc.log`) hoặc gây hiểu lầm (1 file `.webrtc.log` thực chất là end-call log) — rồi ép cả
3 nguồn về cùng một khuôn dữ liệu chung (`CanonicalEvent`) để các bước sau không cần biết log gốc
từng ở định dạng nào.

Từ danh sách sự kiện đã chuẩn hoá, một `TimelineBuilder` loại trùng, rồi sắp xếp theo thời gian
tuyệt đối. Điểm khó nhất ở đây là webrtc log chỉ có mốc thời gian *tương đối* (tính từ lúc tiến
trình khởi động), nên phải "neo" nó vào timestamp tuyệt đối sớm nhất của cùng leg lấy từ end-call
log — nếu leg đó không có end-call log thì neo tạm vào mốc signaling sớm nhất của cả cuộc gọi, và
đánh dấu rõ đây là suy luận (`ANCHORED`) chứ không phải số liệu chắc chắn (`EXACT`).

Đồng hồ server (signaling) và đồng hồ thiết bị (end-call log) có thể lệch nhau, nên trước bước neo có
`ClockSkewAnchor`: so mốc đầu tiên của cùng một lệnh signaling ở hai phía, lấy trung vị theo từng leg, và
chỉ dịch timestamp khi lệch quá 2 giây (dưới đó coi là trễ mạng). Data mẫu hiện không có ca lệch thật nên
phần này mới được kiểm bằng test tổng hợp, chưa kiểm trên data thật.

Trên timeline đã sắp xếp, một bộ calculator đo các khoảng cách giữa các mốc signaling (setup time,
ringing time, thời lượng kết nối...), đếm số lần gửi lại lệnh, và đọc dòng "chỉ số chất lượng" cuối
cùng trong end-call log của mỗi bên (MOS, packet loss, RTT, jitter). Nguyên tắc xuyên suốt: thiếu dữ
liệu thì trả **N/A kèm lý do cụ thể**, không bao giờ mặc định về 0 — vì 0 và "không đo được" là hai
điều khác nhau, gộp chung sẽ đánh lừa người đọc report.

Phần ra quyết định (`RuleVerdictEngine`) không viết thành 1 khối if/else, mà là một **chuỗi luật**
(Chain of Responsibility): mỗi tình huống — thiếu signaling, thiếu log client, chưa từng kết nối,
kết nối rồi bị huỷ tường minh, kết nối rồi rớt media, kết nối và kết thúc bình thường — là một class
`VerdictRule` riêng, xếp theo thứ tự ưu tiên. Luật nào khớp trước thì trả kết luận ngay và dừng lại;
mọi kết luận đều đi kèm bằng chứng trỏ thẳng về dòng log gốc, để không có kết luận nào là "đoán". Kết
quả cuối được gói thành đúng bố cục report cố định theo mẫu spec, có validate theo JSON Schema trước
khi trả ra.

Kiến trúc code chia 4 tầng phụ thuộc một chiều — `domain` (model thuần) ← `analysis` (logic nghiệp
vụ) ← `application` (điều phối, chỉ biết interface) ← `cli`/`ingest`/`report` (adapter I/O cụ thể) —
để sau này thêm một nguồn log, một luật verdict, hay một cách trình bày report mới chỉ cần thêm 1
class, không phải sửa code đang chạy. Chi tiết sơ đồ: `docs/architecture-diagram-v1.md`.

## Kiểm chứng — và 3 lỗi tìm ra nhờ đối chiếu với dữ liệu có nhãn

Bộ dữ liệu mẫu có 2 thư mục đã được mentor gắn nhãn sẵn kết quả thật (`success/`, `fail/`) và 1 thư
mục không gắn nhãn (`for_test/`). Tận dụng điều đó: chạy hệ thống trên toàn bộ 20 cuộc gọi rồi so
verdict với tên thư mục — sai lệch ở đâu tức là bug ở đó, không phải phỏng đoán.

Lần chạy đầu chỉ đúng 7/13 trên tập có nhãn, còn lại 5 UNKNOWN và 1 sai. Đọc log thô của từng ca sai
lộ ra 3 lỗi cùng một bản chất — logic đúng nhưng **bỏ sót hoặc đọc thiếu bằng chứng đã có sẵn**:

1. Signaling có 2 lệnh (`CANCEL`, `FAIL_HARD`) nói dứt khoát "cuộc gọi kết thúc trước khi kết nối",
   nhưng rule engine cũ không dùng tới, lại còn để một rule khác ("thiếu log client thì UNKNOWN")
   chặn trước khi kịp xét — nên 2 cuộc gọi thật sự FAIL bị trả UNKNOWN oan. Sửa bằng cách thêm 1 rule
   đọc đúng 2 lệnh này và cho nó chạy *trước* rule chặn kia.
2. Log WebRTC định dạng native ghi trạng thái ICE bằng câu văn (`Changing IceConnectionState ... =>
   failed`) thay vì tên callback như bản iOS — parser cũ không nhận ra cú pháp này, nên toàn bộ
   nhánh phát hiện lỗi ICE **chưa từng chạy được lần nào** trên 16/28 file thuộc dạng này. Hậu quả cụ
   thể: 1 cuộc gọi thật (`2D9057AA`) kết nối bình thường rồi rớt mạng giữa chừng, bị kết luận nhầm
   thành SUCCESS — câu trả lời sai duy nhất trên toàn tập có nhãn. Sửa parser nhận thêm cú pháp đó,
   thêm 1 rule kiểm tra ICE thất bại ngay cả khi cuộc gọi đã "trông có vẻ" kết thúc bình thường.
3. Một rule khác chỉ coi `*_endcall.log` là bằng chứng phía client, bỏ qua `*_webrtc.log` — dù file
   đó cũng là log thiết bị người dùng và còn nói rõ hơn về việc media có lên được hay không. 3 cuộc
   gọi SUCCESS thật có đủ bằng chứng ICE trong webrtc log vẫn bị trả UNKNOWN vì thiếu đúng cái file
   kia. Sửa bằng cách cho rule chấp nhận cả hai loại log.

Sau khi sửa cả 3 và viết test chống hồi quy cho từng ca: **13/13 đúng** trên tập có nhãn (0 UNKNOWN,
0 sai), bộ `for_test/` không nhãn ra 1 SUCCESS + 6 FAIL đều có căn cứ, 0 cảnh báo parser trên toàn bộ
20 cuộc gọi, 112/112 unit test pass (đã chạy lại `mvn test` và `demo` ngày 2026-10-01). Toàn bộ quá trình đo, soát dữ liệu, và bằng chứng chi tiết từng ca
nằm ở `docs/sample-run-report.md`.

Riêng phần "chỉ số khớp tính tay trên ≥5 cuộc gọi" (acceptance criteria mục 5.1) được đối chiếu trên
**5 cuộc gọi trong `for_test/`** thay vì tập có nhãn — vì tập có nhãn đã tiết lộ sẵn đáp số cuối cùng
(verdict), khớp trên đó không chứng minh được nhiều; `for_test/` không có đáp số nên khớp tính tay ở
đó là bằng chứng khách quan hơn.

## Câu hỏi cần xác minh với mentor

Cả 3 câu đều nảy sinh trực tiếp từ việc đối chiếu code với dữ liệu thật ở trên — không phải câu hỏi
lý thuyết:

1. **Cuộc gọi bị caller chủ động `CANCEL` thì nên gắn issue category gì?** 7/20 cuộc gọi mẫu thuộc
   dạng này. Verdict `FAIL` khớp ground truth, nhưng mục 4.2 của spec không có category nào cho hành
   vi người dùng chủ động huỷ — hiện đang tạm dùng `SIGNALING_FAILURE`, nhưng nhãn đó ngụ ý lỗi hệ
   thống, không đúng bản chất. Cần category riêng, hay giữ nguyên và chỉ nói rõ trong phần tóm tắt?
2. **`requestId` có đổi khi signaling gửi lại (retransmit) một lệnh không?** Cuộc gọi `0EC7B700` có
   **15 event `INVITE`** nhưng hệ thống báo **0 lần gửi lại**, vì cả 15 event dùng chung đúng 1
   `requestId`. Cách đếm hiện tại (số `requestId` phân biệt trừ 1) giả định mỗi lần gửi lại sinh
   `requestId` mới — nếu thực tế server giữ nguyên `requestId` khi gửi lại thì chỉ số "số lần gửi
   lại" **không bao giờ** phát hiện được retransmit thật, cần đổi cách đếm.
3. **`FAIL_HARD` và `CANCEL` có đúng ngữ nghĩa như đang giả định không?** Rule engine hiện dùng 2
   lệnh này làm bằng chứng trực tiếp và dứt khoát cho "cuộc gọi kết thúc trước khi kết nối được",
   dựa trên quan sát rằng trong 20 cuộc gọi mẫu, cả hai **chưa bao giờ** xuất hiện cùng `OK_ACK_OK`.
   Đây là suy luận từ một mẫu nhỏ (8 lần quan sát), cần mentor xác nhận đây có đúng là quy tắc giao
   thức hay chỉ là trùng hợp trong tập dữ liệu hiện có.

Ngoài ra có 1 việc nên làm không cần chờ mentor: hạ độ tin cậy xuống `LOW` khi kết luận chỉ dựa trên
đúng 1 bằng chứng (ví dụ các ca chỉ suy từ việc *không thấy* `OK_ACK_OK` mà không có tín hiệu chấm
dứt tường minh nào khác) — hiện vẫn đang báo `MEDIUM`.

## Còn thiếu gì, và vì sao

- **Phát hiện lỗi TURN riêng biệt**: đã làm (`TurnFailureDetector`, category `TURN_FAILURE`), dựa trên
  việc đếm request/response TURN thay vì so khớp từ khoá. 6/20 cuộc gọi mẫu ra `TURN_FAILURE`.
- **Test Elasticsearch**: có 4 test bằng client giả (ES sập, id document ổn định khi import lại, bulk
  lỗi, file rỗng); chưa có integration test với ES thật (Testcontainers).
- **Hiệu chỉnh lệch đồng hồ trên data thật**: cơ chế đã có (`ClockSkewAnchor`) nhưng chưa có ca lệch thật
  nào trong data mẫu để xác nhận ngưỡng 2 giây.
- **Category riêng cho cuộc gọi bị người dùng chủ động huỷ**: 7/20 cuộc gọi mẫu kết thúc bằng
  `CANCEL`, verdict `FAIL` đúng nhưng taxonomy hiện chưa có category nào cho hành vi người dùng (khác
  lỗi hệ thống) — đang chờ mentor xác nhận nên gắn nhãn gì.
- **Confidence level**: mới suy từ độ đầy đủ dữ liệu, chưa đối chiếu AI-vs-rule (việc của Sprint 3).
- **Chỉ số "Nếu kịp"** (PAIR_PING gap, latency API nội bộ, WARN/ERROR server, ISP/ASN/country): chưa
  làm, ưu tiên xong Core trước.
- **Sanitizer**: chưa cài — field nhạy cảm đã liệt kê ở `docs/sensitive-data-inventory.md`, việc
  mask/drop là deliverable Sprint 2; Sprint 1 chưa gửi gì sang AI nên chưa có rủi ro rò rỉ.
- **AI Provider Proposal** (`docs/ai-provider-proposal.md`): đã có bản so sánh Cloud API vs local,
  đề xuất Cloud API, nhưng **chưa được mentor duyệt**.

---

*Lưu ý:* `sample-data/` không nằm trong git (có thể chứa dữ liệu nhạy cảm); ES/Kibana trong
`docker-compose.yml` chỉ bind `127.0.0.1`.

*Xem thêm:*
- *Bản trình bày trực quan tổng quan Sprint 1 (HTML): [Sprint 1 Baseline Report](https://claude.ai/artifact/GnN7k1uCXwXSJaMyG586Ci)*
- *Kết quả chạy chi tiết (bằng chứng từng ca, output thô, câu hỏi cho mentor): `docs/sample-run-report.md`, bản trực quan [Kết quả chạy data mẫu](https://claude.ai/code/artifact/f8665a78-c5ba-4887-9bf6-7ba4373fe8d6)*
