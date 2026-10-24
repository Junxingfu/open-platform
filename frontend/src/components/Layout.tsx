import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { Icon } from './Icon'
import type { IconName } from './Icon'

interface NavItem {
  to: string
  label: string
  icon: IconName
}

interface NavGroup {
  title: string
  items: NavItem[]
}

export function Layout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)

  const isAdmin = user?.role === 'ADMIN'

  const groups: NavGroup[] = [
    {
      title: '工作台',
      items: [{ to: '/dashboard', label: '概览', icon: 'dashboard' }],
    },
    {
      title: '接入',
      items: [
        { to: '/catalog', label: 'API 目录', icon: 'book' },
        { to: '/guide', label: '接入指南', icon: 'guide' },
      ],
    },
    {
      title: '管理',
      items: [
        { to: '/apps', label: isAdmin ? '应用管理' : '我的应用', icon: 'apps' },
        { to: '/requests', label: isAdmin ? '审批中心' : '接入申请', icon: 'ticket' },
      ],
    },
    {
      title: '运营',
      items: [{ to: '/usage', label: '调用统计', icon: 'chart' }],
    },
  ]

  useEffect(() => {
    setMobileOpen(false)
  }, [location.pathname])

  const meta = resolveMeta(location.pathname)
  const initials = (user?.name || 'OP').slice(0, 1)

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <div className="brand-mark">OP</div>
          <div className="brand-copy">
            <strong>开放平台</strong>
            <span>Developer Portal</span>
          </div>
          <button type="button" className="icon-button sidebar-close" onClick={() => setMobileOpen(false)}>
            <Icon name="close" size={18} />
          </button>
        </div>

        <div className="enterprise-chip">
          <span className="enterprise-dot" />
          <div>
            <strong>{user?.enterpriseName || '—'}</strong>
            <span>企业工作空间</span>
          </div>
        </div>

        <nav className="nav">
          {groups.map((group) => (
            <div className="nav-group" key={group.title}>
              <div className="nav-title">{group.title}</div>
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
                >
                  <Icon name={item.icon} size={18} />
                  <span>{item.label}</span>
                  {item.to === '/requests' && isAdmin && <span className="nav-dot" />}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="help-card">
            <div className="help-icon"><Icon name="doc" size={18} /></div>
            <strong>第一次接入？</strong>
            <p>按指南完成应用创建、产品申请和首次调用。</p>
            <button type="button" className="text-link" onClick={() => navigate('/guide')}>
              查看接入指南 <Icon name="arrowRight" size={14} />
            </button>
          </div>
          <div className="sidebar-user">
            <div className="avatar">{initials}</div>
            <div className="sidebar-user-copy">
              <strong>{user?.name}</strong>
              <span>{isAdmin ? '平台运营' : '企业开发者'}</span>
            </div>
            <button type="button" className="icon-button" title="退出登录" onClick={logout}>
              <Icon name="logout" size={17} />
            </button>
          </div>
        </div>
      </aside>

      {mobileOpen && <div className="sidebar-overlay" onClick={() => setMobileOpen(false)} />}

      <div className="main-shell">
        <header className="topbar">
          <div className="topbar-left">
            <button type="button" className="icon-button mobile-menu" onClick={() => setMobileOpen(true)}>
              <Icon name="menu" size={20} />
            </button>
            <div className="breadcrumb">
              <span>开放平台</span>
              <Icon name="chevron" size={13} />
              <strong>{meta.title}</strong>
            </div>
          </div>
          <div className="topbar-right">
            <span className="env-chip"><span className="pulse-dot" /> Sandbox 环境</span>
            <button type="button" className="icon-button notification-button" title="公告">
              <Icon name="bell" size={18} />
              <span className="notification-dot" />
            </button>
            <div className="topbar-user">
              <div className="avatar avatar-sm">{initials}</div>
              <div>
                <strong>{user?.name}</strong>
                <span>{isAdmin ? '平台运营' : '开发者'}</span>
              </div>
            </div>
          </div>
        </header>

        <main className="content">
          <div className="content-inner">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}

function resolveMeta(pathname: string): { title: string } {
  if (pathname.startsWith('/apis/')) return { title: 'API 详情' }
  const map: Record<string, string> = {
    '/dashboard': '概览',
    '/catalog': 'API 目录',
    '/apps': '应用管理',
    '/requests': '接入申请',
    '/usage': '调用统计',
    '/guide': '接入指南',
  }
  return { title: map[pathname] || '开放平台' }
}
