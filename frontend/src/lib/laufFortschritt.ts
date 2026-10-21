import type {
  CardRefView,
  ChainProgressView,
  KettenStand,
  KettenStation,
  NightRunBudgetView,
  NightRunOutcomeView,
  NightRunProgressView,
  PackageProgressView,
  PackageState,
  ProgressStage,
  StageProgressView,
} from '../api/nightRuns'
import type { NightRunState } from './nightRunLog'
import type { Woerterbuch } from '../i18n/de'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Die reine Rechenlogik des Fortschritts auf der Runner-Seite (Issue #1376, Plan #1372).
 *
 * <p>Zähl-, Stufen- und Zusammenführungsregeln liegen bewusst **hier** und nicht in Komponente
 * oder Seite: Nur dieser Ausschnitt unterliegt der Mutationsprüfung (Review-Fund A7).
 */

/** Die Kopfzahlen eines Laufs: „bearbeitet / übergangen" und die Paket-Kachel. */
export interface Kopfzahlen {
  bearbeitet: number
  uebergangen: number
  gruen: number
  gelb: number
  rot: number
}

/** Ein gemeldeter Vorgang, soweit die Zählung ihn braucht. */
export interface GemeldeterVorgang {
  cardNumber: number
  state: NightRunState
}

/**
 * Die Kopfzahlen, die den Board-Stand vorwegnehmen (Plan #1372 E7).
 *
 * <p>Gemeldete Vorgänge zählen, wie sie sind; bearbeitet ist jeder nicht graue — dieselbe Grenze
 * wie `processedCount`, rot also eingeschlossen. Läuft der Lauf noch, kommt für jede Kartennummer,
 * die er noch nicht gemeldet hat, der Board-Stand dazu: fertig → bearbeitet und grün,
 * zurückgestellt → bearbeitet und rot; in Umsetzung, gezogen und angelegt zählen nirgends. Gelb und
 * übergangen kommen allein aus der Meldung. Karten unter „unbekannt" zählen nicht.
 *
 * @param fortschritt der Fortschritt vom Server; `null`, solange keiner geladen ist
 * @param laeuft ob der Lauf noch läuft — sonst gelten allein die gemeldeten Zahlen
 */
export function vorwegZaehlung(
  items: readonly GemeldeterVorgang[],
  fortschritt: NightRunProgressView | null,
  laeuft: boolean,
): Kopfzahlen {
  const zahlen: Kopfzahlen = {
    bearbeitet: items.filter((i) => i.state !== 'GREY').length,
    uebergangen: items.filter((i) => i.state === 'GREY').length,
    gruen: items.filter((i) => i.state === 'GREEN').length,
    gelb: items.filter((i) => i.state === 'YELLOW').length,
    rot: items.filter((i) => i.state === 'RED').length,
  }
  if (!laeuft || fortschritt === null) {
    return zahlen
  }
  // Gemeldete und unbekannte Karten gelten als schon gesehen: Sie zählen kein zweites Mal.
  const gesehen = new Set([
    ...items.map((i) => i.cardNumber),
    ...fortschritt.unbekannt.map((k) => k.number),
  ])
  for (const paket of fortschritt.pakete) {
    if (gesehen.has(paket.karte.number)) {
      continue
    }
    gesehen.add(paket.karte.number)
    if (paket.zustand === 'FERTIG') {
      zahlen.bearbeitet++
      zahlen.gruen++
    } else if (paket.zustand === 'ZURUECKGESTELLT') {
      zahlen.bearbeitet++
      zahlen.rot++
    }
  }
  return zahlen
}

/** Welche Art von Lauf den Fortschritt zeigt: eine Kette oder eine Umsetzungsnacht. */
export type FortschrittArt = 'KETTE' | 'UMSETZUNGSNACHT'

/** Eine Stufe der Wegleiste. */
export interface Wegstufe {
  stufe: ProgressStage
  name: string
  /** Der Zustand der Stufe als Text — nie allein über die Farbe (E12). */
  zustand: string
  /** Ob der Lauf gerade an dieser Stelle steht. */
  aktuell: boolean
}

/** Die Wegleiste einer Kette: ihre Stufen und, wenn erreicht, das Ende des Wegs. */
export interface Wegleiste {
  stufen: Wegstufe[]
  /** Der Endzustand des Wegs, wenn jede Stufe erreicht ist; sonst `null`. */
  ende: string | null
}

/**
 * Die Wegleiste einer Kette (Plan #1372 E12).
 *
 * <p>Die Stufen kommen, wie der Server sie liefert: Umsetzung trägt eine Kette nur in Variante B,
 * und das entscheidet der Server an der tragenden Karte, nicht diese Funktion. Die Umsetzungsnacht
 * kennt nur die Umsetzung, und solange ihr Fortschritt gezeigt wird, läuft sie.
 *
 * @param kette die Kette; `null`, wo es keine gibt — in der Umsetzungsnacht immer
 */
export function wegleiste(kette: ChainProgressView | null, art: FortschrittArt, sprache: Sprache = 'de'): Wegleiste {
  const t = woerterbuch(sprache).LAUF_FORTSCHRITT
  if (art === 'UMSETZUNGSNACHT') {
    return {
      stufen: [{ stufe: 'UMSETZUNG', name: t.STUFE.UMSETZUNG, zustand: t.ZUSTAND.LAEUFT, aktuell: true }],
      ende: null,
    }
  }
  if (kette === null) {
    return { stufen: [], ende: null }
  }
  return {
    stufen: kette.stufen.map((s) => ({
      stufe: s.stufe,
      name: t.STUFE[s.stufe],
      zustand: t.ZUSTAND[s.zustand],
      aktuell: s.stufe === kette.aktuelleStufe,
    })),
    ende: kette.endeErreicht ? t.ENDE_DES_WEGS : null,
  }
}

/** Das Symbol einer Station der Stufenleiste — neben dem Text, nie an seiner Stelle (E6). */
export type Stationssymbol =
  | 'erledigt'
  | 'ziel-erreicht'
  | 'laeuft'
  | 'wartet'
  | 'projektgrenze'
  | 'abgebrochen'
  | 'steht-aus'
  | 'nicht-vorgesehen'
  | 'erbracht'

/** Eine Station der Stufenleiste während und nach dem Lauf, fertig für die Darstellung. */
export interface Stationsanzeige {
  station: KettenStation
  name: string
  symbol: Stationssymbol
  /** Der Zustandstext des Servers; an der Projektgrenze mit ihrem Grund. */
  text: string
  /** Warte- oder Abbruchgrund, wörtlich; `null`, wo keiner steht oder er schon im Text steht. */
  grund: string | null
  /** Ob die Kette an dieser Station steht — sie läuft, wartet oder brach ab. */
  aktuell: boolean
  /** Ob das die Zielstation ist — ohne gewähltes Ziel „Arbeitspakete“. */
  ziel: boolean
}

/** Die Namen der Stationen der Stufenleiste in einer Sprache. */
export function stationsnamen(sprache: Sprache): Record<KettenStation, string> {
  const t = woerterbuch(sprache).LAUF_FORTSCHRITT
  return { ...t.STUFE, VORBEREITUNG: t.VORBEREITUNG }
}

/** Der Text, den der Server an der Station einer Projektgrenze setzt (Issue #1451). */
const PROJEKTGRENZE = 'Projektgrenze'

/**
 * Die Stufenleiste einer übernommenen Kette (Issue #1453, Plan #1447 E5, E6). Zustand, Text und
 * Grund kommen fertig vom Server; hier entstehen nur Symbol und Markierung. Nichts
 * wird aus Labels abgeleitet.
 */
export function kettenAnzeige(stand: KettenStand, sprache: Sprache = 'de'): Stationsanzeige[] {
  const ziel = stand.ziel ?? 'PAKETE'
  const namen = stationsnamen(sprache)
  return stand.stationen.map(({ station, zustand, text, grund }) => {
    const anzeige = {
      station,
      name: namen[station],
      text,
      grund,
      aktuell: zustand === 'LAEUFT' || zustand === 'WARTET' || zustand === 'ABGEBROCHEN',
      ziel: station === ziel,
    }
    switch (zustand) {
      case 'ERLEDIGT':
        return {
          ...anzeige,
          symbol: stand.zielErreicht && station === stand.ziel ? 'ziel-erreicht' : 'erledigt',
        }
      case 'VOR_DEM_LAUF_ERBRACHT':
        return { ...anzeige, symbol: 'erbracht' }
      case 'LAEUFT':
        return { ...anzeige, symbol: 'laeuft' }
      case 'WARTET':
        return text === PROJEKTGRENZE
          ? {
              ...anzeige,
              symbol: 'projektgrenze',
              text: grund === null ? text : `${text}: ${grund}`,
              grund: null,
            }
          : { ...anzeige, symbol: 'wartet' }
      case 'ABGEBROCHEN':
        return { ...anzeige, symbol: 'abgebrochen' }
      case 'STEHT_AUS':
        return { ...anzeige, symbol: 'steht-aus' }
      case 'NICHT_VORGESEHEN':
        return { ...anzeige, symbol: 'nicht-vorgesehen' }
    }
  })
}

/** Die Ansage des Bands für Vorlesewerkzeuge: alle Stationen mit Text, Grund und Ziel. */
export function kettenAnsage(anzeige: readonly Stationsanzeige[], sprache: Sprache = 'de'): string {
  const t = woerterbuch(sprache).LAUF_FORTSCHRITT
  const teile = anzeige.map((a) => {
    const grund = a.grund === null ? '' : ' (' + a.grund + ')'
    return `${a.name} ${a.text}${grund}${a.ziel ? t.ZIEL : ''}`
  })
  return t.ANSAGE(teile.join('; '))
}

/** Was ein Lauf der Liste für das Zusammenführen mitbringen muss. */
export interface Listenlauf {
  /** Der Schlüssel, an dem die Seite einen Lauf wiedererkennt — auch für eingelesene Läufe. */
  startedAt: string
  /** `true` = vom Server geladen, `false` = allein im Browser (Nachtplan, eingelesener Lauf). */
  gespeichert: boolean
}

/**
 * Führt eine neu geladene Laufliste mit der bisherigen zusammen (Plan #1372 E11).
 *
 * <p>Die Server-Läufe ersetzen alles Servergeführte. Browser-eigene Läufe bleiben stehen, solange
 * der Server keinen Lauf mit demselben `startedAt` führt — ein Nachtplan oder ein eingelesener Lauf,
 * dessen Einlieferung scheiterte, verschwände sonst beim nächsten Takt. Sortiert wie die Liste der
 * Seite: jüngster Start zuerst.
 */
export function laeufeZusammenfuehren<T extends Listenlauf>(
  bisher: readonly T[],
  vomServer: readonly T[],
): T[] {
  const servergefuehrt = new Set(vomServer.map((l) => l.startedAt))
  const browsereigen = bisher.filter((l) => !l.gespeichert && !servergefuehrt.has(l.startedAt))
  return [...vomServer, ...browsereigen].sort((a, b) => b.startedAt.localeCompare(a.startedAt))
}

/** Die Texte des Zustandssatzes — der Block `NACHTLAUF_LAGE` einer Sprache. */
export type Lagetexte = Woerterbuch['NACHTLAUF_LAGE']

/** Die Paketzahlen, die der Satz eines abgeschlossenen Runs nennt. */
export interface Satzzahlen {
  gruen: number
  gelb: number
  rot: number
}

const nummernliste = (pakete: readonly PackageProgressView[]): string =>
  pakete.map((p) => `#${p.karte.number}`).join(', ')

const fertige = (pakete: readonly PackageProgressView[]): number =>
  pakete.filter((p) => p.zustand === 'FERTIG').length

const inUmsetzung = (pakete: readonly PackageProgressView[]): PackageProgressView[] =>
  pakete.filter((p) => p.zustand === 'IN_UMSETZUNG')

/** Die Stufe, an der eine Kette gerade arbeitet; `null` am Ende des Wegs. */
const laufendeStufe = (kette: ChainProgressView): ProgressStage | null =>
  kette.stufen.find((s) => s.zustand === 'LAEUFT')?.stufe ?? kette.aktuelleStufe

/** Der Satz einer einzelnen Kette. */
function kettensatz(kette: ChainProgressView, t: Lagetexte): string {
  const stufe = laufendeStufe(kette)
  if (stufe === null) {
    return t.ENDE_DES_WEGS(kette.anforderung.number)
  }
  const aktiv = inUmsetzung(kette.pakete)
  if (stufe === 'UMSETZUNG' && aktiv.length > 0) {
    return t.SETZT_UM(nummernliste(aktiv), aktiv.length > 1, fertige(kette.pakete), kette.pakete.length)
  }
  return kette.plan === null
    ? t.ZU_ANFORDERUNG(t.STUFE[stufe], kette.anforderung.number)
    : t.ZU_PLAN(t.STUFE[stufe], kette.plan.number)
}

/**
 * Der Zustandssatz der Lage eines Runs (Plan #1882 E4) — jeder Satz aus Daten, die schon vorliegen.
 *
 * <p>Laufend spricht der Fortschritt: an der Umsetzung die Pakete in Arbeit mit „k von n fertig“
 * (n = Pakete der Kette), in der Umsetzungsnacht ohne Nenner, weil die Zahl der anstehenden Pakete
 * dort unbekannt ist; an einer Planstufe die Stufe mit Plan oder, solange es keinen gibt,
 * Anforderung. Bei mehreren Ketten steht die mit laufender Stufe vorn. Abgeschlossen spricht der
 * Befund mit seinem Ausgangswort. Was nicht vorliegt, ergibt keinen Satz (`null`).
 *
 * @param befund der Befund des Servers; nur am abgeschlossenen Run gelesen
 * @param zahlen die Paketzahlen des abgeschlossenen Runs
 */
export function zustandssatz(
  fortschritt: NightRunProgressView | null,
  art: FortschrittArt,
  laeuft: boolean,
  befund: NightRunOutcomeView | null | undefined,
  zahlen: Satzzahlen,
  t: Lagetexte,
): string | null {
  if (!laeuft) {
    return befund == null ? null : t.ABGESCHLOSSEN(t.AUSGANG[befund.verdict], zahlen.gruen, zahlen.gelb, zahlen.rot)
  }
  if (fortschritt?.zuordnung !== 'OK') {
    return null
  }
  if (art === 'UMSETZUNGSNACHT') {
    const aktiv = inUmsetzung(fortschritt.pakete)
    const fertig = fertige(fortschritt.pakete)
    return aktiv.length === 0
      ? t.UMSETZUNG_NACHT(fertig)
      : t.SETZT_UM_NACHT(nummernliste(aktiv), aktiv.length > 1, fertig)
  }
  const [erste, ...weitere] = [...fortschritt.ketten].sort(
    (a, b) => Number(laufendeStufe(b) !== null) - Number(laufendeStufe(a) !== null),
  )
  if (erste === undefined) {
    return null
  }
  return kettensatz(erste, t) + (weitere.length === 0 ? '' : t.WEITERE_KETTEN(weitere.length))
}

/** Die verstrichene Zeit der laufenden Stufe und ihre Vorgabe; `vorgabeMs` ist `null` ohne Vorgabe. */
export interface Stufenzeit {
  verstrichenMs: number
  vorgabeMs: number | null
}

const MINUTE_MS = 60_000

/** Die Zeitvorgabe einer Stufe in Minuten; die Umsetzung hat keine (Plan #1882 E7). */
const vorgabeMinuten = (stufe: ProgressStage, budget: NightRunBudgetView): number | null => {
  switch (stufe) {
    case 'PLAN':
      return budget.planMin
    case 'REVIEW':
      return budget.reviewMin
    case 'PAKETE':
      return budget.paketeMin
    case 'ABDECKUNG':
      return budget.abdeckungMin
    case 'UMSETZUNG':
      return null
  }
}

/**
 * Die Zeit der laufenden Stufe gegen ihre Vorgabe (Plan #1882 E7) — nur an einer Stufe, die läuft
 * und ihren Beginn kennt; sonst `null`. Ein Beginn nach `jetzt` (Uhrenversatz) zählt als 0.
 */
export function stufenzeit(
  stufe: StageProgressView,
  budget: NightRunBudgetView | null,
  jetzt: number,
): Stufenzeit | null {
  if (stufe.zustand !== 'LAEUFT' || stufe.seit === null) {
    return null
  }
  const minuten = budget === null ? null : vorgabeMinuten(stufe.stufe, budget)
  return {
    verstrichenMs: Math.max(0, jetzt - Date.parse(stufe.seit)),
    vorgabeMs: minuten === null ? null : minuten * MINUTE_MS,
  }
}

/** Die Form eines Quadrats im Paketraster — die erste Auszeichnung, vor der Farbe (E6). */
export type Rasterform = 'gefuellt' | 'schraffiert' | 'umriss'

/** Die Farbe eines Quadrats: ein Melder oder „Text schwach“ der Palette. */
export type Rasterfarbe = 'gruen' | 'stahl' | 'zinnob' | 'text-schwach'

/** Wie ein Paket im Raster erscheint. */
export interface Rasterzustand {
  form: Rasterform
  farbe: Rasterfarbe
  /** Ob ein Querstrich das Quadrat durchzieht — am zurückgestellten Paket. */
  querstrich: boolean
}

const RASTER: Readonly<Record<PackageState, Rasterzustand>> = {
  FERTIG: { form: 'gefuellt', farbe: 'gruen', querstrich: false },
  IN_UMSETZUNG: { form: 'schraffiert', farbe: 'stahl', querstrich: false },
  GEZOGEN: { form: 'umriss', farbe: 'stahl', querstrich: false },
  ANGELEGT: { form: 'umriss', farbe: 'text-schwach', querstrich: false },
  ZURUECKGESTELLT: { form: 'gefuellt', farbe: 'zinnob', querstrich: true },
}

/** Form und Farbe eines Pakets im Raster (Plan #1882 E6). */
export function rasterzustand(zustand: PackageState): Rasterzustand {
  return RASTER[zustand]
}

/** Was eine Kettenzeile über die Kosten ihres Kettenvorgangs sagt. */
export interface Kettenkosten {
  betragUsd: number
  /** Betrag geteilt durch `budget.kostenUsd`, auch über 1; `null`, wo kein Balken steht. */
  anteil: number | null
  /** `true` an einer Kette mit Umsetzung: Der Betrag steht als „Planung“ ohne Balken. */
  planung: boolean
}

/** Ein Vorgang, soweit die Kettenkosten ihn brauchen. */
export interface Kostenvorgang {
  cardNumber: number
  verbrauch?: { kostenUsd: number | null }
}

/**
 * Die Kosten einer Kette (Plan #1882 E8): der Betrag des Vorgangs zur Anforderung. Einen Anteil am
 * Budget trägt nur eine Kette ohne Umsetzung (Variante A) — der Deckel einer Kette mit Umsetzung
 * kommt beim Board nicht an. Ohne gemeldeten Betrag `null`.
 */
export function kettenkosten(
  anforderung: CardRefView,
  weg: readonly StageProgressView[],
  items: readonly Kostenvorgang[],
  budget: NightRunBudgetView | null,
): Kettenkosten | null {
  const betragUsd = items.find((i) => i.cardNumber === anforderung.number)?.verbrauch?.kostenUsd ?? null
  if (betragUsd === null) {
    return null
  }
  if (weg.some((s) => s.stufe === 'UMSETZUNG')) {
    return { betragUsd, anteil: null, planung: true }
  }
  const deckel = budget?.kostenUsd ?? null
  return { betragUsd, anteil: deckel === null || deckel <= 0 ? null : betragUsd / deckel, planung: false }
}
