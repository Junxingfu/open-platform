export type ApiEnvelope<T> = {
  code: string
  message: string
  requestId?: string
  data: T
}

export type Role = 'DEVELOPER' | 'ADMIN'

export interface User {
  id: string
  name: string
  email: string
  role: Role
  enterpriseId: string
  enterpriseName: string
}

export interface LoginResult {
  token: string
  user: User
}

export interface AppView {
  appId: string
  name: string
  description: string
  enterpriseId: string
  enterpriseName: string
  authMode: 'OAUTH2' | 'SIGNATURE'
  active: boolean
  createdAt: string
  secretMasked: string
  productCodes: string[]
  productNames: string[]
}

export interface AppCreatedView {
  app: AppView
  appSecret: string
}

export interface CredentialsView {
  appId: string
  appSecret: string
  authMode: string
}

export interface ProductView {
  code: string
  name: string
  description: string
  scopes: string[]
  apiCount: number
  active: boolean
  subscribedAppCount: number
  pendingRequestCount: number
}

export interface ApiSummary {
  apiId: string
  name: string
  method: string
  path: string
  summary: string
  productCode: string
  productName: string
  requiredScope: string
  category: string
  version: string
  authModes: string[]
  status: string
}

export interface HeaderDoc {
  name: string
  required: boolean
  description: string
  example: string
}

export interface ParamDoc {
  name: string
  location: string
  type: string
  required: boolean
  description: string
  example: string
}

export interface ErrorDoc {
  code: string
  message: string
  description: string
}

export interface ApiDetail {
  summary: ApiSummary
  description: string
  authModes: string[]
  rateLimit: string
  requestHeaders: HeaderDoc[]
  requestParams: ParamDoc[]
  requestExample: string
  responseExample: string
  errorCodes: ErrorDoc[]
  relatedApis: ApiSummary[]
}

export interface AccessRequestView {
  id: string
  enterpriseId: string
  enterpriseName: string
  appId: string
  appName: string
  productCode: string
  productName: string
  useCase: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdAt: string
  decidedAt: string | null
  reviewer: string | null
  reviewComment: string | null
}

export interface ActivityView {
  id: string
  appId: string
  appName: string
  apiId: string
  apiName: string
  status: number
  costMs: number
  at: string
}

export interface UsagePoint {
  date: string
  calls: number
  errors: number
}

export interface ApiUsage {
  apiId: string
  name: string
  calls: number
  errors: number
  avgLatencyMs: number
}

export interface UsageView {
  totalCalls: number
  successRate: number
  avgLatencyMs: number
  series: UsagePoint[]
  byApi: ApiUsage[]
  recent: ActivityView[]
}

export interface OnboardingStep {
  key: string
  title: string
  description: string
  done: boolean
  action: string
}

export interface Announcement {
  id: string
  title: string
  content: string
  date: string
  tag: string
}

export interface Stats {
  appCount: number
  approvedProductCount: number
  pendingRequestCount: number
  calls7d: number
  successRate: number
  avgLatencyMs: number
  apiCount: number
  pendingApprovalCount: number
}

export interface DashboardView {
  user: User
  stats: Stats
  onboarding: OnboardingStep[]
  recentActivity: ActivityView[]
  announcements: Announcement[]
}
