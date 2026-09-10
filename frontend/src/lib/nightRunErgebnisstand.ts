/**
 * Parser fuer den strukturierten Ergebnisstand des Nacht-Runners (Issue #773).
 *
 * <p>Reines Modul ohne React und ohne Netzwerk — dasselbe Muster wie `nightRunLog.ts`
 * und `specImport.ts`, und aus demselben Grund: **Die Datei verlaesst den Browser
 * nicht**, an den Server geht allein die verdichtete Auswertung (Plan #718, A1).
 *
 * <p><b>Warum neben dem Protokoll-Parser ein zweiter Weg steht:</b> `nightRunLog.ts`
 * deutet Zeilentexte und muss dazu rund 55 Meldungsformen kennen; der Ergebnisstand
 * (`.claude/night-run-<datum>-<uhrzeit>.json`, geschrieben von `night.mjs` seit
 * claude-workflow-kit#486) sagt dasselbe strukturiert. Beide muenden in denselben Typ
 * `NightRun` aus `nightRunLog.ts` — es gibt keine zweite Deklaration und keinen
 * zweiten Einlieferungsweg. Ein Test haelt beide Deutungen fuer jede Lage gegeneinander,
 * die der Ergebnisstand ueberhaupt erzeugen kann.
 *
 * <p><b>Die Fassungsnummer ist der Vertrag.</b> `schemaFassung: 1` sagt zu, welche
 * Felder es gibt; innerhalb einer bekannten Fassung wird deshalb nicht jedes Feld
 * einzeln nachgeprueft. Geprueft wird, was ueber die Deutbarkeit als Ganzes
 * entscheidet: die Fassung, die Lauf-Art und das Vokabular der Ausgaenge. Ein
 * unbekanntes Wort fuehrt zu einer Ablehnung, nie zu einer geratenen Farbe — im
 * Leitstand waere eine falsche Farbe schlimmer als ein ehrliches „nicht unterstuetzt".
 */

import {
  NIGHT_RUN_EXCERPT_MAX,
  type NightRun,
  type NightRunErrorClass,
  type NightRunItem,
  type NightRunMode,
  type NightRunState,
} from './nightRunLog'

/**
 * Warum ein Stand nicht deutbar war. Die drei Gruende sind bewusst getrennt: Sie
 * verlangen vom Menschen verschiedene Handgriffe — die falsche Datei gewaehlt, ein
 * neueres Kit, oder ein Lauf, den der Leitstand (noch) nicht auswertet.
 */
export type NightRunErgebnisstandGrund = 'kein-json' | 'unbekannte-fassung' | 'nicht-unterstuetzt'

export type NightRunErgebnisstandResult =
  | { ok: true; run: NightRun }
  | { ok: false; grund: NightRunErgebnisstandGrund }

/** Die einzige Fassung, die dieser Parser deutet. */
const FASSUNG = 1

/**
 * Der Pruefblock einer Einheit, so wie `lesePruefung` in `night.mjs` ihn schreibt.
 *
 * <p>`zustand` ist nicht optional: Fassung 1 sagt zu, dass ein vorhandener Pruefblock
 * ihn traegt. Ein Fallback darauf waere eine Kante, die kein Lauf je erzeugt.
 */
interface RohPruefung {
  zustand: string
  rotesKommando?: string
  rotesErgebnis?: string
  fehler?: string
}

/**
 * Eine Einheit des Ergebnisstands — ein Arbeitspaket der Runde. `ausgang` ist aus
 * demselben Grund wie `RohPruefung.zustand` nicht optional: `einheitAnlegen` setzt ihn
 * vor der Session auf `"unbekannt"`, noch bevor die Einheit erstmals geschrieben wird.
 */
interface RohEinheit {
  id: string
  titel: string
  ausgang: string
  grund?: string
  dauerMs?: number
  commit?: string | null
  pruefung?: RohPruefung
}

/** Der Lauf als Ganzes. */
interface RohLauf {
  schemaFassung?: unknown
  start: string
  art?: string
  /**
   * Nur bei `art: "erzeugung"` bedeutungstragend. `night.mjs` schreibt sie additiv seit
   * Version 1.51.0 in JEDEN Lauf (`stufe: args.stufe ?? null`) — ein Implementierungslauf
   * traegt sie deshalb als `null`, nicht als fehlendes Feld; nur Bestaende vor 1.51.0
   * (z. B. die Fixture vom 2026-09-07) kennen das Feld gar nicht.
   */
  stufe?: string | null
  einheiten?: unknown
  abschluss?: unknown
  fehlerklasse?: string
  fehlerText?: string
}

