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
  /**
   * Nummern aller zugehörigen Karten, aufsteigend — direkt zugeordnete und über die Herkunft
   * geerbte gemeinsam. Ohne sie wäre eine gestiegene Zahl nicht nachprüfbar (Issue #634).
   */
  memberNumbers: number[]
  /** Nummern der direkt zugeordneten Karten, aufsteigend. Stets Teilmenge von `memberNumbers`. */
  rootNumbers: number[]
  /**
   * Nummer der Anforderungskarte, aus der dieses Vorhaben eröffnet wurde, oder `null`.
   *
   * `null` ist ein gültiger Dauerzustand, kein Ladezustand: Ein Vorhaben darf auch ohne
   * Herkunftskette zum Gruppieren dienen (Issue #636). Derselbe Name wie in `EpicView` und der
   * JSON-Antwort — ID im Inneren, Nummer nach außen (festgelegt in Issue #638).
   */
  requirementCardNumber: number | null
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
