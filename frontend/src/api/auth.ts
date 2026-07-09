import { apiFetch } from './client'

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

export const authApi = {
  me: () => apiFetch<Me>('/api/me'),
  login: (email: string, password: string) => apiFetch<Me>('/api/auth/login', jsonBody({ email, password })),
  logout: () => apiFetch<void>('/api/auth/logout', { method: 'POST' }),
  register: (email: string, password: string, displayName: string) =>
    apiFetch<{ id: number; email: string }>('/api/auth/register', jsonBody({ email, password, displayName })),
  verify: (token: string) => apiFetch<void>(`/api/auth/verify?token=${encodeURIComponent(token)}`),
  forgot: (email: string) => apiFetch<void>('/api/auth/forgot', jsonBody({ email })),
  reset: (token: string, newPassword: string) => apiFetch<void>('/api/auth/reset', jsonBody({ token, newPassword })),
}
