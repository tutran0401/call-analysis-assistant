import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { ReportView } from './components/ReportView'
import type { ChatResponse, Report } from './types'

const report: Report = {
  callId: 'CALL-1',
  verdict: 'SUCCESS',
  qualityFlag: true,
  issueCategory: 'NETWORK_PACKET_LOSS',
  alternativeCauses: ['NETWORK_DELAY_JITTER chưa xác nhận được'],
  confidenceLevel: 'MEDIUM',
  summary: 'Cuộc gọi thành công nhưng callee bị mất gói.',
  evidence: [{ id: 'EV001', source: 'signaling', timestamp: '10:00:06.320', description: 'OK_ACK: LegA CONFIRMED' }],
  metrics: [
    { name: 'MOS (callee)', value: '2.9', source: 'End Call' },
    { name: 'RTT / Jitter', value: 'N/A (không có trong file log)', source: 'End Call' },
  ],
  suggestions: ['Kiểm tra mạng phía callee.'],
  dataLimitations: ['Thiếu callee_webrtc.log'],
  analysis: 'Packet loss 9.8% (EV001).',
  degraded: false,
}

const response = (overrides: Partial<ChatResponse> = {}): ChatResponse => ({
  requestId: 'req-1234abcd',
  intent: 'ANALYZE_CALL',
  focus: [],
  report,
  message: null,
  degraded: false,
  fallbackReason: null,
  warnings: [],
  renderedText: '# Báo cáo phân tích cuộc gọi',
  ...overrides,
})

function mockFetch(chat: () => Response | Promise<Response>, health: object | null = null) {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      if (url === '/api/health') {
        return Promise.resolve(
          health ? new Response(JSON.stringify(health)) : new Response('{}', { status: 503 }),
        )
      }
      return Promise.resolve(chat())
    }),
  )
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

describe('ReportView', () => {
  it('renders every section of the 4.5 template in order', () => {
    render(<ReportView report={report} />)

    const headings = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(headings).toEqual([
      'Evidence chính',
      'Chỉ số cuộc gọi',
      'Vấn đề chất lượng / nguyên nhân khả dĩ',
      'Đề xuất',
      'Giới hạn dữ liệu',
    ])
    expect(screen.getByText('SUCCESS')).toBeInTheDocument()
    expect(screen.getByText(/Có - NETWORK_PACKET_LOSS/)).toBeInTheDocument()
    expect(screen.getByText('EV001')).toBeInTheDocument()
    expect(screen.getByText('Thiếu callee_webrtc.log')).toBeInTheDocument()
    expect(screen.getByText(/mất gói trên đường truyền/)).toBeInTheDocument()
    expect(screen.getByText(/Phân tích:/)).toBeInTheDocument()
  })

  it('never injects HTML from report text', () => {
    const hostile = { ...report, summary: '<img src=x onerror=alert(1)> tóm tắt' }
    const { container } = render(<ReportView report={hostile} />)

    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText(/<img src=x/)).toBeInTheDocument()
  })

  it('omits the analysis line for rule-based reports', () => {
    render(<ReportView report={{ ...report, analysis: '', qualityFlag: false }} />)

    expect(screen.queryByText(/Phân tích:/)).toBeNull()
    expect(screen.getByText('Không')).toBeInTheDocument()
  })
})

describe('App', () => {
  beforeEach(() => {
    mockFetch(() => json(response()))
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('keeps the submit button disabled until there is a question or a file', async () => {
    render(<App />)
    const button = screen.getByRole('button', { name: 'Phân tích' })
    expect(button).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'Phân tích cuộc gọi này')
    expect(button).toBeEnabled()
  })

  it('sends question and files as multipart and shows the report', async () => {
    render(<App />)
    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'Phân tích cuộc gọi này')
    await userEvent.upload(
      screen.getByLabelText('Đính kèm file log'),
      new File(['x'], 'caller_endcall.log', { type: 'text/plain' }),
    )
    expect(screen.getByText(/caller_endcall.log/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Phân tích' }))

    expect(await screen.findByRole('article', { name: 'Báo cáo phân tích cuộc gọi' })).toBeInTheDocument()
    const chatCall = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.find((c) => c[0] === '/api/chat')!
    const body = chatCall[1].body as FormData
    expect(body.get('message')).toBe('Phân tích cuộc gọi này')
    expect((body.getAll('files')[0] as File).name).toBe('caller_endcall.log')
    expect(chatCall[1].method).toBe('POST')
  })

  it('shows the fixed refusal for out-of-scope questions and no report', async () => {
    mockFetch(() =>
      json(response({ intent: 'OUT_OF_SCOPE', report: null, message: 'Mình chỉ hỗ trợ phân tích cuộc gọi.' })),
    )
    render(<App />)
    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'Viết email giúp mình')
    await userEvent.click(screen.getByRole('button', { name: 'Phân tích' }))

    expect(await screen.findByText('Mình chỉ hỗ trợ phân tích cuộc gọi.')).toBeInTheDocument()
    expect(screen.queryByRole('article')).toBeNull()
  })

  it('shows a degraded banner with the reason when the AI fell back to rules', async () => {
    mockFetch(() => json(response({ degraded: true, fallbackReason: 'AI_TIMEOUT' })))
    render(<App />)
    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'Phân tích')
    await userEvent.click(screen.getByRole('button', { name: 'Phân tích' }))

    expect(await screen.findByText(/AI_TIMEOUT/)).toBeInTheDocument()
    expect(screen.getByText(/kết quả rule-based/)).toBeInTheDocument()
  })

  it('shows the friendly server error instead of crashing', async () => {
    mockFetch(() => json({ error: 'File đính kèm vượt giới hạn kích thước cho phép (10 MB mỗi file).' }, 413))
    render(<App />)
    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'Phân tích')
    await userEvent.click(screen.getByRole('button', { name: 'Phân tích' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('vượt giới hạn kích thước')
  })

  it('explains when the backend cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    render(<App />)
    await userEvent.type(screen.getByLabelText('Câu hỏi'), 'Phân tích')
    await userEvent.click(screen.getByRole('button', { name: 'Phân tích' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Không kết nối được')
  })

  it('warns up front when AI is not configured or Elasticsearch is down', async () => {
    mockFetch(() => json(response()), { status: 'UP', ai: { model: 'none', configured: false }, elasticsearch: 'DOWN' })
    render(<App />)

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('AI chưa được cấu hình'))
    expect(screen.getByRole('status')).toHaveTextContent('Elasticsearch chưa chạy')
  })

  it('lets the user remove an attached file', async () => {
    render(<App />)
    await userEvent.upload(screen.getByLabelText('Đính kèm file log'), new File(['x'], 'a.log'))
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ a.log' }))

    expect(screen.queryByText(/a.log/)).toBeNull()
  })
})
