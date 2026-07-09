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
  update: (
    cardId: number,
    title: string,
    description: string | null,
    dependencies?: number[],
    shortcode?: string | null,
  ) =>
    apiFetch<Card>(`/api/cards/${cardId}`, {
      method: 'PATCH',
      body: JSON.stringify({ title, description, dependencies, shortcode }),
    }),
}

export type CardsApi = typeof cardsApi
