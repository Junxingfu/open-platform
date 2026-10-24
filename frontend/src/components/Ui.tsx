import type { ReactNode } from 'react'
import { Icon } from './Icon'
import type { IconName } from './Icon'
import { useToast } from './Toast'

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <header className="page-header">
      <div>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1>{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}

export function Loading({ label = '加载中…' }: { label?: string }) {
  return (
    <div className="loading-panel">
      <span className="spinner" />
      <span>{label}</span>
    </div>
  )
}

export function EmptyState({
  title,
  description,
  action,
  icon = 'spark',
}: {
  title: string
  description: string
  action?: ReactNode
  icon?: 'spark' | 'apps' | 'book' | 'chart' | 'ticket'
}) {
  return (
    <div className="empty-state">
      <div className="empty-icon"><Icon name={icon} size={26} /></div>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  )
}

export function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
  }
  return <span className={`status status-${status.toLowerCase()}`}>{map[status] || status}</span>
}

export function MethodBadge({ method }: { method: string }) {
  return <span className={`method method-${method.toLowerCase()}`}>{method}</span>
}

export function CopyButton({ value, label = '复制' }: { value: string; label?: string }) {
  const { toast } = useToast()
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value)
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = value
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    toast('已复制到剪贴板')
  }
  return (
    <button type="button" className="copy-button" onClick={copy} title={label}>
      <Icon name="copy" size={14} />
      <span>{label}</span>
    </button>
  )
}

export function CodeBlock({ code, className = '' }: { code: string; className?: string }) {
  return (
    <div className={`code-block ${className}`}>
      <div className="code-toolbar">
        <span>示例</span>
        <CopyButton value={code} />
      </div>
      <pre><code>{code}</code></pre>
    </div>
  )
}

export function StatCard({
  label,
  value,
  hint,
  icon,
  tone = 'blue',
}: {
  label: string
  value: ReactNode
  hint?: string
  icon: IconName
  tone?: 'blue' | 'teal' | 'violet' | 'amber'
}) {
  return (
    <div className="stat-card">
      <div className={`stat-icon stat-${tone}`}><Icon name={icon} size={20} /></div>
      <div className="stat-copy">
        <span>{label}</span>
        <strong>{value}</strong>
        {hint && <small>{hint}</small>}
      </div>
    </div>
  )
}
