import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { Icon } from '../components/Icon'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('dev@demo.com')
  const [password, setPassword] = useState('Demo@1234')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (user) navigate('/dashboard', { replace: true })
  }, [user, navigate])

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(email.trim(), password)
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  const fill = (nextEmail: string, nextPassword: string) => {
    setEmail(nextEmail)
    setPassword(nextPassword)
    setError('')
  }

  return (
    <div className="login-page">
      <div className="login-visual">
        <div className="login-grid" />
        <div className="login-orb login-orb-a" />
        <div className="login-orb login-orb-b" />

        <div className="login-brand">
          <div className="brand-mark brand-mark-lg">OP</div>
          <div>
            <strong>开放平台</strong>
            <span>Open Platform · Developer Portal</span>
          </div>
        </div>

        <div className="login-hero">
          <span className="login-kicker"><span className="pulse-dot" /> 企业级 API 接入控制台</span>
          <h1>让企业接入，<br />像调用一个接口一样简单。</h1>
          <p>
            在一个工作台里完成 API 发现、文档阅读、应用创建、产品申请与调用监控。
            用标准化的方式把合作方接进来。
          </p>
          <div className="login-points">
            <div>
              <span className="login-point-icon"><Icon name="book" size={17} /></span>
              <div><strong>统一 API 目录</strong><span>接口文档、参数、示例、错误码</span></div>
            </div>
            <div>
              <span className="login-point-icon"><Icon name="key" size={17} /></span>
              <div><strong>应用与凭证</strong><span>OAuth2 / HMAC 双认证模式</span></div>
            </div>
            <div>
              <span className="login-point-icon"><Icon name="shield" size={17} /></span>
              <div><strong>申请与审批</strong><span>按 API 产品开通，权限边界清晰</span></div>
            </div>
          </div>
        </div>

        <div className="login-footer">
          <span>© 2026 开放平台运营中心</span>
          <span>Sandbox · v0.1 MVP</span>
        </div>
      </div>

      <div className="login-form-side">
        <div className="login-card">
          <div className="login-card-head">
            <span className="eyebrow">WELCOME BACK</span>
            <h2>登录开发者控制台</h2>
            <p>使用企业账号登录，管理应用与 API 接入。</p>
          </div>

          <form onSubmit={submit} className="login-form">
            <label>
              <span>邮箱</span>
              <div className="input-with-icon">
                <Icon name="user" size={17} />
                <input
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="you@company.com"
                  autoComplete="username"
                  required
                />
              </div>
            </label>
            <label>
              <span>密码</span>
              <div className="input-with-icon">
                <Icon name="lock" size={17} />
                <input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="请输入密码"
                  autoComplete="current-password"
                  required
                />
              </div>
            </label>

            {error && (
              <div className="form-error">
                <Icon name="alert" size={15} />
                <span>{error}</span>
              </div>
            )}

            <button type="submit" className="button button-primary button-lg" disabled={loading}>
              {loading ? '登录中…' : '登录'}
              {!loading && <Icon name="arrowRight" size={16} />}
            </button>
          </form>

          <div className="demo-box">
            <div className="demo-box-head">
              <span>演示账号</span>
              <span className="demo-hint">点击快速填充</span>
            </div>
            <button type="button" className="demo-account" onClick={() => fill('dev@demo.com', 'Demo@1234')}>
              <span className="demo-avatar">张</span>
              <span><strong>企业开发者</strong><small>dev@demo.com / Demo@1234</small></span>
              <Icon name="chevron" size={15} />
            </button>
            <button type="button" className="demo-account" onClick={() => fill('admin@demo.com', 'Admin@1234')}>
              <span className="demo-avatar demo-avatar-admin">李</span>
              <span><strong>平台运营</strong><small>admin@demo.com / Admin@1234</small></span>
              <Icon name="chevron" size={15} />
            </button>
          </div>

          <p className="login-note">
            登录即表示同意平台接入规范与数据安全约定。演示环境数据每次重启会重置。
          </p>
        </div>
      </div>
    </div>
  )
}
