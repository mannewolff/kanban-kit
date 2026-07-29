import type { BoardColumn } from './boards'
import { apiFetch } from './client'

/** Richtung beim Sortieren einer Spalte nach Kartennummer. */
export type SortDirection = 'ASC' | 'DESC'

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
  // Die Richtung geht bei jedem Aufruf mit — das Backend merkt sich keinen Toggle-Zustand.
  sortByNumber: (columnId: number, direction: SortDirection) =>
    apiFetch<void>(`/api/columns/${columnId}/cards/sort-by-number`, {
      method: 'POST',
      body: JSON.stringify({ direction }),
    }),
}

export type ColumnsApi = typeof columnsApi
