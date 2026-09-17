import type {
  VerbrauchAngaben,
  VerbrauchKennzahlen,
  VerbrauchZeitraumArt,
} from '../api/nightRunUsage'
import { betrag } from './nachtlaufFormat'

/**
 * Die Textrechnung der Verbrauchs-Auswertung (Issue #940, Plan #933): Beschriftung der Zeiträume,
 * Richtung des Vergleichs mit dem Vorzeitraum, der Anteil aus dem Zwischenspeicher und die
 * Hinweise, die leere oder angeschnittene Zeiträume erklären. Ohne React und ohne Netzzugriff.
 *
 * Der Kern ist derselbe wie im Backend: **„nicht gemessen" ist nicht 0** (Plan E5).
 */

/** Der Satz aus #926 AK 9 — ausschließlich für einen aufbewahrten Zeitraum ohne Lauf. */
export const KEIN_LAUF_TEXT = 'In diesem Zeitraum hat kein Lauf stattgefunden.'

const DATUM = new Intl.DateTimeFormat('de-DE', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'UTC',
})

const MONAT = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric', timeZone: 'UTC' })

/** Tag und Monat ohne Jahr — für die Zeilen der Nächte, wo die Spalte schmal ist. */
const TAG_MONAT = new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', timeZone: 'UTC' })

const WOCHENTAG = new Intl.DateTimeFormat('de-DE', { weekday: 'short', timeZone: 'UTC' })

const PROZENT = new Intl.NumberFormat('de-DE', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
})

/**
 * Ein Kalenderdatum `JJJJ-MM-TT` als Mitternacht UTC. Formatiert wird ebenfalls in UTC — so kann
 * die Zone des Browsers das Datum nie um einen Tag verschieben.
 */
const alsDatum = (tag: string): Date => new Date(`${tag}T00:00:00Z`)

const folgetag = (tag: string): Date => {
  const datum = alsDatum(tag)
  datum.setUTCDate(datum.getUTCDate() + 1)
  return datum
}

const VORZEITRAUM: Record<VerbrauchZeitraumArt, string> = {
  DAY: 'Vornacht',
  WEEK: 'Vorwoche',
  MONTH: 'Vormonat',
}

/**
 * Der Name eines Zeitraums. Eine Nacht heißt nach Beginn und Folgetag, eine Woche nach ihrer ersten
 * und letzten Nacht, ein Monat nach Name und Jahr.
 */
export function zeitraumBeschriftung(
  kennzahlen: Pick<VerbrauchKennzahlen, 'type' | 'firstDay' | 'lastDay'>,
): string {
  switch (kennzahlen.type) {
    case 'DAY':
      return `Nacht vom ${DATUM.format(alsDatum(kennzahlen.firstDay))} auf den ${DATUM.format(folgetag(kennzahlen.firstDay))}`
    case 'WEEK':
      return `Woche vom ${DATUM.format(alsDatum(kennzahlen.firstDay))} bis ${DATUM.format(alsDatum(kennzahlen.lastDay))}`
    case 'MONTH':
      return MONAT.format(alsDatum(kennzahlen.firstDay))
  }
}

/** Der Vorzeitraum beim Namen seiner Art — der Titel seiner Platte. */
export function vorzeitraumName(kennzahlen: Pick<VerbrauchKennzahlen, 'type'>): string {
  return VORZEITRAUM[kennzahlen.type]
}

/**
 * Eine Nacht in der Kurzform der Nächte-Platte: Wochentag, Beginn und Folgetag ohne Jahr. Die
 * Spalte ist schmal, das Jahr steht schon in der Beschriftung des Zeitraums darüber.
 */
export function nachtKurz(nacht: string): string {
  return `${WOCHENTAG.format(alsDatum(nacht))} ${TAG_MONAT.format(alsDatum(nacht))} → ${TAG_MONAT.format(folgetag(nacht))}`
}

/** „1 Lauf" bzw. „n Läufe" — die Zahl steht in Kopfzeilen, Einordnungen und Nächte-Zeilen. */
export function laeufeText(anzahl: number): string {
  return anzahl === 1 ? '1 Lauf' : `${anzahl} Läufe`
}

/** „1 Karte" bzw. „n Karten". */
export function kartenText(anzahl: number): string {
  return anzahl === 1 ? '1 Karte' : `${anzahl} Karten`
}

