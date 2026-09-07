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

/**
 * Obergrenze fuer gespeicherte Auszuege — `excerpt` je Arbeitspaket und `unparsedSample` je Lauf
 * (Plan #718, A16). Die Zahl lebt je Sprache an genau einem Ort: hier und in
 * `NightRunLimits.EXCERPT_MAX`; ein JUnit-Test (`NightRunErrorClassSyncTest`) haelt beide samt der
 * Spaltenlaenge in `V29__night_run.sql` gleich. Liefen sie auseinander, antwortete ein Auszug knapp
 * ueber der Spaltengrenze mit 500 statt 400.
 *
 * <p>Gezaehlt wird in UTF-16-Codeeinheiten wie bei `TextLimits`; Postgres zaehlt Codepoints und ist
 * damit nie enger.
 */
export const NIGHT_RUN_EXCERPT_MAX = 4000

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
  /**
   * Das Rohprotokoll genau dieses Arbeitspakets — jede Zeile so, wie sie in der Datei
   * steht, samt Zeitstempel-Praefix und Sitzungsstrom (Plan #744, A1–A3). Leer, wenn das
   * Paket keine Session durchlaufen hat.
   *
   * <p><b>Diese Zeilen verlassen den Browser nicht.</b> Sie tragen Projekt-Quelltext,
   * Pfade und Sitzungs-IDs; `NightRunItemSubmission` und `zurEinlieferung` picken ihre
   * Felder einzeln und kennen `rawLines` nicht (Plan #718, A1).
   */
  rawLines: string[]
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
const FAIL = 'Fehler: '

/** Ein Lauf beginnt hier — **unmittelbar** nach dem Praefix, nie als Teilzeichenkette. */
const START = /^Nacht-Runner startet \(Modus (\w+)/

/** Abschluss eines Laufs; `Dry-Run beendet` markiert ihn zugleich als Probelauf. */
const ABSCHLUSS = /^(Nacht-Runner beendet|Nacht-Review beendet|Dry-Run beendet)/
const DRY_RUN = 'Dry-Run beendet'
const STUFE = /beendet \(Stufe ([^)]+)\)/

/**
 * Minuten aus einer Regex-Gruppe in Millisekunden. Der Runner schreibt sie mit
 * Nachkommastelle (`nach 8.8 min`) — an echten Protokollen belegt, nicht an Fixtures.
 */
const minuten = (wert: string): number => Math.round(Number(wert) * 60_000)

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
    re: /^ {2}Erfolg nach (\d+(?:\.\d+)?) min, Commit ([0-9a-f]{7,40}), Issue #(\d+) in In review\./,
    deute: (m) => ({ cardNumber: Number(m[3]), state: "GREEN", durationMs: minuten(m[1]), commit: m[2] }),
  },
  {
    re: /^ {2}Salvage erfolgreich, Commit ([0-9a-f]{7,40}), Issue #(\d+) in In review\./,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "GREEN", commit: m[1] }),
  },
  {
    re: /^ {2}FEHLSCHLAG nach (\d+(?:\.\d+)?) min: Issue #(\d+) nicht in In review UND Working Tree dirty/,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "RED", errorClass: "HARD_ABORT", durationMs: minuten(m[1]) }),
  },
  {
    re: /^ {2}Fehlschlag nach (\d+(?:\.\d+)?) min: Issue #(\d+) — die Session hat nichts hinterlassen/,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "RED", errorClass: "CHECKS_NOT_STARTED", durationMs: minuten(m[1]) }),
  },
  {
    re: /^ {2}Fehlschlag nach (\d+(?:\.\d+)?) min: Issue #(\d+) nicht in In review, Tree sauber/,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "RED", errorClass: "UNEXPECTED_STATE", durationMs: minuten(m[1]) }),
  },
  {
    re: /^ {2}INFRASTRUKTUR-FEHLSCHLAG nach (\d+(?:\.\d+)?) min .*Issue #(\d+) bleibt unangetastet\./,
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
    re: /^ {2}Erfolg nach (\d+(?:\.\d+)?) min: Issue #(\d+) geprueft (?:ohne|mit) Befund/,
    deute: (m) => ({ cardNumber: Number(m[2]), state: "GREEN", durationMs: minuten(m[1]) }),
  },
  {
    re: /^ {2}Nach (\d+(?:\.\d+)?) min: Issue #(\d+) — Befunde vorhanden, aber kein Body-Vorschlag/,
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
    re: /^#(\d+) uebersprungen: /,
    deute: (m) => ({ cardNumber: Number(m[1]), state: "GREY" }),
  },

  // --- Sessionbeginn: eroeffnet ein Arbeitspaket, dessen Ausgang noch aussteht.
  {
    re: /^(?:Review-)?Session \d+\/\d+: Issue #(\d+) — (.*)$/,
    deute: (m) => ({ cardNumber: Number(m[1]), title: m[2], eroeffnet: true }),
  },
]

