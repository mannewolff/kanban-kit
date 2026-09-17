import type { WeeklyThroughput } from '../api/dashboard'
import type { NightRunErrorClassCounts, NightRunItemView, NightRunServerMode, NightRunView } from '../api/nightRuns'
import type { NightRunErrorClass, NightRunState } from './nightRunLog'

/**
 * Die Rechnung des Leitstands (#979, Entwurf `docs/entwurf-leitstand.html` Z. 1200–1678): aus den
 * Antworten der vorhandenen Endpunkte die Aussagen von Laufband, Kacheln und Platten. Ohne React
 * und ohne Netzzugriff.
 *
 * **Was keine Datenquelle hat, erscheint nicht** (CLAUDE-design.md, „Vorlage und Abnahme"): kein
 * Budget, keine Stufen der laufenden Kette, kein Verlauf von Durchlauf- und Implementierungszeit. Die
 * Funktionen liefern dort `null`, und die Ansicht lässt die Stelle weg.
 */

/** Die Melder des Entwurfs als Namen — die Werte liegen im Theme. */
export type Melder = 'gruen' | 'bernst' | 'zinnob' | 'stahl' | 'grau'

const ZAHL_1 = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
const ZAHL_2 = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const GANZ = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 0 })
const TAG_ZEIT = new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
const ZEIT = new Intl.DateTimeFormat('de-DE', { hour: '2-digit', minute: '2-digit' })

/** Der Zustand eines Arbeitspakets als Melder (Entwurf: LED grün, gelb, rot, grau). */
export const MELDER_JE_ZUSTAND: Record<NightRunState, Melder> = {
  GREEN: 'gruen',
  YELLOW: 'bernst',
  RED: 'zinnob',
  GREY: 'grau',
}

/**
 * Die Farbe einer Fehlerklasse (Entwurf Z. 632–634, 1612–1651): Rot heißt „die Arbeit ist
 * gescheitert", Bernstein „sie wartet auf etwas", Grau „ein Werkzeug der Prüfung fiel aus".
 */
export const MELDER_JE_FEHLERKLASSE: Record<NightRunErrorClass, Melder> = {
  CHECKS_RED: 'zinnob',
  CHECKS_NOT_STARTED: 'zinnob',
  UNEXPECTED_STATE: 'zinnob',
  HARD_ABORT: 'zinnob',
  TIME_BUDGET_EXCEEDED: 'zinnob',
  AWAITING_DECISION: 'bernst',
  DEPENDENCY_UNMET: 'bernst',
  REVIEWER_FAILED: 'grau',
}

const MODUS: Record<NightRunServerMode, string> = {
  CHAIN: 'Kette',
  IMPLEMENTATION: 'Umsetzung',
  REVIEW: 'Review',
}

/** Der Name der Betriebsart eines Laufs. */
export function modusName(mode: NightRunServerMode): string {
  return MODUS[mode]
}

/** „1 Vorgang" bzw. „n Vorgänge". */
export function vorgaenge(anzahl: number): string {
  return anzahl === 1 ? '1 Vorgang' : `${anzahl} Vorgänge`
}

/** Der jüngste Lauf nach Startzeit; `null` ohne Lauf. Die Reihenfolge der Antwort ist nicht zugesichert. */
export function juengsterLauf(laeufe: readonly NightRunView[]): NightRunView | null {
  if (laeufe.length === 0) {
    return null
  }
  return laeufe.reduce((a, b) => (b.startedAt > a.startedAt ? b : a))
}

/**
 * Der Melder eines ganzen Laufs: läuft er noch, stahl; sonst der schlechteste Zustand seiner Pakete.
 *
 * <p>Die Form ist absichtlich schmal — `complete` und die Zustände. Die Server-Sicht erfüllt sie,
 * und das Anzeigemodell der Nachtlauf-Seite (#988) ebenso; eine zweite Rechenstelle für dieselbe
 * Frage liefe beim nächsten Zustand auseinander.
 */
