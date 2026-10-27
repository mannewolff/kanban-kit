import type { NightRunModelRow, NightRunModels, ProgressStage } from '../api/nightRuns'
import type { Woerterbuch } from '../i18n/de'
import type { Formatierer } from '../i18n/format'

/**
 * Reine Hilfen der Platte „Modelle“ (Issue #1949, Plan #1905 E10–E12): Kopftext, Zeilenname,
 * Quote, Erklärung je Stufe und Werte mit Teilmenge. Gerechnet hat das Backend; hier entstehen
 * nur die Texte.
 */

type Texte = Woerterbuch['LEITSTAND']

/** Formatiert einen Zeitpunkt als Datum der Oberflächensprache. */
type Datum = (iso: string) => string

/** Der Bezugszeitraum der Platte — dieselben Wochenfenster wie bei „Nacharbeit“ (AK 7). */
const WOCHEN = 12

/**
 * Der Kopf: „letzte 12 Wochen“ oder „seit ‹Datum›“ (E11), dazu die Vorgänge ohne Modellangabe,
 * wenn es welche gab (AK 8).
 */
export function kopfText(t: Texte, d: NightRunModels, datum: Datum): string {
  const zeitraum =
    d.lastTwelveWeeks || d.dataSince === null ? t.NACHARBEIT_LETZTE_WOCHEN(WOCHEN) : t.NACHARBEIT_SEIT(datum(d.dataSince))
  return d.withoutModel > 0 ? `${zeitraum} · ${t.MODELLE_OHNE_MODELL(d.withoutModel)}` : zeitraum
}

/** Eine bekannte Denktiefe in der Sprache der Oberfläche, eine unbekannte wie gemeldet (AK 1, 11). */
export function denktiefe(t: Texte, effort: string): string {
  return Object.hasOwn(t.MODELLE_DENKTIEFE, effort) ? t.MODELLE_DENKTIEFE[effort as keyof Texte['MODELLE_DENKTIEFE']] : effort
}

/** „‹Modell› · ‹Denktiefe›“; der Modellname bleibt wie gemeldet (E12). */
export function zeilenName(t: Texte, zeile: NightRunModelRow): string {
  return zeile.effort === null ? zeile.model : `${zeile.model} · ${denktiefe(t, zeile.effort)}`
}

/** „4 von 5 erfolgreich“, unter der Mindestzahl des Backends „zu wenig Daten“ (AK 4). */
export function quoteText(t: Texte, zeile: NightRunModelRow): string {
  return zeile.enoughData ? t.MODELLE_QUOTE(zeile.succeeded, zeile.attempts) : t.MODELLE_ZU_WENIG_DATEN
}

/** Was „erfolgreich“ in einer Stufe heißt (AK 2, 3). */
export function erfolgErklaerung(t: Texte, stufe: ProgressStage): string {
  return t.MODELLE_ERFOLG_ERKLAERUNG[stufe]
}

/**
 * Ein Wert mit seiner Teilmenge (AK 2): ohne Zusatz, wenn alle Vorgänge ihn tragen, sonst „aus 9 von
 * 12“; ohne Wert „nicht gemessen“.
 */
export function teilmenge(t: Texte, wert: string | null, aus: number, von: number): string {
  if (wert === null) return t.MODELLE_NICHT_GEMESSEN
  return aus < von ? t.MODELLE_AUS(wert, aus, von) : wert
}

/** Der Median der Kosten je Vorgang als Dollarbetrag. */
export function kostenText(t: Texte, f: Pick<Formatierer, 'geld'>, zeile: NightRunModelRow): string {
  const wert = zeile.costMedianUsd === null ? null : f.geld(zeile.costMedianUsd)
  return teilmenge(t, wert, zeile.costFrom, zeile.attempts)
}

/** Der Median der Modellzeit je Vorgang als Dauer. */
export function modellzeitText(t: Texte, f: Pick<Formatierer, 'dauer'>, zeile: NightRunModelRow): string {
  const wert = zeile.modelTimeMedianMs === null ? null : f.dauer(zeile.modelTimeMedianMs / 1000)
  return teilmenge(t, wert, zeile.modelTimeFrom, zeile.attempts)
}
