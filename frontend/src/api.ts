const TOKEN_KEY = 'op_portal_token'

export class ApiError extends Error {
  status: number
  code: string

  constructor(message: string, status: number, code = '') {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown }

/**
 * 统一请求封装：
 * - 自动带门户 token；
 * - 自动处理 JSON 序列化；
 * - 把后端 ApiResponse.code != "0" 转成 ApiError；
 * - 401 时清理 token 并通知 AuthProvider 跳转登录。
 */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  let body: BodyInit | undefined

  if (options.body !== undefined) {
    if (options.body instanceof FormData || typeof options.body === 'string') {
      body = options.body as BodyInit
    } else {
      headers.set('Content-Type', 'application/json')
      body = JSON.stringify(options.body)
    }
  }

  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response: Response
  try {
    response = await fetch(path, { ...options, headers, body })
  } catch {
    throw new ApiError('无法连接后端服务，请确认 8081 端口已启动', 0)
  }

  const text = await response.text()
  let payload: { code?: string; message?: string; data?: T } = {}
  try {
    payload = text ? JSON.parse(text) : {}
  } catch {
    throw new ApiError('服务端返回了非 JSON 内容', response.status)
  }

  if (response.status === 401) {
    clearToken()
    window.dispatchEvent(new Event('portal:unauthorized'))
  }

  if (!response.ok || payload.code !== '0') {
    throw new ApiError(payload.message || `请求失败（HTTP ${response.status}）`, response.status, payload.code)
  }

  return payload.data as T
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatDate(value?: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString('zh-CN')
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat('zh-CN').format(value)
}
