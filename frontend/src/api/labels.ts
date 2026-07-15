import { apiFetch } from './client'

export interface Label {
  id: number
  boardId: number
  name: string
  color: string
}

const json = (data: unknown, method: string): RequestInit => ({ method, body: JSON.stringify(data) })

export const labelsApi = {
  list: (boardId: number) => apiFetch<Label[]>(`/api/boards/${boardId}/labels`),
  create: (boardId: number, name: string, color: string) =>
    apiFetch<Label>(`/api/boards/${boardId}/labels`, json({ name, color }, 'POST')),
  update: (labelId: number, name: string, color: string) =>
    apiFetch<Label>(`/api/labels/${labelId}`, json({ name, color }, 'PATCH')),
  remove: (labelId: number) => apiFetch<void>(`/api/labels/${labelId}`, { method: 'DELETE' }),
}

export type LabelsApi = typeof labelsApi
