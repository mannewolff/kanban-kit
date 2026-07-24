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
  ideaStored: boolean
  movedToDoneAt: string | null
  dependencies: number[]
  type: CardType
  parentId: number | null
  shortcode: string | null
  assignees: number[]
  dueDate: string | null
  labels: number[]
}

/**
 * Strukturelle Karten-Form, die das CardDetailModal tatsächlich liest — erfüllt sowohl die
 * board-gebundene `Card` als auch die board-nullable Pool-`Idea`. So kann das Modal beide öffnen,
 * ohne die FE-`Card` global board-nullable zu machen. `number` ist board-nullable (Legacy-Ideen
 * ohne projektweite Nummer). Board-spezifische Felder (`boardId`/`columnId`/`positionInColumn`/
 * `movedToDoneAt`) nutzt das Modal nicht und stehen deshalb bewusst nicht hier.
 */
export interface CardDetail {
  id: number
  number: number | null
  title: string
  description: string | null
  type: CardType
  dependencies: number[]
  assignees: number[]
  labels: number[]
  parentId: number | null
  shortcode: string | null
  dueDate: string | null
  archived: boolean
  ideaStored: boolean
}

export interface CardActivity {
  id: number
  actorUserId: number | null
  type: string
  detail: string
  createdAt: string
}

export const cardsApi = {
  list: (boardId: number) => apiFetch<Card[]>(`/api/boards/${boardId}/cards`),
  getActivity: (cardId: number) => apiFetch<CardActivity[]>(`/api/cards/${cardId}/activity`),
  listTrash: (boardId: number) => apiFetch<Card[]>(`/api/boards/${boardId}/trash`),
  restoreDeleted: (cardId: number) =>
    apiFetch<Card>(`/api/cards/${cardId}/restore-deleted`, { method: 'POST' }),
  purge: (cardId: number) => apiFetch<void>(`/api/cards/${cardId}/purge`, { method: 'DELETE' }),
  create: (
    boardId: number,
    columnId: number,
    title: string,
    description?: string,
    parentId?: number | null,
    ideaStored?: boolean,
    // Inhaltlicher Zusatz-Feldsatz beim atomaren Anlegen (Backend #325); leer/weggelassen = wie bisher.
    extra?: {
      dependencies?: number[]
      dueDate?: string | null
      assigneeIds?: number[]
      labelIds?: number[]
    },
  ) =>
    apiFetch<Card>(`/api/boards/${boardId}/cards`, {
      method: 'POST',
      body: JSON.stringify({ columnId, title, description, parentId, ideaStored, ...extra }),
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
  setLabels: (cardId: number, labels: number[]) =>
    apiFetch<Card>(`/api/cards/${cardId}/labels`, {
      method: 'PUT',
      body: JSON.stringify({ labels }),
    }),
  archive: (cardId: number) => apiFetch<Card>(`/api/cards/${cardId}/archive`, { method: 'POST' }),
  moveToIdeaStorage: (cardId: number) =>
    apiFetch<Card>(`/api/cards/${cardId}/idea-storage`, { method: 'POST' }),
  bulkArchive: (cardIds: number[]) =>
    apiFetch<Card[]>(`/api/cards/bulk-archive`, { method: 'POST', body: JSON.stringify({ cardIds }) }),
  bulkTransfer: (cardIds: number[], targetBoardId: number, targetColumnId: number) =>
    apiFetch<Card[]>(`/api/cards/bulk-transfer`, {
      method: 'POST',
      body: JSON.stringify({ cardIds, targetBoardId, targetColumnId }),
    }),
  bulkDelete: (cardIds: number[]) =>
    apiFetch<void>(`/api/cards/bulk-delete`, { method: 'POST', body: JSON.stringify({ cardIds }) }),
  restore: (cardId: number) => apiFetch<Card>(`/api/cards/${cardId}/restore`, { method: 'POST' }),
  remove: (cardId: number) => apiFetch<void>(`/api/cards/${cardId}`, { method: 'DELETE' }),
  update: (
    cardId: number,
    title: string,
    description: string | null,
    dependencies?: number[],
    shortcode?: string | null,
    parentId?: number | null,
    dueDate?: string | null,
  ) =>
    apiFetch<Card>(`/api/cards/${cardId}`, {
      method: 'PATCH',
      body: JSON.stringify({ title, description, dependencies, shortcode, parentId, dueDate }),
    }),
}

export type CardsApi = typeof cardsApi