/**
 * Beginn des Pruefblocks. Er schliesst das zuletzt offene Arbeitspaket (Plan #744, A1):
 * Ab hier ordnet die Nummer in der Zeile zu, nicht mehr der offene Puffer.
 */
const PRUEFBLOCK = /^Pruefungen der Sessions:$/

/**
 * Die fuehrende Kartennummer einer stummen Zeile — Sitzungsecho `  #100 > …` und die
 * Freigabezeile `#104 bewusst ohne Pruefung freigegeben …`. Sie entscheidet, ob die
 * Zeile noch zum offenen Arbeitspaket gehoert oder schon den naechsten Kandidaten
 * betrifft; ohne sie landete der Grund fuer das Uebergehen von #104 im Rohprotokoll
 * von #100.
 */
const STUMME_NUMMER = /^ {0,2}#(\d+) /

/**
 * Zeilen, die gedeutet sind, aber keinen Zustand tragen — Fortschrittsmeldungen des
 * Runners. Bewusst eine reine Musterliste ohne Deutungsfunktionen: Sie sind die
 * Mehrheit der Runner-Zeilen, und je eine leere Funktion waere Ballast ohne Aussage.
 */
const STUMME_MUSTER: readonly RegExp[] = [
  /^ {2}#\d+ > /, // Sitzungsecho — liefert die Zuordnung Zeile → Arbeitspaket
  /^#\d+ bewusst ohne Pruefung freigegeben/,
  /^ {2}Vorflug-Session (?:startet|nicht auswertbar)/,
  /^ {2}Reviewer .*: /,
  /^ {2}Kein Reviewer konfiguriert: /,
  /^ {2}Tracker \(.*\): /,
  /^ {2}buildChecks rot — einmaliger Format-Fix/,
  /^ {2}FORMAT-FIX angewendet/,
  /^ {2}SALVAGE-VERSUCH gestartet/,
  /^ {2}Salvage nicht moeglich: /,
  /^ {2}Hinweis: die vorherige Pruef-Zusammenfassung/,
  /^ {2}CLI-Meldung: /,
  /^ {2}I[nm] (?:Ready|Backlog) vorhandene Labels: /,
  /^ {2}Tippfehler im --(?:review-)?label-Wert\?/,
  /^WARNUNG: /,
  /^Ready ist leer — nichts zu tun\./,
  /^Keine Review-Kandidaten im Backlog/,
  /^Morgen-Ritual: /,
  PRUEFBLOCK,
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
  /** Das Arbeitspaket, dessen Rohprotokoll gerade mitgeschrieben wird. */
  aktuellesPaket: NightRunItem | null
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
  aktuellesPaket: null,
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
    rawLines: [],
  }
  a.items.push(neu)
  a.nachNummer.set(nummer, neu)
  return neu
}

/**
 * Wendet den Pruefblock auf ein bereits bekanntes Arbeitspaket an. Alle drei Muster
 * haengen die Rohzeile an das **per Nummer** referenzierte Paket an — unabhaengig davon,
 * ob sein Zustand kippt (Plan #744, A2). Eine Zeile zu einer unbekannten Nummer wird
 * verworfen.
 */
