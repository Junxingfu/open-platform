import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, clearToken, getToken, setToken } from './api'
import type { LoginResult, User } from './types'

interface AuthState {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<User>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState<boolean>(Boolean(getToken()))

  useEffect(() => {
    if (!getToken()) {
      setLoading(false)
      return
    }
    api<User>('/portal/api/auth/me')
      .then(setUser)
      .catch(() => {
        clearToken()
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const onUnauthorized = () => {
      clearToken()
      setUser(null)
      setLoading(false)
    }
    window.addEventListener('portal:unauthorized', onUnauthorized)
    return () => window.removeEventListener('portal:unauthorized', onUnauthorized)
  }, [])

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    login: async (email, password) => {
      const result = await api<LoginResult>('/portal/api/auth/login', {
        method: 'POST',
        body: { email, password },
      })
      setToken(result.token)
      setUser(result.user)
      setLoading(false)
      return result.user
    },
    logout: () => {
      clearToken()
      setUser(null)
      setLoading(false)
      api('/portal/api/auth/logout', { method: 'POST' }).catch(() => undefined)
    },
  }), [user, loading])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
