import type { Report } from '../types'

// Diễn giải tiếng Việt cho issue category; khớp TextReportRenderer của backend.
const CATEGORY_EXPLANATION: Record<string, string> = {
  NONE: 'không phát hiện vấn đề chất lượng nào',
  NETWORK_PACKET_LOSS: 'mất gói trên đường truyền',
  NETWORK_DELAY_JITTER: 'độ trễ hoặc jitter mạng cao',
  ICE_FAILURE: 'kết nối media (ICE) không thiết lập được hoặc bị ngắt giữa chừng',
  TURN_FAILURE: 'client không đi hết được vòng request/response với TURN',
  SIGNALING_FAILURE: 'lỗi ở tầng signaling',
  UNKNOWN: 'chưa quy được nguyên nhân cụ thể',
}

const VERDICT_CLASS: Record<string, string> = {
  SUCCESS: 'badge badge-success',
  FAIL: 'badge badge-fail',
  UNKNOWN: 'badge badge-unknown',
}

function List({ items, empty }: { items: string[]; empty: string }) {
  if (items.length === 0) return <p className="muted">{empty}</p>
  return (
    <ul>
      {items.map((item, index) => (
        <li key={index}>{item}</li>
      ))}
    </ul>
  )
}

/** Hiển thị report đúng bố cục mẫu mục 4.5; chỉ render text từ JSON, không bao giờ chèn HTML. */
export function ReportView({ report }: { report: Report }) {
  return (
    <article className="report" aria-label="Báo cáo phân tích cuộc gọi">
      <h2>Báo cáo phân tích cuộc gọi</h2>

      <dl className="header-grid">
        <dt>Call-ID</dt>
        <dd className="mono">{report.callId}</dd>
        <dt>Kết luận</dt>
        <dd>
          <span className={VERDICT_CLASS[report.verdict] ?? 'badge'}>{report.verdict}</span>
        </dd>
        <dt>Cờ chất lượng</dt>
        <dd>
          {report.qualityFlag ? (
            <span className="badge badge-warn">Có - {report.issueCategory}</span>
          ) : (
            'Không'
          )}
        </dd>
        <dt>Độ tin cậy</dt>
        <dd>{report.confidenceLevel}</dd>
      </dl>
      <p className="summary">
        <strong>Tóm tắt: </strong>
        {report.summary}
      </p>

      <section>
        <h3>Evidence chính</h3>
        {report.evidence.length === 0 ? (
          <p className="muted">Không có evidence.</p>
        ) : (
          <ol className="evidence">
            {report.evidence.map((item) => (
              <li key={item.id}>
                <span className="chip">{item.id}</span>{' '}
                <span className="muted">
                  [{item.source} {item.timestamp}]
                </span>{' '}
                {item.description}
              </li>
            ))}
          </ol>
        )}
        {report.analysis && (
          <p className="analysis">
            <strong>Phân tích: </strong>
            {report.analysis}
          </p>
        )}
      </section>

      <section>
        <h3>Chỉ số cuộc gọi</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Chỉ số</th>
                <th>Giá trị</th>
                <th>Nguồn</th>
              </tr>
            </thead>
            <tbody>
              {report.metrics.map((row) => (
                <tr key={row.name}>
                  <td>{row.name}</td>
                  <td className={row.value.startsWith('N/A') ? 'muted' : undefined}>{row.value}</td>
                  <td>{row.source}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h3>Vấn đề chất lượng / nguyên nhân khả dĩ</h3>
        <ul>
          <li>
            Chính: <strong>{report.issueCategory}</strong> -{' '}
            {CATEGORY_EXPLANATION[report.issueCategory] ?? 'không có mô tả cho category này'}
          </li>
          {report.alternativeCauses.map((cause, index) => (
            <li key={index}>Khả dĩ khác: {cause}</li>
          ))}
        </ul>
      </section>

      <section>
        <h3>Đề xuất</h3>
        <List items={report.suggestions} empty="Không có đề xuất bổ sung." />
      </section>

      <section>
        <h3>Giới hạn dữ liệu</h3>
        <List items={report.dataLimitations} empty="Không có." />
      </section>
    </article>
  )
}
