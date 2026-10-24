import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from '../components/Icon'
import { CodeBlock, PageHeader } from '../components/Ui'

const oauthTokenExample = `curl -s -X POST http://127.0.0.1:8081/open/oauth2/token \\
  -H 'Content-Type: application/x-www-form-urlencoded' \\
  -d 'grant_type=client_credentials' \\
  -d 'client_id=你的AppId' \\
  -d 'client_secret=你的AppSecret' \\
  -d 'scope=enterprise.read'`

const oauthCallExample = `curl -s -X POST http://127.0.0.1:8081/open/v1/enterprise/verify \\
  -H "Authorization: Bearer <access_token>" \\
  -H 'Content-Type: application/json' \\
  -d '{"enterpriseName":"示例科技有限公司","creditCode":"91310000MA1K1234XX"}'`

const oauthResponseExample = `{
  "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImxvY2FsLWRldiJ9...",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "enterprise.read"
}`

const signatureExample = `#!/usr/bin/env python3
import base64, hashlib, hmac, json, time, uuid

APP_ID = "你的AppId"
APP_SECRET = "你的AppSecret"
PATH = "/open/v1/enterprise/verify"
body = json.dumps(
    {"enterpriseName": "示例科技有限公司", "creditCode": "91310000MA1K1234XX"},
    ensure_ascii=False, separators=(",", ":"),
).encode("utf-8")

timestamp = str(int(time.time() * 1000))
nonce = uuid.uuid4().hex
content_sha256 = hashlib.sha256(body).hexdigest()
canonical = "\\n".join(["POST", PATH, "", timestamp, nonce, content_sha256])
signature = base64.b64encode(
    hmac.new(APP_SECRET.encode(), canonical.encode(), hashlib.sha256).digest()
).decode()

# 请求头：X-App-Id / X-Timestamp / X-Nonce /
#        X-Signature-Method: HMAC-SHA256 / X-Signature / Content-SHA256`

const canonicalExample = `POST
/open/v1/enterprise/verify
<排序后的 query，无则为空行>
1793851200000
9f2c7c1d5e6a4b8c9d0e1f2a3b4c5d6e
e3b0c44298fc1c149afbf4c8996fb924...`

const errorRows = [
  ['40000', 'parameter invalid', '参数缺失或格式错误'],
  ['40100', 'unauthorized', '未携带认证凭证'],
  ['40101', 'token invalid', 'token 无效、过期或被撤销'],
  ['40102', 'signature invalid', '签名或请求体摘要不一致'],
  ['40103', 'timestamp invalid', '时间戳超出允许窗口'],
  ['40104', 'nonce replayed', 'nonce 重复（重放请求）'],
  ['40300', 'forbidden', '应用/企业停用、未订阅产品或 scope 不足'],
  ['40400', 'api not found', '接口未登记或路径错误'],
]