/** Zustand und Fehlerklasse eines Arbeitspakets — der Kern der Deutung. */
interface Farbe {
  state: NightRunState
  errorClass?: NightRunErrorClass
}

/**
 * Die Mapping-Tabelle aus Issue #773, eine Zeile je Pruefzustand. Als **Map** und
 * nicht als Bedingungskette: Die Tabelle ist die Spezifikation, und nur als Liste
 * laesst sich lesen, dass jede ihrer Zeilen genau einmal vorkommt. Ein Zustand, der
 * hier fehlt, ist damit automatisch „nicht unterstuetzt".
 *
 * <p>Der Auszug steht **einmal** je Zustand: Was der Nachweis sagt, haengt an ihm
 * selbst und nicht am Ausgang der Runde — `fehlschlag` und `erfolg` bei rotem Nachweis
 * unterscheiden sich in der Farbe, nicht im Satz. Fehlt eine Farbe zu einem Ausgang
 * (`fehlschlag` + `geprueft`), erzeugt der Runner dieses Paar nicht, und der Parser
 * lehnt es ab, statt es zu erraten.
 */
const NACH_ZUSTAND = new Map<
  string,
  { auszug: (p: RohPruefung) => string; erfolg?: Farbe; fehlschlag?: Farbe }
>([
  ['geprueft', { auszug: () => 'geprüft', erfolg: { state: 'GREEN' } }],
  ['leeresPaket', { auszug: () => 'leeres Paket', erfolg: { state: 'GREEN' } }],
  [
    'ungeprueft',
    {
      auszug: () => 'Nachweis fehlt — die Session hat keine Prüfung gefahren',
      erfolg: { state: 'YELLOW', errorClass: 'CHECKS_NOT_STARTED' },
      fehlschlag: { state: 'RED', errorClass: 'CHECKS_NOT_STARTED' },
    },
  ],
  [
    'unlesbar',
    {
      auszug: (p) => `Nachweis unlesbar (${p.fehler})`,
      erfolg: { state: 'YELLOW', errorClass: 'CHECKS_NOT_STARTED' },
      fehlschlag: { state: 'RED', errorClass: 'CHECKS_NOT_STARTED' },
    },
  ],
  [
    'rot',
    {
      auszug: (p) => `Nachweis rot — ${p.rotesKommando} endete ${p.rotesErgebnis}`,
      erfolg: { state: 'YELLOW', errorClass: 'CHECKS_RED' },
      fehlschlag: { state: 'RED', errorClass: 'CHECKS_RED' },
    },
  ],
])

/**
 * Die Sonderfaelle eines zurueckgestellten Pakets, **in dieser Reihenfolge**: Der
 * Grundtext ist nicht exklusiv (eine unerfuellte Abhaengigkeit kann `kit:klaeren`
 * erwaehnen), und der erste Treffer gewinnt. Ohne feste Ordnung kippte derselbe Text
 * je nach Auswertungsreihenfolge auf eine andere Farbe.
 */
const ZURUECKGESTELLT: ReadonlyArray<{ trifft: (grund: string) => boolean } & Farbe> = [
  { trifft: (g) => g.includes('Abhaengigkeit'), state: 'GREY', errorClass: 'DEPENDENCY_UNMET' },
  { trifft: (g) => g.includes('kit:klaeren'), state: 'RED', errorClass: 'AWAITING_DECISION' },
  {
    trifft: (g) => g.startsWith('Session ohne In-review-Ergebnis'),
    state: 'RED',
    errorClass: 'UNEXPECTED_STATE',
  },
]

/**
 * Jedes andere Zurueckstellen — die Praefix-Gates `[Fachlich]`/`[Idee]`/`[Plan]`, die
 * Ablehnungen des Review-Gates und jedes Gate, das das Kit spaeter ergaenzt. Grau ohne
 * Fehlerklasse: Das Paket wurde bewusst uebergangen, es ist nichts kaputt.
 */
const ZURUECKGESTELLT_SONST: Farbe = { state: 'GREY' }

/**
 * Was ein Ausgang ohne Pruefblock ueber das Paket sagt — die beiden Implementierungslauf-
 * Faelle `unbekannt`/`harterStopp` und, seit Plan #803, die vier festen Ausgaenge eines
 * Nachtplan-Laufs (`night.mjs` ab 1.51.0, Erzeugungsmodus). Alle vier tragen einen festen
 * Text ohne Laufzeit-Eingabe; nur `uebersprungen` braucht wegen seines dynamischen
 * Freitexts (`grund`) einen eigenen Zweig in {@link deuteEinheit}.
 */
