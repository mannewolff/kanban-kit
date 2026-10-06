import { apiFetch } from './client'

/** Ein Label des Standardsatzes (Issue #1485): Name, Gruppe und die Farbe, mit der es entsteht. */
export interface StandardLabel {
  name: string
  gruppe: string
  farbe: string
}

/** Ergebnis des Anlegens über alle nicht archivierten Boards aller Projekte. */
export interface StandardLabelErgebnis {
  boards: number
  angelegt: number
  uebersprungen: number
}

/** Standard-Labels der Installation (nur Plattform-Admin, Issue #1486). */
export const standardLabelsApi = {
  satz: () => apiFetch<StandardLabel[]>('/api/admin/standard-labels'),
  anlegen: () =>
    apiFetch<StandardLabelErgebnis>('/api/admin/standard-labels/anlegen', { method: 'POST' }),
}