export function GuidePage() {
  const [tab, setTab] = useState<'oauth' | 'signature'>('oauth')

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="INTEGRATION GUIDE"
        title="接入指南"
        description="从创建应用、开通产品到完成第一次调用。按步骤操作，约 10 分钟即可跑通。"
        actions={<Link to="/catalog" className="button button-secondary"><Icon name="book" size={16} /> 浏览 API 目录</Link>}
      />

      <div className="guide-layout">
        <aside className="guide-steps">
          <div className="guide-steps-title">接入流程</div>
          {[
            ['01', '创建应用', '在“我的应用”创建应用，选择 OAuth2 或 HMAC 认证。'],
            ['02', '申请产品', '按“应用 + API 产品”提交接入申请。'],
            ['03', '等待审批', '平台运营审核后自动开通订阅与 scope。'],
            ['04', '发起调用', '换取 token 或计算签名，调用开放 API。'],
            ['05', '查看统计', '在“调用统计”观察成功率与耗时。'],
          ].map(([no, title, desc]) => (
            <div className="guide-step" key={no}>
              <span>{no}</span>
              <div><strong>{title}</strong><p>{desc}</p></div>
            </div>
          ))}
          <div className="guide-tip">
            <Icon name="shield" size={17} />
            <p>AppSecret 等同于密码，请勿写入前端代码或提交到 Git。建议使用环境变量或密钥管理服务。</p>
          </div>
        </aside>

        <div className="page-stack">
          <section className="panel">
            <div className="section-head">
              <div><div className="eyebrow">AUTHENTICATION</div><h2>选择认证方式</h2></div>
            </div>
            <div className="guide-tabs">
              <button type="button" className={tab === 'oauth' ? 'active' : ''} onClick={() => setTab('oauth')}>
                OAuth2 客户端凭证
              </button>
              <button type="button" className={tab === 'signature' ? 'active' : ''} onClick={() => setTab('signature')}>
                AppKey + HMAC 签名
              </button>
            </div>

            {tab === 'oauth' ? (
              <div className="guide-content">
                <p className="prose">
                  应用使用 <code>client_id</code> + <code>client_secret</code> 换取有效期 15 分钟的 JWT，
                  然后在请求头携带 <code>Authorization: Bearer &lt;token&gt;</code>。适合标准服务端到服务端调用。
                </p>
                <h3 className="subhead">1. 获取 access_token</h3>
                <CodeBlock code={oauthTokenExample} />
                <h3 className="subhead">2. 携带 token 调用业务接口</h3>
                <CodeBlock code={oauthCallExample} />
                <h3 className="subhead">3. token 响应示例</h3>
                <CodeBlock code={oauthResponseExample} />
              </div>
            ) : (
              <div className="guide-content">
                <p className="prose">
                  每个请求携带 <code>X-App-Id</code>、<code>X-Timestamp</code>、<code>X-Nonce</code>、
                  <code>X-Signature-Method</code>、<code>X-Signature</code>、<code>Content-SHA256</code>。
                  服务端用 AppSecret 重算 HMAC-SHA256 并做常量时间比较。
                </p>
                <h3 className="subhead">待签名串</h3>
                <CodeBlock code={canonicalExample} />
                <h3 className="subhead">Python 示例</h3>
                <CodeBlock code={signatureExample} />
                <div className="warning-note">
                  <Icon name="alert" size={16} />
                  <span>签名模式下请求体不能是表单（application/x-www-form-urlencoded）；请使用 JSON 请求体。</span>
                </div>
              </div>
            )}
          </section>

          <section className="panel">
            <div className="section-head">
              <div><div className="eyebrow">ENDPOINTS</div><h2>开放 API 一览</h2></div>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead><tr><th>方法</th><th>路径</th><th>说明</th><th>所需 scope</th></tr></thead>
                <tbody>
                  <tr><td><span className="method method-post">POST</span></td><td><code>/open/v1/enterprise/verify</code></td><td>企业信息核验</td><td><code>enterprise.read</code></td></tr>
                  <tr><td><span className="method method-get">GET</span></td><td><code>/open/v1/enterprise/basic</code></td><td>企业基础信息</td><td><code>enterprise.read</code></td></tr>
                  <tr><td><span className="method method-post">POST</span></td><td><code>/open/v1/enterprise/risk-check</code></td><td>企业风险核验</td><td><code>enterprise.risk</code></td></tr>
                </tbody>
              </table>
            </div>
          </section>

          <section className="panel">
            <div className="section-head">
              <div><div className="eyebrow">ERRORS</div><h2>常见错误码</h2></div>
            </div>
            <div className="table-wrap">
              <table className="data-table">
                <thead><tr><th>错误码</th><th>标识</th><th>说明</th></tr></thead>
                <tbody>
                  {errorRows.map(([code, name, desc]) => (
                    <tr key={code}><td><code>{code}</code></td><td>{name}</td><td>{desc}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="panel callout-panel">
            <div className="callout-icon"><Icon name="spark" size={22} /></div>
            <div>
              <h2>下一步：申请 API 产品</h2>
              <p>选择应用和产品提交接入申请，审批通过后即可获得 scope 并开始调用。</p>
            </div>
            <Link to="/requests" className="button button-primary">去申请接入 <Icon name="arrowRight" size={15} /></Link>
          </section>
        </div>
      </div>
    </div>
  )
}
