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
 *
 * <p><b>Der Pruef-Lauf ist seit Issue #816 deutbar.</b> Issue #773 hatte ihn ausdruecklich
 * ausgeschlossen; der Ausschluss ist damit aufgehoben. Sein Vokabular gilt aber **nur**
 * im Modus `REVIEW`: Die vier Pruef-only-Ausgaenge sagen in einem Implementierungs- oder
 * Nachtplan-Lauf nichts, und umgekehrt erzeugt ein Pruef-Lauf die Ausgaenge der anderen
 * Modi nie. Beide Richtungen lehnen deshalb ab, statt modus-unabhaengig weiterzudeuten.
 *
 * <p><b>Der Ketten-Lauf ist seit Issue #854 deutbar.</b> Eine Einheit ist dort kein
 * Arbeitspaket, sondern ein ganzer Vorgang (Plan, Pruefung, Pakete, Abdeckung zu einer
 * fachlichen Anforderung). Sein Vokabular ist nach denselben beiden Richtungen abgegrenzt
 * wie das des Pruef-Laufs ({@link NUR_KETTE}, {@link NIE_IN_KETTE}).
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

/**
 * Eine Ablehnung sagt seit Issue #857 mehr als nur ihren Grund: **welches Wort** nicht
 * gedeutet werden konnte und **welche Ausgabe** des Nachtlaufs die Datei geschrieben hat
 * (AK 10 aus Issue #842). Wer entscheiden will, ob er ein neueres Werkzeug braucht, muss
 * dafuer sonst in die Datei sehen.
 *
 * <p>Beide Felder sind optional, weil es Lagen ohne sie gibt und ein Platzhalter eine
 * Auskunft vortaeuschte: `wort` fehlt, wo gar kein Wort im Spiel ist (`einheiten` ist kein
 * Array, `abschluss` hat den falschen Typ), `erzeugtVon` fehlt, solange nichts geparst ist
 * oder das Kopffeld keine Zeichenkette traegt.
 */
export type NightRunErgebnisstandResult =
  | { ok: true; run: NightRun }
  | { ok: false; grund: NightRunErgebnisstandGrund; wort?: string; erzeugtVon?: string }

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

/** Was jede Stufe eines Ketten-Vorgangs traegt, unabhaengig von ihrer Art: ihre Dauer. */
interface RohStufe {
  dauerMs?: number
}

/**
 * Die Stufen eines Ketten-Vorgangs, so wie `stufenDerKette` in `night.mjs` sie schreibt —
 * in genau dieser Reihenfolge, siehe {@link KETTEN_STUFEN}. Ein eigenes Feld tragen nur die
 * beiden Stufen, die ein **Dokument** hinterlassen: Plan und Pakete.
 *
 * <p>`plan.id` ist `string | null`, weil `stufePlan` den Block **vor** der Session anlegt
 * (`{ id: null, dauerMs: 0, … }`). Ein vorhandener Block sagt also nur, dass die Stufe
 * begonnen hat — ob ein Plan entstand, sagt allein die ID.
 */
interface RohStufen {
  plan?: RohStufe & { id: string | null }
  review?: RohStufe
  pakete?: RohStufe & { ids: string[] }
  abdeckung?: RohStufe & { grund?: string }
}

/**
 * Eine Einheit des Ergebnisstands — ein Arbeitspaket der Runde, in einem Ketten-Lauf ein
 * ganzer Vorgang. `ausgang` ist aus demselben Grund wie `RohPruefung.zustand` nicht
 * optional: `einheitAnlegen` setzt ihn vor der Session auf `"unbekannt"`, noch bevor die
 * Einheit erstmals geschrieben wird.
 */
interface RohEinheit {
  id: string
  titel: string
  ausgang: string
  grund?: string
  dauerMs?: number
  commit?: string | null
  pruefung?: RohPruefung
  stufen?: RohStufen
}

