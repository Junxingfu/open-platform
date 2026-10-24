import { useState } from 'react'
import { api, formatDate } from '../api'
import { useAuth } from '../auth'
import { useApi } from '../hooks'
import type { AppCreatedView, AppView, CredentialsView } from '../types'
import { Icon } from '../components/Icon'
import { Modal } from '../components/Modal'
import { useToast } from '../components/Toast'
import { CopyButton, EmptyState, Loading, PageHeader } from '../components/Ui'

export function AppsPage() {
  const { user } = useAuth()
  const { toast } = useToast()
  const { data, loading, error, reload } = useApi<AppView[]>('/portal/api/apps')
  const isAdmin = user?.role === 'ADMIN'

  const [createOpen, setCreateOpen] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [authMode, setAuthMode] = useState<'OAUTH2' | 'SIGNATURE'>('OAUTH2')
  const [creating, setCreating] = useState(false)
  const [created, setCreated] = useState<AppCreatedView | null>(null)

  const [credentials, setCredentials] = useState<CredentialsView | null>(null)
  const [rotateTarget, setRotateTarget] = useState<AppView | null>(null)
  const [rotating, setRotating] = useState(false)

  const submitCreate = async () => {
    if (!name.trim()) {
      toast('请输入应用名称', 'error')
      return
    }
    setCreating(true)
    try {
      const result = await api<AppCreatedView>('/portal/api/apps', {
        method: 'POST',
        body: { name: name.trim(), description: description.trim(), authMode },
      })
      setCreateOpen(false)
      setName('')
      setDescription('')
      setCreated(result)
      reload()
      toast('应用创建成功，请保存好密钥')
    } catch (err) {
      toast(err instanceof Error ? err.message : '创建失败', 'error')
    } finally {
      setCreating(false)
    }
  }

  const viewCredentials = async (app: AppView) => {
    try {
      const result = await api<CredentialsView>(`/portal/api/apps/${app.appId}/credentials`)
      setCredentials(result)
    } catch (err) {
      toast(err instanceof Error ? err.message : '密钥读取失败', 'error')
    }
  }

  const rotate = async () => {
    if (!rotateTarget) return
    setRotating(true)
    try {
      const result = await api<CredentialsView>(`/portal/api/apps/${rotateTarget.appId}/rotate-secret`, {
        method: 'POST',
      })
      setRotateTarget(null)
      setCredentials(result)
      reload()
      toast('密钥已轮换，旧密钥立即失效')
    } catch (err) {
      toast(err instanceof Error ? err.message : '轮换失败', 'error')
    } finally {
      setRotating(false)
    }
  }

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow={isAdmin ? 'APPLICATION OPERATIONS' : 'APPLICATIONS'}
        title={isAdmin ? '应用管理' : '我的应用'}
        description={isAdmin
          ? '查看平台全部企业应用及其 API 产品开通情况。'
          : '应用是调用开放 API 的身份载体。在这里创建应用、获取凭证并管理密钥。'}
        actions={!isAdmin && (
          <button type="button" className="button button-primary" onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={16} /> 创建应用
          </button>
        )}
      />

      {isAdmin && (
        <div className="info-banner">
          <Icon name="shield" size={18} />
          <span>平台运营视角：展示全部企业的应用。审批申请请前往“审批中心”。</span>
        </div>
      )}

      {loading && <Loading label="正在加载应用…" />}
      {error && (
        <div className="panel error-panel">
          <Icon name="alert" size={20} />
          <div><strong>应用加载失败</strong><p>{error}</p></div>
          <button type="button" className="button button-secondary" onClick={reload}>重试</button>
        </div>
      )}

      {!loading && !error && data && (
        data.length === 0 ? (
          <EmptyState
            icon="apps"
            title="还没有应用"
            description="创建一个应用作为调用开放 API 的身份，创建后即可申请 API 产品。"
            action={!isAdmin && (
              <button type="button" className="button button-primary" onClick={() => setCreateOpen(true)}>
                创建第一个应用
              </button>
            )}
          />
        ) : (
          <div className="app-list">
            {data.map((app) => (
              <article className="app-card" key={app.appId}>
                <div className="app-card-head">
                  <div className="app-icon"><Icon name="apps" size={20} /></div>
                  <div className="app-title">
                    <h3>{app.name}</h3>
                    <span className="mono">{app.appId}</span>
                  </div>
                  <span className={`auth-badge auth-${app.authMode.toLowerCase()}`}>
                    {app.authMode === 'OAUTH2' ? 'OAuth2' : 'HMAC 签名'}
                  </span>
                </div>

                <p className="app-description">{app.description || '暂无描述'}</p>
                {isAdmin && <div className="app-enterprise"><Icon name="user" size={14} /> {app.enterpriseName}</div>}

                <div className="app-products">
                  <span className="label">已开通产品</span>
                  {app.productNames.length === 0
                    ? <span className="muted">暂未开通，去“接入申请”申请产品</span>
                    : app.productNames.map((product) => <span className="tag tag-soft" key={product}>{product}</span>)}
                </div>

                <div className="credential-row">
                  <div>
                    <span className="label">AppSecret</span>
                    <code>{app.secretMasked}</code>
                  </div>
                  <div className="credential-actions">
                    <button type="button" className="button button-ghost button-sm" onClick={() => viewCredentials(app)}>
                      <Icon name="key" size={14} /> 查看
                    </button>
                    <button type="button" className="button button-ghost button-sm" onClick={() => setRotateTarget(app)}>
                      <Icon name="refresh" size={14} /> 轮换
                    </button>
                  </div>
                </div>

                <div className="app-card-foot">
                  <span><Icon name="clock" size={13} /> 创建于 {formatDate(app.createdAt)}</span>
                  <span className={`status ${app.active ? 'status-approved' : 'status-rejected'}`}>
                    {app.active ? '运行中' : '已停用'}
                  </span>
                </div>
              </article>
            ))}
          </div>
        )
      )}

      {createOpen && (
        <Modal
          title="创建应用"
          subtitle="应用用于标识调用方身份，可随时轮换密钥。"
          onClose={() => setCreateOpen(false)}
          footer={
            <>
              <button type="button" className="button button-ghost" onClick={() => setCreateOpen(false)}>取消</button>
              <button type="button" className="button button-primary" onClick={submitCreate} disabled={creating}>
                {creating ? '创建中…' : '创建应用'}
              </button>
            </>
          }
        >
          <div className="form-stack">
            <label>
              <span>应用名称</span>
              <input value={name} onChange={(event) => setName(event.target.value)} placeholder="例如：供应商准入服务" maxLength={60} />
            </label>
            <label>
              <span>应用描述</span>
              <textarea rows={3} value={description} onChange={(event) => setDescription(event.target.value)}
                placeholder="简单描述应用用途，便于团队识别。" maxLength={200} />
            </label>
            <div>
              <span className="form-label">认证方式</span>
              <div className="auth-options">
                <button
                  type="button"
                  className={`auth-option ${authMode === 'OAUTH2' ? 'selected' : ''}`}
                  onClick={() => setAuthMode('OAUTH2')}
                >
                  <span className="auth-option-icon"><Icon name="shield" size={18} /></span>
                  <strong>OAuth2</strong>
                  <small>用 client_id/secret 换取 15 分钟 JWT，适合标准服务端接入。</small>
                </button>
                <button
                  type="button"
                  className={`auth-option ${authMode === 'SIGNATURE' ? 'selected' : ''}`}
                  onClick={() => setAuthMode('SIGNATURE')}
                >
                  <span className="auth-option-icon"><Icon name="key" size={18} /></span>
                  <strong>HMAC 签名</strong>
                  <small>每个请求携带时间戳、nonce 与签名，适合弱改造系统。</small>
                </button>
              </div>
            </div>
          </div>
        </Modal>
      )}

      {created && (
        <Modal
          title="应用创建成功"
          subtitle="完整密钥只展示一次，请立即复制并安全保存。"
          onClose={() => setCreated(null)}
          footer={<button type="button" className="button button-primary" onClick={() => setCreated(null)}>我已保存</button>}
        >
          <div className="secret-panel">
            <div><span>AppId</span><code>{created.app.appId}</code><CopyButton value={created.app.appId} /></div>
            <div><span>AppSecret</span><code>{created.appSecret}</code><CopyButton value={created.appSecret} /></div>
          </div>
          <div className="warning-note">
            <Icon name="alert" size={16} />
            <span>密钥泄露后请立即在应用列表中轮换；轮换后旧密钥立即失效。</span>
          </div>
        </Modal>
      )}

      {credentials && (
        <Modal
          title="应用凭证"
          subtitle={`${credentials.appId} · ${credentials.authMode}`}
          onClose={() => setCredentials(null)}
          footer={<button type="button" className="button button-primary" onClick={() => setCredentials(null)}>关闭</button>}
        >
          <div className="secret-panel">
            <div><span>AppId</span><code>{credentials.appId}</code><CopyButton value={credentials.appId} /></div>
            <div><span>AppSecret</span><code>{credentials.appSecret}</code><CopyButton value={credentials.appSecret} /></div>
          </div>
          <div className="warning-note">
            <Icon name="shield" size={16} />
            <span>请勿将密钥提交到代码仓库或前端页面；建议通过环境变量/密钥管理系统注入。</span>
          </div>
        </Modal>
      )}

      {rotateTarget && (
        <Modal
          title="轮换应用密钥"
          subtitle={`${rotateTarget.name} · ${rotateTarget.appId}`}
          onClose={() => setRotateTarget(null)}
          footer={
            <>
              <button type="button" className="button button-ghost" onClick={() => setRotateTarget(null)}>取消</button>
              <button type="button" className="button button-danger" onClick={rotate} disabled={rotating}>
                {rotating ? '轮换中…' : '确认轮换'}
              </button>
            </>
          }
        >
          <p className="prose">轮换后旧密钥立即失效，正在使用旧密钥的调用会返回 401。请确认已准备好更新调用方配置。</p>
        </Modal>
      )}
    </div>
  )
}
