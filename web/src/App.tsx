import { useEffect, useState } from 'react'
import { ApiError, getHealth, sendChat } from './api'
import { FilePicker } from './components/FilePicker'
import { ReportView } from './components/ReportView'
import { fallbackMessage } from './messages'
import type { ChatResponse, Health } from './types'

const EXAMPLE = 'Phân tích giúp mình cuộc gọi này, vì sao bên nhận không nghe được?'

export default function App() {
  const [message, setMessage] = useState('')
  const [callId, setCallId] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<{ message: string; requestId: string | null } | null>(null)
  const [result, setResult] = useState<ChatResponse | null>(null)
  const [health, setHealth] = useState<Health | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    getHealth().then(setHealth)
  }, [])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (loading) return
    setLoading(true)
    setError(null)
    setCopied(false)
    try {
      setResult(await sendChat(message, files, callId))
    } catch (e) {
      setResult(null)
      setError(
        e instanceof ApiError
          ? { message: e.message, requestId: e.requestId }
          : { message: 'Có lỗi không xác định. Vui lòng thử lại.', requestId: null },
      )
    } finally {
      setLoading(false)
    }
  }

  async function copyReport() {
    if (!result) return
    try {
      await navigator.clipboard.writeText(result.renderedText)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  const canSubmit = !loading && (message.trim().length > 0 || files.length > 0)

  return (
    <main className="page">
      <header>
        <h1>Trợ lý phân tích cuộc gọi</h1>
        <p className="muted">Mô tả yêu cầu bằng tiếng Việt và đính kèm log của cuộc gọi.</p>
      </header>

      {health && (health.status !== 'UP' || !health.ai.configured || health.elasticsearch === 'DOWN') && (
        <div className="banner banner-info" role="status">
          {!health.ai.configured && 'AI chưa được cấu hình: báo cáo sẽ dựa hoàn toàn trên rule-based. '}
          {health.elasticsearch === 'DOWN' && 'Elasticsearch chưa chạy: không tự lấy được signaling của cuộc gọi. '}
        </div>
      )}

      <form onSubmit={submit} className="card">
        <label htmlFor="message">Câu hỏi</label>
        <textarea
          id="message"
          rows={3}
          value={message}
          placeholder={EXAMPLE}
          disabled={loading}
          onChange={(e) => setMessage(e.target.value)}
        />
        <FilePicker files={files} onChange={setFiles} disabled={loading} />
        <label htmlFor="callId">
          Call-ID <span className="muted">(tuỳ chọn - cần khi chỉ đính kèm log WebRTC, vì loại log này không chứa Call-ID)</span>
        </label>
        <input
          id="callId"
          type="text"
          className="text-input mono"
          value={callId}
          placeholder="ví dụ DE7DD314-F432-45CB-BCB4-AE9103CC0919"
          disabled={loading}
          onChange={(e) => setCallId(e.target.value)}
        />
        <div className="actions">
          <button type="submit" disabled={!canSubmit}>
            {loading ? 'Đang phân tích…' : 'Phân tích'}
          </button>
        </div>
      </form>

      {loading && (
        <p className="muted" role="status">
          Đang phân tích cuộc gọi, vui lòng đợi trong giây lát…
        </p>
      )}

      {error && (
        <div className="banner banner-error" role="alert">
          {error.message}
          {error.requestId && <span className="muted mono"> (mã yêu cầu: {error.requestId})</span>}
        </div>
      )}

      {result && result.report === null && (
        <div className="banner banner-info" role="status">
          {result.message}
        </div>
      )}

      {result?.report && (
        <>
          {result.degraded && (
            <div className="banner banner-warn" role="status">
              {fallbackMessage(result.fallbackReason)} — đây là kết quả rule-based.
              {result.fallbackReason && <span className="muted mono"> [{result.fallbackReason}]</span>}
            </div>
          )}
          <div className="toolbar">
            <span className="muted mono">{result.requestId}</span>
            <button type="button" className="secondary" onClick={copyReport}>
              {copied ? 'Đã sao chép' : 'Sao chép báo cáo'}
            </button>
          </div>
          <ReportView report={result.report} />
          {result.warnings.length > 0 && (
            <details className="card">
              <summary>Ghi chú xử lý ({result.warnings.length})</summary>
              <ul>
                {result.warnings.map((warning, index) => (
                  <li key={index}>{warning}</li>
                ))}
              </ul>
            </details>
          )}
        </>
      )}
    </main>
  )
}
