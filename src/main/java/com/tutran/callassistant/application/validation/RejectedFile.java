package com.tutran.callassistant.application.validation;

/** File không được đưa vào phân tích, kèm lý do để hiện cho người dùng ở "Giới hạn dữ liệu". */
public record RejectedFile(String name, String reason) {

    public String asLimitation() {
        return "Bỏ qua file " + name + ": " + reason;
    }
}