export type VergleichsRichtung = 'teurer' | 'billiger' | 'unveraendert' | 'nicht-vergleichbar'

/** Kosten werden am Server auf sechs Nachkommastellen gespeichert; feiner ist kein Unterschied. */
const MIKRODOLLAR = 1_000_000

/**
 * Ob der Zeitraum teurer oder billiger war als der Vorzeitraum — samt Unterschied, damit der Leser
 * nicht selbst rechnet (#926 AK 6). Fehlt eine der beiden Kostenangaben, ist der Vergleich nicht
 * möglich; eine gemessene Null ist dagegen ein Wert.
 */
export function vergleichMitVorzeitraum(
  aktuell: VerbrauchAngaben,
  vorher: VerbrauchAngaben,
): { richtung: VergleichsRichtung; text: string } {
  if (aktuell.costUsd === null || vorher.costUsd === null) {
    return {
      richtung: 'nicht-vergleichbar',
      text: 'nicht vergleichbar — eine der beiden Kostenangaben fehlt',
    }
  }
  const differenz =
    Math.round(aktuell.costUsd * MIKRODOLLAR) - Math.round(vorher.costUsd * MIKRODOLLAR)
  if (differenz === 0) {
    return { richtung: 'unveraendert', text: 'genauso teuer wie im Vorzeitraum' }
  }
  const richtung = differenz > 0 ? 'teurer' : 'billiger'
  return {
    richtung,
    text: `${betrag(Math.abs(differenz) / MIKRODOLLAR)} ${richtung} als im Vorzeitraum`,
  }
}

/**
 * Der Anteil der Eingabe aus dem Zwischenspeicher (#926 AK 13). Den Wert rechnet der Server
 * (`NightRunUsage#cachedInputSharePercent`); ohne Eingabemenge liefert er `null`, und das heißt
 * „nicht bestimmt" — nicht 0 %.
 */
export function zwischenspeicherAnteil(angaben: VerbrauchAngaben): string {
  return angaben.cachedInputSharePercent === null
    ? 'nicht bestimmt'
    : `${PROZENT.format(angaben.cachedInputSharePercent)} %`
}

export type ZeitraumFall =
  | 'vor-aufbewahrung'
  | 'teilweise'
  | 'kein-lauf'
  | 'nicht-gemessen'
  | 'vollstaendig'

/**
 * Welcher der vier erklärungsbedürftigen Fälle vorliegt. Sie sind paarweise verwechselbar, die
 * Reihenfolge der Prüfung ist deshalb die Aussage: Ein Zeitraum vor der Aufbewahrung ist leer,
 * aber nicht „ohne Lauf"; ein angeschnittener Zeitraum kann leer aussehen und ist trotzdem nur
 * unvollständig (Plan E8).
 */
export function zeitraumFall(kennzahlen: VerbrauchKennzahlen): ZeitraumFall {
  if (kennzahlen.coverage === 'BEFORE_RETENTION') {
    return 'vor-aufbewahrung'
  }
  if (kennzahlen.coverage === 'PARTIAL') {
    return 'teilweise'
  }
  if (kennzahlen.noRuns) {
    return 'kein-lauf'
  }
  return kennzahlen.usage.total.costUsd === null ? 'nicht-gemessen' : 'vollstaendig'
}

const HINWEIS: Record<Exclude<ZeitraumFall, 'vollstaendig'>, string> = {
  'kein-lauf': KEIN_LAUF_TEXT,
  'vor-aufbewahrung':
    'Dieser Zeitraum liegt vor dem ältesten aufbewahrten Lauf — seine Läufe sind nicht mehr gespeichert.',
  teilweise:
    'Dieser Zeitraum ist nur teilweise abgedeckt: Er beginnt vor dem ältesten aufbewahrten Lauf, die Zahlen sind unvollständig.',
  'nicht-gemessen': 'In diesem Zeitraum liefen Läufe, ihr Verbrauch wurde aber nicht gemessen.',
}

/** Der Hinweis zum Fall; `null`, wenn es nichts zu erklären gibt. */
export function zeitraumHinweis(kennzahlen: VerbrauchKennzahlen): string | null {
  const fall = zeitraumFall(kennzahlen)
  return fall === 'vollstaendig' ? null : HINWEIS[fall]
}
