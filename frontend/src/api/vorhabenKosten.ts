import { ApiError, apiFetch } from './client'

/**
 * Abruf der Kostenzeile je Vorhaben (Issue #2006, Endpunkte aus Issue #2005, Plan #1990).
 *
 * Beide Aufrufe ergeben `null` statt eines Fehlers, wenn der Server mit 403 (Mitglied ohne
 * Verbrauchsrecht) oder 404 (kein Mitglied, kein Vorhaben) antwortet: Wer den Verbrauch nicht sieht,
 * sieht die Zeile nicht (fachlich #1954 AK 10) — ohne Meldung, weil es nichts zu melden gibt.
 * Beträge in USD, wie der Server sie meldet.
 */

/** Wie die Voraussage entstand: aus der Anforderung, vor dem Schnitt oder aus den Paketen. */
export type VoraussageArt = 'ANFORDERUNG' | 'VOR_DEM_SCHNITT' | 'PAKETE'

/** Warum keine Voraussage beziffert wird (fachlich #1954 AK 7). */
export type ZuWenigDatenGrund = 'ZU_WENIG_VORHABEN' | 'STUFE_OHNE_PREIS' | 'KEINE_PAKETPREISE'

/** Eine bezifferte Voraussage mit typischem Betrag und Spanne (15 % bis 85 %). */
export interface VoraussageView {
  art: VoraussageArt
  typisch: number
  unten: number
  oben: number
  /** Aus wie vielen Paketen sie rechnet. */
  paketzahl: number
  /** Pakete ohne Aufgabenstufe, die mit dem Paketpreis über alle Aufgabenstufen rechnen. */
  ohneStufe: number
}

/**
 * Ein nicht abgeschlossenes Vorhaben. Gilt `hatDokumentOderPaket`, ist genau eines von `voraussage`
 * und `zuWenigDaten` gesetzt; sonst keines.
 */
export interface LaufendView {
  hatDokumentOderPaket: boolean
  bisher: number
  unvollstaendig: boolean
  voraussage: VoraussageView | null
  zuWenigDaten: ZuWenigDatenGrund | null
}

/** Eine festgehaltene Voraussage neben den tatsächlichen Kosten; ohne Bezifferung alle Felder `null`. */
export interface VergleichView {
  beziffert: boolean
  art: VoraussageArt | null
  typisch: number | null
  unten: number | null
  oben: number | null
  /** Abweichung in ganzen Prozent; `null`, wenn keine zu nennen ist. */
  abweichungProzent: number | null
}

/** Ein Vorhaben mit festgehaltenem Abschluss (fachlich #1954 AK 8). */
export interface AbschlussView {
  abgeschlossenAt: string
  tatsaechlich: number
  unvollstaendig: boolean
  anforderung: VergleichView
  pakete: VergleichView
}

/** Die Kostenzeile eines Vorhabens; genau eines von `laufend` und `abschluss` ist gesetzt. */
export interface VorhabenKostenView {
  epicId: number
  number: number
  archiviert: boolean
  laufend: LaufendView | null
  abschluss: AbschlussView | null
}

/** 403 und 404 werden zu `null`, jeder andere Fehler bleibt einer. */
function ohneRechtAlsNull<T>(abruf: Promise<T>): Promise<T | null> {
  return abruf.catch((err: unknown) => {
    if (err instanceof ApiError && (err.status === 403 || err.status === 404)) return null
    throw err
  })
}

export const vorhabenKostenApi = {
  /** Die Kostenzeilen aller Vorhaben eines Boards, archivierte eingeschlossen. */
  jeBoard: (boardId: number) =>
    ohneRechtAlsNull(apiFetch<VorhabenKostenView[]>(`/api/boards/${boardId}/night-run-epic-costs`)),
  /** Die Kostenzeile des Vorhabens mit dieser Karten-ID, auch archiviert. */
  jeVorhaben: (cardId: number) =>
    ohneRechtAlsNull(apiFetch<VorhabenKostenView>(`/api/cards/${cardId}/night-run-epic-costs`)),
}

export type VorhabenKostenApi = typeof vorhabenKostenApi
