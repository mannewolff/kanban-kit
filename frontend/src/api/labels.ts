import { apiFetch } from './client'

export interface Label {
  id: number
  boardId: number
  name: string
  color: string
  /** Ob das Label auf der Vorhaben-Kachel als Marke gezählt wird (Issue #659). */
  countOnEpicTile: boolean
}

const json = (data: unknown, method: string): RequestInit => ({ method, body: JSON.stringify(data) })

export const labelsApi = {
  list: (boardId: number) => apiFetch<Label[]>(`/api/boards/${boardId}/labels`),
  create: (boardId: number, name: string, color: string) =>
    apiFetch<Label>(`/api/boards/${boardId}/labels`, json({ name, color }, 'POST')),
  /**
   * `countOnEpicTile` ist bewusst optional: Fehlt das Feld im Request, lässt das Backend den
   * gespeicherten Wert unverändert (Issue #659). Ein Umbenennen darf die Einstellung nicht
   * zurücksetzen, und ein Pflichtparameter bräche die Bestandsaufrufer (TS strict).
   */
  update: (labelId: number, name: string, color: string, countOnEpicTile?: boolean) =>
    apiFetch<Label>(
      `/api/labels/${labelId}`,
      json(
        countOnEpicTile === undefined ? { name, color } : { name, color, countOnEpicTile },
        'PATCH',
      ),
    ),
  remove: (labelId: number) => apiFetch<void>(`/api/labels/${labelId}`, { method: 'DELETE' }),
}

export type LabelsApi = typeof labelsApi
