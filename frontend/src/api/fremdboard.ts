import { apiFetch } from './client'

/**
 * Board eines Projekts in einem fremden System (Issue #2169; Endpunkt aus Issue #2166, Plan #2152
 * A2, A13, A18). `lies` und `speichere` gelten nur für den Owner des Projekts; `stand` (Issue #2224,
 * Plan #2221 E1, E2) gilt für jedes Mitglied und nennt nur, ob das Board im fremden System liegt, von
 * wann der Stand ist und ob der letzte Abgleich gescheitert ist.
 *
 * Der Zugang geht nur hinaus, nie herein: Keine Antwort enthält ihn, auch nicht verschlüsselt.
 * Fehlende Angaben kommen als `null`. Fehler wirft {@link apiFetch} als
 * {@link import('./client').ApiError}; der Grund einer Ablehnung kommt aus `apiErrorMessage`.
 */

/** Die fremden Systeme; zuerst nur GitLab. */
export type FremdBoardArt = 'GITLAB'

/** Labelnamen je Status im fremden System. Done hat keins: Eine geschlossene Karte gilt als Done. */
export interface FremdBoardStatuslabels {
  backlog: string
  ready: string
  inProgress: string
  inReview: string
}

/** Die Vorgabe der Statuslabels — dieselben Namen wie die Spalten des Kits. */
export const STANDARD_STATUSLABELS: Readonly<FremdBoardStatuslabels> = {
  backlog: 'Backlog',
  ready: 'Ready',
  inProgress: 'In progress',
  inReview: 'In review',
}

/** Die Verbindung, wie der Server sie zeigt — ohne Zugang. */
export interface FremdBoardAnsicht {
  angebunden: boolean
  /** `false`, wenn auf dem Server kein Schlüssel gesetzt ist; dann lässt sich nichts anbinden. */
  schluesselGesetzt: boolean
  art: FremdBoardArt | null
  basisUrl: string | null
  projektPfad: string | null
  kitKonto: string | null
  statuslabels: FremdBoardStatuslabels | null
  angebundenAm: string | null
  letzterAbgleichAm: string | null
  letzterFehler: string | null
  /** `false`, wenn sich der hinterlegte Zugang nicht entschlüsseln lässt; er muss neu hinterlegt werden. */
  zugangLesbar: boolean
}

/**
 * Die knappe Auskunft für jedes Mitglied — ohne Konto des Kits, Fehlertext, Adresse oder Zugang.
 * `standAm` ist leer, solange nach dem Anbinden noch kein Abgleich gelungen ist.
 */
export interface FremdBoardStand {
  imFremdsystem: boolean
  art: FremdBoardArt | null
  standAm: string | null
  abgleichGescheitert: boolean
}

/**
 * Was der Owner angibt. `zugang` ist beim ersten Anbinden Pflicht; danach ersetzt er den alten, und
 * ohne ihn (`null`) bleibt der alte.
 */
export interface FremdBoardEingabe {
  art: FremdBoardArt
  basisUrl: string
  projektPfad: string
  zugang: string | null
  kitKonto: string | null
  statuslabels: FremdBoardStatuslabels
}

const pfad = (projectId: number) => `/api/projects/${projectId}/fremdboard`

export const fremdBoardApi = {
  lies: (projectId: number) => apiFetch<FremdBoardAnsicht>(pfad(projectId)),
  speichere: (projectId: number, eingabe: FremdBoardEingabe) =>
    apiFetch<FremdBoardAnsicht>(pfad(projectId), {
      method: 'PUT',
      body: JSON.stringify(eingabe),
    }),
  stand: (projectId: number) => apiFetch<FremdBoardStand>(`${pfad(projectId)}/stand`),
}

export type FremdBoardApi = typeof fremdBoardApi
