import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import { ToastProvider } from './components/Toast'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'
import { CatalogPage } from './pages/CatalogPage'
import { ApiDetailPage } from './pages/ApiDetailPage'
import { AppsPage } from './pages/AppsPage'
import { RequestsPage } from './pages/RequestsPage'
import { UsagePage } from './pages/UsagePage'
import { GuidePage } from './pages/GuidePage'
import { NotFoundPage } from './pages/NotFoundPage'

function ProtectedLayout() {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <div className="boot-screen">
        <div className="brand-mark brand-mark-lg">OP</div>
        <span className="spinner" />
        <p>正在加载开发者控制台…</p>
      </div>
    )
  }
  if (!user) return <Navigate to="/login" replace />
  return <Layout />
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedLayout />}>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/catalog" element={<CatalogPage />} />
              <Route path="/apis/:apiId" element={<ApiDetailPage />} />
              <Route path="/apps" element={<AppsPage />} />
              <Route path="/requests" element={<RequestsPage />} />
              <Route path="/usage" element={<UsagePage />} />
              <Route path="/guide" element={<GuidePage />} />
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
