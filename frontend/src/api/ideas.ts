import { apiFetch } from './client'
import type { CardType } from './cards'

/**
 * Projektweite Idee — eine board-lose Karte im Ideen-Pool oder eine bereits eingeplante bzw.
 * Legacy-Karte mit gesetztem Ideen-Flag. Spiegelt die board-optionale `CardView` des Backends:
 * `boardId`/`columnId` sind `null`, solange die Idee im Pool liegt. Seit #402 trägt eine neue
 * Pool-Idee sofort eine projektweite `number`; nur Legacy-Ideen ohne Nummer haben hier `null`.
 * `targetBoardId` hält das (z. B. aus dem kanbancompat-Ingest) notierte Zielboard für die
 * Vorauswahl beim Einplanen.
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
  // Volle Karten-Form: das Backend liefert diese Felder in `listProjectIdeas` (dieselbe `CardView`
  // wie Board-Karten) bereits mit; nur board-lose Ideen tragen `boardId`/`columnId`/`number` als
  // `null`. So erfüllt eine Idee das strukturelle `CardDetail` und ist im CardDetailModal voll
  // bearbeitbar (Zuständige/Abhängigkeiten/Fälligkeit …).
  positionInColumn: number
  archived: boolean
  movedToDoneAt: string | null
  dependencies: number[]
  parentId: number | null
  shortcode: string | null
  assignees: number[]
  dueDate: string | null
  labels: number[]
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
  /**
   * Legt mehrere Pool-Ideen in einem Zug an (Batch-Endpoint aus #492) — Ziel des
   * Spezifikations-Imports (#493). Alles-oder-nichts: Verletzt ein Element die Feldgrenzen des
   * Servers (Titel > 300, Beschreibung > 10.000, mehr als 200 Elemente), entsteht keine einzige
   * Idee. Antwort: die angelegten Ideen in Eingabereihenfolge, jeweils mit `id` und `number`.
   */
  createBatch: (
    projectId: number,
    input: {
      ideas: Array<{ title: string; description?: string | null }>
      targetBoardId?: number | null
    },
  ) =>
    apiFetch<Idea[]>(`/api/projects/${projectId}/ideas/batch`, {
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