const OHNE_PRUEFUNG = new Map<string, Farbe & { excerpt: string }>([
  [
    'unbekannt',
    {
      state: 'RED',
      errorClass: 'HARD_ABORT',
      excerpt: 'Lauf mitten in der Runde abgebrochen — kein Ausgang',
    },
  ],
  ['harterStopp', { state: 'RED', errorClass: 'HARD_ABORT', excerpt: 'Harter Stopp' }],
  [
    'verbraucht',
    { state: 'GREEN', excerpt: 'Dokument(e) erzeugt und geprüft — Label entfernt' },
  ],
  [
    'liegengeblieben',
    { state: 'GREY', excerpt: 'Über die Obergrenze (--max) hinaus — bleibt liegen' },
  ],
  [
    'offen',
    {
      state: 'GREY',
      excerpt: 'Noch nicht jedes erzeugte Dokument hat einen Endzustand — Label bleibt stehen',
    },
  ],
  [
    'ohneErgebnis',
    {
      state: 'RED',
      excerpt: 'Keine verwertbare Erzeugung — Session ohne Dokument oder Prüfrunde ohne Anker',
    },
  ],
])

/**
 * Bestimmt den Lauf-Modus aus `(art, stufe)` — eine Tupel-Tabelle statt einer
 * Bedingungskette, weil nur so ablesbar ist, dass jede Kombination genau einmal
 * entschieden wird (Plan #803, Architektonische Entscheidung 3/5). `null` liefert sie
 * fuer jede nicht ausdruecklich gelistete Kombination: Der Aufrufer lehnt dann ab, statt
 * zu raten (Praemisse aus Issue #773).
 *
 * <p>Nur `("erzeugung", "plan")` ist Nachtplan — auch der Geschwisterlauf
 * `("erzeugung", "issue")` (`kit:nightissues`, Arbeitspaket-Stufe desselben Runners)
 * bleibt bewusst aussen vor: eine generelle Oeffnung fuer jede `erzeugung`-Stufe waere
 * genau die Oeffnung, die Issue #802 als Nicht-Ziel ausschliesst.
 */
function bestimmeModus(art: string | undefined, stufe: string | null | undefined): NightRunMode | null {
  if (art === 'erzeugung' && stufe === 'plan') return 'NIGHTPLAN'
  if (art === 'implementierung' && (stufe === undefined || stufe === null)) return 'IMPLEMENTATION'
  if (art === undefined && (stufe === undefined || stufe === null)) return 'IMPLEMENTATION'
  return null
}

/** Auszuege gehen an den Server und teilen sich die Spaltengrenze mit dem Protokoll-Parser. */
const gekuerzt = (text: string): string => text.slice(0, NIGHT_RUN_EXCERPT_MAX)

/** Die Deutung einer Einheit; `null` heisst: Vokabular unbekannt, also nicht unterstuetzt. */
function deuteEinheit(e: RohEinheit): (Farbe & { excerpt: string }) | null {
  const ohnePruefung = OHNE_PRUEFUNG.get(e.ausgang)
  if (ohnePruefung) return ohnePruefung

  if (e.ausgang === 'zurueckgestellt') {
    const grund = typeof e.grund === 'string' ? e.grund : ''
    const farbe = ZURUECKGESTELLT.find((z) => z.trifft(grund)) ?? ZURUECKGESTELLT_SONST
    return { state: farbe.state, errorClass: farbe.errorClass, excerpt: gekuerzt(grund) }
  }

  // Modus-unabhaengig (Plan #803, Entscheidung 7): derselbe Ausgang kennt bereits der
  // Text-Protokoll-Parser (`nightRunLog.ts`, Muster `^#(\d+) uebersprungen: `). Fallback
  // `''` bei fehlendem/nicht-stringartigem `grund` spiegelt das Muster von `zurueckgestellt`
  // oben — kein Befund, deshalb keine Fehlerklasse.
  if (e.ausgang === 'uebersprungen') {
    const grund = typeof e.grund === 'string' ? e.grund : ''
    return { state: 'GREY', excerpt: gekuerzt(grund) }
  }

  if (e.ausgang !== 'erfolg' && e.ausgang !== 'fehlschlag') return null
  // Ein Ausgang aus einer Runde ohne Pruefblock ist unvollstaendig: Die Farbe haengt
  // am Nachweis, und ohne ihn bliebe nur Raten.
  const p = e.pruefung
  if (!p) return null
  const zeile = NACH_ZUSTAND.get(p.zustand)
  if (!zeile) return null
  const farbe = e.ausgang === 'erfolg' ? zeile.erfolg : zeile.fehlschlag
  if (!farbe) return null
  return { state: farbe.state, errorClass: farbe.errorClass, excerpt: gekuerzt(zeile.auszug(p)) }
}

