import { apiFetch } from './client'

export interface BoardColumn {
  id: number
  name: string
  position: number
  wipLimit: number | null
}

export interface Board {
  id: number
  projectId: number
  name: string
  createdAt: string
  columns: BoardColumn[]
}

export const boardsApi = {
  list: (projectId: number) => apiFetch<Board[]>(`/api/projects/${projectId}/boards`),
  get: (boardId: number) => apiFetch<Board>(`/api/boards/${boardId}`),
  create: (projectId: number, name: string) =>
    apiFetch<Board>(`/api/projects/${projectId}/boards`, { method: 'POST', body: JSON.stringify({ name }) }),
}
