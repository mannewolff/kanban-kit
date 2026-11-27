import type { CodeAnalysisWeek } from '../api/dashboard'
import { formatierer } from '../i18n/format'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Kopf, Formate und Texte der Platte „Codeanalyse“ (Issue #2278, Plan #2272 E7, E9, E11).
 * Gerechnet wird im Backend; hier wird nur formatiert.
 */

/**
 * Das Datum in UTC, nicht in der Zone des Lesers (E11): Die Wochen der Platte beginnen Montag
 * 00:00 UTC, ein Datum in Ortszeit läge daneben einen Tag daneben. Die Schreibweise folgt
 * `formatierer().datum`.
 */
const DATUM_UTC: Readonly<Record<Sprache, Intl.DateTimeFormatOptions>> = {
  de: { day: 'numeric', month: 'numeric', year: 'numeric', timeZone: 'UTC' },
  en: { dateStyle: 'medium', timeZone: 'UTC' },
}

function datumUtc(zeitpunkt: string, sprache: Sprache): string {
  return new Intl.DateTimeFormat(formatierer(sprache).gebietsschema, DATUM_UTC[sprache]).format(new Date(zeitpunkt))
}

/**
 * Der Kopftext: „Kalenderwochen, letzte 12 Wochen“ oder, wenn die erste Messung im Fenster liegt,
 * „Kalenderwochen, seit ‹Datum›“ mit dem Datum in UTC.
 */
export function codeanalyseKopf(dataSince: string | null, sprache: Sprache): string {
  const t = woerterbuch(sprache).LEITSTAND_CODEANALYSE
  return t.KOPF(dataSince === null ? t.LETZTE_WOCHEN : t.SEIT(datumUtc(dataSince, sprache)))
}

/** Eine Befunddichte mit einer Nachkommastelle in der Schreibweise der Sprache; ohne Dichte `null`. */
export function dichteText(dichte: number | null, sprache: Sprache): string | null {
  return dichte === null
    ? null
    : formatierer(sprache).zahl(dichte, { minimumFractionDigits: 1, maximumFractionDigits: 1 })
}

/**
 * Der Rückgang der Funde in ganzen Prozent; ein Anstieg steht negativ mit Minuszeichen (U+2212).
 * Eine Änderung, die auf ganze Prozent gerundet null wäre, steht mit ihrer ersten geltenden Stelle
 * da (−0,4), damit ein kleiner Anstieg sein Vorzeichen behält (Issue #2283). Ohne Fund vorher `null`.
 */
export function rueckgangText(prozent: number | null, sprache: Sprache): string | null {
  if (prozent === null) {
    return null
  }
  const betragProzent = Math.abs(prozent)
  const ganz = Math.round(betragProzent)
  const zahl = formatierer(sprache).zahl
  // Bei `prozent === 0` liefern beide Zweige „0“; eine eigene Bedingung dafür wäre nicht messbar.
  const betrag = ganz === 0 ? zahl(betragProzent, { maximumSignificantDigits: 1 }) : zahl(ganz)
  return prozent < 0 ? `−${betrag}` : betrag
}

/** Die Höhen einer Woche der Wochenleiste in Prozent der größten Dichte im Fenster. */
export interface DichteHoehe {
  vorher: number | null
  endzustand: number | null
}

/**
 * Die Höhen je Woche: die Summen-Dichte vor der Codeanalyse und im Endzustand in Prozent der
 * größten Dichte im Fenster. Eine Woche ohne zählende Messung oder ohne Zeilen trägt keine Dichte
 * und ist `null` — die Leiste zeigt dort die Lücke, keinen Balken der Höhe null.
 */
export function wochenHoehen(wochen: readonly CodeAnalysisWeek[]): (DichteHoehe | null)[] {
  const paare = wochen.map((w) => ({ vorher: w.before?.findingsDensity ?? null, endzustand: w.final?.findingsDensity ?? null }))
  // Eine fehlende Dichte zählt für das Maximum wie 0; die Untergrenze ist ohnehin 0.
  const max = Math.max(0, ...paare.flatMap((p) => [p.vorher ?? 0, p.endzustand ?? 0]))
  const hoehe = (dichte: number | null): number | null => {
    if (dichte === null) {
      return null
    }
    // Eine positive Dichte fällt nie auf Höhe 0: Die Säule der Höhe 0 ist die Grundlinie, und die
    // hieße „keine Funde“ (Issue #2283).
    return dichte === 0 ? 0 : Math.max(1, Math.round((dichte / max) * 100))
  }
  return paare.map((p) =>
    p.vorher === null && p.endzustand === null ? null : { vorher: hoehe(p.vorher), endzustand: hoehe(p.endzustand) },
  )
}

/** Der Text eines Grunds, warum Messungen nicht zählen; ein unbekannter Grund erscheint, wie er gemeldet wurde. */
export function grundText(grund: string, sprache: Sprache): string {
  const gruende: Readonly<Record<string, string>> = woerterbuch(sprache).LEITSTAND_CODEANALYSE.GRUENDE
  return Object.hasOwn(gruende, grund) ? gruende[grund] : grund
}

/** Die jüngste Woche mit zählenden Messungen im Fenster, oder keine. */
export function juengsteZaehlendeWoche(wochen: readonly CodeAnalysisWeek[]): CodeAnalysisWeek | null {
  return wochen.findLast((w) => w.countingMeasurements > 0) ?? null
}
