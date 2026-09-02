/**
 * Parser fuer die Protokolle des Nacht-Runners (Issue #720).
 *
 * <p>Reines Modul ohne React und ohne Netzwerk — dasselbe Muster wie `listSort.ts` und
 * `epicTiles.ts`. Der inhaltliche Praezedenzfall ist `specImport.ts`: **Die Datei
 * verlaesst den Browser nicht.** Die Protokolle sind mehrere Megabyte gross und tragen
 * Projekt-Quelltext, Sitzungs-IDs und Pfade; an den Server geht allein die verdichtete
 * Auswertung (Entscheidung A1 des Plans #718).
 *
 * <p><b>Die Zuordnung Zeilenmuster → Ereignis ist eine explizite Tabelle</b>
 * ({@link MUSTER}), keine verstreute Bedingungskette: `night.mjs` schreibt rund 55
 * verschiedene Zeilen, und nur als Liste laesst sich auf Vollzaehligkeit pruefen. Der
 * Test „vollstaendiger Lauf" macht genau das zum Testergebnis.
 *
 * <p><b>Was als ungedeutet zaehlt, ist eng gefasst.</b> Eine Zeile ohne
 * Zeitstempel-Praefix gehoert zum Sitzungsstrom (rohes JSON der Headless-Sessions) und
 * wird weder gedeutet noch gezaehlt — sie stellt die Mehrheit der Datei. Die eine
 * Ausnahme ist `Fehler: …`: `night.mjs` hat zwei Schreiber, und `fail()` schreibt ohne
 * Praefix. Ohne diese Ausnahme verschluckte die Regel ausgerechnet die haertesten
 * Abbrueche.
 */

/**
 * Die Fehlerklassen als **Array**, nicht als blosse Typdeklaration: Eine TS-Union
 * existiert zur Laufzeit nicht und waere mit dem Java-Enum aus Issue #721 nicht
 * vergleichbar. Ein Test haelt beide Listen gleich.
 */
export const NIGHT_RUN_ERROR_CLASSES = [
  'CHECKS_RED',
  'CHECKS_NOT_STARTED',
  'DEPENDENCY_UNMET',
  'UNEXPECTED_STATE',
  'HARD_ABORT',
  'AWAITING_DECISION',
  'REVIEWER_FAILED',
] as const

export type NightRunErrorClass = (typeof NIGHT_RUN_ERROR_CLASSES)[number]

export type NightRunState = 'GREEN' | 'YELLOW' | 'RED' | 'GREY'

export type NightRunMode = 'IMPLEMENTATION' | 'REVIEW'

export interface NightRunItem {
  /** Projektweite Kartennummer des Arbeitspakets. */
  cardNumber: number
  /** Titel zum Zeitpunkt des Laufs; leer, wenn die Zeile ihn nicht traegt. */
  title: string
  state: NightRunState
  errorClass?: NightRunErrorClass
  durationMs?: number
  commit?: string
  /** Die Zeile, die den Zustand begruendet — ohne Zeitstempel-Praefix. */
  excerpt: string
  /** Reihenfolge im Lauf, bei 0 beginnend. */
  position: number
}

export interface NightRun {
  /** ISO-Zeitstempel der Startzeile — die Identitaet des Laufs (Plan #718, A4). */
  startedAt: string
  mode: NightRunMode
  /** Pruefstufe, nur im Review-Modus. */
  stage?: string
  durationMs: number
  processedCount: number
  skippedCount: number
  unparsedCount: number
  /** Hoechstens fuenf ungedeutete Runner-Zeilen, ohne Praefix. */
  unparsedSample: string[]
  /** Kein Abschluss gefunden — der Lauf laeuft noch oder wurde abgebrochen. */
  incomplete: boolean
  items: NightRunItem[]
  /**
   * Zustand auf Lauf-Ebene. Ein Lauf ohne Arbeitspaket traegt seinen Zustand hier —
   * etwa nach einem harten Abbruch durch `fail()`. Bleibt `undefined`, wenn die
   * Zustaende an den Arbeitspaketen haengen.
   */
  runState?: NightRunState
  runErrorClass?: NightRunErrorClass
  runExcerpt?: string
}

