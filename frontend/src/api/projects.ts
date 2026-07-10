import { apiFetch } from './client'

export interface Project {
  id: number
  name: string
  role: string
  createdAt: string
}

const json = (data: unknown, method: string): RequestInit => ({ method, body: JSON.stringify(data) })

export const projectsApi = {
  list: () => apiFetch<Project[]>('/api/projects'),
  create: (name: string, ownerEmail: string) =>
    apiFetch<Project>('/api/projects', json({ name, ownerEmail }, 'POST')),
  rename: (id: number, name: string) => apiFetch<Project>(`/api/projects/${id}`, json({ name }, 'PATCH')),
  remove: (id: number) => apiFetch<void>(`/api/projects/${id}`, { method: 'DELETE' }),
}
