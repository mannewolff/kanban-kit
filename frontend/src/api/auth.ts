import { apiFetch, ApiError } from './client'

export interface Membership {
  projectId: number
  role: string
}

/** Gewähltes Erscheinungsbild am Konto (#1911): SYSTEM folgt Browser bzw. Betriebssystem. */
export type ColorScheme = 'SYSTEM' | 'LIGHT' | 'DARK'

const COLOR_SCHEMES: ReadonlyArray<string> = ['SYSTEM', 'LIGHT', 'DARK'] satisfies ColorScheme[]

export interface Me {
  userId: number
  email: string
  displayName: string
  platformRole: string
  memberships: Membership[]
  colorScheme: ColorScheme
}

const jsonBody = (data: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(data) })

/** Laufzeit-Verengung der sicherheitsrelevanten Me-Antwort (Autorisierungsquelle). */
function isMe(data: unknown): data is Omit<Me, 'colorScheme'> & { colorScheme?: unknown } {
  if (typeof data !== 'object' || data === null) return false
  const d = data as Record<string, unknown>
  return (
    typeof d.userId === 'number' &&
    typeof d.email === 'string' &&
    typeof d.displayName === 'string' &&
    typeof d.platformRole === 'string' &&
    Array.isArray(d.memberships)
  )
}

function parseMe(data: unknown): Me {
  if (!isMe(data)) {
    throw new ApiError(500, 'Unerwartete Antwort vom Server (Me).')
  }
  // Die Farbwahl ist keine Autorisierungsquelle: Fehlt sie oder ist sie unbekannt, gilt „System".
  const colorScheme = COLOR_SCHEMES.includes(String(data.colorScheme)) ? (data.colorScheme as ColorScheme) : 'SYSTEM'
  return { ...data, colorScheme }
}

export const authApi = {
  me: () => apiFetch<Me>('/api/me', {}, parseMe),
  updateProfile: (displayName: string) =>
    apiFetch<Me>('/api/me', { method: 'PATCH', body: JSON.stringify({ displayName }) }, parseMe),
  setColorScheme: (colorScheme: ColorScheme) =>
    apiFetch<Me>('/api/me/color-scheme', { method: 'PUT', body: JSON.stringify({ colorScheme }) }, parseMe),
  // Anmeldung per Einmalcode (Plan #1801): anfordern, abgetippten Code oder Link aus der Mail einlösen.
  requestCode: (email: string) => apiFetch<void>('/api/auth/code/request', jsonBody({ email })),
  verifyCode: (email: string, code: string) =>
    apiFetch<Me>('/api/auth/code/verify', jsonBody({ email, code }), parseMe),
  linkLogin: (token: string) => apiFetch<Me>('/api/auth/code/link', jsonBody({ token }), parseMe),
  logout: () => apiFetch<void>('/api/auth/logout', { method: 'POST' }),
  register: (email: string, displayName: string) =>
    apiFetch<{ id: number; email: string }>('/api/auth/register', jsonBody({ email, displayName })),
}