function deutePruefzeile(a: Aufbau, inhalt: string, roh: string): boolean {
  const gelaufen = PRUEF_GELAUFEN.exec(inhalt)
  if (gelaufen) {
    const item = a.nachNummer.get(Number(gelaufen[1]))
    item?.rawLines.push(roh)
    // Nur ein als erfolgreich gemeldetes Paket kippt auf gelb — ein rotes bleibt rot.
    if (item?.state === 'GREEN' && / -> rot /.test(gelaufen[2])) {
      item.state = 'YELLOW'
      item.errorClass = 'CHECKS_RED'
      item.excerpt = inhalt
    }
    return true
  }
  const ungeprueft = PRUEF_UNGEPRUEFT.exec(inhalt)
  if (ungeprueft) {
    const item = a.nachNummer.get(Number(ungeprueft[1]))
    item?.rawLines.push(roh)
    if (item?.state === 'GREEN') {
      item.state = 'YELLOW'
      item.errorClass = 'CHECKS_NOT_STARTED'
      item.excerpt = inhalt
    }
    return true
  }
  const leer = PRUEF_LEER.exec(inhalt)
  if (!leer) return false
  // Ein leeres Paket gilt als geprueft: Es gab nichts zu pruefen, und die
  // Erfolgszeile liegt vor. Der Zustand bleibt, wie er ist.
  a.nachNummer.get(Number(leer[1]))?.rawLines.push(roh)
  return true
}

/** Was die Deutung einer Zeile ueber ihre Zugehoerigkeit zu einem Arbeitspaket sagt. */
interface Deutung {
  /** Ein Muster griff. */
  gedeutet: boolean
  /** Die Kartennummer, die die Zeile nennt. */
  cardNumber?: number
  /** Das Arbeitspaket, das die Zeile eroeffnet. */
  eroeffnet?: NightRunItem
  /** Die Zeile ist bereits zugeordnet (Pruefblock) — nicht erneut anhaengen. */
  eigenzuordnung?: boolean
}

/**
 * Deutet eine Runner-Zeile.
 *
 * @param inhalt die Zeile ohne Zeitstempel-Praefix — daran greifen die Muster
 * @param roh die Zeile, wie sie in der Datei steht — sie geht ins Rohprotokoll
 */
function deuteZeile(a: Aufbau, inhalt: string, roh: string): Deutung {
  if (deutePruefzeile(a, inhalt, roh)) return { gedeutet: true, eigenzuordnung: true }
  if (STUMME_MUSTER.some((re) => re.test(inhalt))) {
    const nummer = STUMME_NUMMER.exec(inhalt)
    return { gedeutet: true, cardNumber: nummer ? Number(nummer[1]) : undefined }
  }
  for (const { re, deute } of MUSTER) {
    const m = re.exec(inhalt)
    if (!m) continue
    const t = deute(m)
    let eroeffnet: NightRunItem | undefined
    if (t.cardNumber !== undefined) {
      const item = paket(a, t.cardNumber, inhalt)
      if (t.title) item.title = t.title
      if (t.eroeffnet) eroeffnet = item
      else if (t.state) {
        item.state = t.state
        item.errorClass = t.errorClass
        item.excerpt = inhalt
        if (t.durationMs !== undefined) item.durationMs = t.durationMs
        if (t.commit !== undefined) item.commit = t.commit
      }
    }
    return { gedeutet: true, cardNumber: t.cardNumber, eroeffnet }
  }
  return { gedeutet: false }
}

/**
 * Schreibt die Rohzeile ins Protokoll des offenen Arbeitspakets — oder schliesst es.
 *
 * <p>Geschlossen wird bei einer neuen Session-Oeffnungszeile und bei jeder gedeuteten
 * Zeile mit **fremder** Kartennummer: Zwischen dem Ausgang eines Pakets und der
 * Oeffnungszeile des naechsten schreibt der Runner die Gate-Zeilen der uebergangenen
 * Kandidaten. Zeilen mit derselben Nummer — Sitzungsecho, Salvage, `HARTER STOPP`,
 * `CLI-Meldung` — bleiben beim Paket (Plan #744, A1).
 */
