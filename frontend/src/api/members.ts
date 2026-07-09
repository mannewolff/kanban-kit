import { apiFetch } from './client'
import type { ProjectRole } from '../lib/roles'

export interface Member {
  userId: number
  email: string
  displayName: string
  role: ProjectRole
}

const json = (data: unknown, method: string): RequestInit => ({ method, body: JSON.stringify(data) })

export const membersApi = {
  list: (projectId: number) => apiFetch<Member[]>(`/api/projects/${projectId}/members`),
  changeRole: (projectId: number, userId: number, role: ProjectRole) =>
    apiFetch<Member>(`/api/projects/${projectId}/members/${userId}`, json({ role }, 'PATCH')),
  remove: (projectId: number, userId: number) =>
    apiFetch<void>(`/api/projects/${projectId}/members/${userId}`, { method: 'DELETE' }),
  invite: (projectId: number, email: string, role: ProjectRole) =>
    apiFetch<void>(`/api/projects/${projectId}/invitations`, json({ email, role }, 'POST')),
  accept: (token: string) => apiFetch<Member>('/api/invitations/accept', json({ token }, 'POST')),
}

export type MembersApi = typeof membersApi