/** Eine Einheit in ein Arbeitspaket der Auswertung uebersetzen. */
function baueItem(e: RohEinheit, position: number): NightRunItem | null {
  const deutung = deuteEinheit(e)
  if (!deutung) return null
  return {
    cardNumber: Number(e.id),
    title: e.titel,
    state: deutung.state,
    ...(deutung.errorClass === undefined ? {} : { errorClass: deutung.errorClass }),
    ...(typeof e.dauerMs === 'number' ? { durationMs: e.dauerMs } : {}),
    ...(typeof e.commit === 'string' ? { commit: e.commit } : {}),
    excerpt: deutung.excerpt,
    position,
    // Der Ergebnisstand traegt kein Rohprotokoll — anders als das Textprotokoll, aus
    // dem `nightRunLog.ts` die Zeilen je Paket mitschreibt.
    rawLines: [],
  }
}

/** Der Auszug eines Laufs, der hart gestoppt wurde. */
function laufAuszug(l: RohLauf): string {
  if (l.fehlerText !== undefined) return l.fehlerText
  // `merkeFehlerklasse` setzt die Klasse ohne Text — dann sagt wenigstens sie, wo es riss.
  return l.fehlerklasse === undefined ? 'Harter Stopp' : `Harter Stopp (${l.fehlerklasse})`
}

/**
 * Deutet den Ergebnisstand eines Nachtlaufs.
 *
 * @param text der vollstaendige Dateiinhalt
 */
export function parseNightRunErgebnisstand(text: string): NightRunErgebnisstandResult {
  let roh: unknown
  try {
    roh = JSON.parse(text)
  } catch {
    return { ok: false, grund: 'kein-json' }
  }
  if (typeof roh !== 'object' || roh === null || Array.isArray(roh) || !('schemaFassung' in roh)) {
    return { ok: false, grund: 'kein-json' }
  }

  const lauf = roh as unknown as RohLauf
  if (lauf.schemaFassung !== FASSUNG) return { ok: false, grund: 'unbekannte-fassung' }

  // Ein Pruef-Lauf traegt andere Ausgaenge und eine Stufe; ihn hier zu deuten hiesse,
  // ein zweites Vokabular zu erraten (Issue #773 grenzt ihn ausdruecklich aus). Diese
  // Ablehnung bleibt vor der Tupel-Tabelle stehen, weil `bestimmeModus` `art: "review"`
  // gar nicht kennt und sie sonst genauso als `null` ablehnen wuerde — hier aber bewusst
  // unbenannt bleiben soll, dass es sich um den ausgeschlossenen Pruef-Lauf handelt.
  if (lauf.art === 'review') return { ok: false, grund: 'nicht-unterstuetzt' }
  const modus = bestimmeModus(lauf.art, lauf.stufe)
  if (modus === null) return { ok: false, grund: 'nicht-unterstuetzt' }
  if (!Array.isArray(lauf.einheiten)) return { ok: false, grund: 'nicht-unterstuetzt' }
  if (lauf.abschluss !== null && typeof lauf.abschluss !== 'string') {
    return { ok: false, grund: 'nicht-unterstuetzt' }
  }

  const einheiten = lauf.einheiten as RohEinheit[]
  const items: NightRunItem[] = []
  for (const [position, e] of einheiten.entries()) {
    const item = baueItem(e, position)
    if (!item) return { ok: false, grund: 'nicht-unterstuetzt' }
    items.push(item)
  }

  const harterStopp = lauf.abschluss === 'harterStopp'
  return {
    ok: true,
    run: {
      startedAt: lauf.start,
      mode: modus,
      // Dokumentierte Untergrenze: die Summe der Runden, ohne die Zeit zwischen ihnen
      // (Board-Aufrufe, Gates). Der Stand traegt keinen Endzeitstempel.
      durationMs: einheiten.reduce((summe, e) => summe + (e.dauerMs ?? 0), 0),
      processedCount: items.filter((i) => i.state !== 'GREY').length,
      skippedCount: items.filter((i) => i.state === 'GREY').length,
      // Es gibt nichts Ungedeutetes: Was nicht ins Vokabular passt, hat den ganzen
      // Stand oben abgelehnt.
      unparsedCount: 0,
      unparsedSample: [],
      incomplete: lauf.abschluss === null,
      items,
      ...(harterStopp
        ? { runState: 'RED' as const, runErrorClass: 'HARD_ABORT' as const, runExcerpt: gekuerzt(laufAuszug(lauf)) }
        : {}),
    },
  }
}
