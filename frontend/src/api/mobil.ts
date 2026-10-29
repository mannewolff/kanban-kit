import { apiFetch } from './client'
import { leserZone } from './nightRunUsage'

/**
 * Die Läufe auf dem Handy (Issue #1995, Plan #1989 E19), gegen `GET /api/mobil/laeufe` und
 * `GET /api/mobil/laeufe/{laufId}` aus Issue #1994. Nur lesen.
 *
 * Felder ohne Wert kommen als `null`, wie bei den übrigen Nachtlauf-Antworten.
 */

/** Wie ein Lauf erscheint; `BEENDET` gibt es nur beim einzelnen Lauf, nie in der Liste. */
export type MobilerZustand = 'LAEUFT' | 'HAENGT' | 'GESCHEITERT' | 'BEENDET'

/** Eine Zeile der Liste. */
export interface MobilerLauf {
  laufId: number
  projectId: number
  projectName: string
  /** Board des Zugangsschlüssels, über den der Lauf gemeldet wird; `null`, wenn keines erkennbar ist. */
  boardId: number | null
  boardName: string | null
  zustand: MobilerZustand
  startedAt: string
  durationMs: number
}

/** Woher die letzte Meldung stammt; den Satz bildet die Oberfläche. */
export type MobileMeldungsArt = 'ABBRUCHGRUND' | 'PAKET' | 'STILLE' | 'STUFE'

/** Die letzte Meldung eines Laufs als Daten. */
export interface MobileMeldung {
  art: MobileMeldungsArt
  /** Abbruchgrund oder Auszug des Pakets; sonst `null`. */
  text: string | null
  cardNumber: number | null
  /** Name der Stufe bei `STUFE`; sonst `null`. */
  stage: string | null
  /** Bei `STILLE` das letzte Lebenszeichen, bei `STUFE` der Beginn der Stufe; sonst `null`. */
  seit: string | null
}

/** Ein einzelner Lauf mit seiner letzten Meldung. */
export interface MobilerLaufDetail {
  lauf: MobilerLauf
  meldung: MobileMeldung | null
}

export const mobilApi = {
  /** Die Läufe der laufenden und der vorigen Nacht; die Nachtgrenze zieht die Zone des Lesers. */
  laeufe: (zone: string = leserZone()) =>
    apiFetch<MobilerLauf[]>(`/api/mobil/laeufe?${new URLSearchParams({ zone })}`),
  lauf: (laufId: number) => apiFetch<MobilerLaufDetail>(`/api/mobil/laeufe/${laufId}`),
}
