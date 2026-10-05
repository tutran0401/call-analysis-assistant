import type { ChatResponse, Health } from './types'

/** Lỗi đã có thông báo thân thiện bằng tiếng Việt, hiển thị thẳng cho người dùng. */
export class ApiError extends Error {}

export async function sendChat(message: string, files: File[]): Promise<ChatResponse> {
  const form = new FormData()
  form.append('message', message)
  files.forEach((file) => form.append('files', file))

  let response: Response
  try {
    response = await fetch('/api/chat', { method: 'POST', body: form })
  } catch {
    throw new ApiError('Không kết nối được tới máy chủ phân tích. Hãy kiểm tra backend đã chạy chưa.')
  }

  if (!response.ok) {
    let message = 'Hệ thống gặp sự cố khi phân tích. Vui lòng thử lại.'
    try {
      const body = await response.json()
      if (typeof body?.error === 'string') message = body.error
    } catch {
      // Thân phản hồi không phải JSON: giữ thông báo mặc định.
    }
    throw new ApiError(message)
  }
  return (await response.json()) as ChatResponse
}

export async function getHealth(): Promise<Health | null> {
  try {
    const response = await fetch('/api/health')
    return response.ok ? ((await response.json()) as Health) : null
  } catch {
    return null
  }
}