function uebernimm(a: Aufbau, roh: string, d: Deutung): void {
  if (d.eroeffnet) {
    a.aktuellesPaket = d.eroeffnet
    d.eroeffnet.rawLines.push(roh)
    return
  }
  const offen = a.aktuellesPaket
  if (!offen) return
  if (d.eigenzuordnung) return
  if (d.cardNumber !== undefined && d.cardNumber !== offen.cardNumber) {
    a.aktuellesPaket = null
    return
  }
  offen.rawLines.push(roh)
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
 * Beendet den offenen Aufbau und schreibt ihn ins Protokoll: Probelaeufe werden
 * nur gezaehlt, echte Laeufe in die Ausgabeform gebracht. Ohne offenen Aufbau
 * geschieht nichts.
 */
function schliesseAufbau(a: Aufbau | null, log: NightRunLog): void {
  if (!a) return
  if (a.dryRun) log.dryRunCount++
  else log.runs.push(fertig(a))
}

/**
 * Nimmt eine Zeile **ohne** Zeitstempel-Praefix auf.
 *
 * <p>Der zweite Schreiber: `fail()` haengt kein Praefix an und beendet den Lauf.
 * Alles andere ohne Praefix ist Sitzungsstrom — weder gedeutet noch gezaehlt,
 * aber Teil des Rohprotokolls des offenen Arbeitspakets. Ohne offenen Aufbau
 * gehoert die Zeile zu keinem Lauf.
 */
function deutePraefixlos(a: Aufbau | null, zeile: string): void {
  if (!a) return
  // Auch bei einem harten Abbruch ist genau diese Zeile die, die man im Rohprotokoll
  // sehen will — sie gehoert noch zum Paket und schliesst es erst danach.
  a.aktuellesPaket?.rawLines.push(zeile)
  if (!zeile.startsWith(FAIL)) return
  a.aktuellesPaket = null
  a.runState = 'RED'
  a.runErrorClass = 'HARD_ABORT'
  a.runExcerpt = zeile
  a.abgeschlossen = true
}

/**
 * Wertet Abschlusszeile und Pruefblock-Kopf aus. Beide schliessen das offene
 * Arbeitspaket und gehoeren selbst keinem mehr an (Plan #744, A1).
 */
function markiereAbschluss(a: Aufbau, inhalt: string): void {
  if (ABSCHLUSS.test(inhalt)) {
    a.abgeschlossen = true
    if (inhalt.startsWith(DRY_RUN)) a.dryRun = true
    a.stage = STUFE.exec(inhalt)?.[1] ?? a.stage
    a.aktuellesPaket = null
  }
  if (PRUEFBLOCK.test(inhalt)) a.aktuellesPaket = null
}

/**
 * Nimmt eine Zeile **mit** Zeitstempel-Praefix in den offenen Aufbau auf:
 * Abschluss markieren, deuten, Ungedeutetes zaehlen, Rohzeile zuordnen.
 *
 * @param inhalt die Zeile ohne Zeitstempel-Praefix — daran greifen die Muster
 * @param zeile die Zeile, wie sie in der Datei steht — sie geht ins Rohprotokoll
 */
function deuteMitPraefix(a: Aufbau, inhalt: string, zeile: string): void {
  markiereAbschluss(a, inhalt)

  const deutung = deuteZeile(a, inhalt, zeile)
  if (!deutung.gedeutet) {
    a.unparsedCount++
    if (a.unparsedSample.length < 5) a.unparsedSample.push(inhalt)
  }
  uebernimm(a, zeile, deutung)
}

/**
 * Zerlegt ein Nachtlauf-Protokoll in Auswertungen je Lauf.
 *
 * @param text der vollstaendige Dateiinhalt; Text vor der ersten Startzeile wird ignoriert
 */
export function parseNightRunLog(text: string): NightRunLog {
  const log: NightRunLog = { runs: [], dryRunCount: 0 }
  let a: Aufbau | null = null

  for (const rohzeile of text.split('\n')) {
    const zeile = rohzeile.replace(/\r$/, '')
    const m = PRAEFIX.exec(zeile)

    if (!m) {
      deutePraefixlos(a, zeile)
      continue
    }

    const [, zeitstempel, inhalt] = m
    const start = START.exec(inhalt)
    if (start) {
      schliesseAufbau(a, log)
      a = neuerAufbau(zeitstempel, start[1])
      continue
    }
    if (!a) continue // Runner-Zeilen vor dem ersten Start gehoeren zu keinem Lauf.

    a.letzterZeitstempel = zeitstempel
    deuteMitPraefix(a, inhalt, zeile)
  }
  schliesseAufbau(a, log)

  return log
}
