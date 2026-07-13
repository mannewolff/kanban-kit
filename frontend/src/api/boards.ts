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
  listArchived: (projectId: number) =>
    apiFetch<Board[]>(`/api/projects/${projectId}/boards/archived`),
  get: (boardId: number) => apiFetch<Board>(`/api/boards/${boardId}`),
  create: (projectId: number, name: string) =>
    apiFetch<Board>(`/api/projects/${projectId}/boards`, { method: 'POST', body: JSON.stringify({ name }) }),
  // Löschen archiviert das Board (reversibel).
  remove: (boardId: number) => apiFetch<void>(`/api/boards/${boardId}`, { method: 'DELETE' }),
  restore: (boardId: number) =>
    apiFetch<Board>(`/api/boards/${boardId}/restore`, { method: 'POST' }),
  // Endgültiges, unwiderrufliches Löschen eines bereits archivierten Boards.
  purge: (boardId: number) => apiFetch<void>(`/api/boards/${boardId}/purge`, { method: 'DELETE' }),
}