export interface NightRunLog {
  runs: NightRun[]
  /** Zahl der verworfenen Probelaeufe — die Oberflaeche meldet ein Protokoll aus lauter Probelaeufen als solches. */
  dryRunCount: number
}

/** `[ISO-Zeitstempel] Text` — so schreibt `log()` in `night.mjs`. */
const PRAEFIX = /^\[(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z)\] (.*)$/

/** Der zweite Schreiber: `fail()` schreibt ohne Praefix und beendet den Lauf. */
const FAIL = /^Fehler: /

/** Ein Lauf beginnt hier — **unmittelbar** nach dem Praefix, nie als Teilzeichenkette. */
const START = /^Nacht-Runner startet \(Modus (\w+)/

/** Abschluss eines Laufs; `Dry-Run beendet` markiert ihn zugleich als Probelauf. */
const ABSCHLUSS = /^(Nacht-Runner beendet|Nacht-Review beendet|Dry-Run beendet)/
const DRY_RUN = /^Dry-Run beendet/
const STUFE = /beendet \(Stufe ([^)]+)\)/

/** Minuten aus einer Regex-Gruppe in Millisekunden. */
const minuten = (wert: string): number => Number(wert) * 60_000

/** Was ein Muster ueber eine Zeile aussagt. */
interface Treffer {
  /** Kartennummer des betroffenen Arbeitspakets, falls die Zeile eine nennt. */
  cardNumber?: number
  title?: string
  state?: NightRunState
  errorClass?: NightRunErrorClass
  durationMs?: number
  commit?: string
  /** Die Zeile eroeffnet ein Arbeitspaket, ohne seinen Ausgang zu kennen. */
  eroeffnet?: boolean
}

/**
 * Die Musterliste. Reihenfolge ist bedeutsam: Der erste Treffer gewinnt, spezielle
 * Muster stehen vor allgemeinen (etwa `kit:klaeren` vor dem generischen
 * „uebersprungen").
 */
