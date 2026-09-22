import type {
  VerbrauchAngaben,
  VerbrauchKennzahlen,
  VerbrauchZeitraumArt,
} from '../api/nightRunUsage'
import { leserZone } from '../api/nightRunUsage'
import { betrag } from './nachtlaufFormat'

/**
 * Die Textrechnung der Verbrauchs-Auswertung (Issue #940, Plan #933): Beschriftung der Zeiträume,
 * Richtung des Vergleichs mit dem Vorzeitraum, der Anteil aus dem Zwischenspeicher und die
 * Hinweise, die leere oder angeschnittene Zeiträume erklären. Ohne React und ohne Netzzugriff.
 *
 * Der Kern ist derselbe wie im Backend: **„nicht gemessen" ist nicht 0** (Plan E5).
 */

/**
 * Der Satz aus #926 AK 9 — ausschließlich für einen aufbewahrten Zeitraum ohne jeden Eintrag.
 *
 * Seit Issue #1017 zählt der Zeitraum Läufe **und** Sitzungen (#984 AK 1): An einem Tag ohne
 * Nachtlauf, an dem im Gespräch gearbeitet wurde, stehen Zahlen — der Satz erschiene sonst über
 * einer Arbeit, die stattgefunden hat.
 */
export const KEIN_LAUF_TEXT =
  'In diesem Zeitraum hat weder ein Lauf noch eine Sitzung stattgefunden.'

/** Ein Zeitraum ganz vor dem Erfassungsbeginn — und niemals eine 0 (#984 AK 6). */
export const NICHT_ERFASST_TEXT = 'nicht erfasst'

/** Ein Zeitraum, der den Erfassungsbeginn schneidet: Zahlen sind da, aber unvollständig. */
export const TEILWEISE_ERFASST_TEXT = 'teilweise erfasst'

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
  DAY: 'Vorzyklus',
  WEEK: 'Vorwoche',
  MONTH: 'Vormonat',
}

/**
 * Der Name eines Zyklus (Issue #1127): Er läuft von 12:00 bis 12:00 und heißt nach seinem
 * Beginn und dem Folgetag. `tag` ist sein Beginn als Kalendertag `JJJJ-MM-TT`.
 */
export function zyklusBeschriftung(tag: string): string {
  return `Zyklus ${zyklusSpanne(tag)}`
}

/** Die Spanne eines Zyklus ohne das Wort davor — „vom 21.09.2026 auf den 22.09.2026" (#1135). */
export function zyklusSpanne(tag: string): string {
  return `vom ${DATUM.format(alsDatum(tag))} auf den ${DATUM.format(folgetag(tag))}`
}

/** Die Teile eines Zeitpunkts in einer Zone — Kalendertag und Stunde, wie die Wanduhr dort sie zeigt. */
const WANDUHR = (zone: string) =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    hourCycle: 'h23',
  })

/**
 * Der Zyklus, dem ein Lauf angehört (Issue #1127): sein Beginn als Kalendertag `JJJJ-MM-TT`.
 *
 * <p>Maßgeblich ist die Startzeit in der Zone des Lesers — dieselbe Regel und dieselbe Zone wie
 * Verbrauchsauswertung und Plattform-Leitstand ({@link leserZone}). Ein Lauf vor 12:00 gehört zum
 * Zyklus, der am Vortag um 12:00 begann.
 */
export function zyklusDesStarts(zeitpunkt: string, zone: string = leserZone()): string {
  const teile = Object.fromEntries(
    WANDUHR(zone)
      .formatToParts(new Date(zeitpunkt))
      .map((teil) => [teil.type, teil.value]),
  )
  const tag = `${teile.year}-${teile.month}-${teile.day}`
  return Number(teile.hour) >= 12 ? tag : zyklusDavor(tag)
}

/** Der Zyklus unmittelbar vor dem genannten (Issue #1134) — Beginn als Kalendertag `JJJJ-MM-TT`. */
export function zyklusDavor(tag: string): string {
  const vortag = alsDatum(tag)
  vortag.setUTCDate(vortag.getUTCDate() - 1)
  return vortag.toISOString().slice(0, 10)
}

/**
 * Der Name eines Zeitraums. Ein Zyklus heißt nach Beginn und Folgetag, eine Woche nach ihrer ersten
 * und letzten Nacht, ein Monat nach Name und Jahr.
 */