export function laufMelder(lauf: {
  complete: boolean
  items: readonly { state: NightRunState }[]
}): Melder {
  if (!lauf.complete) {
    return 'stahl'
  }
  if (lauf.items.some((item) => item.state === 'RED')) {
    return 'zinnob'
  }
  return lauf.items.some((item) => item.state === 'YELLOW') ? 'bernst' : 'gruen'
}

/** Die Aussage des Laufbands (Entwurf Z. 1203–1230). */
export interface Laufband {
  titel: string
  melder: Melder
  laeuft: boolean
  /** Der jüngste Vorgang; `null`, solange der Lauf keinen gemeldet hat. */
  vorgang: { nummer: number; titel: string } | null
  zeitpunkt: string
  minuten: number
  /** Kosten in Dollar ohne Einheit; `null`, wenn der Verbrauch nicht gemessen wurde. */
  kosten: string | null
}

export function laufband(lauf: NightRunView): Laufband {
  const modus = modusName(lauf.mode)
  const gesamt = lauf.items.length
  const letzter = lauf.items.at(-1)
  const beginn = new Date(lauf.startedAt)
  let titel: string
  if (lauf.complete) {
    titel = `${modus} abgeschlossen — ${vorgaenge(gesamt)}`
  } else if (gesamt === 0) {
    titel = `${modus} läuft`
  } else {
    const bearbeitet = lauf.items.filter((item) => item.state !== 'GREY').length
    titel = `${modus} läuft — Vorgang ${bearbeitet} von ${gesamt}`
  }
  return {
    titel,
    melder: laufMelder(lauf),
    laeuft: !lauf.complete,
    vorgang: letzter ? { nummer: letzter.cardNumber, titel: letzter.title } : null,
    zeitpunkt: lauf.complete ? `Beginn ${TAG_ZEIT.format(beginn)}` : `seit ${ZEIT.format(beginn)}`,
    minuten: Math.round(lauf.durationMs / 60_000),
    kosten: lauf.usage?.costUsd == null ? null : dollar(lauf.usage.costUsd),
  }
}

/** Ein Dollarbetrag mit zwei Nachkommastellen, ohne Einheit. */
export function dollar(usd: number): string {
  return ZAHL_2.format(usd)
}

/** Dollar mit zwei Nachkommastellen und Einheit; `null` bleibt `null` — nicht gemessen ist nicht 0. */
export function kostenText(usd: number | null): string | null {
  return usd === null ? null : `${dollar(usd)} $`
}