const MUSTER: ReadonlyArray<{ re: RegExp; deute: (m: RegExpExecArray) => Treffer }> = [
  // --- Implementierungs-Lauf, Ausgaenge je Arbeitspaket
  {
    re: /^ {2}Erfolg nach (\d+) min, Commit ([0-9a-f]{7,40}), Issue #(\d+) in In review\./,
    deute: (m) => ({ cardNumber: Number(m[3]), state: "GREEN", durationMs: minuten(m[1]), commit: m[2] }),
  },
  {
    re: /^ {2}Salvage erfolgreich, Commit ([0-9a-f]{7,40}), Issue #(\d+) in In review\./,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "GREEN", commit: m[1] }),
  },
  {
    re: /^ {2}FEHLSCHLAG nach (\d+) min: Issue #(\d+) nicht in In review UND Working Tree dirty/,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "RED", errorClass: "HARD_ABORT", durationMs: minuten(m[1]) }),
  },
  {
    re: /^ {2}Fehlschlag nach (\d+) min: Issue #(\d+) — die Session hat nichts hinterlassen/,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "RED", errorClass: "CHECKS_NOT_STARTED", durationMs: minuten(m[1]) }),
  },
  {
    re: /^ {2}Fehlschlag nach (\d+) min: Issue #(\d+) nicht in In review, Tree sauber/,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "RED", errorClass: "UNEXPECTED_STATE", durationMs: minuten(m[1]) }),
  },
  {
    re: /^ {2}INFRASTRUKTUR-FEHLSCHLAG nach (\d+) min .*Issue #(\d+) bleibt unangetastet\./,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "RED", errorClass: "HARD_ABORT", durationMs: minuten(m[1]) }),
  },
  {
    re: /^ {2}HARTER STOPP: (?:erfolgreiche Runde zu|die Review-Session zu) Issue #(\d+)/,
    deute: (m) => ({ cardNumber: Number(m[1]), state: "RED", errorClass: "HARD_ABORT" }),
  },
  {
    re: /^ {2}SALVAGE-VERSUCH gescheitert — harter Stopp\. Issue #(\d+)/,
    deute: (m) => ({ cardNumber: Number(m[1]), state: "RED", errorClass: "HARD_ABORT" }),
  },

  // --- Pruef-Lauf, Ausgaenge je Arbeitspaket
  {
    re: /^ {2}Erfolg nach (\d+) min: Issue #(\d+) geprueft (?:ohne|mit) Befund/,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "GREEN", durationMs: minuten(m[1]) }),
  },
  {
    re: /^ {2}Nach (\d+) min: Issue #(\d+) — Befunde vorhanden, aber kein Body-Vorschlag/,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "YELLOW", errorClass: "CHECKS_NOT_STARTED", durationMs: minuten(m[1]) }),
  },

  // --- Uebergangene Arbeitspakete. `kit:klaeren` steht vor dem generischen Muster:
  // eine offene Entscheidung ist kein blosses Ueberspringen, sondern rot.
  {
    re: /^ {2}#(\d+) (.*?) -> uebersprungen \(kit:klaeren/,
    deute: (m) => ({ cardNumber: Number(m[1]), title: m[2], state: "RED", errorClass: "AWAITING_DECISION" }),
  },
  {
    re: /^ {2}#(\d+) (.*?) -> uebersprungen \(/,
    deute: (m) => ({ cardNumber: Number(m[1]), title: m[2], state: "GREY" }),
  },
  {
    re: /^ {2}#(\d+) (.*?) -> ueber --max \d+, (?:bleibt|bliebe) liegen/,
    deute: (m) => ({ cardNumber: Number(m[1]), title: m[2], state: "GREY" }),
  },
  {
    re: /^#(\d+) zurueckgestellt: Abhaengigkeit/,
    deute: (m) => ({ cardNumber: Number(m[1]), state: "GREY", errorClass: "DEPENDENCY_UNMET" }),
  },
  {
    re: /^#(\d+) uebersprungen: traegt bereits einen Issue-Review-Marker\./,
    deute: (m) => ({ cardNumber: Number(m[1]), state: "GREY" }),
  },

  // --- Sessionbeginn: eroeffnet ein Arbeitspaket, dessen Ausgang noch aussteht.
  {
    re: /^(?:Review-)?Session \d+\/\d+: Issue #(\d+) — (.*)$/,
    deute: (m) => ({ cardNumber: Number(m[1]), title: m[2], eroeffnet: true }),
  },
]

/**
 * Zeilen, die gedeutet sind, aber keinen Zustand tragen — Fortschrittsmeldungen des
 * Runners. Bewusst eine reine Musterliste ohne Deutungsfunktionen: Sie sind die
 * Mehrheit der Runner-Zeilen, und je eine leere Funktion waere Ballast ohne Aussage.
 */
const STUMME_MUSTER: readonly RegExp[] = [
  /^ {2}#\d+ > /, // Sitzungsecho — liefert die Zuordnung Zeile → Arbeitspaket
  /^#\d+ bewusst ohne Pruefung freigegeben/,
  /^ {2}Vorflug-Session (?:startet|nicht auswertbar)/,
  /^ {2}Reviewer .* in \w+: /,
  /^ {2}Kein Reviewer konfiguriert: /,
  /^ {2}Tracker \(.*\): /,
  /^ {2}buildChecks rot — einmaliger Format-Fix/,
  /^ {2}FORMAT-FIX angewendet/,
  /^ {2}SALVAGE-VERSUCH gestartet/,
  /^ {2}Salvage nicht moeglich: /,
  /^ {2}Hinweis: die vorherige Pruef-Zusammenfassung/,
  /^ {2}CLI-Meldung: /,
  /^ {2}In (?:Ready|Backlog) vorhandene Labels: /,
  /^ {2}Tippfehler im --(?:review-)?label-Wert\?/,
  /^WARNUNG: /,
  /^Ready ist leer — nichts zu tun\./,
  /^Keine Review-Kandidaten im Backlog/,
  /^Morgen-Ritual: /,
  /^Pruefungen der Sessions:$/,
  /^Pruefungen: keine Implementierungs-Runde gelaufen\.$/,
  /^ {2}Summe: \d+ Session\(s\)/,
  ABSCHLUSS,
]

