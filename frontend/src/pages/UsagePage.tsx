import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { formatDateTime, formatNumber } from '../api'
import { useApi } from '../hooks'
import type { UsageView } from '../types'
import { Icon } from '../components/Icon'
import { EmptyState, Loading, PageHeader, StatCard } from '../components/Ui'

export function UsagePage() {
  const [days, setDays] = useState(7)
  const { data, loading, error, reload } = useApi<UsageView>(`/portal/api/usage?days=${days}`)

  const maxCalls = useMemo(() => {
    if (!data?.series.length) return 1
    return Math.max(1, ...data.series.map((point) => point.calls))
  }, [data])

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="OBSERVABILITY"
        title="调用统计"
        description="查看开放 API 的调用量、成功率与耗时分布。数据为当前进程内存采集，重启后重置。"
        actions={
          <div className="filter-chips">
            {[7, 14, 30].map((item) => (
              <button type="button" key={item} className={days === item ? 'active' : ''} onClick={() => setDays(item)}>
                近 {item} 天
              </button>
            ))}
          </div>
        }
      />

      {loading && <Loading label="正在加载调用统计…" />}
      {error && (
        <div className="panel error-panel">
          <Icon name="alert" size={20} />
          <div><strong>统计加载失败</strong><p>{error}</p></div>
          <button type="button" className="button button-secondary" onClick={reload}>重试</button>
        </div>
      )}

      {!loading && !error && data && (
        <>
          <div className="stats-grid stats-grid-3">
            <StatCard label="总调用量" value={formatNumber(data.totalCalls)} hint={`近 ${days} 天`} icon="chart" tone="blue" />
            <StatCard label="成功率" value={data.totalCalls > 0 ? `${data.successRate}%` : '—'} hint="2xx / 3xx 占比" icon="shield" tone="teal" />
            <StatCard label="平均耗时" value={data.totalCalls > 0 ? `${data.avgLatencyMs} ms` : '—'} hint="服务端处理时间" icon="clock" tone="violet" />
          </div>

          <section className="panel">
            <div className="panel-head">
              <div>
                <div className="eyebrow">TRAFFIC</div>
                <h2>调用趋势</h2>
              </div>
              <div className="chart-legend">
                <span><i className="legend-dot legend-calls" /> 调用量</span>
                <span><i className="legend-dot legend-errors" /> 错误</span>
              </div>
            </div>

            {data.totalCalls === 0 ? (
              <EmptyState
                icon="chart"
                title="暂无调用数据"
                description="完成应用创建与产品申请后，按照接入指南调用接口，统计会自动出现在这里。"
                action={<Link to="/guide" className="button button-primary">查看接入指南</Link>}
              />
            ) : (
              <div className="chart">
                <div className="chart-grid">
                  {[0, 1, 2, 3].map((line) => <span key={line} />)}
                </div>
                <div className="chart-bars">
                  {data.series.map((point) => (
                    <div className="chart-column" key={point.date}>
                      <div className="chart-bar-wrap">
                        <div
                          className="chart-bar"
                          style={{ height: `${Math.max(3, (point.calls / maxCalls) * 100)}%` }}
                          title={`${point.date}：${point.calls} 次调用，${point.errors} 次错误`}
                        >
                          {point.errors > 0 && (
                            <span
                              className="chart-error"
                              style={{ height: `${Math.max(4, (point.errors / Math.max(point.calls, 1)) * 100)}%` }}
                            />
                          )}
                          <span className="chart-value">{point.calls}</span>
                        </div>
                      </div>
                      <span className="chart-label">{point.date.slice(5)}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </section>

          <div className="usage-grid">
            <section className="panel">
              <div className="panel-head">
                <div><div className="eyebrow">BY API</div><h2>接口调用排行</h2></div>
              </div>
              {data.byApi.length === 0 ? (
                <div className="inline-empty">暂无 API 调用数据</div>
              ) : (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead><tr><th>API</th><th className="align-right">调用</th><th className="align-right">错误</th><th className="align-right">平均耗时</th></tr></thead>
                    <tbody>
                      {data.byApi.map((item) => (
                        <tr key={item.apiId}>
                          <td><span className="cell-strong">{item.name}</span><div className="mono muted">{item.apiId}</div></td>
                          <td className="align-right">{formatNumber(item.calls)}</td>
                          <td className="align-right">{item.errors > 0 ? <span className="text-danger">{item.errors}</span> : '0'}</td>
                          <td className="align-right">{item.avgLatencyMs} ms</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className="panel">
              <div className="panel-head">
                <div><div className="eyebrow">RECENT</div><h2>最近调用</h2></div>
              </div>
              {data.recent.length === 0 ? (
                <div className="inline-empty">暂无调用记录</div>
              ) : (
                <div className="recent-list">
                  {data.recent.map((item) => (
                    <div className="recent-item" key={item.id}>
                      <div className={`recent-status ${item.status < 400 ? 'ok' : 'bad'}`}>{item.status}</div>
                      <div className="recent-copy">
                        <strong>{item.apiName}</strong>
                        <span>{item.appName} · {formatDateTime(item.at)}</span>
                      </div>
                      <span className="recent-cost">{item.costMs} ms</span>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  )
}
