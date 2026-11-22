import { apiFetch } from './client'

/**
 * Art des Zugangs, über den ein Kommentar kam (Issue #2208, Plan #2206 E6): `STANDARD` ein
 * gewöhnlicher Projekt-Zugang, `APP` der Zugang der Desktop-App; `null` bei der Oberfläche, älteren
 * und gespiegelten Kommentaren.
 */
export type ZugangArt = 'STANDARD' | 'APP'

export interface Comment {
  id: number
  cardId: number
  authorUserId: number | null
  authorName: string
  body: string
  createdAt: string
  updatedAt: string
  zugangArt: ZugangArt | null
  /**
   * Ob der Kommentar von der KI stammt — das hält der Server beim Anlegen fest, nicht eine Zeile im
   * Text (Kriterium 11 des Fachplans #2202).
   */
  vonKi: boolean
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