/** Zeilen des Pruefblocks — sie aendern den Zustand eines bereits bekannten Pakets. */
const PRUEF_GELAUFEN = /^ {2}Issue #(\d+): gelaufen: (.*?) \| ausgelassen:/
const PRUEF_UNGEPRUEFT = /^ {2}Issue #(\d+): ungeprueft — /
const PRUEF_LEER = /^ {2}Issue #(\d+): leeres Paket — /

/** Ein Lauf im Aufbau; `dryRun` entscheidet am Ende ueber Verwerfen. */
interface Aufbau {
  startedAt: string
  mode: NightRunMode
  stage?: string
  letzterZeitstempel: string
  dryRun: boolean
  abgeschlossen: boolean
  unparsedCount: number
  unparsedSample: string[]
  items: NightRunItem[]
  nachNummer: Map<number, NightRunItem>
  runState?: NightRunState
  runErrorClass?: NightRunErrorClass
  runExcerpt?: string
}

const neuerAufbau = (startedAt: string, modus: string): Aufbau => ({
  startedAt,
  mode: modus.toLowerCase().startsWith('review') ? 'REVIEW' : 'IMPLEMENTATION',
  letzterZeitstempel: startedAt,
  dryRun: false,
  abgeschlossen: false,
  unparsedCount: 0,
  unparsedSample: [],
  items: [],
  nachNummer: new Map(),
})

/** Legt ein Arbeitspaket an oder liefert das vorhandene — je Nummer genau eines. */
function paket(a: Aufbau, nummer: number, zeile: string): NightRunItem {
  const vorhanden = a.nachNummer.get(nummer)
  if (vorhanden) return vorhanden
  const neu: NightRunItem = {
    cardNumber: nummer,
    title: '',
    // Bis ein Ausgang bekannt ist, gilt der Abbruch: Ein eroeffnetes Paket ohne
    // Ausgang bedeutet, dass der Lauf mittendrin endete.
    state: 'RED',
    errorClass: 'HARD_ABORT',
    excerpt: zeile,
    position: a.items.length,
  }
  a.items.push(neu)
  a.nachNummer.set(nummer, neu)
  return neu
}

/** Wendet den Pruefblock auf ein bereits bekanntes Arbeitspaket an. */
function deutePruefzeile(a: Aufbau, zeile: string): boolean {
  const gelaufen = PRUEF_GELAUFEN.exec(zeile)
  if (gelaufen) {
    const item = a.nachNummer.get(Number(gelaufen[1]))
    // Nur ein als erfolgreich gemeldetes Paket kippt auf gelb — ein rotes bleibt rot.
    if (item?.state === 'GREEN' && / -> rot /.test(gelaufen[2])) {
      item.state = 'YELLOW'
      item.errorClass = 'CHECKS_RED'
      item.excerpt = zeile
    }
    return true
  }
  const ungeprueft = PRUEF_UNGEPRUEFT.exec(zeile)
  if (ungeprueft) {
    const item = a.nachNummer.get(Number(ungeprueft[1]))
    if (item?.state === 'GREEN') {
      item.state = 'YELLOW'
      item.errorClass = 'CHECKS_NOT_STARTED'
      item.excerpt = zeile
    }
    return true
  }
  // Ein leeres Paket gilt als geprueft: Es gab nichts zu pruefen, und die
  // Erfolgszeile liegt vor. Der Zustand bleibt, wie er ist.
  return PRUEF_LEER.test(zeile)
}

