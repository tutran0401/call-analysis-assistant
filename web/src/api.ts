import type { ChatResponse, Health } from './types'

/** Hạn chót phía trình duyệt: lâu hơn timeout AI (30 s) + Elasticsearch của backend, để backend kịp fallback. */
export const CHAT_TIMEOUT_MS = 90_000
const HEALTH_TIMEOUT_MS = 5_000

/** Lỗi đã có thông báo thân thiện bằng tiếng Việt, hiển thị thẳng cho người dùng. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly requestId: string | null = null,
  ) {
    super(message)
  }
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    return await fetch(url, { ...init, signal: controller.signal })
  } finally {
    clearTimeout(timer)
  }
}

export async function sendChat(
  message: string,
  files: File[],
  callId = '',
  timeoutMs = CHAT_TIMEOUT_MS,
): Promise<ChatResponse> {
  const form = new FormData()
  form.append('message', message)
  if (callId.trim()) form.append('callId', callId.trim())
  files.forEach((file) => form.append('files', file))

  let response: Response
  try {
    response = await fetchWithTimeout('/api/chat', { method: 'POST', body: form }, timeoutMs)
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw new ApiError(
        `Máy chủ phân tích không phản hồi sau ${Math.round(timeoutMs / 1000)} giây. Vui lòng thử lại sau.`,
      )
    }
    throw new ApiError('Không kết nối được tới máy chủ phân tích. Hãy kiểm tra backend đã chạy chưa.')
  }

  if (!response.ok) {
    let message = 'Hệ thống gặp sự cố khi phân tích. Vui lòng thử lại.'
    let requestId = response.headers?.get('X-Request-Id') ?? null
    try {
      const body = await response.json()
      if (typeof body?.error === 'string') message = body.error
      if (typeof body?.requestId === 'string') requestId = body.requestId
    } catch {
      // Thân phản hồi không phải JSON: giữ thông báo mặc định.
    }
    throw new ApiError(message, requestId)
  }
  return (await response.json()) as ChatResponse
}

export async function getHealth(): Promise<Health | null> {
  try {
    const response = await fetchWithTimeout('/api/health', {}, HEALTH_TIMEOUT_MS)
    return response.ok ? ((await response.json()) as Health) : null
  } catch {
    return null
  }
}