export function zeitraumBeschriftung(
  kennzahlen: Pick<VerbrauchKennzahlen, 'type' | 'firstDay' | 'lastDay'>,
): string {
  switch (kennzahlen.type) {
    case 'DAY':
      return zyklusBeschriftung(kennzahlen.firstDay)
    case 'WEEK':
      return `Woche vom ${DATUM.format(alsDatum(kennzahlen.firstDay))} bis ${DATUM.format(alsDatum(kennzahlen.lastDay))}`
    case 'MONTH':
      return MONAT.format(alsDatum(kennzahlen.firstDay))
  }
}

/** Ein ISO-Zeitpunkt als Kalendertag — für Grenzen wie den Erfassungs- oder Aufbewahrungsbeginn. */
export function zeitpunktDatum(zeitpunkt: string): string {
  return DATUM.format(new Date(zeitpunkt))
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

/** „1 Sitzung" bzw. „n Sitzungen" — der Anteil neben den Läufen (#984 AK 1). */
export function sitzungenText(anzahl: number): string {
  return anzahl === 1 ? '1 Sitzung' : `${anzahl} Sitzungen`
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
  // Gezählt wird über beide Gattungen (#984 AK 1): Ein Zeitraum mit Sitzungen und ohne Nachtlauf
  // ist nicht leer. `runCount` ist am Server genau die Addition der beiden Zahlen.
  if (kennzahlen.runCount === 0) {
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
  // „Nicht gemessen" bleibt dem Kartenblatt vorbehalten, wo es einen bekannten Lauf ohne Zahl
  // meint (#984 AK 6). Hier geht es um einen ganzen Zeitraum, und der Satz benennt das.
  'nicht-gemessen': 'In diesem Zeitraum liefen Läufe, ihr Verbrauch liegt aber nicht vor.',
}

/** Was der Erfassungsbeginn über den interaktiven Anteil eines Zeitraums aussagt (#984 AK 6). */
export type Erfassungsstand = 'nicht-erfasst' | 'teilweise-erfasst' | 'erfasst'

type Erfassung = Pick<VerbrauchKennzahlen, 'from' | 'to' | 'interactiveUsageSince'>

/**
 * Ob der interaktive Anteil dieses Zeitraums erfasst ist (#984 AK 6, Plan #1007 E18). Ohne je
 * gemeldete Sitzung gibt es keinen Beginn — dann ist nichts erfasst, und eine 0 wäre die
 * Behauptung, es sei nichts verbraucht worden. `to` ist ausschließlich: Beginnt die Erfassung
 * genau am Ende, liegt der Zeitraum noch ganz davor.
 */
export function erfassungsstand(kennzahlen: Erfassung): Erfassungsstand {
  if (kennzahlen.interactiveUsageSince === null) {
    return 'nicht-erfasst'
  }
  const seit = Date.parse(kennzahlen.interactiveUsageSince)
  if (Date.parse(kennzahlen.to) <= seit) {
    return 'nicht-erfasst'
  }
  return Date.parse(kennzahlen.from) < seit ? 'teilweise-erfasst' : 'erfasst'
}

/**
 * Der Hinweis zur Erfassungslücke; `null`, wenn es keine gibt. Ohne gemeldeten Beginn schweigt er
 * ebenfalls: Dass ein Projekt noch nie eine Sitzung gemeldet hat, ist sein Zustand und kein Befund
 * über diesen Zeitraum — der Anteil selbst sagt dort {@link NICHT_ERFASST_TEXT}.
 */
function erfassungsHinweis(kennzahlen: Erfassung): string | null {
  if (kennzahlen.interactiveUsageSince === null) {
    return null
  }
  const seit = DATUM.format(new Date(kennzahlen.interactiveUsageSince))
  switch (erfassungsstand(kennzahlen)) {
    case 'nicht-erfasst':
      return `Interaktive Sitzungen werden erst seit dem ${seit} erfasst — dieser Zeitraum liegt ganz davor.`
    case 'teilweise-erfasst':
      return `Interaktive Sitzungen werden erst seit dem ${seit} erfasst — dieser Zeitraum beginnt davor.`
    case 'erfasst':
      return null
  }
}

/**
 * Der Hinweis zum Zeitraum; `null`, wenn es nichts zu erklären gibt. Treffen Erfassungslücke und
 * Abdeckungslücke zusammen, steht die Erfassung vorn — sie erklärt die größere Lücke: Die
 * Abdeckung fehlt für aufbewahrte Läufe, die Erfassung für eine ganze Gattung.
 */
export function zeitraumHinweis(kennzahlen: VerbrauchKennzahlen): string | null {
  const fall = zeitraumFall(kennzahlen)
  const teile = [erfassungsHinweis(kennzahlen), fall === 'vollstaendig' ? null : HINWEIS[fall]]
  const text = teile.filter((teil): teil is string => teil !== null).join(' ')
  return text === '' ? null : text
}