/** Deutet eine Runner-Zeile; liefert `false`, wenn kein Muster greift. */
function deuteZeile(a: Aufbau, zeile: string): boolean {
  if (deutePruefzeile(a, zeile)) return true
  if (STUMME_MUSTER.some((re) => re.test(zeile))) return true
  for (const { re, deute } of MUSTER) {
    const m = re.exec(zeile)
    if (!m) continue
    const t = deute(m)
    if (t.cardNumber !== undefined) {
      const item = paket(a, t.cardNumber, zeile)
      if (t.title) item.title = t.title
      if (t.eroeffnet) return true
      if (t.state) {
        item.state = t.state
        item.errorClass = t.errorClass
        item.excerpt = zeile
        if (t.durationMs !== undefined) item.durationMs = t.durationMs
        if (t.commit !== undefined) item.commit = t.commit
      }
    }
    return true
  }
  return false
}

/** Schliesst einen Lauf ab und bringt ihn in die Ausgabeform. */
function fertig(a: Aufbau): NightRun {
  const start = Date.parse(a.startedAt)
  const ende = Date.parse(a.letzterZeitstempel)
  return {
    startedAt: a.startedAt,
    mode: a.mode,
    ...(a.stage === undefined ? {} : { stage: a.stage }),
    durationMs: ende - start,
    processedCount: a.items.filter((i) => i.state !== 'GREY').length,
    skippedCount: a.items.filter((i) => i.state === 'GREY').length,
    unparsedCount: a.unparsedCount,
    unparsedSample: a.unparsedSample,
    incomplete: !a.abgeschlossen,
    items: a.items,
    ...(a.runState === undefined ? {} : { runState: a.runState }),
    ...(a.runErrorClass === undefined ? {} : { runErrorClass: a.runErrorClass }),
    ...(a.runExcerpt === undefined ? {} : { runExcerpt: a.runExcerpt }),
  }
}

/**
 * Zerlegt ein Nachtlauf-Protokoll in Auswertungen je Lauf.
 *
 * @param text der vollstaendige Dateiinhalt; Text vor der ersten Startzeile wird ignoriert
 */
export function parseNightRunLog(text: string): NightRunLog {
  const runs: NightRun[] = []
  let dryRunCount = 0
  let a: Aufbau | null = null

  const schliesse = () => {
    if (!a) return
    if (a.dryRun) dryRunCount++
    else runs.push(fertig(a))
    a = null
  }

  for (const rohzeile of text.split('\n')) {
    const zeile = rohzeile.replace(/\r$/, '')
    const m = PRAEFIX.exec(zeile)

    if (!m) {
      // Der zweite Schreiber: `fail()` haengt kein Praefix an und beendet den Lauf.
      if (a && FAIL.test(zeile)) {
        a.runState = 'RED'
        a.runErrorClass = 'HARD_ABORT'
        a.runExcerpt = zeile
        a.abgeschlossen = true
      }
      // Alles andere ohne Praefix ist Sitzungsstrom — weder gedeutet noch gezaehlt.
      continue
    }

    const [, zeitstempel, inhalt] = m
    const start = START.exec(inhalt)
    if (start) {
      schliesse()
      a = neuerAufbau(zeitstempel, start[1])
      continue
    }
    if (!a) continue // Runner-Zeilen vor dem ersten Start gehoeren zu keinem Lauf.

    a.letzterZeitstempel = zeitstempel
    if (ABSCHLUSS.test(inhalt)) {
      a.abgeschlossen = true
      if (DRY_RUN.test(inhalt)) a.dryRun = true
      a.stage = STUFE.exec(inhalt)?.[1] ?? a.stage
    }

    if (!deuteZeile(a, inhalt)) {
      a.unparsedCount++
      if (a.unparsedSample.length < 5) a.unparsedSample.push(inhalt)
    }
  }
  schliesse()

  return { runs, dryRunCount }
}
