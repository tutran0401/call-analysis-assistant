package com.tutran.callassistant.ingest.file;

/**
 * Parser cho log phía client - những file người dùng đính kèm (end-call log, WebRTC log).
 *
 * <p>Phân biệt với parser của signaling export là có lý do thật: signaling đi vào hệ thống qua
 * Elasticsearch (hoặc qua nguồn signaling riêng), nên khi quét thư mục cuộc gọi phải <b>không</b>
 * parse lại {@code signaling.json} - nếu parse, mọi sự kiện signaling sẽ bị đếm hai lần. Nhờ có
 * interface con này, {@link DirectoryClientLogSource} chỉ nhận đúng các parser client log, còn
 * bộ nhận diện loại file vẫn nhận đủ cả ba parser để nhận dạng được mọi loại.
 */
public interface ClientLogParser extends LogParser {
}
