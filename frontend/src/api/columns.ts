import type { BoardColumn } from './boards'
import { apiFetch } from './client'

/** Spalten-Verwaltung eines Boards (anlegen, umbenennen/WIP-Limit, löschen, neu ordnen). */
export const columnsApi = {
  create: (boardId: number, name: string, wipLimit?: number | null) =>
    apiFetch<BoardColumn>(`/api/boards/${boardId}/columns`, {
      method: 'POST',
      body: JSON.stringify({ name, wipLimit }),
    }),
  // Name und WIP-Limit werden gemeinsam gesetzt (das Backend kombiniert beides).
  update: (columnId: number, name: string, wipLimit?: number | null) =>
    apiFetch<BoardColumn>(`/api/columns/${columnId}`, {
      method: 'PATCH',
      body: JSON.stringify({ name, wipLimit }),
    }),
  remove: (columnId: number) =>
    apiFetch<void>(`/api/columns/${columnId}`, { method: 'DELETE' }),
  reorder: (boardId: number, columnIds: number[]) =>
    apiFetch<BoardColumn[]>(`/api/boards/${boardId}/columns/order`, {
      method: 'PUT',
      body: JSON.stringify({ columnIds }),
    }),
}

export type ColumnsApi = typeof columnsApi