/** Dauer eines Arbeitspakets wie im Entwurf: `mm:ss`, ab einer Stunde `h:mm:ss`; ohne Messung `—`. */
export function paketDauer(ms: number | null): string {
  if (ms === null) {
    return '—'
  }
  const sekunden = Math.round(ms / 1000)
  const h = Math.floor(sekunden / 3600)
  const m = Math.floor((sekunden % 3600) / 60)
  const s = String(sekunden % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${String(m).padStart(2, '0')}:${s}`
}

/** Dauer eines Laufs als „4 h 12 min" bzw. „12 min". */
export function laufDauer(ms: number): string {
  const minuten = Math.round(ms / 60_000)
  const h = Math.floor(minuten / 60)
  return h > 0 ? `${h} h ${minuten % 60} min` : `${minuten} min`
}

/**
 * Dieselbe Dauer in Wert und Einheit getrennt, für das Instrument „Dauer" der Laufplatte (#988,
 * Mockup `docs/mockup-nachtlauf-lauf.html` Z. 397: „4:12 h"). Nicht {@link laufDauer}: Ein
 * Instrument setzt die Einheit klein hinter den Wert, und „4 h 12 min" ließe sich dafür nicht
 * teilen. Nicht {@link paketDauer}: Die zählt Sekunden mit, und über vier Stunden sagt „4:12:00"
 * nichts, was „4:12 h" nicht kürzer sagte.
 */
export function laufDauerGeteilt(ms: number): { wert: string; einheit: string } {
  const minuten = Math.round(ms / 60_000)
  const h = Math.floor(minuten / 60)
  return h > 0
    ? { wert: `${h}:${String(minuten % 60).padStart(2, '0')}`, einheit: 'h' }
    : { wert: String(minuten), einheit: 'min' }
}

/** Ein Zeitpunkt als Tag und Uhrzeit („14.09. 23:10") — Beginn eines Laufs in Notiz und Metazeile. */
export function tagZeit(iso: string): string {
  return TAG_ZEIT.format(new Date(iso))
}

/** Die Notiz im Kopf der Platte „Letzter Lauf": Beginn, Dauer, Zahl der Pakete. */
export function laufNotiz(lauf: NightRunView): string {
  const pakete = lauf.items.length === 1 ? '1 Paket' : `${lauf.items.length} Pakete`
  return `${tagZeit(lauf.startedAt)} · ${laufDauer(lauf.durationMs)} · ${pakete}`
}

/**
 * Der Anteil des Zwischenspeichers an der Eingabe in Prozent (#988, Instrument „Cache-Quote");
 * `null`, wo eine der beiden Zahlen fehlt oder die Eingabe null ist.
 *
 * <p><b>Dieselbe Formel wie am Server</b> (`NightRunUsage#cachedInputSharePercent`): Zwischenspeicher
 * mal hundert geteilt durch Eingabe, `null` ohne eine der beiden Zahlen oder bei Eingabe null. Die
 * Sicht eines Laufs (`api/nightRuns.ts`) führt den fertigen Anteil nicht — nur die Verbrauchs-Sicht
 * tut das. Gerechnet wird hier deshalb, aber nicht anders.
 *
 * <p><b>Ohne Bezugsgröße kein Prozentwert.</b> „0 %" behauptete eine gemessene Quote von null,
 * „100 %" eine vollständige Wiederverwendung — beides sind Aussagen über eine Division, die es
 * nicht gibt. Eine gemessene Null im Zwischenspeicher ist dagegen ein Wert: Dort lief die Messung
 * und ergab nichts.
 */
export function cacheQuote(zwischenspeicher: number | null, eingabe: number | null): number | null {
  if (zwischenspeicher === null || eingabe === null || eingabe === 0) {
    return null
  }
  return Math.round((zwischenspeicher / eingabe) * 100)
}

/** Ob ein Arbeitspaket ein Abbruch ist — der Filter „Nur Abbrüche" (Entwurf Z. 1419). */
export function istAbbruch(item: NightRunItemView): boolean {
  return item.state === 'RED' || item.state === 'YELLOW'
}

/** Kurzer Commit-Hash wie im Entwurf (sieben Zeichen). */
export function kurzHash(hash: string | null): string | null {
  return hash === null ? null : hash.slice(0, 7)
}

/** Die erste Zeile eines Auszugs — die Zeile unter dem Titel ist einzeilig. */
export function ersteZeile(text: string | null): string | null {
  const zeile = text?.split('\n').find((z) => z.trim() !== '')
  return zeile === undefined ? null : zeile.trim()
}

// ---------------------------------------------------------------------------------------------
// Kacheln
// ---------------------------------------------------------------------------------------------

/** Die Richtung eines Deltas: gut (grün), schlecht (zinnober) oder neutral. */
export type DeltaArt = 'gut' | 'schlecht' | 'neutral'

export interface Delta {
  text: string
  art: DeltaArt
}

export interface Kachel {
  /** `null` ohne Datenbasis — die Ansicht zeigt dann einen Leerwert, keine Null. */
  wert: string | null
  einheit: string
  basis: string
  /** Werte der Sparkline; `null`, wenn es keinen Verlauf gibt. */
  verlauf: readonly number[] | null
  delta: Delta | null
}

/** Durchsatz · Woche (Entwurf Z. 1236–1258): letzte Woche, Verlauf über zwölf, Delta zur Vorwoche. */
export function durchsatzKachel(wochen: readonly WeeklyThroughput[]): Kachel {
  const werte = wochen.map((w) => w.doneCount)
  if (werte.every((w) => w === 0)) {
    return { wert: null, einheit: 'Karten', basis: `${werte.length} Wochen`, verlauf: null, delta: null }
  }
  const letzte = werte.at(-1)!
  const vorige = werte.at(-2) ?? 0
  return {
    wert: GANZ.format(letzte),
    einheit: letzte === 1 ? 'Karte' : 'Karten',
    basis: `${werte.length} Wochen`,
    verlauf: werte,
    delta: wochenDelta(letzte, vorige),
  }
}

function wochenDelta(letzte: number, vorige: number): Delta {
  if (letzte === vorige) {
    return { text: '± 0', art: 'neutral' }
  }
  const art: DeltaArt = letzte > vorige ? 'gut' : 'schlecht'
  const pfeil = letzte > vorige ? '▲' : '▼'
  if (vorige === 0) {
    return { text: `${pfeil} ${letzte} zur Vorwoche`, art }
  }
  return { text: `${pfeil} ${GANZ.format(Math.round((Math.abs(letzte - vorige) / vorige) * 100))} %`, art }
}

/** Durchlaufzeit in Tagen (Entwurf Z. 1260–1282). Kein Verlauf, kein Delta: Die Antwort trägt nur den Schnitt. */
export function durchlaufKachel(sekunden: number | null, stichprobe: number): Kachel {
  return {
    wert: sekunden === null ? null : ZAHL_1.format(sekunden / 86_400),
    einheit: 'Tage',
    basis: stichprobe === 1 ? '1 Karte' : `${stichprobe} Karten`,
    verlauf: null,
    delta: null,
  }
}

/**
 * Implementierungszeit (Entwurf Z. 1284–1306): wie lange eine erledigte Karte in In Progress lag.
 * Unter einer Stunde in Minuten — KI-Umsetzungen dauern oft weniger, und „0,3 Stunden" liest sich
 * schlecht.
 */
export function implementierungKachel(sekunden: number | null, stichprobe: number): Kachel {
  const basis = stichprobe === 1 ? '1 Karte' : `${stichprobe} Karten`
  if (sekunden === null || stichprobe === 0) {
    return { wert: null, einheit: 'Stunden', basis, verlauf: null, delta: null }
  }
  // Die Grenze liegt bei den gerundeten Minuten: 3570 s wären sonst „60 Minuten" statt „1,0 Stunden".
  const minuten = Math.round(sekunden / 60)
  const inMinuten = minuten < 60
  return {
    wert: inMinuten ? GANZ.format(minuten) : ZAHL_1.format(sekunden / 3600),
    einheit: inMinuten ? 'Minuten' : 'Stunden',
    basis,
    verlauf: null,
    delta: null,
  }
}

/** Anteil grüner Pakete über die aufbewahrten Läufe (Entwurf Z. 1308–1323); graue zählen nicht. */
export interface GruenAnteil {
  prozent: number | null
  gruen: number
  gelb: number
  rot: number
  gesamt: number
}

export function gruenAnteil(laeufe: readonly NightRunView[]): GruenAnteil {
  const gezaehlt = paketZaehlung(laeufe.flatMap((lauf) => lauf.items))
  return {
    prozent: gezaehlt.gesamt === 0 ? null : Math.round((gezaehlt.gruen / gezaehlt.gesamt) * 100),
    ...gezaehlt,
  }
}

/**
 * Die Pakete einer Menge nach Zustand gezählt — grün, gelb, rot und ihre Summe. **Graue zählen
 * nicht mit**: Ein übergangener Vorgang lief nie, und ihn in die Summe zu nehmen machte aus der
 * Auswahlregel eines Laufs einen Mangel. Dieselbe Grenze, die `processedCount` zieht.
 *
 * <p>Sie nimmt nur den Zustand und nicht die ganze Server-Sicht: Der Anteil des Leitstands
 * ({@link gruenAnteil}) und das Instrument „Pakete" der Laufplatte (#988) reichen verschiedene
 * Formen herein, zählen aber dasselbe.
 */
export function paketZaehlung(
  pakete: readonly { state: NightRunState }[],
): { gruen: number; gelb: number; rot: number; gesamt: number } {
  const gruen = pakete.filter((p) => p.state === 'GREEN').length
  const gelb = pakete.filter((p) => p.state === 'YELLOW').length
  const rot = pakete.filter((p) => p.state === 'RED').length
  return { gruen, gelb, rot, gesamt: gruen + gelb + rot }
}

/**
 * Punkte einer Sparkline im Rahmen 124 × 36 des Entwurfs: links 2, rechts 122, oben 4, unten 30.
 * Ein einzelner Wert oder lauter gleiche Werte liegen auf halber Höhe.
 */
export function funkenPunkte(werte: readonly number[]): Array<{ x: number; y: number }> {
  const min = Math.min(...werte)
  const max = Math.max(...werte)
  const schritt = werte.length > 1 ? 120 / (werte.length - 1) : 0
  return werte.map((wert, i) => ({
    x: Math.round((2 + i * schritt) * 10) / 10,
    y: max === min ? 17 : Math.round((30 - ((wert - min) / (max - min)) * 26) * 10) / 10,
  }))
}

/** Die Kalenderwoche nach ISO 8601 eines Wochenbeginns — Beschriftung unter dem Balkenwerk. */
export function kalenderwoche(iso: string): number {
  const datum = new Date(iso)
  const tag = new Date(Date.UTC(datum.getUTCFullYear(), datum.getUTCMonth(), datum.getUTCDate()))
  const wochentag = tag.getUTCDay() || 7
  tag.setUTCDate(tag.getUTCDate() + 4 - wochentag)
  const jahresbeginn = new Date(Date.UTC(tag.getUTCFullYear(), 0, 1))
  return Math.ceil(((tag.getTime() - jahresbeginn.getTime()) / 86_400_000 + 1) / 7)
}

/** Höhen der Balken in Prozent des höchsten Werts; ohne Wert über null steht jeder Balken bei 0. */
export function balkenHoehen(werte: readonly number[]): number[] {
  const max = Math.max(0, ...werte)
  return werte.map((wert) => (max === 0 ? 0 : Math.round((wert / max) * 100)))
}

/** Eine Zeile der Abbruchgründe (Entwurf Z. 1606–1653): Klasse, Zahl, Breite relativ zur häufigsten. */
export interface Klassenzeile {
  klasse: NightRunErrorClass
  zahl: number
  breite: number
  melder: Melder
}

export function abbruchgruende(zaehler: NightRunErrorClassCounts): Klassenzeile[] {
  const eintraege = (Object.entries(zaehler) as Array<[NightRunErrorClass, number]>).filter(([, zahl]) => zahl > 0)
  const max = Math.max(0, ...eintraege.map(([, zahl]) => zahl))
  return eintraege
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([klasse, zahl]) => ({
      klasse,
      zahl,
      breite: Math.round((zahl / max) * 100),
      melder: MELDER_JE_FEHLERKLASSE[klasse],
    }))
}

/** Eine Token-Menge in der Einheit des Entwurfs: „4,82 Mio", „186 Tsd" oder die Zahl selbst. */
export function tokenMenge(anzahl: number | null): { wert: string; einheit: string } | null {
  if (anzahl === null) {
    return null
  }
  if (anzahl >= 1_000_000) {
    return { wert: ZAHL_2.format(anzahl / 1_000_000), einheit: 'Mio' }
  }
  if (anzahl >= 1000) {
    return { wert: GANZ.format(Math.round(anzahl / 1000)), einheit: 'Tsd' }
  }
  return { wert: GANZ.format(anzahl), einheit: 'Token' }
}

/** Eine Token-Menge als Fließtext („3,66 Mio", „710 Tsd"). */
export function tokenText(anzahl: number): string {
  const menge = tokenMenge(anzahl)!
  return `${menge.wert} ${menge.einheit}`
}
