package com.tutran.callassistant.report;

import com.tutran.callassistant.domain.report.Report;

import java.util.List;

/**
 * Kiểm tra report trước khi trả ra ngoài, trả về danh sách vấn đề phát hiện được (rỗng là hợp lệ).
 *
 * <p>Đây chính là chỗ của <b>Guardrails</b> trong kiến trúc mục tiêu (mục 3.1). Sprint 1 chỉ kiểm
 * tra được đúng schema ({@link ReportSchemaGuard}) vì chưa có AI; Sprint 2 sẽ thêm các guard kiểm
 * tra evidence có thật hay không và verdict của AI có lệch với rule hay không - thêm vào là thêm một
 * implementation, pipeline không phải sửa.
 *
 * <p>Guard <b>không</b> ném exception: một report sai schema vẫn được trả ra kèm cảnh báo, vì mất
 * hẳn report thì tệ hơn là có report kèm ghi chú.
 */
public interface ReportGuard {

    List<String> inspect(Report report);
}
