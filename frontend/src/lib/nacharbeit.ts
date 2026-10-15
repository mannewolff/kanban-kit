import type { NightRunRework, NightRunReworkLine, NightRunReworkWeek, NightRunReworkWeekValue } from '../api/nightRuns'
import type { Woerterbuch } from '../i18n/de'

/**
 * Reine Hilfen der Platte „Nacharbeit“ (Issue #1759, Plan #1752): Kopftext, Zeilenzustand,
 * Balkenzustand und Hinweis je Woche. Die Platte selbst rechnet nicht, sie zeichnet nur.
 */

type Texte = Woerterbuch['LEITSTAND']

/** Formatiert einen Zeitpunkt als Datum der Oberflächensprache. */
type Datum = (iso: string) => string

/**
 * `iso`, wenn es echt nach `bezug` liegt, sonst `null`. Ein fehlender Zeitpunkt wird über `String` zu
 * „null“ bzw. „undefined“, `Date.parse` daraus zu NaN — und NaN liegt nie später (#1789).
 */
const spaeterAls = (iso: string | null, bezug: string | undefined): string | null =>
  Date.parse(String(iso)) > Date.parse(String(bezug)) ? iso : null

/**
 * Der Beginn, den der Kopf nennt (E8): der Beginn der Paket-Daten, wenn er nach dem ältesten
 * Wochenfenster liegt, sonst `null` für „letzte 12 Wochen“.
 */
function kopfBeginn(d: NightRunRework): string | null {
  return spaeterAls(d.packages.dataSince, d.weeks[0]?.weekStart)
}

/** Der Bezugszeitraum im Kopf der Platte: „letzte 12 Wochen“ oder „seit ‹Datum›“ (AK 4, E8). */
export function kopfText(t: Texte, d: NightRunRework, datum: Datum): string {
  const beginn = kopfBeginn(d)
  return beginn === null ? t.NACHARBEIT_LETZTE_WOCHEN(d.weeks.length) : t.NACHARBEIT_SEIT(datum(beginn))
}

/**
 * Der eigene Beginn der Dokument-Zeile (E8): nur, wenn sie später beginnt als der Zeitraum, den der
 * Kopf nennt; sonst `null`.
 */
export function dokumentSeit(t: Texte, d: NightRunRework, datum: Datum): string | null {
  const beginn = spaeterAls(d.documents.dataSince, kopfBeginn(d) ?? d.weeks[0]?.weekStart)
  return beginn === null ? null : t.NACHARBEIT_SEIT(datum(beginn))
}

/** Was eine Zeile zeigt: nie „0 von 0“, sondern einen Satz (AK 6). */
export type ZeilenZustand =
  | { art: 'keineRuns' }
  | { art: 'keineKarte' }
  | { art: 'quote'; betroffen: number; bearbeitet: number }

export function zeilenZustand(runsImZeitraum: boolean, zeile: NightRunReworkLine): ZeilenZustand {
  if (!runsImZeitraum) return { art: 'keineRuns' }
  if (zeile.processed === 0) return { art: 'keineKarte' }
  return { art: 'quote', betroffen: zeile.affected, bearbeitet: zeile.processed }
}

/** Die drei sichtbar verschiedenen Zustände einer Gruppe in einer Woche (AK 5, 14; E13). */
export type BalkenZustand = { art: 'keineDaten' } | { art: 'grundlinie' } | { art: 'balken'; prozent: number }

export function balkenZustand(wert: NightRunReworkWeekValue): BalkenZustand {
  if (wert.state === 'KEINE_DATEN') return { art: 'keineDaten' }
  // Eine Quote ohne Bezugszahl wäre „0 von 0“ — sie wird wie eine Woche ohne Karte gezeigt.
  if (wert.state === 'KEINE_KARTE' || wert.processed === 0) return { art: 'grundlinie' }
  return { art: 'balken', prozent: Math.round((wert.affected * 100) / wert.processed) }
}

/** Der Teil einer Gruppe im Hinweis einer Woche. */
function gruppenHinweis(t: Texte, gruppe: string, wert: NightRunReworkWeekValue): string {
  const zustand = balkenZustand(wert)
  if (zustand.art === 'keineDaten') return t.NACHARBEIT_HINWEIS_KEINE_DATEN(gruppe)
  if (zustand.art === 'grundlinie') return t.NACHARBEIT_HINWEIS_OHNE_KARTE(gruppe)
  return t.NACHARBEIT_HINWEIS_QUOTE(gruppe, t.NACHARBEIT_QUOTE(wert.affected, wert.processed))
}

/** Der Hinweis beim Überfahren einer Woche: „Pakete 1 von 14 · Dokumente 0 von 6“ oder „keine Daten“. */
export function wochenHinweis(t: Texte, woche: NightRunReworkWeek): string {
  if (woche.packages.state === 'KEINE_DATEN' && woche.documents.state === 'KEINE_DATEN') {
    return t.NACHARBEIT_KEINE_DATEN
  }
  return `${gruppenHinweis(t, t.NACHARBEIT_GRUPPE_PAKETE, woche.packages)} · ${gruppenHinweis(t, t.NACHARBEIT_GRUPPE_DOKUMENTE, woche.documents)}`
}