/** Der Lauf als Ganzes. */
interface RohLauf {
  schemaFassung?: unknown
  /**
   * Die Kit-Ausgabe, die den Stand geschrieben hat (`night.mjs` schreibt sie in jeden Kopf).
   * `unknown` und nicht `string`: Der Wert geht nur in eine Meldung, und eine fremde Datei
   * darf dort nichts anderes als eine Zeichenkette einschleusen.
   */
  erzeugtVon?: unknown
  start: string
  art?: string
  /**
   * Bedeutungstragend bei `art: "erzeugung"` und — seit Issue #816 — bei `art: "review"`.
   * `night.mjs` schreibt sie additiv seit Version 1.51.0 in JEDEN Lauf
   * (`stufe: args.stufe ?? null`) — ein Implementierungslauf traegt sie deshalb als `null`,
   * nicht als fehlendes Feld; nur Bestaende vor 1.51.0 (z. B. die Fixture vom 2026-09-07)
   * kennen das Feld gar nicht.
   *
   * <p>Bei Bestaenden vor 1.51.0 (Feld fehlt) ist `"issue"` eine Annahme — die
   * tatsaechliche Stufe eines damaligen `--stufe plan`-Laufs stuende nirgends im Stand.
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
 * Die Ausgaenge, die **nur** ein Pruef-Lauf schreibt — `werteReviewSession` in `night.mjs`
 * (Issue #816). `syntheseOhneBeleg` fehlt hier, weil sein Auszug aus dem dynamischen
 * `grund` kommt und er deshalb einen eigenen Zweig in {@link deuteEinheit} braucht.
 *
 * <p>Die Farben sind die des Text-Protokoll-Parsers: `mitBefund` ist gruen, weil ein
 * Review MIT Befund die gelungene Runde ist — er wartet planmaessig auf den Menschen,
 * es ist nichts kaputt.
 */
const PRUEF_AUSGAENGE = new Map<string, Farbe & { excerpt: string }>([
  ['ohneBefund', { state: 'GREEN', excerpt: 'geprüft ohne Befund — Marker gesetzt' }],
  ['mitBefund', { state: 'GREEN', excerpt: 'geprüft mit Befund — kein Marker, wartet auf dich' }],
  [
    'schaerfungFehlt',
    {
      state: 'YELLOW',
      errorClass: 'CHECKS_NOT_STARTED',
      excerpt: 'Befunde vorhanden, aber kein Body-Vorschlag — Schärfung fehlt',
    },
  ],
  [
    'ohneErgebnis',
    {
      state: 'RED',
      errorClass: 'CHECKS_NOT_STARTED',
      excerpt: 'Die Review-Session hat nichts hinterlassen — weder Marker noch Befunde',
    },
  ],
])

/**
 * Die vier Pruef-only-Ausgaenge. In einem Implementierungs- oder Nachtplan-Lauf sagen
 * sie nichts; sie bleiben dort nicht unterstuetzt. `ohneErgebnis` steht bewusst NICHT
 * hier: Der Nachtplan-Lauf kennt ihn mit eigener Bedeutung (siehe {@link OHNE_PRUEFUNG}).
 */
const NUR_PRUEFLAUF: ReadonlySet<string> = new Set([
  'ohneBefund',
  'mitBefund',
  'schaerfungFehlt',
  'syntheseOhneBeleg',
])

/**
 * Umgekehrt: Ein Pruef-Lauf erzeugt diese Ausgaenge nie. Sie modus-unabhaengig
 * weiterzudeuten hiesse, fuer eine kaputte oder fremde Datei eine Farbe zu raten
 * (entschieden am 2026-09-11, Issue-Review zu #816, Fund 2).
 */
const NIE_IM_PRUEFLAUF: ReadonlySet<string> = new Set([
  'erfolg',
  'fehlschlag',
  'zurueckgestellt',
  'verbraucht',
  'offen',
])

/**
 * Die Ausgaenge eines Ketten-Laufs mit festem Text — `laufeEineKette` in `night.mjs`
 * (Plan #638). `abgebrochen` fehlt hier, weil Farbe und Auszug an seinem dynamischen
 * `grund` haengen; er bekommt einen eigenen Zweig in {@link deuteKettenAusgang} —
 * dasselbe Muster wie `syntheseOhneBeleg` im Pruef-Lauf.
 *
 * <p>`angehalten` ist rot mit `AWAITING_DECISION`: Die Kette hat genau eine Stopp-Frage
 * am Fachplan hinterlassen und wartet damit auf einen Menschen — dieselbe Lage wie ein
 * wegen `kit:klaeren` zurueckgestelltes Paket.
 */
const KETTEN_AUSGAENGE = new Map<string, Farbe & { excerpt: string }>([
  [
    'fertig',
    { state: 'GREEN', excerpt: 'Kette vollständig durchlaufen — Plan, Prüfung, Pakete, Abdeckung' },
  ],
  [
    'angehalten',
    {
      state: 'RED',
      errorClass: 'AWAITING_DECISION',
      excerpt: 'Stopp-Frage am Fachplan — die Kette wartet auf eine Entscheidung',
    },
  ],
])

/** Der Grund-Praefix, an dem `ketteSession` einen Zeitbudget-Abbruch erkennbar macht. */
const ZEITBUDGET_PRAEFIX = 'Zeitbudget '

/**
 * Die Ausgaenge, die **nur** ein Ketten-Lauf schreibt — analog {@link NUR_PRUEFLAUF}. In
 * jedem anderen Modus sagen sie nichts und bleiben dort nicht unterstuetzt.
 */
const NUR_KETTE: ReadonlySet<string> = new Set(['fertig', 'angehalten', 'abgebrochen'])

/**
 * Umgekehrt: Eine Kette erzeugt diese Ausgaenge nie — die vier Nachtplan-Ausgaenge, die
 * vier Pruef-Ausgaenge und das Vokabular des Implementierungslaufs. Sie modus-unabhaengig
 * weiterzudeuten hiesse, fuer eine kaputte oder fremde Datei eine Farbe zu raten; bei
 * `erfolg` faellt das besonders ins Gewicht, weil er sonst auf das bestehende Vokabular
 * durchfiele. `uebersprungen`, `liegengeblieben` und `unbekannt` fehlen bewusst: Die
 * schreibt der Kandidaten-Durchlauf der Kette genauso wie der jedes anderen Modus.
 */
const NIE_IN_KETTE: ReadonlySet<string> = new Set([
  'verbraucht',
  'offen',
  'ohneErgebnis',
  'ohneBefund',
  'mitBefund',
  'schaerfungFehlt',
  'syntheseOhneBeleg',
  'erfolg',
  'fehlschlag',
  'zurueckgestellt',
  'harterStopp',
])

/** Die Stufen, auf denen `night.mjs --review` laeuft. */
const REVIEW_STUFEN: ReadonlySet<string> = new Set(['fachlich', 'plan', 'issue'])

/** Die Stufe, mit der `runReviewLoop` ohne `--stufe` laeuft (`args.stufe ?? "issue"`). */
const REVIEW_STUFE_DEFAULT = 'issue'

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
 *
 * <p>`("review", <Stufe>)` ist seit Issue #816 der Pruef-Lauf — der Ausschluss aus
 * Issue #773 ist damit aufgehoben. Anders als beim Nachtplan zaehlt hier jede Stufe, auf
 * der der Runner tatsaechlich laeuft; eine unbekannte bleibt `null`.
 *
 * <p>`("kette", null)` ist seit Issue #854 der Ketten-Lauf. Die Kette traegt ihre Stufen
 * **in der Einheit** und schreibt am Lauf deshalb immer `stufe: null` (`--kette` kennt
 * kein `--stufe`); ein gesetzter Wert waere eine Kombination, die kein Runner erzeugt.
 */
function bestimmeModus(art: string | undefined, stufe: string | null | undefined): NightRunMode | null {
  if (art === 'erzeugung' && stufe === 'plan') return 'NIGHTPLAN'
  // Lose Gleichheit mit Bedacht: fehlendes und ausdruecklich leeres Feld sind hier dasselbe.
  if (art === 'kette' && stufe == null) return 'CHAIN'
  if (art === 'review' && (stufe === undefined || stufe === null || REVIEW_STUFEN.has(stufe))) return 'REVIEW'
  if (art === 'implementierung' && (stufe === undefined || stufe === null)) return 'IMPLEMENTATION'
  if (art === undefined && (stufe === undefined || stufe === null)) return 'IMPLEMENTATION'
  return null
}

/** Auszuege gehen an den Server und teilen sich die Spaltengrenze mit dem Protokoll-Parser. */
const gekuerzt = (text: string): string => text.slice(0, NIGHT_RUN_EXCERPT_MAX)

/**
 * Die Ausgaenge eines Pruef-Laufs, soweit sie nur dort vorkommen; `null` heisst: kein
 * Pruef-Ausgang, es gilt das modus-unabhaengige Vokabular weiter unten.
 */
function deutePruefAusgang(e: RohEinheit): (Farbe & { excerpt: string }) | null {
  const fest = PRUEF_AUSGAENGE.get(e.ausgang)
  if (fest) return fest
  if (e.ausgang !== 'syntheseOhneBeleg') return null
  // Der Grund traegt je unbelegtem Fund eine Zeile (`syntheseGrundText`). Fallback `''`
  // wie bei `zurueckgestellt`/`uebersprungen`: Der Ausgang steht auch ohne ihn fest.
  const grund = typeof e.grund === 'string' ? e.grund : ''
  return { state: 'RED', errorClass: 'AWAITING_DECISION', excerpt: gekuerzt(grund) }
}

/**
 * Ob die Kette bis zu ihrem Abbruch ein Dokument hinterlassen hat — die Frage, die beim
 * Zeitbudget ueber Gelb und Rot entscheidet: Ein Plan oder ein Arbeitspaket ist ein
 * verwertbares Ergebnis, das der Mensch weiterfuehren kann; ohne beides war die Nacht an
 * dieser Stelle umsonst.
 */
function dokumentEntstanden(stufen: RohStufen | undefined): boolean {
  if (stufen === undefined) return false
  // `plan.id` und nicht der blosse Block: Den legt `stufePlan` schon vor der Session an.
  if (typeof stufen.plan?.id === 'string') return true
  return stufen.pakete !== undefined && stufen.pakete.ids.length > 0
}

/**
 * Die Ausgaenge eines Ketten-Laufs, soweit sie nur dort vorkommen; `null` heisst: kein
 * Ketten-Ausgang, es gilt das modus-unabhaengige Vokabular weiter unten.
 */
function deuteKettenAusgang(e: RohEinheit): (Farbe & { excerpt: string }) | null {
  const fest = KETTEN_AUSGAENGE.get(e.ausgang)
  if (fest) return fest
  if (e.ausgang !== 'abgebrochen') return null
  // Fallback `''` wie bei `zurueckgestellt`/`syntheseOhneBeleg`: Der Ausgang steht auch
  // ohne Grund fest, und ohne Zeitbudget-Praefix ist er ein harter Abbruch.
  const grund = typeof e.grund === 'string' ? e.grund : ''
  if (!grund.startsWith(ZEITBUDGET_PRAEFIX)) {
    return { state: 'RED', errorClass: 'HARD_ABORT', excerpt: gekuerzt(grund) }
  }
  // Nur beim Zeitbudget rettet ein erzeugtes Dokument die Farbe. Kostenbudget und
  // technischer Fehler bleiben rot, auch wenn nebenbei etwas entstanden ist: Dort ist die
  // Kette nicht an ihrer Uhr, sondern an einem Befund gescheitert.
  const state = dokumentEntstanden(e.stufen) ? 'YELLOW' : 'RED'
  return { state, errorClass: 'TIME_BUDGET_EXCEEDED', excerpt: gekuerzt(grund) }
}

/** Eine erreichte Stufe, so wie ihre Zeile sie braucht: Name, Dokumente, eigener Grund. */
interface ErreichteStufe {
  name: string
  dokumente: string
  grund?: string
}

/**
 * Die erreichten Stufen in der Reihenfolge, in der `stufenDerKette` sie laeuft — aus ihr
 * allein folgt, wie jede ausging (siehe {@link stufenText}); der Stand fuehrt je Stufe
 * keinen eigenen Ausgang.
 *
 * <p>Dokumente nennen nur Plan und Pakete; Pruefung und Abdeckung hinterlassen einen Marker
 * beziehungsweise einen Text und haben nichts zu nennen. Einen eigenen `grund` traegt nur
 * die Abdeckung.
 */
function erreichteStufen(stufen: RohStufen): ErreichteStufe[] {
  const { plan, review, pakete, abdeckung } = stufen
  return [
    ...(plan === undefined
      ? []
      : [{ name: 'plan', dokumente: typeof plan.id === 'string' ? ` #${plan.id}` : '' }]),
    ...(review === undefined ? [] : [{ name: 'review', dokumente: '' }]),
    ...(pakete === undefined
      ? []
      : [
          {
            name: 'pakete',
            dokumente:
              pakete.ids.length === 0 ? '' : ` ${pakete.ids.map((id) => `#${id}`).join(', ')}`,
          },
        ]),
    ...(abdeckung === undefined
      ? []
      : [{ name: 'abdeckung', dokumente: '', grund: abdeckung.grund }]),
  ]
}

/** Der Ausgang der Einheit, wie ihn die letzte erreichte Stufe traegt — samt `grund`. */
function ausgangDerEinheit(e: RohEinheit): string {
  return typeof e.grund === 'string' ? `${e.ausgang} — ${e.grund}` : e.ausgang
}

/**
 * Welche Stufen der Vorgang durchlaufen hat und wie jede ausging (AK 2 aus Issue #842) —
 * als mehrzeiliger Text unter dem Auszug des Ausgangs, den der Leitstand mit
 * `whiteSpace: 'pre-wrap'` rendert (Plan #849, E2).
 *
 * <p><b>Die Ableitung (E3):</b> `stufenDerKette` laeuft die vier Stufen streng sequenziell
 * und bricht bei der ersten nicht fertigen ab; jede Stufenfunktion haengt ihren Stand
 * **vor** der Session ein. Eine Stufe, die im Stand steht, hat also begonnen, alle ausser
 * der letzten sind gelungen, und die letzte traegt den Ausgang der Einheit — bei `fertig`
 * ebenfalls „gelungen".
 *
 * <p><b>Die Ausnahme Abdeckung (E3):</b> Sie ist eine Auskunft, kein Tor — `stufeAbdeckung`
 * gibt auch bei Zeitbudget, Fehlstart oder fehlendem Text `{ ausgang: "fertig" }` zurueck
 * und legt den Grund an der Stufe ab. Ein vorhandener `grund` bestimmt deshalb ihre Zeile,
 * auch wenn die Einheit `fertig` ist; sonst hiesse eine gescheiterte Abdeckung „gelungen".
 *
 * @param kopf der Auszug des Ausgangs — er benennt Ausgang und `grund` der Einheit bereits
 *   ausformuliert und steht dem Stufenblock deshalb voran
 */
function stufenText(e: RohEinheit, kopf: string): string {
  if (e.stufen === undefined) return gekuerzt(kopf)
  const erreicht = erreichteStufen(e.stufen)
  const zeilen = erreicht.map((stufe, i) => {
    const letzte = i === erreicht.length - 1
    const ergebnis =
      typeof stufe.grund === 'string'
        ? stufe.grund
        : !letzte || e.ausgang === 'fertig'
          ? 'gelungen'
          : ausgangDerEinheit(e)
    return `${stufe.name}${stufe.dokumente}: ${ergebnis}`
  })
  return gekuerzt([kopf, ...zeilen].join('\n'))
}

/**
 * Die Dauer einer Einheit (Plan #849, E12): ihr eigenes `dauerMs`, sonst die Summe ueber
 * ihre Stufen — eine Ketten-Einheit traegt kein eigenes, jede ihrer Stufen eines.
 *
 * <p>`undefined` und nicht 0, wenn weder das eine noch das andere vorliegt: An einem
 * zurueckgestellten oder uebersprungenen Paket stuende sonst ploetzlich „0s", wo heute gar
 * keine Dauer steht (AK 9 aus Issue #842).
 */
function dauerDerEinheit(e: RohEinheit): number | undefined {
  if (typeof e.dauerMs === 'number') return e.dauerMs
  if (e.stufen === undefined) return undefined
  return Object.values(e.stufen).reduce<number>(
    (summe, stufe) => summe + (typeof stufe?.dauerMs === 'number' ? stufe.dauerMs : 0),
    0,
  )
}

/** Die Deutung einer Einheit; `null` heisst: Vokabular unbekannt, also nicht unterstuetzt. */
function deuteEinheit(e: RohEinheit, modus: NightRunMode): (Farbe & { excerpt: string }) | null {
  if (modus === 'REVIEW') {
    if (NIE_IM_PRUEFLAUF.has(e.ausgang)) return null
    const pruef = deutePruefAusgang(e)
    if (pruef) return pruef
  } else if (modus === 'CHAIN') {
    if (NIE_IN_KETTE.has(e.ausgang)) return null
    const ketten = deuteKettenAusgang(e)
    if (ketten) return ketten
  } else if (NUR_PRUEFLAUF.has(e.ausgang) || NUR_KETTE.has(e.ausgang)) return null

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
function baueItem(e: RohEinheit, position: number, modus: NightRunMode): NightRunItem | null {
  const deutung = deuteEinheit(e, modus)
  if (!deutung) return null
  const dauer = dauerDerEinheit(e)
  return {
    cardNumber: Number(e.id),
    title: e.titel,
    state: deutung.state,
    ...(deutung.errorClass === undefined ? {} : { errorClass: deutung.errorClass }),
    ...(dauer === undefined ? {} : { durationMs: dauer }),
    ...(typeof e.commit === 'string' ? { commit: e.commit } : {}),
    // Die Stufen stehen nur im Ketten-Stand; jeder andere Modus behaelt seinen Auszug.
    excerpt: modus === 'CHAIN' ? stufenText(e, deutung.excerpt) : deutung.excerpt,
    position,
    // Der Ergebnisstand traegt kein Rohprotokoll — anders als das Textprotokoll, aus
    // dem `nightRunLog.ts` die Zeilen je Paket mitschreibt.
    rawLines: [],
  }
}

/**
 * Eine Ablehnung, die beides mitnimmt, was AK 10 aus Issue #842 verlangt: das nicht gedeutete
 * Wort — sofern es an dieser Stelle ueberhaupt eines gibt — und die erzeugende Ausgabe des
 * Nachtlaufs, sofern der Stand sie als Zeichenkette fuehrt.
 *
 * <p>Ueber sie laeuft jede Ablehnung **nach** dem Parsen, einschliesslich der unbekannten
 * Aufbaufassung (E11 aus Plan #849): Gerade dort ist die Frage nach dem neueren Werkzeug am
 * dringendsten. Ohne geparstes Objekt (`kein-json`) gibt es nichts mitzunehmen; diese beiden
 * Rueckgaben stehen deshalb als einzige fuer sich.
 */
function abgelehnt(
  lauf: RohLauf,
  grund: NightRunErgebnisstandGrund,
  wort?: string,
): NightRunErgebnisstandResult {
  return {
    ok: false,
    grund,
    ...(wort === undefined ? {} : { wort }),
    ...(typeof lauf.erzeugtVon === 'string' ? { erzeugtVon: lauf.erzeugtVon } : {}),
  }
}

/**
 * Das Wort einer Modus-Ablehnung: nicht ein einzelnes Feld, sondern das Paar — erst beide
 * zusammen entscheiden ueber den Modus, und eines allein benannte die falsche Stelle.
 * Ein fehlendes oder leeres Feld heisst „ohne"; `undefined` und `null` sind fuer die
 * Modus-Bestimmung dasselbe und lesen sich hier deshalb auch gleich.
 */
const artUndStufe = (l: RohLauf): string => `art=${l.art ?? 'ohne'}/stufe=${l.stufe ?? 'ohne'}`

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
  if (lauf.schemaFassung !== FASSUNG) return abgelehnt(lauf, 'unbekannte-fassung')

  const modus = bestimmeModus(lauf.art, lauf.stufe)
  if (modus === null) return abgelehnt(lauf, 'nicht-unterstuetzt', artUndStufe(lauf))
  // Kein Wort: Was hier nicht stimmt, ist die Form des Felds, nicht eine Vokabel darin.
  if (!Array.isArray(lauf.einheiten)) return abgelehnt(lauf, 'nicht-unterstuetzt')
  if (lauf.abschluss !== null && typeof lauf.abschluss !== 'string') {
    return abgelehnt(lauf, 'nicht-unterstuetzt')
  }

  const einheiten = lauf.einheiten as RohEinheit[]
  const items: NightRunItem[] = []
  for (const [position, e] of einheiten.entries()) {
    const item = baueItem(e, position, modus)
    if (!item) return abgelehnt(lauf, 'nicht-unterstuetzt', e.ausgang)
    items.push(item)
  }

  const harterStopp = lauf.abschluss === 'harterStopp'
  return {
    ok: true,
    run: {
      startedAt: lauf.start,
      mode: modus,
      // Nur der Pruef-Lauf traegt eine Stufe — wie im Text-Parser, der sie aus der
      // Abschlusszeile `Nacht-Review beendet (Stufe …)` liest.
      ...(modus === 'REVIEW' ? { stage: lauf.stufe ?? REVIEW_STUFE_DEFAULT } : {}),
      // Dokumentierte Untergrenze: die Summe der Runden, ohne die Zeit zwischen ihnen
      // (Board-Aufrufe, Gates). Der Stand traegt keinen Endzeitstempel. Dieselbe Rechnung
      // wie am Arbeitspaket, damit Vorgangs- und Laufdauer per Konstruktion zusammenpassen;
      // eine Einheit ohne jede Dauer zaehlt hier wie bisher als 0 (E12).
      durationMs: einheiten.reduce((summe, e) => summe + (dauerDerEinheit(e) ?? 0), 0),
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
