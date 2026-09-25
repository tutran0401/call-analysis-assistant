package com.tutran.callassistant.analysis;

/**
 * Tập lệnh (cmd) signaling mà cả tầng metrics và tầng rule dựa vào, gom về một chỗ để hai
 * tầng không thể hiểu lệch nhau về cùng một tên lệnh.
 *
 * <p>Tập lệnh quan sát được trong data mẫu là
 * {@code INIT_CALL, INVITE, TRYING, RINGING, ACK_RINGING, OK, OK_ACK_OK, ICE, ACK_ICE,
 * PAIR_PING, PAIR_PONG_PAIR_PING, BYE, ACK_BYE, BYE_ACK_BYE, LOG_STATS}. Không có lệnh nào
 * tên đúng là "OK_ACK" như trong định nghĩa chỉ số của spec; {@link #OK_ACK_OK} được coi là
 * tương đương (sự kiện ack-of-OK đã được gộp tên). Giả định ánh xạ này được ghi rõ ở đây để
 * sửa đúng một chỗ khi mentor xác nhận ngữ nghĩa thật của các lệnh signaling.
 */
public final class SignalingCommands {

    /** Bắt đầu thiết lập cuộc gọi. */
    public static final String INIT_CALL = "INIT_CALL";
    public static final String INVITE = "INVITE";
    public static final String TRYING = "TRYING";
    public static final String RINGING = "RINGING";
    public static final String OK = "OK";
    /** Cuộc gọi đã được xác nhận (CONFIRMED) - mốc phân định SUCCESS/FAIL của rule baseline. */
    public static final String OK_ACK_OK = "OK_ACK_OK";
    public static final String BYE = "BYE";

    private SignalingCommands() {
    }
}
