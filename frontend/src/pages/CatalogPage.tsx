import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useApi } from '../hooks'
import type { ApiSummary, ProductView } from '../types'
import { Icon } from '../components/Icon'
import { EmptyState, Loading, MethodBadge, PageHeader } from '../components/Ui'

export function CatalogPage() {
  const [keyword, setKeyword] = useState('')
  const [method, setMethod] = useState('ALL')
  const [product, setProduct] = useState('ALL')

  const query = useMemo(() => {
    const params = new URLSearchParams()
    if (keyword.trim()) params.set('keyword', keyword.trim())
    if (method !== 'ALL') params.set('method', method)
    if (product !== 'ALL') params.set('product', product)
    const text = params.toString()
    return text ? `?${text}` : ''
  }, [keyword, method, product])

  const { data, loading, error, reload } = useApi<ApiSummary[]>(`/portal/api/apis${query}`)
  const { data: products } = useApi<ProductView[]>('/portal/api/products')

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="API CATALOG"
        title="API 目录"
        description="浏览平台开放的 API 能力，查看接口文档、认证方式与接入要求。"
        actions={
          <Link to="/guide" className="button button-secondary">
            <Icon name="guide" size={16} /> 接入指南
          </Link>
        }
      />

      <div className="catalog-hero">
        <div>
          <span className="login-kicker"><span className="pulse-dot" /> Sandbox 沙箱可用</span>
          <h2>找到适合业务场景的 API，快速完成接入。</h2>
          <p>所有接口均支持 OAuth2 与 HMAC 签名两种认证方式，按 API 产品统一授权。</p>
        </div>
        <div className="catalog-hero-stats">
          <div><strong>{data?.length ?? '—'}</strong><span>可发现接口</span></div>
          <div><strong>{products?.length ?? '—'}</strong><span>API 产品</span></div>
        </div>
      </div>

      <div className="filter-bar">
        <div className="search-box">
          <Icon name="search" size={17} />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索 API 名称、路径或能力，例如：企业核验"
          />
        </div>
        <div className="method-tabs">
          {['ALL', 'GET', 'POST'].map((item) => (
            <button
              type="button"
              key={item}
              className={method === item ? 'active' : ''}
              onClick={() => setMethod(item)}
            >
              {item === 'ALL' ? '全部方法' : item}
            </button>
          ))}
        </div>
        <select value={product} onChange={(event) => setProduct(event.target.value)} className="select">
          <option value="ALL">全部产品</option>
          {products?.map((item) => (
            <option value={item.code} key={item.code}>{item.name}</option>
          ))}
        </select>
      </div>

      {loading && <Loading label="正在加载 API 目录…" />}
      {error && (
        <div className="panel error-panel">
          <Icon name="alert" size={20} />
          <div><strong>目录加载失败</strong><p>{error}</p></div>
          <button type="button" className="button button-secondary" onClick={reload}>重试</button>
        </div>
      )}

      {!loading && !error && data && (
        <>
          <div className="result-line">
            <span>共 <strong>{data.length}</strong> 个接口</span>
            {(keyword || method !== 'ALL' || product !== 'ALL') && (
              <button type="button" className="text-link" onClick={() => { setKeyword(''); setMethod('ALL'); setProduct('ALL') }}>
                清除筛选
              </button>
            )}
          </div>

          {data.length === 0 ? (
            <EmptyState
              icon="book"
              title="没有找到匹配的 API"
              description="尝试更换关键字或清空筛选条件；如果仍找不到，可能需要先在门户申请对应 API 产品。"
            />
          ) : (
            <div className="api-grid">
              {data.map((item) => (
                <Link to={`/apis/${item.apiId}`} className="api-card" key={item.apiId}>
                  <div className="api-card-top">
                    <MethodBadge method={item.method} />
                    <span className="api-version">{item.version}</span>
                  </div>
                  <h3>{item.name}</h3>
                  <p className="api-summary">{item.summary}</p>
                  <code className="api-path">{item.path}</code>
                  <div className="api-meta">
                    <span className="tag tag-soft">{item.productName}</span>
                    <span className="tag tag-outline">{item.requiredScope}</span>
                  </div>
                  <div className="api-card-foot">
                    <span>{item.category}</span>
                    <span className="api-link">查看文档 <Icon name="arrowRight" size={14} /></span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
