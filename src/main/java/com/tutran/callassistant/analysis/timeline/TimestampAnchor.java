package com.tutran.callassistant.analysis.timeline;

import com.tutran.callassistant.domain.event.CanonicalEvent;

import java.util.List;

/**
 * Gán thời gian tuyệt đối cho những sự kiện chưa có (Strategy).
 *
 * <p>Sprint 1 có đúng một implementation: neo mốc thời gian tương đối của log WebRTC vào
 * một timestamp tuyệt đối quan sát được. Việc tách thành interface là vì đây chính là chỗ
 * sẽ được cải thiện - xử lý lệch đồng hồ server/thiết bị là giới hạn đã biết của Sprint 1,
 * và một chiến lược tương quan tốt hơn sẽ được thêm vào (chứ không phải sửa đè) sau này.
 * {@code TimelineBuilder} áp dụng tuần tự mọi anchor được đăng ký.
 */
public interface TimestampAnchor {

    List<CanonicalEvent> anchor(List<CanonicalEvent> events);
}
