import { apiFetch } from './client'

export type PlatformRole = 'ADMIN' | 'USER'

export interface AdminUser {
  id: number
  email: string
  displayName: string
  platformRole: PlatformRole
  emailVerified: boolean
  approvedAt: string | null
  disabled: boolean
}

/** Abweisungen wegen Last einer Person in einer vollen Stunde (Issue #1003). */
export interface OverloadRejection {
  userId: number
  displayName: string
  /** Beginn der vollen Stunde, ISO-8601 in UTC. */
  hour: string
  rejections: number
}

export const adminApi = {
  listUsers: () => apiFetch<AdminUser[]>('/api/admin/users'),
  setRole: (id: number, platformRole: PlatformRole) =>
    apiFetch<AdminUser>(`/api/admin/users/${id}`, { method: 'PATCH', body: JSON.stringify({ platformRole }) }),
  setDisplayName: (id: number, displayName: string) =>
    apiFetch<AdminUser>(`/api/admin/users/${id}/display-name`, {
      method: 'PATCH',
      body: JSON.stringify({ displayName }),
    }),
  approve: (id: number) => apiFetch<AdminUser>(`/api/admin/users/${id}/approve`, { method: 'POST' }),
  disable: (id: number) => apiFetch<AdminUser>(`/api/admin/users/${id}/disable`, { method: 'POST' }),
  enable: (id: number) => apiFetch<AdminUser>(`/api/admin/users/${id}/enable`, { method: 'POST' }),
  listOverloadRejections: () => apiFetch<OverloadRejection[]>('/api/admin/overload-rejections'),
  bootstrap: (token: string) =>
    apiFetch<AdminUser>('/api/admin/bootstrap', { method: 'POST', body: JSON.stringify({ token }) }),
}

export type AdminApi = typeof adminApi
