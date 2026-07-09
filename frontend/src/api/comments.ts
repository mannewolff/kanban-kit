import { apiFetch } from './client'

export interface Comment {
  id: number
  cardId: number
  authorUserId: number | null
  authorName: string
  body: string
  createdAt: string
  updatedAt: string
}

export const commentsApi = {
  list: (cardId: number) => apiFetch<Comment[]>(`/api/cards/${cardId}/comments`),
  create: (cardId: number, body: string) =>
    apiFetch<Comment>(`/api/cards/${cardId}/comments`, { method: 'POST', body: JSON.stringify({ body }) }),
  update: (id: number, body: string) =>
    apiFetch<Comment>(`/api/comments/${id}`, { method: 'PATCH', body: JSON.stringify({ body }) }),
  remove: (id: number) => apiFetch<void>(`/api/comments/${id}`, { method: 'DELETE' }),
}

export type CommentsApi = typeof commentsApi
