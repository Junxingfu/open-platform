import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, formatDateTime } from '../api'
import { useAuth } from '../auth'
import { useApi } from '../hooks'
import type { AccessRequestView, AppView, ProductView } from '../types'
import { Icon } from '../components/Icon'
import { Modal } from '../components/Modal'
import { useToast } from '../components/Toast'
import { EmptyState, Loading, PageHeader, StatusBadge } from '../components/Ui'

export function RequestsPage() {
  const { user } = useAuth()
  const { toast } = useToast()
  const isAdmin = user?.role === 'ADMIN'

  const { data, loading, error, reload } = useApi<AccessRequestView[]>('/portal/api/access-requests')
  const { data: apps } = useApi<AppView[]>('/portal/api/apps')
  const { data: products } = useApi<ProductView[]>('/portal/api/products')

  const [filter, setFilter] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL')
  const [appId, setAppId] = useState('')
  const [productCode, setProductCode] = useState('')
  const [useCase, setUseCase] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const [decisionTarget, setDecisionTarget] = useState<AccessRequestView | null>(null)
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT'>('APPROVE')
  const [comment, setComment] = useState('')
  const [deciding, setDeciding] = useState(false)

  useEffect(() => {
    if (!appId && apps?.length) setAppId(apps[0].appId)
  }, [apps, appId])

  useEffect(() => {
    if (!productCode && products?.length) setProductCode(products[0].code)
  }, [products, productCode])

  const filtered = useMemo(() => {
    if (!data) return []
    return filter === 'ALL' ? data : data.filter((item) => item.status === filter)
  }, [data, filter])

  const pendingCount = data?.filter((item) => item.status === 'PENDING').length || 0

  const submit = async () => {
    if (!appId || !productCode || !useCase.trim()) {
      toast('请完整填写应用、产品和接入场景', 'error')
      return
    }
    setSubmitting(true)
    try {
      await api('/portal/api/access-requests', {
        method: 'POST',
        body: { appId, productCode, useCase: useCase.trim() },
      })
      toast('接入申请已提交，请等待平台运营审批')
      setUseCase('')
      reload()
    } catch (err) {
      toast(err instanceof Error ? err.message : '提交失败', 'error')
    } finally {
      setSubmitting(false)
    }
  }

  const submitDecision = async () => {
    if (!decisionTarget) return
    setDeciding(true)
    try {
      await api(`/portal/api/access-requests/${decisionTarget.id}/decision`, {
        method: 'POST',
        body: { decision, comment: comment.trim() },
      })
      toast(decision === 'APPROVE' ? '已通过申请，产品已开通' : '已驳回申请')
      setDecisionTarget(null)
      setComment('')
      reload()
    } catch (err) {
      toast(err instanceof Error ? err.message : '审批失败', 'error')
    } finally {
      setDeciding(false)
    }
  }

  const openDecision = (item: AccessRequestView, next: 'APPROVE' | 'REJECT') => {
    setDecisionTarget(item)
    setDecision(next)
    setComment('')
  }

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow={isAdmin ? 'APPROVAL CENTER' : 'ACCESS REQUESTS'}
        title={isAdmin ? '审批中心' : '接入申请'}
        description={isAdmin
          ? '审批企业提交的 API 产品接入申请。通过后系统自动为应用开通订阅与 scope。'
          : '按“应用 + API 产品”提交接入申请，审批通过后即可调用产品下的接口。'}
        actions={!isAdmin && <Link to="/catalog" className="button button-secondary"><Icon name="book" size={16} /> 去 API 目录</Link>}
      />

      {isAdmin && pendingCount > 0 && (
        <div className="alert-banner">
          <div className="alert-banner-icon"><Icon name="ticket" size={20} /></div>
          <div><strong>{pendingCount} 条申请等待处理</strong><p>建议在 1 个工作日内完成审批，缩短合作方接入周期。</p></div>
        </div>
      )}

      {!isAdmin && (
        <section className="panel request-form-panel">
          <div className="section-head">
            <div>
              <div className="eyebrow">NEW REQUEST</div>
              <h2>发起接入申请</h2>
            </div>
            <span className="muted">审批通过后自动开通</span>
          </div>
          {(!apps || apps.length === 0) ? (
            <div className="inline-empty">
              还没有应用，先 <Link to="/apps">创建一个应用</Link> 再申请 API 产品。
            </div>
          ) : (
            <div className="request-form">
              <label>
                <span>应用</span>
                <select className="select" value={appId} onChange={(event) => setAppId(event.target.value)}>
                  {apps.map((app) => <option key={app.appId} value={app.appId}>{app.name}（{app.appId}）</option>)}
                </select>
              </label>
              <label>
                <span>API 产品</span>
                <select className="select" value={productCode} onChange={(event) => setProductCode(event.target.value)}>
                  {products?.map((product) => (
                    <option key={product.code} value={product.code}>
                      {product.name}（{product.subscribedAppCount > 0 ? '已开通应用 ' + product.subscribedAppCount + ' 个' : '未开通'}）
                    </option>
                  ))}
                </select>
              </label>
              <label className="request-form-wide">
                <span>接入场景</span>
                <textarea
                  rows={3}
                  value={useCase}
                  onChange={(event) => setUseCase(event.target.value)}
                  placeholder="描述业务场景、调用频率、数据用途，便于平台运营评估。"
                />
              </label>
              <div className="request-form-actions">
                <span className="form-hint">提交后可在下方列表查看审批进度。</span>
                <button type="button" className="button button-primary" onClick={submit} disabled={submitting}>
                  {submitting ? '提交中…' : '提交申请'} <Icon name="arrowRight" size={15} />
                </button>
              </div>
            </div>
          )}
        </section>
      )}

      <section className="panel">
        <div className="panel-head">
          <div>
            <div className="eyebrow">REQUEST HISTORY</div>
            <h2>{isAdmin ? '全部申请' : '我的申请'}</h2>
          </div>
          <div className="filter-chips">
            {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map((item) => (
              <button
                type="button"
                key={item}
                className={filter === item ? 'active' : ''}
                onClick={() => setFilter(item)}
              >
                {item === 'ALL' ? '全部' : item === 'PENDING' ? '待审批' : item === 'APPROVED' ? '已通过' : '已驳回'}
              </button>
            ))}
          </div>
        </div>

        {loading && <Loading label="正在加载申请…" />}
        {error && (
          <div className="error-panel">
            <Icon name="alert" size={18} />
            <span>{error}</span>
            <button type="button" className="button button-secondary button-sm" onClick={reload}>重试</button>
          </div>
        )}

        {!loading && !error && filtered.length === 0 && (
          <EmptyState
            icon="ticket"
            title={filter === 'ALL' ? '暂无接入申请' : '该状态下暂无申请'}
            description={isAdmin ? '企业提交 API 产品申请后会出现在这里。' : '从 API 目录或上方表单发起第一条接入申请。'}
            action={!isAdmin && <Link to="/catalog" className="button button-primary">浏览 API 目录</Link>}
          />
        )}

        {!loading && !error && filtered.length > 0 && (
          <div className="request-list">
            {filtered.map((item) => (
              <article className="request-card" key={item.id}>
                <div className="request-card-top">
                  <div>
                    <div className="request-app">{item.appName}<span className="mono">{item.appId}</span></div>
                    <strong>{item.productName}</strong>
                    {isAdmin && <span className="request-enterprise">{item.enterpriseName}</span>}
                  </div>
                  <StatusBadge status={item.status} />
                </div>
                <p className="request-usecase">{item.useCase}</p>
                <div className="request-meta">
                  <span><Icon name="clock" size={13} /> 提交于 {formatDateTime(item.createdAt)}</span>
                  {item.decidedAt && <span>处理于 {formatDateTime(item.decidedAt)}</span>}
                  {item.reviewer && <span>审批人：{item.reviewer}</span>}
                </div>
                {item.reviewComment && <div className="review-comment">审批意见：{item.reviewComment}</div>}
                {isAdmin && item.status === 'PENDING' && (
                  <div className="request-actions">
                    <button type="button" className="button button-success button-sm" onClick={() => openDecision(item, 'APPROVE')}>
                      <Icon name="check" size={14} /> 通过
                    </button>
                    <button type="button" className="button button-danger-ghost button-sm" onClick={() => openDecision(item, 'REJECT')}>
                      <Icon name="close" size={14} /> 驳回
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </section>

      {decisionTarget && (
        <Modal
          title={decision === 'APPROVE' ? '通过接入申请' : '驳回接入申请'}
          subtitle={`${decisionTarget.appName} · ${decisionTarget.productName}`}
          onClose={() => setDecisionTarget(null)}
          footer={
            <>
              <button type="button" className="button button-ghost" onClick={() => setDecisionTarget(null)}>取消</button>
              <button
                type="button"
                className={decision === 'APPROVE' ? 'button button-success' : 'button button-danger'}
                onClick={submitDecision}
                disabled={deciding}
              >
                {deciding ? '处理中…' : decision === 'APPROVE' ? '确认通过并开通' : '确认驳回'}
              </button>
            </>
          }
        >
          <div className="form-stack">
            <div className="decision-summary">
              <strong>{decisionTarget.enterpriseName}</strong>
              <span>申请应用：{decisionTarget.appName}</span>
              <span>申请产品：{decisionTarget.productName}</span>
            </div>
            <label>
              <span>审批意见（选填）</span>
              <textarea rows={3} value={comment} onChange={(event) => setComment(event.target.value)}
                placeholder={decision === 'APPROVE' ? '例如：资料完整，同意接入。' : '请说明驳回原因，便于企业补充材料。'} />
            </label>
          </div>
        </Modal>
      )}
    </div>
  )
}
