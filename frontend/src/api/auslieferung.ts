import { apiFetch } from './client'

/** Ein gemeldeter Stand mit Adresse (Issue #2215). */
export interface GemeldeterStand {
  /** Adresse der Vorschau bzw. Veröffentlichung; `null`, wenn keine gemeldet ist. */
  url: string | null
  /** Der gemeldete Stand (Commit). */
  revision: string
  finishedAt: string
}

/** Die jüngste Vorschau und die jüngste Veröffentlichung eines Projekts; je `null` ohne. */
export interface AuslieferungZuletzt {
  vorschau: GemeldeterStand | null
  veroeffentlichung: GemeldeterStand | null
}

export const auslieferungApi = {
  zuletzt: (projectId: number) => apiFetch<AuslieferungZuletzt>(`/api/projects/${projectId}/auslieferung/zuletzt`),
}

export type AuslieferungApi = typeof auslieferungApi
