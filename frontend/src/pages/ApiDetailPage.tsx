import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api'
import { useApi } from '../hooks'
import type { ApiDetail, AppView } from '../types'
import { Icon } from '../components/Icon'
import { Modal } from '../components/Modal'
import { useToast } from '../components/Toast'
import { CodeBlock, Loading, MethodBadge } from '../components/Ui'

export function ApiDetailPage() {
  const { apiId = '' } = useParams()
  const navigate = useNavigate()
  const { toast } = useToast()
  const { data, loading, error, reload } = useApi<ApiDetail>(`/portal/api/apis/${encodeURIComponent(apiId)}`)

  const [requestOpen, setRequestOpen] = useState(false)
  const [apps, setApps] = useState<AppView[]>([])
  const [appId, setAppId] = useState('')
  const [useCase, setUseCase] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!requestOpen) return
    api<AppView[]>('/portal/api/apps')
      .then((result) => {
        setApps(result)
        setAppId((current) => current || result[0]?.appId || '')
      })
      .catch((err) => toast(err instanceof Error ? err.message : '应用列表加载失败', 'error'))
  }, [requestOpen, toast])

  const submitRequest = async () => {
    if (!appId) {
      toast('请先选择应用', 'error')
      return
    }
    if (!useCase.trim()) {
      toast('请填写接入场景', 'error')
      return
    }
    setSubmitting(true)
    try {
      await api('/portal/api/access-requests', {
        method: 'POST',
        body: { appId, productCode: data?.summary.productCode, useCase: useCase.trim() },
      })
      toast('接入申请已提交，平台运营将尽快审批')
      setRequestOpen(false)
      setUseCase('')
      navigate('/requests')
    } catch (err) {
      toast(err instanceof Error ? err.message : '提交失败', 'error')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <Loading label="正在加载接口文档…" />
  if (error || !data) {
    return (
      <div className="panel error-panel">
        <Icon name="alert" size={20} />
        <div><strong>接口文档加载失败</strong><p>{error || '接口不存在'}</p></div>
        <button type="button" className="button button-secondary" onClick={reload}>重试</button>
      </div>
    )
  }

  const summary = data.summary

  return (
    <div className="page-stack">
      <Link to="/catalog" className="back-link">
        <Icon name="arrowLeft" size={15} /> 返回 API 目录
      </Link>

      <div className="api-detail-head">
        <div className="api-detail-title">
          <MethodBadge method={summary.method} />
          <h1>{summary.name}</h1>
          <span className="api-version">{summary.version}</span>
        </div>
        <p>{summary.summary}</p>
        <code className="api-path api-path-lg">{summary.path}</code>
        <div className="api-meta">
          <span className="tag tag-soft">{summary.productName}</span>
          <span className="tag tag-outline">{summary.requiredScope}</span>
          <span className="tag tag-outline">{summary.category}</span>
          <span className="status status-approved">{summary.status}</span>
        </div>
      </div>

      <div className="api-detail-grid">
        <div className="page-stack">
          <section className="panel">
            <div className="section-head">
              <div>
                <div className="eyebrow">OVERVIEW</div>
                <h2>接口说明</h2>
              </div>
            </div>
            <p className="prose">{data.description}</p>
          </section>

          <section className="panel">
            <div className="section-head">
              <div>
                <div className="eyebrow">REQUEST</div>
                <h2>请求参数</h2>
              </div>
              <span className="muted">{data.requestParams.length} 个参数</span>
            </div>
            {data.requestParams.length === 0 ? (
              <div className="inline-empty">该接口无请求参数，直接发起 GET 请求即可。</div>
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>参数名</th>
                      <th>位置</th>
                      <th>类型</th>
                      <th>必填</th>
                      <th>说明</th>
                      <th>示例</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.requestParams.map((param) => (
                      <tr key={param.name}>
                        <td><code>{param.name}</code></td>
                        <td>{param.location}</td>
                        <td>{param.type}</td>
                        <td>{param.required ? <span className="required">是</span> : '否'}</td>
                        <td>{param.description}</td>
                        <td><code className="muted">{param.example}</code></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="panel">
            <div className="section-head">
              <div>
                <div className="eyebrow">HEADERS</div>
                <h2>请求头</h2>
              </div>
              <span className="muted">OAuth2 与签名模式二选一</span>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>请求头</th>
                    <th>必填</th>
                    <th>说明</th>
                    <th>示例</th>
                  </tr>
                </thead>
                <tbody>
                  {data.requestHeaders.map((header) => (
                    <tr key={header.name}>
                      <td><code>{header.name}</code></td>
                      <td>{header.required ? '是' : '按认证方式'}</td>
                      <td>{header.description}</td>
                      <td><code className="muted">{header.example}</code></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="panel">
            <div className="section-head">
              <div>
                <div className="eyebrow">EXAMPLES</div>
                <h2>请求与响应示例</h2>
              </div>
            </div>
            <div className="example-grid">
              <div>
                <h3 className="subhead">请求示例</h3>
                <CodeBlock code={data.requestExample} />
              </div>
              <div>
                <h3 className="subhead">响应示例</h3>
                <CodeBlock code={data.responseExample} />
              </div>
            </div>
          </section>

          <section className="panel">
            <div className="section-head">
              <div>
                <div className="eyebrow">ERRORS</div>
                <h2>错误码</h2>
              </div>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr><th>错误码</th><th>标识</th><th>说明</th></tr>
                </thead>
                <tbody>
                  {data.errorCodes.map((item) => (
                    <tr key={item.code}>
                      <td><code>{item.code}</code></td>
                      <td>{item.message}</td>
                      <td>{item.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          {data.relatedApis.length > 0 && (
            <section className="panel">
              <div className="section-head">
                <div>
                  <div className="eyebrow">RELATED</div>
                  <h2>相关接口</h2>
                </div>
              </div>
              <div className="related-list">
                {data.relatedApis.map((item) => (
                  <Link to={`/apis/${item.apiId}`} className="related-item" key={item.apiId}>
                    <MethodBadge method={item.method} />
                    <div><strong>{item.name}</strong><span>{item.path}</span></div>
                    <Icon name="arrowRight" size={15} />
                  </Link>
                ))}
              </div>
            </section>
          )}
        </div>

        <aside className="api-aside">
          <div className="panel aside-card">
            <div className="eyebrow">INTEGRATION</div>
            <h2>接入信息</h2>
            <dl className="meta-list">
              <div><dt>所属产品</dt><dd>{summary.productName}</dd></div>
              <div><dt>所需权限</dt><dd><code>{summary.requiredScope}</code></dd></div>
              <div><dt>认证方式</dt><dd>{data.authModes.join(' / ')}</dd></div>
              <div><dt>限流策略</dt><dd>{data.rateLimit}</dd></div>
              <div><dt>接口状态</dt><dd><span className="status status-approved">已上线</span></dd></div>
            </dl>
            <button type="button" className="button button-primary button-block" onClick={() => setRequestOpen(true)}>
              <Icon name="ticket" size={16} /> 申请接入该产品
            </button>
            <Link to="/guide" className="button button-ghost button-block">
              查看接入指南 <Icon name="arrowRight" size={15} />
            </Link>
          </div>
          <div className="aside-tip">
            <Icon name="shield" size={18} />
            <p>审批通过后，应用会自动获得 <code>{summary.requiredScope}</code> 权限，无需手工配置 scope。</p>
          </div>
        </aside>
      </div>

      {requestOpen && (
        <Modal
          title="申请接入"
          subtitle={`${summary.productName} · ${summary.apiId}`}
          onClose={() => setRequestOpen(false)}
          footer={
            <>
              <button type="button" className="button button-ghost" onClick={() => setRequestOpen(false)}>取消</button>
              <button type="button" className="button button-primary" onClick={submitRequest} disabled={submitting}>
                {submitting ? '提交中…' : '提交申请'}
              </button>
            </>
          }
        >
          {apps.length === 0 ? (
            <div className="inline-empty">
              还没有可用应用，<Link to="/apps">先去创建一个应用</Link>。
            </div>
          ) : (
            <div className="form-stack">
              <label>
                <span>选择应用</span>
                <select className="select" value={appId} onChange={(event) => setAppId(event.target.value)}>
                  {apps.map((app) => <option value={app.appId} key={app.appId}>{app.name}（{app.appId}）</option>)}
                </select>
              </label>
              <label>
                <span>接入场景</span>
                <textarea
                  rows={4}
                  value={useCase}
                  onChange={(event) => setUseCase(event.target.value)}
                  placeholder="简单描述业务场景、调用频率与预期用途，便于平台运营评估。"
                />
              </label>
              <p className="form-hint">提交后平台运营会在 1 个工作日内处理；审批结果可在“接入申请”查看。</p>
            </div>
          )}
        </Modal>
      )}
    </div>
  )
}
