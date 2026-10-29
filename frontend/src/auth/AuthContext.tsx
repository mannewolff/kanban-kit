import { useColorScheme } from '@mui/material/styles'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, type Me, type Vorschau } from '../api/auth'
import { setUnauthorizedHandler, setVorschauKennung } from '../api/client'
import { farbmodusAus } from '../lib/farbmodus'
import { abmelden as aboAbmelden } from '../lib/pushAbo'

interface AuthState {
  user: Me | null
  /** Die laufende „Ansicht als XY“ dieser Anmeldung; `null` außerhalb (Plan #1914, E14). */
  vorschau: Vorschau | null
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

  // Die Kopfzeile `X-Vorschau` (E8) folgt dem Nutzer im selben Zug und nicht erst in einem Effekt:
  // Die Effekte der Kinder laufen vor denen des Providers, und ihre erste Anfrage nach einem Wechsel
  // trüge sonst noch die alte Kennung — der Server wiese sie mit `vorschau-gewechselt` ab.
  const uebernimm = useCallback((me: Me | null) => {
    setVorschauKennung(me?.vorschau?.kennung ?? null)
    setUser(me)
  }, [])

  const refresh = useCallback(async () => {
    try {
      uebernimm(await authApi.me())
    } catch {
      uebernimm(null)
    } finally {
      setLoading(false)
    }
  }, [uebernimm])

  useEffect(() => {
    void refresh()
  }, [refresh])

  // Das Erscheinungsbild folgt der Wahl am Konto (#1911). Ohne Nutzer — bis zur Selbstauskunft,
  // nach dem Abmelden oder nach einem 401 — gilt „System": Ein geteilter Rechner zeigt nicht die
  // Wahl des Vorgängers. In der Vorschau liefert /api/me statt der Wahl von XY die des Admins
  // (Issue #1921), der Admin behält so seine eigene (Plan #1914, E5).
  const { setMode } = useColorScheme()
  const colorScheme = user?.colorScheme
  useEffect(() => {
    setMode(farbmodusAus(colorScheme))
  }, [colorScheme, setMode])

  // Eine serverseitig beendete Sitzung meldet sich als 401 an einem beliebigen Endpunkt.
  // Der zurückgesetzte Nutzer lässt `ProtectedRoute` auf die Anmeldeseite umleiten.
  useEffect(() => {
    setUnauthorizedHandler(() => uebernimm(null))
    return () => setUnauthorizedHandler(null)
  }, [uebernimm])

  const loginWithCode = useCallback(
    async (email: string, code: string) => {
      uebernimm(await authApi.verifyCode(email, code))
    },
    [uebernimm],
  )

  const loginWithLink = useCallback(
    async (token: string) => {
      uebernimm(await authApi.linkLogin(token))
    },
    [uebernimm],
  )

  // Das Abo für Benachrichtigungen endet vor der Sitzung (Plan #1989, E14): Danach nähme der Server
  // das Löschen nicht mehr an. Ein Fehler dabei hält das Abmelden nicht auf.
  const logout = useCallback(async () => {
    try {
      await aboAbmelden()
    } catch {
      // Das Abo endet am Server spätestens mit der Sitzung.
    }
    await authApi.logout()
    uebernimm(null)
  }, [uebernimm])

  const vorschau = user?.vorschau ?? null
  const value = useMemo(
    () => ({ user, vorschau, loading, loginWithCode, loginWithLink, logout, refresh }),
    [user, vorschau, loading, loginWithCode, loginWithLink, logout, refresh],
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
