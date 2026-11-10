import { apiFetch } from './client'

/**
 * Die Einstellungen der Mail-Benachrichtigung (Issue #2082, Plan #2070 E12), gegen
 * `/api/me/benachrichtigungen` und `/api/projects/{projectId}/benachrichtigung` aus Issue #2076.
 */

/** Ein Projekt, für das die Person ihren Schalter bedienen darf, mit beiden Schaltern. */
export interface BenachrichtigungsProjekt {
  projectId: number
  projectName: string
  projektAn: boolean
  personAn: boolean
}

/** Beginn und Ende als Uhrzeit `HH:mm`, dazu die IANA-Zeitzone. */
export interface Ruhezeit {
  beginn: string
  ende: string
  zeitzone: string
}

/** Die eigenen Einstellungen; `ruhezeit` ist `null`, wenn keine eingestellt ist. */
export interface MeineBenachrichtigungen {
  projekte: BenachrichtigungsProjekt[]
  ruhezeit: Ruhezeit | null
}

export interface ProjektSchalter {
  an: boolean
}

const MEINE = '/api/me/benachrichtigungen'

export const benachrichtigungenApi = {
  einstellungen: () => apiFetch<MeineBenachrichtigungen>(MEINE),
  schaltePerson: (projectId: number, an: boolean) =>
    apiFetch<void>(`${MEINE}/projekte/${projectId}`, { method: 'PUT', body: JSON.stringify({ an }) }),
  setzeRuhezeit: (ruhezeit: Ruhezeit) =>
    apiFetch<void>(`${MEINE}/ruhezeit`, { method: 'PUT', body: JSON.stringify(ruhezeit) }),
  loescheRuhezeit: () => apiFetch<void>(`${MEINE}/ruhezeit`, { method: 'DELETE' }),
  projektschalter: (projectId: number) =>
    apiFetch<ProjektSchalter>(`/api/projects/${projectId}/benachrichtigung`),
  schalteProjekt: (projectId: number, an: boolean) =>
    apiFetch<ProjektSchalter>(`/api/projects/${projectId}/benachrichtigung`, {
      method: 'PUT',
      body: JSON.stringify({ an }),
    }),
}
