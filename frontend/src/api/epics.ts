import { apiFetch } from './client'
import type { Card } from './cards'

export interface Epic {
  id: number
  number: number
  title: string
  description: string | null
  shortcode: string | null
  done: number
  total: number
}

export const epicsApi = {
  list: (boardId: number) => apiFetch<Epic[]>(`/api/boards/${boardId}/epics`),
  create: (boardId: number, title: string, description: string, shortcode: string | null) =>
    apiFetch<Card>(`/api/boards/${boardId}/cards`, {
      method: 'POST',
      body: JSON.stringify({ type: 'EPIC', title, description, shortcode }),
    }),
  /** Ordnet eine Karte einem Epic zu (parentId) oder löst die Zuordnung (null). */
  assign: (cardId: number, parentId: number | null) =>
    apiFetch<Card>(`/api/cards/${cardId}/parent`, { method: 'PATCH', body: JSON.stringify({ parentId }) }),
  remove: (id: number) => apiFetch<void>(`/api/cards/${id}`, { method: 'DELETE' }),
}

export type EpicsApi = typeof epicsApi
