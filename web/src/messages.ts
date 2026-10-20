// Câu giải thích thân thiện cho mã fallback của backend (ChatAnalysisService). Mã gốc vẫn hiện kèm,
// để người dùng báo lại được chính xác.
const FALLBACK_MESSAGES: Record<string, string> = {
  AI_TIMEOUT: 'AI phản hồi quá lâu',
  AI_UNAVAILABLE: 'Không kết nối được dịch vụ AI',
  AI_NOT_CONFIGURED: 'Chưa cấu hình AI (thiếu API key)',
  AI_INVALID_RESPONSE: 'AI trả lời sai định dạng',
  AI_ERROR: 'Lỗi không dự kiến khi gọi AI',
  GUARDRAIL_REJECTED: 'Câu trả lời của AI không qua được bước kiểm tra (Guardrails)',
  INPUT_NOT_SANITIZED: 'Dữ liệu chưa được làm sạch hoàn toàn nên không gửi sang AI',
  OUTPUT_SCHEMA_INVALID: 'Báo cáo do AI tạo không đúng cấu trúc',
}

export function fallbackMessage(reason: string | null): string {
  if (!reason) return 'Phân tích AI không khả dụng'
  return FALLBACK_MESSAGES[reason] ?? 'Phân tích AI không khả dụng'
}
