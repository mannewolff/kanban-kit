import { apiFetch } from './client'

export type CardType = 'CARD' | 'EPIC'

export interface Card {
  id: number
  boardId: number
  columnId: number
  number: number
  title: string
  description: string | null
  positionInColumn: number
  archived: boolean
  movedToDoneAt: string | null
  dependencies: number[]
  type: CardType
  parentId: number | null
  shortcode: string | null
  assignees: number[]
}

export const cardsApi = {
  list: (boardId: number) => apiFetch<Card[]>(`/api/boards/${boardId}/cards`),
  create: (boardId: number, columnId: number, title: string, description?: string, parentId?: number | null) =>
    apiFetch<Card>(`/api/boards/${boardId}/cards`, {
      method: 'POST',
      body: JSON.stringify({ columnId, title, description, parentId }),
    }),
  move: (cardId: number, columnId: number, position: number) =>
    apiFetch<Card>(`/api/cards/${cardId}/move`, { method: 'POST', body: JSON.stringify({ columnId, position }) }),
  transfer: (cardId: number, targetBoardId: number, targetColumnId: number) =>
    apiFetch<Card>(`/api/cards/${cardId}/transfer`, {
      method: 'POST',
      body: JSON.stringify({ targetBoardId, targetColumnId }),
    }),
  setAssignees: (cardId: number, assignees: number[]) =>
    apiFetch<Card>(`/api/cards/${cardId}/assignees`, {
      method: 'PUT',
      body: JSON.stringify({ assignees }),
    }),
  archive: (cardId: number) => apiFetch<Card>(`/api/cards/${cardId}/archive`, { method: 'POST' }),
  restore: (cardId: number) => apiFetch<Card>(`/api/cards/${cardId}/restore`, { method: 'POST' }),
  remove: (cardId: number) => apiFetch<void>(`/api/cards/${cardId}`, { method: 'DELETE' }),
  update: (
    cardId: number,
    title: string,
    description: string | null,
    dependencies?: number[],
    shortcode?: string | null,
    parentId?: number | null,
  ) =>
    apiFetch<Card>(`/api/cards/${cardId}`, {
      method: 'PATCH',
      body: JSON.stringify({ title, description, dependencies, shortcode, parentId }),
    }),
}

export type CardsApi = typeof cardsApi
