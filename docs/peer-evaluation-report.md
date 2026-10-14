# Peer Evaluation Report (Sprint 3 T7)

> **Trạng thái: CHƯA THỰC HIỆN, mới có khung.** Theo mục 7.3, báo cáo này đánh giá sản phẩm của bạn còn lại
> (Bế Nguyễn Hà Sơn), chạy trên tập `dev` **theo Runbook của bạn ấy**. Cần: quyền đọc repo của bạn ấy và Runbook đi
> kèm. Hai thứ này chưa có trong phiên làm việc này nên chưa thể chạy. Không điền kết quả giả; khi có repo, làm theo
> các bước dưới và điền vào từng mục.

| Mục | Nội dung |
| --- | --- |
| Người đánh giá | Trần Anh Tú |
| Sản phẩm được đánh giá | Repo của Bế Nguyễn Hà Sơn, commit: `<hash>` |
| Ngày chạy | `<yyyy-mm-dd>` |
| Môi trường | Windows 11, JDK / Node / Docker: `<phiên bản>` |
| Tập dữ liệu | `dev` (data mẫu Mentor cấp) |

## 1. Runbook có đủ để tự dựng và chạy được không?

Ghi lại từng bước **đúng như Runbook viết**, chỗ nào phải tự đoán hoặc tự sửa thì ghi rõ.

| Bước | Runbook ghi | Kết quả | Phải làm thêm gì ngoài Runbook |
| --- | --- | --- | --- |
| Cài đặt yêu cầu | | | |
| Dựng ES (+ Kibana) | | | |
| Import data mẫu | | | |
| Cấu hình AI | | | |
| Chạy backend + Web UI | | | |
| Chạy test | | | |
| Chạy benchmark `dev` | | | |

**Kết luận:** Đủ / Đủ nhưng phải đoán N chỗ / Không đủ (lý do).

## 2. Issue tìm được (tối thiểu 2, kèm bước tái hiện)

Gợi ý chỗ nên thử (những chỗ chính sản phẩm của mình từng hỏng khi chạy thật):

- File đặt tên sai loại (`caller_endcall.log` nhưng nội dung là WebRTC log); file của Call-ID khác; file rỗng /
  nhị phân / > 10 MB.
- Chỉ đính kèm một file; chỉ đính kèm WebRTC log (không có Call-ID).
- Log chứa JWT / số điện thoại / IP, kể cả trong **tên file** (`0933444555_webrtc.log`): kiểm report, response, log.
- Câu hỏi ngoài phạm vi; câu hỏi rất dài; câu hỏi tiếng Việt gửi bằng curl trên Windows.
- AI tắt / sai key / timeout: có fallback không, UI báo gì.
- Elasticsearch tắt giữa chừng; index chưa import.
- Cùng một cuộc gọi hỏi 3 cách: verdict có giống nhau không.

### Issue 1: `<tiêu đề>`

- **Mức độ:** cao / trung bình / thấp
- **Bước tái hiện:**
  1. …
- **Kết quả mong đợi:** …
- **Kết quả thực tế:** …
- **Bằng chứng:** (request_id, ảnh chụp, đoạn log đã sanitize)

### Issue 2: `<tiêu đề>`

- **Mức độ:**
- **Bước tái hiện:**
- **Kết quả mong đợi:**
- **Kết quả thực tế:**
- **Bằng chứng:**

## 3. Điểm thiết kế học được / muốn áp dụng (tối thiểu 1)

| Điểm thiết kế | Vì sao hay | Áp dụng vào sản phẩm của mình thế nào |
| --- | --- | --- |
| | | |

## 4. Số liệu chạy `dev` (nếu Runbook cho phép)

| Metric | Giá trị | Ghi chú |
| --- | --- | --- |
| Verdict Accuracy | | |
| Consistency | | |
| Pipeline Success Rate | | |
| Latency P50 / P95 | | |

---

Ghi chú quy trình (mục 7.3): **không sửa code của nhau**. Người nhận báo cáo tự quyết định fix issue nào; issue
không fix được ghi vào Known Limitations của họ.
