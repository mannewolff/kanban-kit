import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, type Me } from '../api/auth'
import { setUnauthorizedHandler } from '../api/client'

interface AuthState {
  user: Me | null
  loading: boolean
  loginWithCode: (email: string, code: string) => Promise<void>
  loginWithLink: (token: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      setUser(await authApi.me())
    } catch {
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  // Eine serverseitig beendete Sitzung meldet sich als 401 an einem beliebigen Endpunkt.
  // Der zurückgesetzte Nutzer lässt `ProtectedRoute` auf die Anmeldeseite umleiten.
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    return () => setUnauthorizedHandler(null)
  }, [])

  const loginWithCode = useCallback(async (email: string, code: string) => {
    setUser(await authApi.verifyCode(email, code))
  }, [])

  const loginWithLink = useCallback(async (token: string) => {
    setUser(await authApi.linkLogin(token))
  }, [])

  const logout = useCallback(async () => {
    await authApi.logout()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, loading, loginWithCode, loginWithLink, logout, refresh }),
    [user, loading, loginWithCode, loginWithLink, logout, refresh],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth muss innerhalb von AuthProvider verwendet werden')
  }
  return context
}
