import { apiFetch } from './client'

/**
 * Rückfragen der KI an einer Karte (Issue #2217; Endpunkte aus Issue #2210, Plan #2206 E8, E11).
 * Ausgewertet wird allein im Server: Stand der Rückfrage und je Frage kommen fertig, die Oberfläche
 * zeigt sie nur und schickt die Wahl des Kunden zurück.
 */

/** Stand einer Rückfrage; `UEBERHOLT` heißt: Eine neuere Vorschau ist gemeldet, die Freigabe gilt nicht mehr. */
export type RueckfrageZustand = 'OFFEN' | 'BEANTWORTET' | 'UEBERHOLT'

/** Stand einer Frage. */
export type FrageZustand = 'OFFEN' | 'BEANTWORTET' | 'ZURUECKGESTELLT'

/** Eine angebotene Möglichkeit; ihre Nummer zählt ab 1 in der Reihenfolge der Liste. */
export interface RueckfrageOption {
  text: string
  hinweis: string | null
  empfohlen: boolean
}

/** Eine Frage oder ein Weg einer Rückfrage mit ihrem Stand. */
export interface RueckfrageEintrag {
  nr: number
  art: 'FRAGE' | 'WEG'
  frage: string
  empfehlung: string
  grund: string
  einarbeitung: string
  optionen: RueckfrageOption[]
  zustand: FrageZustand
  /** Nummer der gewählten Möglichkeit, ab 1; `null` ohne. */
  wahl: number | null
  /** Eigene Antwort; `null` ohne. */
  text: string | null
  empfehlungUebernommen: boolean
}

/** Eine Rückfrage der KI mit ihrem Stand. */
export interface Rueckfrage {
  /** ID des Kommentars der Rückfrage; auf sie verweist die Antwort. */
  kommentarId: number
  angelegtAm: string
  zustand: RueckfrageZustand
  /** Adresse der Vorschau einer Freigabe-Rückfrage; sonst `null`. */
  vorschau: string | null
  /** Stand, den eine Freigabe-Rückfrage zur Freigabe stellt; sonst `null`. */
  commit: string | null
  eintraege: RueckfrageEintrag[]
}

/** Die Antwort auf eine Frage: genau eines von `wahl`, `text` und `zurueckstellen`. */
export type Einzelantwort =
  | { nr: number; wahl: number }
  | { nr: number; text: string }
  | { nr: number; zurueckstellen: true }

/** Was der Kunde antwortet; der Server schreibt daraus den Kommentar „Antwort“. */
export interface AntwortAuftrag {
  /** Alle Empfehlungen übernehmen; `antworten` nennt dann nur Abweichungen. */
  ja?: boolean
  antworten?: Einzelantwort[]
  /** Nummer des gewählten Wegs. */
  weg?: number
  /** Den Stand einer Freigabe-Rückfrage freigeben. */
  freigeben?: boolean
  /** Der angezeigte Stand, den `freigeben` bestätigt; weicht er ab, antwortet der Server mit 409 (Issue #2253). */
  commit?: string | null
}

export const rueckfragenApi = {
  liste: (cardId: number) => apiFetch<Rueckfrage[]>(`/api/cards/${cardId}/rueckfragen`),
  antworten: (cardId: number, kommentarId: number, auftrag: AntwortAuftrag) =>
    apiFetch<Rueckfrage>(`/api/cards/${cardId}/rueckfragen/${kommentarId}/antwort`, {
      method: 'POST',
      body: JSON.stringify(auftrag),
    }),
}

export type RueckfragenApi = typeof rueckfragenApi
