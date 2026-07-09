import { apiFetch } from './client'

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
}

export const cardsApi = {
  list: (boardId: number) => apiFetch<Card[]>(`/api/boards/${boardId}/cards`),
  create: (boardId: number, columnId: number, title: string) =>
    apiFetch<Card>(`/api/boards/${boardId}/cards`, { method: 'POST', body: JSON.stringify({ columnId, title }) }),
  move: (cardId: number, columnId: number, position: number) =>
    apiFetch<Card>(`/api/cards/${cardId}/move`, { method: 'POST', body: JSON.stringify({ columnId, position }) }),
}

export type CardsApi = typeof cardsApi
