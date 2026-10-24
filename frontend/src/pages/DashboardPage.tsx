import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { formatDateTime, formatNumber } from '../api'
import { useApi } from '../hooks'
import type { DashboardView } from '../types'
import { Icon } from '../components/Icon'
import { EmptyState, Loading, PageHeader, StatCard } from '../components/Ui'

export function DashboardPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { data, loading, error, reload } = useApi<DashboardView>('/portal/api/dashboard')

  const hour = new Date().getHours()
  const greeting = hour < 6 ? '夜深了' : hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
  const isAdmin = user?.role === 'ADMIN'

  if (loading) return <Loading label="正在加载工作台…" />
  if (error || !data) {
    return (
      <div className="panel error-panel">
        <Icon name="alert" size={22} />
        <div><strong>工作台加载失败</strong><p>{error || '未知错误'}</p></div>
        <button type="button" className="button button-secondary" onClick={reload}>重试</button>
      </div>
    )
  }

  const stats = data.stats
  const doneSteps = data.onboarding.filter((step) => step.done).length
  const progress = Math.round((doneSteps / Math.max(data.onboarding.length, 1)) * 100)

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow={isAdmin ? 'PLATFORM OPERATIONS' : 'DEVELOPER WORKSPACE'}
        title={`${greeting}，${data.user.name}`}
        description={isAdmin
          ? '查看平台接入情况，处理企业提交的 API 产品申请。'
          : `欢迎回到 ${data.user.enterpriseName} 的开放平台工作台，继续你的接入旅程。`}
        actions={
          <>
            <button type="button" className="button button-secondary" onClick={() => navigate('/catalog')}>
              <Icon name="book" size={16} /> 浏览 API 目录
            </button>
            <button type="button" className="button button-primary" onClick={() => navigate('/apps')}>
              <Icon name="plus" size={16} /> 创建应用
            </button>
          </>
        }
      />

      {isAdmin && stats.pendingApprovalCount > 0 && (
        <div className="alert-banner">
          <div className="alert-banner-icon"><Icon name="ticket" size={20} /></div>
          <div>
            <strong>有 {stats.pendingApprovalCount} 条接入申请待审批</strong>
            <p>及时处理企业申请，帮助合作方尽快完成 API 接入。</p>
          </div>
          <button type="button" className="button button-dark" onClick={() => navigate('/requests')}>
            去审批 <Icon name="arrowRight" size={15} />
          </button>
        </div>
      )}

      <div className="stats-grid">
        <StatCard
          label={isAdmin ? '平台应用' : '我的应用'}
          value={stats.appCount}
          hint={isAdmin ? '全部企业应用' : '已创建的应用'}
          icon="apps"
          tone="blue"
        />
        <StatCard
          label="已开通产品"
          value={stats.approvedProductCount}
          hint={`目录共 ${stats.apiCount} 个 API`}
          icon="book"
          tone="teal"
        />
        <StatCard
          label="近 7 日调用"
          value={formatNumber(stats.calls7d)}
          hint={stats.calls7d > 0 ? `成功率 ${stats.successRate}%` : '完成首次调用后展示'}
          icon="chart"
          tone="violet"
        />
        <StatCard
          label={isAdmin ? '待审批申请' : '待处理申请'}
          value={isAdmin ? stats.pendingApprovalCount : stats.pendingRequestCount}
          hint={isAdmin ? '需要平台运营处理' : '审核中的接入申请'}
          icon="ticket"
          tone="amber"
        />
      </div>

      <div className="dashboard-grid">
        <section className="panel onboarding-panel">
          <div className="panel-head">
            <div>
              <div className="eyebrow">QUICK START</div>
              <h2>完成接入，只需三步</h2>
            </div>
            <div className="progress-ring" style={{ '--progress': `${progress}%` } as React.CSSProperties}>
              <span>{progress}%</span>
            </div>
          </div>
          <div className="step-list">
            {data.onboarding.map((step, index) => (
              <div className={`step-item ${step.done ? 'done' : ''}`} key={step.key}>
                <div className="step-index">
                  {step.done ? <Icon name="check" size={15} /> : index + 1}
                </div>
                <div className="step-copy">
                  <strong>{step.title}</strong>
                  <p>{step.description}</p>
                </div>
                <button type="button" className="step-action" onClick={() => navigate(step.action)}>
                  {step.done ? '查看' : '去完成'} <Icon name="chevron" size={14} />
                </button>
              </div>
            ))}
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <div>
              <div className="eyebrow">ANNOUNCEMENTS</div>
              <h2>平台公告</h2>
            </div>
            <Icon name="bell" size={18} className="muted-icon" />
          </div>
          <div className="announcement-list">
            {data.announcements.map((item) => (
              <article className="announcement" key={item.id}>
                <div className="announcement-meta">
                  <span className="tag">{item.tag}</span>
                  <span>{item.date}</span>
                </div>
                <strong>{item.title}</strong>
                <p>{item.content}</p>
              </article>
            ))}
          </div>
        </section>
      </div>

      <section className="panel">
        <div className="panel-head">
          <div>
            <div className="eyebrow">RECENT ACTIVITY</div>
            <h2>最近调用</h2>
          </div>
          <button type="button" className="text-link" onClick={() => navigate('/usage')}>
            查看全部统计 <Icon name="arrowRight" size={14} />
          </button>
        </div>

        {data.recentActivity.length === 0 ? (
          <EmptyState
            icon="chart"
            title="还没有调用记录"
            description="完成应用创建和产品申请后，按接入指南调用第一个 API，这里会展示实时调用情况。"
            action={<button type="button" className="button button-primary" onClick={() => navigate('/guide')}>查看接入指南</button>}
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>应用</th>
                  <th>API</th>
                  <th>状态</th>
                  <th className="align-right">耗时</th>
                  <th className="align-right">时间</th>
                </tr>
              </thead>
              <tbody>
                {data.recentActivity.map((item) => (
                  <tr key={item.id}>
                    <td><span className="cell-strong">{item.appName}</span></td>
                    <td>{item.apiName}</td>
                    <td>
                      <span className={`http-status ${item.status < 400 ? 'ok' : 'bad'}`}>
                        {item.status}
                      </span>
                    </td>
                    <td className="align-right">{item.costMs} ms</td>
                    <td className="align-right muted">{formatDateTime(item.at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
