import { apiFetch, ApiError } from './client'

export interface Membership {
  projectId: number
  role: string
}

export interface Me {
  userId: number
  email: string
  displayName: string
  platformRole: string
  memberships: Membership[]
}

const jsonBody = (data: unknown): RequestInit => ({ method: 'POST', body: JSON.stringify(data) })

/** Laufzeit-Verengung der sicherheitsrelevanten Me-Antwort (Autorisierungsquelle). */
function isMe(data: unknown): data is Me {
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
  return data
}

export const authApi = {
  me: () => apiFetch<Me>('/api/me', {}, parseMe),
  updateProfile: (displayName: string) =>
    apiFetch<Me>('/api/me', { method: 'PATCH', body: JSON.stringify({ displayName }) }, parseMe),
  login: (email: string, password: string) =>
    apiFetch<Me>('/api/auth/login', jsonBody({ email, password }), parseMe),
  logout: () => apiFetch<void>('/api/auth/logout', { method: 'POST' }),
  register: (email: string, password: string, displayName: string) =>
    apiFetch<{ id: number; email: string }>('/api/auth/register', jsonBody({ email, password, displayName })),
  verify: (token: string) => apiFetch<void>(`/api/auth/verify?token=${encodeURIComponent(token)}`),
  forgot: (email: string) => apiFetch<void>('/api/auth/forgot', jsonBody({ email })),
  reset: (token: string, newPassword: string) => apiFetch<void>('/api/auth/reset', jsonBody({ token, newPassword })),
}
