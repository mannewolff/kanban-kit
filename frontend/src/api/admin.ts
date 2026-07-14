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

export const adminApi = {
  listUsers: () => apiFetch<AdminUser[]>('/api/admin/users'),
  setRole: (id: number, platformRole: PlatformRole) =>
    apiFetch<AdminUser>(`/api/admin/users/${id}`, { method: 'PATCH', body: JSON.stringify({ platformRole }) }),
  approve: (id: number) => apiFetch<AdminUser>(`/api/admin/users/${id}/approve`, { method: 'POST' }),
  disable: (id: number) => apiFetch<AdminUser>(`/api/admin/users/${id}/disable`, { method: 'POST' }),
  enable: (id: number) => apiFetch<AdminUser>(`/api/admin/users/${id}/enable`, { method: 'POST' }),
  bootstrap: (token: string) =>
    apiFetch<AdminUser>('/api/admin/bootstrap', { method: 'POST', body: JSON.stringify({ token }) }),
}

export type AdminApi = typeof adminApi
