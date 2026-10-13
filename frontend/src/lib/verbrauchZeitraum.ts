import type { VerbrauchAngaben, VerbrauchKennzahlen } from '../api/nightRunUsage'
import { leserZone } from '../api/nightRunUsage'
import { formatierer } from '../i18n/format'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Die Textrechnung der Verbrauchs-Auswertung (Issue #940, Plan #933): Beschriftung der Zeiträume,
 * Richtung des Vergleichs mit dem Vorzeitraum und die Hinweise, die leere oder angeschnittene
 * Zeiträume erklären. Ohne React und ohne Netzzugriff.
 *
 * Der Kern ist derselbe wie im Backend: **„nicht gemessen" ist nicht 0** (Plan E5).
 */

/** Ein Zeitraum ganz vor dem Erfassungsbeginn — und niemals eine 0 (#984 AK 6). Deutsche Fassung. */
export const NICHT_ERFASST_TEXT = woerterbuch('de').VERBRAUCH_ZEITRAUM.NICHT_ERFASST

/** Ein Zeitraum, der den Erfassungsbeginn schneidet: Zahlen sind da, aber unvollständig. Deutsche Fassung. */
export const TEILWEISE_ERFASST_TEXT = woerterbuch('de').VERBRAUCH_ZEITRAUM.TEILWEISE_ERFASST

/** Das Kalenderdatum je Sprache: `21.09.2026` bzw. `Sep 21, 2026`, stets in UTC. */
const DATUM: Readonly<Record<Sprache, Intl.DateTimeFormatOptions>> = {
  de: { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' },
  en: { dateStyle: 'medium', timeZone: 'UTC' },
}

const datum = (wert: Date, sprache: Sprache): string =>
  new Intl.DateTimeFormat(formatierer(sprache).gebietsschema, DATUM[sprache]).format(wert)

const monat = (wert: Date, sprache: Sprache): string =>
  new Intl.DateTimeFormat(formatierer(sprache).gebietsschema, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(wert)

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

/**
 * Der Name eines Zyklus (Issue #1127): Er läuft von 12:00 bis 12:00 und heißt nach seinem
 * Beginn und dem Folgetag. `tag` ist sein Beginn als Kalendertag `JJJJ-MM-TT`.
 */
export function zyklusBeschriftung(tag: string, sprache: Sprache = 'de'): string {
  return woerterbuch(sprache).VERBRAUCH_ZEITRAUM.SCHICHT(zyklusSpanne(tag, sprache))
}

/** Die Spanne eines Zyklus ohne das Wort davor — „vom 21.09.2026 auf den 22.09.2026" (#1135). */
export function zyklusSpanne(tag: string, sprache: Sprache = 'de'): string {
  return woerterbuch(sprache).VERBRAUCH_ZEITRAUM.SPANNE(datum(alsDatum(tag), sprache), datum(folgetag(tag), sprache))
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
  sprache: Sprache = 'de',
): string {
  switch (kennzahlen.type) {
    case 'DAY':
      return zyklusBeschriftung(kennzahlen.firstDay, sprache)
    case 'WEEK':
      return woerterbuch(sprache).VERBRAUCH_ZEITRAUM.WOCHE(
        datum(alsDatum(kennzahlen.firstDay), sprache),
        datum(alsDatum(kennzahlen.lastDay), sprache),
      )
    case 'MONTH':
      return monat(alsDatum(kennzahlen.firstDay), sprache)
  }
}

/** Ein ISO-Zeitpunkt als Kalendertag — für Grenzen wie den Erfassungs- oder Aufbewahrungsbeginn. */
export function zeitpunktDatum(zeitpunkt: string, sprache: Sprache = 'de'): string {
  return datum(new Date(zeitpunkt), sprache)
}

/** „1 Run" bzw. „n Runs" — die Zahl steht in Kopfzeilen, Einordnungen und Nächte-Zeilen. */
export function laeufeText(anzahl: number, sprache: Sprache = 'de'): string {
  return woerterbuch(sprache).VERBRAUCH_ZEITRAUM.LAEUFE(anzahl)
}

/** „1 Sitzung" bzw. „n Sitzungen" — der Anteil neben den Läufen (#984 AK 1). */
export function sitzungenText(anzahl: number, sprache: Sprache = 'de'): string {
  return woerterbuch(sprache).VERBRAUCH_ZEITRAUM.SITZUNGEN(anzahl)
}

/** „1 Karte" bzw. „n Karten". */
export function kartenText(anzahl: number, sprache: Sprache = 'de'): string {
  return woerterbuch(sprache).VERBRAUCH_ZEITRAUM.KARTEN(anzahl)
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
  sprache: Sprache = 'de',
): { richtung: VergleichsRichtung; text: string } {
  const t = woerterbuch(sprache).VERBRAUCH_ZEITRAUM
  if (aktuell.costUsd === null || vorher.costUsd === null) {
    return { richtung: 'nicht-vergleichbar', text: t.NICHT_VERGLEICHBAR }
  }
  const differenz =
    Math.round(aktuell.costUsd * MIKRODOLLAR) - Math.round(vorher.costUsd * MIKRODOLLAR)
  if (differenz === 0) {
    return { richtung: 'unveraendert', text: t.UNVERAENDERT }
  }
  const betrag = formatierer(sprache).geld(Math.abs(differenz) / MIKRODOLLAR)
  return differenz > 0
    ? { richtung: 'teurer', text: t.TEURER(betrag) }
    : { richtung: 'billiger', text: t.BILLIGER(betrag) }
}

type ZeitraumFall =
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
function zeitraumFall(kennzahlen: VerbrauchKennzahlen): ZeitraumFall {
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

/**
 * Der Hinweis je Fall. „Nicht gemessen" bleibt dem Kartenblatt vorbehalten, wo es einen bekannten
 * Lauf ohne Zahl meint (#984 AK 6); hier geht es um einen ganzen Zeitraum, und der Satz benennt das.
 * `kein-lauf` ist der Satz aus #926 AK 9 — seit Issue #1017 über Läufe **und** Sitzungen (#984 AK 1).
 */
function hinweis(fall: Exclude<ZeitraumFall, 'vollstaendig'>, sprache: Sprache): string {
  const t = woerterbuch(sprache).VERBRAUCH_ZEITRAUM
  const texte: Record<Exclude<ZeitraumFall, 'vollstaendig'>, string> = {
    'kein-lauf': t.KEIN_LAUF,
    'vor-aufbewahrung': t.VOR_AUFBEWAHRUNG,
    teilweise: t.TEILWEISE,
    'nicht-gemessen': t.NICHT_GEMESSEN,
  }
  return texte[fall]
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
function erfassungsHinweis(kennzahlen: Erfassung, sprache: Sprache): string | null {
  if (kennzahlen.interactiveUsageSince === null) {
    return null
  }
  const t = woerterbuch(sprache).VERBRAUCH_ZEITRAUM
  const seit = datum(new Date(kennzahlen.interactiveUsageSince), sprache)
  switch (erfassungsstand(kennzahlen)) {
    case 'nicht-erfasst':
      return t.ERFASSUNG_DAVOR(seit)
    case 'teilweise-erfasst':
      return t.ERFASSUNG_BEGINNT_DAVOR(seit)
    case 'erfasst':
      return null
  }
}

/**
 * Der Hinweis zum Zeitraum; `null`, wenn es nichts zu erklären gibt. Treffen Erfassungslücke und
 * Abdeckungslücke zusammen, steht die Erfassung vorn — sie erklärt die größere Lücke: Die
 * Abdeckung fehlt für aufbewahrte Läufe, die Erfassung für eine ganze Gattung.
 */
export function zeitraumHinweis(kennzahlen: VerbrauchKennzahlen, sprache: Sprache = 'de'): string | null {
  const fall = zeitraumFall(kennzahlen)
  const teile = [erfassungsHinweis(kennzahlen, sprache), fall === 'vollstaendig' ? null : hinweis(fall, sprache)]
  const text = teile.filter((teil): teil is string => teil !== null).join(' ')
  return text === '' ? null : text
}
