import { apiFetch } from './client'

export type PlatformRole = 'ADMIN' | 'USER'

export interface AdminUser {
  id: number
  email: string
  displayName: string
  platformRole: PlatformRole
  emailVerified: boolean
  approvedAt: string | null
}

export const adminApi = {
  listUsers: () => apiFetch<AdminUser[]>('/api/admin/users'),
  setRole: (id: number, platformRole: PlatformRole) =>
    apiFetch<AdminUser>(`/api/admin/users/${id}`, { method: 'PATCH', body: JSON.stringify({ platformRole }) }),
  approve: (id: number) => apiFetch<AdminUser>(`/api/admin/users/${id}/approve`, { method: 'POST' }),
  bootstrap: (token: string) =>
    apiFetch<AdminUser>('/api/admin/bootstrap', { method: 'POST', body: JSON.stringify({ token }) }),
}

export type AdminApi = typeof adminApi
