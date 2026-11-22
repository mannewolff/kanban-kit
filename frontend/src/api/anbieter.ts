import { apiFetch } from './client'

/**
 * „Wartet auf dich“ und die Erreichbarkeit des Rechenzentrums (Issue #2218; Endpunkte aus Issue
 * #2216, Plan #2206 E12, E14). Ausgewertet wird im Server: Welche Karten warten und ob das
 * Rechenzentrum erreichbar ist, kommt fertig; die Oberfläche zeigt es nur.
 */

/** Warum eine Karte auf den Kunden wartet. */
export type WarteGrund = 'RUECKFRAGE' | 'FREIGABE'

/** Eine Karte, an der der Kunde etwas tun muss. */
export interface WartendeKarte {
  cardId: number
  boardId: number
  /** Projektweite Nummer der Karte. */
  number: number
  title: string
  grund: WarteGrund
}

/** Die Lage eines Boards, das das Rechenzentrum schon gelesen hat. */
export interface BoardLage {
  boardId: number
  nichtErreichbar: boolean
  zuletztGelesen: string
}

/** Die Lage eines Projekts; `nichtErreichbar` ist allein für die Rolle Kunde je wahr. */
export interface AnbieterLage {
  nichtErreichbar: boolean
  boards: BoardLage[]
}

export const anbieterApi = {
  wartetAufDich: (projectId: number) => apiFetch<WartendeKarte[]>(`/api/projects/${projectId}/wartet-auf-dich`),
  lage: (projectId: number) => apiFetch<AnbieterLage>(`/api/projects/${projectId}/anbieter/lage`),
}

export type AnbieterApi = typeof anbieterApi
