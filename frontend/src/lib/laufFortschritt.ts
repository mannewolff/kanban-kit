import type {
  ChainProgressView,
  KettenStand,
  KettenStation,
  NightRunProgressView,
  ProgressStage,
  StageState,
} from '../api/nightRuns'
import type { NightRunState } from './nightRunLog'

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

/** Der Zustand einer Stufe als Text — nie allein über die Farbe (E12). */
export type Stufentext = 'erreicht' | 'läuft' | 'offen'

/** Eine Stufe der Wegleiste. */
export interface Wegstufe {
  stufe: ProgressStage
  name: string
  zustand: Stufentext
  /** Ob der Lauf gerade an dieser Stelle steht. */
  aktuell: boolean
}

/** Die Wegleiste einer Kette: ihre Stufen und, wenn erreicht, das Ende des Wegs. */
export interface Wegleiste {
  stufen: Wegstufe[]
  /** {@link ENDE_DES_WEGS}, wenn jede Stufe erreicht ist; sonst `null`. */
  ende: string | null
}

/** Der Endzustand eines Wegs, der planmäßig endet — in Variante A nach der Abdeckung. */
export const ENDE_DES_WEGS = 'Ende des Wegs erreicht'

const STUFENNAME: Record<ProgressStage, string> = {
  PLAN: 'Plan',
  REVIEW: 'Prüfung',
  PAKETE: 'Arbeitspakete',
  ABDECKUNG: 'Abdeckung',
  UMSETZUNG: 'Umsetzung',
}

const STUFENTEXT: Record<StageState, Stufentext> = {
  ERREICHT: 'erreicht',
  LAEUFT: 'läuft',
  OFFEN: 'offen',
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
export function wegleiste(kette: ChainProgressView | null, art: FortschrittArt): Wegleiste {
  if (art === 'UMSETZUNGSNACHT') {
    return {
      stufen: [{ stufe: 'UMSETZUNG', name: STUFENNAME.UMSETZUNG, zustand: 'läuft', aktuell: true }],
      ende: null,
    }
  }
  if (kette === null) {
    return { stufen: [], ende: null }
  }
  return {
    stufen: kette.stufen.map((s) => ({
      stufe: s.stufe,
      name: STUFENNAME[s.stufe],
      zustand: STUFENTEXT[s.zustand],
      aktuell: s.stufe === kette.aktuelleStufe,
    })),
    ende: kette.endeErreicht ? ENDE_DES_WEGS : null,
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

/** Die Namen der Stationen der Stufenleiste — auch für die Übersicht „Heute Nacht“ (Issue #1455). */
export const STATIONSNAME: Record<KettenStation, string> = {
  ...STUFENNAME,
  VORBEREITUNG: 'Veröffentlichung vorbereitet',
}

/** Der Text, den der Server an der Station einer Projektgrenze setzt (Issue #1451). */
const PROJEKTGRENZE = 'Projektgrenze'

/**
 * Die Stufenleiste einer übernommenen Kette (Issue #1453, Plan #1447 E5, E6). Zustand, Text und
 * Grund kommen fertig vom Server; hier entstehen nur Symbol und Markierung. Nichts
 * wird aus Labels abgeleitet.
 */
export function kettenAnzeige(stand: KettenStand): Stationsanzeige[] {
  const ziel = stand.ziel ?? 'PAKETE'
  return stand.stationen.map(({ station, zustand, text, grund }) => {
    const anzeige = {
      station,
      name: STATIONSNAME[station],
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
export function kettenAnsage(anzeige: readonly Stationsanzeige[]): string {
  const teile = anzeige.map((a) => {
    const grund = a.grund === null ? '' : ' (' + a.grund + ')'
    return `${a.name} ${a.text}${grund}${a.ziel ? ', Ziel' : ''}`
  })
  return `Nacht-Kette: ${teile.join('; ')}`
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
