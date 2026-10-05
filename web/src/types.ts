// Khớp với ChatResponse / Report (report-schema-v2.json) của backend.

export type Verdict = 'SUCCESS' | 'FAIL' | 'UNKNOWN'

export interface EvidenceItem {
  id: string
  source: string
  timestamp: string
  description: string
}

export interface MetricRow {
  name: string
  value: string
  source: string
}

export interface Report {
  callId: string
  verdict: Verdict
  qualityFlag: boolean
  issueCategory: string
  alternativeCauses: string[]
  confidenceLevel: 'HIGH' | 'MEDIUM' | 'LOW'
  summary: string
  evidence: EvidenceItem[]
  metrics: MetricRow[]
  suggestions: string[]
  dataLimitations: string[]
  analysis: string
  degraded: boolean
}

export interface ChatResponse {
  requestId: string
  intent: 'ANALYZE_CALL' | 'ANALYZE_WITH_FOCUS' | 'OUT_OF_SCOPE'
  focus: string[]
  report: Report | null
  message: string | null
  degraded: boolean
  fallbackReason: string | null
  warnings: string[]
  renderedText: string
}

export interface Health {
  status: string
  ai: { model: string; configured: boolean }
  elasticsearch: 'UP' | 'DOWN'
}
