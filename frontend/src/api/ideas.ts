import { apiFetch } from './client'
import type { CardType } from './cards'

/**
 * Projektweite Idee — eine board-lose Karte im Ideen-Pool oder eine bereits eingeplante bzw.
 * Legacy-Karte mit gesetztem Ideen-Flag. Spiegelt die board-optionale `CardView` des Backends:
 * `boardId`/`columnId`/`number` sind `null`, solange die Idee im Pool liegt; `targetBoardId` hält
 * das (z. B. aus dem kanbancompat-Ingest) notierte Zielboard für die Vorauswahl beim Einplanen.
 */
export interface Idea {
  id: number
  boardId: number | null
  columnId: number | null
  number: number | null
  title: string
  description: string | null
  ideaStored: boolean
  targetBoardId: number | null
  type: CardType
}

export const ideasApi = {
  list: (projectId: number) => apiFetch<Idea[]>(`/api/projects/${projectId}/ideas`),
  create: (
    projectId: number,
    input: { title: string; description?: string | null; targetBoardId?: number | null },
  ) =>
    apiFetch<Idea>(`/api/projects/${projectId}/ideas`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  // Plant eine Pool-Idee auf ein Board ein (board-gebunden, nicht mehr Idee).
  planOntoBoard: (cardId: number, targetBoardId: number) =>
    apiFetch<Idea>(`/api/cards/${cardId}/plan`, {
      method: 'PUT',
      body: JSON.stringify({ targetBoardId }),
    }),
  // Holt eine eingeplante/Legacy-Karte zurück in den board-losen Ideen-Pool.
  moveBackToPool: (cardId: number) =>
    apiFetch<Idea>(`/api/cards/${cardId}/to-pool`, { method: 'PUT' }),
}

export type IdeasApi = typeof ideasApi
