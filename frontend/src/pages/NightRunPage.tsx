// Schriften des Entwurfs `docs/mockup-leitstand-nachtlauf.html`, mit der Anwendung ausgeliefert.
// AK 11 des Fachplans #903 verbietet den Bezug von einem fremden Server: Eine Instanz ohne
// Internetzugang soll dasselbe Schriftbild zeigen wie der Entwurf.
//
// Die Importe stehen hier und nicht in `main.tsx`, wo Carlito steht: Diese Seite ist ein lazy
// geladener Route-Chunk (`App.tsx`), ein Import in `main.tsx` lüde die sieben Schnitte in jede
// Seite der Anwendung — entgegen dem Performance-Budget aus `CLAUDE-react.md`.
//
// Je Datei ein Gewicht aus dem Latin-Subset, keine Sammelimporte der Pakete: Chivo führt neun
// Gewichte, IBM Plex Sans sieben — gebraucht werden sieben Schnitte insgesamt.
import '@fontsource/chivo/latin-600.css'
import '@fontsource/chivo/latin-800.css'
import '@fontsource/ibm-plex-sans/latin-400.css'
import '@fontsource/ibm-plex-sans/latin-500.css'
import '@fontsource/ibm-plex-sans/latin-600.css'
import '@fontsource/ibm-plex-mono/latin-400.css'
import '@fontsource/ibm-plex-mono/latin-600.css'
import Accordion from '@mui/material/Accordion'
import AccordionDetails from '@mui/material/AccordionDetails'
import AccordionSummary from '@mui/material/AccordionSummary'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Divider from '@mui/material/Divider'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { ThemeProvider } from '@mui/material/styles'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { cardsApi, type Card, type CardByNumber } from '../api/cards'
import { apiErrorMessage } from '../api/client'
import {
  nightRunsApi,
  type NightRunErrorClassCounts,
  type NightRunServerMode,
  type NightRunSubmission,
  type NightRunUsage,
  type NightRunUsageView,
  type NightRunView,
} from '../api/nightRuns'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { CardDetailModal } from '../components/CardDetailModal'
import {
  NachtlaufKennzahlen,
  type NachtlaufKennzahl,
} from '../components/nachtlauf/NachtlaufKennzahlen'
import { NachtlaufKopf } from '../components/nachtlauf/NachtlaufKopf'
import type { Kartenchip } from '../components/nachtlauf/NachtlaufKartenchips'
import { NachtlaufVorgang, type Chipgruppe } from '../components/nachtlauf/NachtlaufVorgang'
import {
  NachtlaufStufenband,
  type Bandabschnitt as BandabschnittForm,
} from '../components/nachtlauf/NachtlaufStufenband'
import { NachtlaufAnteilsbalken } from '../components/nachtlauf/NachtlaufAnteilsbalken'
import { NachtlaufVerbrauchBereich } from '../components/nachtlauf/NachtlaufVerbrauchBereich'
import { NachtlaufFuss, type Fussangabe as FussangabeForm } from '../components/nachtlauf/NachtlaufFuss'
import { NACHTLAUF_TON } from '../nachtlaufDesign'
import { nachtlaufTheme } from '../nachtlaufDesign'
import { useSnackbar } from '../components/SnackbarProvider'
import { formatDuration } from '../lib/formatDuration'
import { betrag, kosten, menge } from '../lib/nachtlaufFormat'
import {
  buildHandoffText,
  nightRunZustandsText,
  NIGHT_RUN_ERROR_CLASS_TEXT,
  type NightRunHandoffItem,
} from '../lib/nightRunHandoff'
import {
  parseNightRunErgebnisstand,
  NIGHT_RUN_AUSZUG_LIEGENGEBLIEBEN,
  type NightRunErgebnisstandGrund,
} from '../lib/nightRunErgebnisstand'
import {
  type NightRun,
  type NightRunErrorClass,
  type NightRunItem,
  type NightRunKennzahlen,
  type NightRunKettenStufe,
  type NightRunKettenStufen,
  type NightRunMode,
  type NightRunStand,
  type NightRunState,
  type NightRunStufenvorgaben,
} from '../lib/nightRunLog'
import { readTextFile } from '../lib/readTextFile'
import { useProjectName } from '../lib/useProjectName'

/**
 * Auswertung der Nachtläufe eines Projekts (Issue #725, Plan #718).
 *
 * **Gelesen wird der Ergebnisstand des Runners** (`night-run-<datum>-<uhrzeit>.json`, Issue #774),
 * nicht mehr das Textprotokoll: Der Stand sagt strukturiert, was die Deutung von rund 55
 * Meldungsformen zuvor erraten musste. Ein Lauf ohne Ergebnisstand ist damit nicht mehr
 * einlieferbar — das ist gewollt, eine geratene Farbe wäre im Leitstand schlimmer als ein
 * ehrliches „nicht auswertbar".
 *
 * **Die Datei verlässt den Browser nicht** (Entscheidung A1, Präzedenzfall `lib/specImport.ts`):
 * Sie wird über {@link readTextFile} eingelesen, mit {@link parseNightRunErgebnisstand} hier
 * gedeutet, und an den Server geht allein die verdichtete Auswertung. Der Stand trägt Pfade der
 * geänderten Dateien und die Kennzahlen der Sessions.
 *
 * **Die Herkunftskette wird erst beim Aufklappen eines Laufs aufgelöst** (A8). `cardsApi.byNumber`
 * liefert genau eine Karte; bis zu 190 aufbewahrte Läufe mit je 10 bis 15 Arbeitspaketen und zwei
 * Kettenschritten wären Tausende Anfragen bei jedem Seitenaufruf.
 */

/**
 * Ein Arbeitspaket in der Anzeigeform — frisch gedeutet und aufbewahrt sehen gleich aus.
 *
 * Es **erweitert** den Typ des Übernahmetexts (#727), statt seine Felder zu wiederholen: So ist an
 * der Deklaration ablesbar, dass hier genau das Arbeitspaket steht, aus dem `buildHandoffText` den
 * Text erzeugt — und ein fehlendes Feld bräche den Build, statt eine zweite Wahrheit anzulegen.
 *
 * `rawLines` bleibt unbesetzt: Der Ergebnisstand trägt kein Rohprotokoll (#773), also gibt es hier
 * keins mehr zu zeigen (#774).
 */
interface AnzeigeItem extends NightRunHandoffItem {
  durationMs: number | undefined
  /**
   * Der aufbewahrte Verbrauch (Issue #949); `undefined` allein am eben geparsten Lauf — dort gibt
   * es noch keinen aufbewahrten Stand. Ein aufbewahrter ohne Messung traegt vier leere Felder.
   */
  verbrauch: Verbrauch | undefined
}

/**
 * Der aufbewahrte Verbrauch in der Anzeigeform (Issue #949) — `undefined` statt `null`, wie
 * ueberall im Anzeigemodell (Issue #734). Jedes Feld fehlt einzeln: Ein hochgeladener Lauf traegt
 * einen Kostenbetrag ohne Mengen, ein gemeldeter beides.
 */
interface Verbrauch {
  kostenUsd: number | undefined
  eingabe: number | undefined
  ausgabe: number | undefined
  zwischenspeicher: number | undefined
}

/** Ein Lauf in der Anzeigeform. */
interface AnzeigeLauf {
  /**
   * `true` = vom Server geladen und damit Teil der aufbewahrten Läufe, `false` = eben erst im
   * Browser geparst. Nur ein aufbewahrter Lauf zeigt Häufigkeiten (#726): Der Endpunkt zählt
   * einschließlich des angezeigten Laufs, also stünde dieselbe Fehlerklasse einmal mit N und
   * einmal mit N-1 auf der Seite, wenn das Senden gescheitert ist.
   */
  gespeichert: boolean
  startedAt: string
  mode: NightRun['mode']
  durationMs: number
  processedCount: number
  skippedCount: number
  unparsedCount: number
  unparsedSample: string[]
  /**
   * Wie der Lauf hereinkam (Issue #949); `undefined` bei einem eben geparsten Lauf — der ist noch
   * gar nicht eingeliefert, und „hochgeladen" waere dort eine Behauptung ueber die Zukunft.
   */
  herkunft: NightRunView['origin'] | undefined
  tokenName: string | undefined
  eingeliefertAm: string | undefined
  zuletztGemeldetAm: string | undefined
  /** `false`, solange die Kette den Lauf nicht abgeschlossen gemeldet hat. */
  vollstaendig: boolean
  verbrauch: Verbrauch | undefined
  items: AnzeigeItem[]
}

/** Was zu einer projektweiten Kartennummer bekannt ist; `null` = nicht auflösbar (404). */
type Kartenkatalog = ReadonlyMap<number, CardByNumber | null>

/**
 * Die Vorhaben je Karten-**ID** (`parentId`), nicht je Kartennummer: Die Zuordnung eines
 * Arbeitspakets zu seinem Vorhaben läuft über die ID, der Herkunftsweg dagegen über die Nummer
 * (#818). Ein fehlender Schlüssel heißt „der Abruf läuft noch", `null` heißt „nicht abrufbar".
 */
type Vorhabenkatalog = ReadonlyMap<number, Card | null>

/** Je Fehlerklasse die Zahl der aufbewahrten Läufe, in denen sie vorkam; `null` = nicht abrufbar. */
type Haeufigkeiten = NightRunErrorClassCounts | null

/** Ein Schritt der Herkunftskette oberhalb des Arbeitspakets. */
interface Kettenglied {
  nummer: number
  karte: CardByNumber | null
}

/**
 * Die Zustandsfarben als Palettenpfade — nie als Hex-Literal (`lib/designGuard.ts` ließe das rot
 * werden) und nie über `palette.success/warning/error`, die im Frontend an Dutzenden Stellen für
 * Lösch-Buttons, Alerts und Feldfehler in Gebrauch sind (Plan #718, A15).
 */
const ZUSTAND_FARBE: Record<NightRunState, string> = {
  GREEN: 'nightRun.green',
  YELLOW: 'nightRun.yellow',
  RED: 'nightRun.red',
  GREY: 'nightRun.grey',
}

/**
 * Die Chip-Beschriftung je Lauf-Modus (Plan #803). Als `Record` über alle Werte, nicht als
 * Inline-Bedingung: Ein weiterer Modus bricht den Build, statt still auf „Umsetzungs-Lauf" zu
 * fallen — dieselbe Absicherung wie bei {@link ZUSTAND_FARBE}.
 */
const MODUS_TEXT: Record<NightRunMode, string> = {
  IMPLEMENTATION: 'Umsetzungs-Lauf',
  REVIEW: 'Prüf-Lauf',
  NIGHTPLAN: 'Nachtplan-Lauf',
  CHAIN: 'Ketten-Lauf',
}

/**
 * Die Stufen des Wegs von der fachlichen Anforderung über den Plan zum Arbeitspaket (#715). Die
 * Zuordnung läuft über die **Titel-Präfixe**, die der Workflow ohnehin vergibt (`/fachplan`,
 * `/plan`) — nicht über die Position in der `derivedFrom`-Kette: Ein Vorhaben oder eine Idee kann
 * dazwischenliegen, und dann bezeichnete die Position die falsche Stufe.
 */
const STUFEN: ReadonlyArray<{ label: string; praefix: string }> = [
  { label: 'Fachliche Anforderung', praefix: '[Fachlich]' },
  { label: 'Plan', praefix: '[Plan]' },
]

/**
 * Der Grundsatz zu einem nicht deutbaren Stand — eine Zeile je Grund aus
 * {@link parseNightRunErgebnisstand}. Die drei sind bewusst unterschieden: Sie verlangen vom
 * Betreiber verschiedene Handgriffe (die falsche Datei gewählt, ein neueres Kit, oder ein Lauf,
 * den der Leitstand nicht auswertet).
 *
 * <p>„Fassung" bezeichnet hier den **Aufbau** des Protokolls und nichts sonst; die erzeugende
 * Ausgabe des Nachtlaufs heißt in der Meldung „erzeugt von" (AK 10 aus Issue #842).
 */
const GRUNDSATZ: Record<NightRunErgebnisstandGrund, string> = {
  'kein-json': 'Nicht auswertbar',
  'unbekannte-fassung': 'Fassung nicht unterstützt',
  'nicht-unterstuetzt': 'Lauf-Art oder Vokabular nicht unterstützt',
}

/**
 * Was die Seite zu einem nicht deutbaren Stand sagt: der Grundsatz, das nicht gedeutete Wort —
 * sofern es eines gibt — und die Herkunft. Wer entscheiden will, ob er ein neueres Werkzeug
 * braucht, soll das aus der Meldung erfahren, statt dafür in die Datei zu sehen (AK 10 aus
 * Issue #842).
 *
 * <p>Die fehlende Herkunft wird **benannt** statt weggelassen: Eine Meldung, der man die
 * Auslassung nicht ansieht, ließe offen, ob der Leitstand nicht nachgesehen hat oder der Stand
 * nichts hergab.
 */
const nichtDeutbar = (ergebnis: {
  grund: NightRunErgebnisstandGrund
  wort?: string
  erzeugtVon?: string
}): string =>
  [
    GRUNDSATZ[ergebnis.grund],
    ...(ergebnis.wort === undefined ? [] : [`nicht gedeutet: ${ergebnis.wort}`]),
    ergebnis.erzeugtVon === undefined
      ? 'Herkunft nicht angegeben'
      : `erzeugt von ${ergebnis.erzeugtVon}`,
  ].join(' — ')

/**
 * Ein Lauf ohne Abschluss wird **angezeigt, aber nicht eingeliefert**: Der Server legt je
 * `(projectId, startedAt)` nur einmal an, ein unvollständiger Stand blockierte den späteren
 * vollständigen dauerhaft.
 */
const UNVOLLSTAENDIG = 'Lauf noch nicht abgeschlossen — nicht gespeichert'

/**
 * Holt die Häufigkeiten vom Server; ein Fehlschlag ergibt `null` statt einer Ausnahme. Die Zahlen
 * kommen bewusst vom Endpunkt und werden **nicht** aus den geladenen Läufen gerechnet (#726): Der
 * Ringpuffer liegt am Server, eine zweite Rechenstelle liefe auseinander. Scheitert der Abruf,
 * bleiben Läufe und Befunde sichtbar — deshalb hier kein `notify`, sondern ein Hinweis an der
 * Stelle, an der sonst die Zahl stünde.
 */
const zaehlerLaden = (projektId: number): Promise<Haeufigkeiten> =>
  nightRunsApi.errorClassCounts(projektId).catch(() => null)

const nachStartAbsteigend = (a: AnzeigeLauf, b: AnzeigeLauf) => b.startedAt.localeCompare(a.startedAt)

const ausParser = (run: NightRun): AnzeigeLauf => ({
  gespeichert: false,
  startedAt: run.startedAt,
  mode: run.mode,
  durationMs: run.durationMs,
  processedCount: run.processedCount,
  skippedCount: run.skippedCount,
  unparsedCount: run.unparsedCount,
  unparsedSample: run.unparsedSample,
  // Die Herkunftsfelder bleiben leer: Ein eben geparster Lauf ist nicht gespeichert. Die
  // Vollstaendigkeit kennt der Stand dagegen selbst.
  herkunft: undefined,
  tokenName: undefined,
  eingeliefertAm: undefined,
  zuletztGemeldetAm: undefined,
  vollstaendig: !run.incomplete,
  verbrauch: undefined,
  items: run.items.map((item) => ({
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    errorClass: item.errorClass,
    durationMs: item.durationMs,
    excerpt: item.excerpt,
    verbrauch: undefined,
  })),
})

/**
 * Der Verbrauch vom Server in der Anzeigeform.
 *
 * <p>Ein `null` des Servers wird zum **leeren Verbrauch** und nicht zu `undefined`: Beides heisst
 * hier Verschiedenes. Ein leerer Verbrauch ist ein aufbewahrter Lauf, an dem nichts gemessen wurde
 * — das gehoert als Fehlanzeige auf die Seite. `undefined` bleibt dem eben geparsten Lauf
 * vorbehalten, an dem es noch gar nichts aufzubewahren gab.
 */
const ausVerbrauch = (view: NightRunUsageView | null): Verbrauch =>
  view === null
    ? { kostenUsd: undefined, eingabe: undefined, ausgabe: undefined, zwischenspeicher: undefined }
    : {
        kostenUsd: view.costUsd ?? undefined,
        eingabe: view.inputTokens ?? undefined,
        ausgabe: view.outputTokens ?? undefined,
        zwischenspeicher: view.cachedInputTokens ?? undefined,
      }

/**
 * Der Lauf vom Server in der Anzeigeform (#725).
 *
 * **Hier wird `null` zu `undefined`** (Issue #734): Der Server schickt ein fehlendes Feld als
 * `null`, die Anzeigeform kennt dafür nur `undefined` — und `ausSicht` ist die einzige Stelle, an
 * der Server-Daten hereinkommen. Die Übersetzung gehört deshalb hierher und nicht in die
 * Renderpfade: `null` weiterzureichen ergäbe `null / 1000 === 0` als Dauer, `Auszug: null` im
 * Auszug und `Fehlerklasse: undefined` im Übernahmetext — drei stille Falschaussagen, die jeder
 * neue Leser des Anzeigemodells erneut abfangen müsste.
 */
const ausSicht = (view: NightRunView): AnzeigeLauf => ({
  gespeichert: true,
  startedAt: view.startedAt,
  mode: view.mode,
  durationMs: view.durationMs,
  processedCount: view.processedCount,
  skippedCount: view.skippedCount,
  unparsedCount: view.unparsedCount,
  unparsedSample: view.unparsedSample == null ? [] : view.unparsedSample.split('\n'),
  herkunft: view.origin,
  tokenName: view.tokenName ?? undefined,
  eingeliefertAm: view.createdAt,
  zuletztGemeldetAm: view.updatedAt ?? undefined,
  vollstaendig: view.complete,
  verbrauch: ausVerbrauch(view.usage),
  items: view.items.map((item) => ({
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    errorClass: item.errorClass ?? undefined,
    durationMs: item.durationMs ?? undefined,
    excerpt: item.excerpt ?? undefined,
    verbrauch: ausVerbrauch(item.usage),
  })),
})

/**
 * Ein Lauf, wie er an den Server geht: Kennzahlen, Zustände, Kartennummern, Fehlerklassen und die
 * kurzen Auszüge — nie die Datei. Optionale Felder werden weggelassen statt auf `undefined`
 * gesetzt, damit der Request-Body keine leeren Schlüssel trägt.
 *
 * `unparsedSample` geht seit Issue #774 gar nicht mehr hinaus: Der Ergebnisstand ist strukturiert
 * und kennt keine ungedeuteten Zeilen (#773) — was nicht ins Vokabular passt, lehnt der Parser als
 * Ganzes ab. Ein Zweig für einen Auszug, den es nicht geben kann, wäre unerreichbar. Aufbewahrte
 * Läufe aus der Zeit der Protokolldeutung tragen ihn weiterhin und zeigen ihn auch an.
 */
/**
 * Grenzt einen einlieferbaren Lauf typseitig ein (Plan #803, Entscheidung 8): `NIGHTPLAN` bleibt
 * browser-only, und dieser Type-Guard ist der einzige Weg, `zurEinlieferung` überhaupt aufzurufen —
 * kein Cast an `mode`, der hebelte den Schutz aus.
 */
function istEinlieferbar(run: NightRun): run is NightRun & { mode: NightRunServerMode } {
  return run.mode !== 'NIGHTPLAN'
}

/**
 * Der gemeldete Kostenbetrag als Verbrauchsangabe (Issue #948) — oder gar kein Schlüssel, wo
 * nichts gemessen wurde. `{ costUsd: undefined }` wäre der falsche Zwischenzustand: Er stünde im
 * Body als leeres Objekt und behauptete eine Messung ohne Wert.
 */
const alsVerbrauch = (kostenUsd: number | undefined): { usage?: NightRunUsage } =>
  kostenUsd === undefined ? {} : { usage: { costUsd: kostenUsd } }

const zurEinlieferung = (run: NightRun & { mode: NightRunServerMode }): NightRunSubmission => ({
  startedAt: run.startedAt,
  mode: run.mode,
  durationMs: run.durationMs,
  processedCount: run.processedCount,
  skippedCount: run.skippedCount,
  unparsedCount: run.unparsedCount,
  // Die Summe über **alle** Sitzungen des Laufs, nicht die über die Arbeitspakete: Die Differenz
  // beider Zahlen ist der keinem Paket zuordenbare Rest, und aus den Paketen gerechnet wäre er
  // per Konstruktion null.
  ...alsVerbrauch(run.stand?.kostenSumme),
  items: run.items.map((item) => ({
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    ...(item.errorClass === undefined ? {} : { errorClass: item.errorClass }),
    ...(item.durationMs === undefined ? {} : { durationMs: item.durationMs }),
    ...(item.commit === undefined ? {} : { commitHash: item.commit }),
    excerpt: item.excerpt,
    ...alsVerbrauch(item.kennzahlen?.kostenUsd),
  })),
})

/**
 * Die Vorfahren eines Arbeitspakets, von der nächsten Stufe aufwärts. Das `gesehen`-Set bricht
 * einen Herkunftsring ab: Er kann nur an der API vorbei entstehen (siehe `DerivationNode.broken`),
 * ließe die Schleife aber endlos laufen.
 */
function kette(start: number, katalog: Kartenkatalog): Kettenglied[] {
  const glieder: Kettenglied[] = []
  const gesehen = new Set<number>([start])
  let naechste = katalog.get(start)?.derivedFrom ?? null

  while (naechste !== null && !gesehen.has(naechste)) {
    gesehen.add(naechste)
    const karte = katalog.get(naechste) ?? null
    glieder.push({ nummer: naechste, karte })
    naechste = karte?.derivedFrom ?? null
  }
  return glieder
}

/**
 * Der Zustand einer Kettenstufe. Die fünf Fälle sind bewusst unterschieden (#715, A8):
 *
 * - **treffer** — die Stufe existiert; meldet der Lauf zu ihrer Karte einen roten Zustand, ist
 *   der Weg dort **abgerissen** (gescheitert oder auf eine Entscheidung wartend).
 * - **ohne** — das Arbeitspaket hat gar keinen Vorfahren. Kein Abriss, sondern ein legitim kurzer
 *   Weg (etwa ein Sonar-Befund).
 * - **noch-nicht-erreicht** — die Kette ist begonnen, aber diese Stufe fehlt. Sie zu erzeugen ist
 *   genau der Schritt, den der Nachtlauf heute nicht fährt.
 * - **nicht-gefunden** — eine Nummer der Kette ließ sich nicht auflösen; das ist weder „ohne"
 *   noch ein Abriss.
 * - **entfaellt** — die Frage stellt sich in diesem Lauf-Modus nicht; es erscheint **keine Zeile**
 *   (#858, siehe unten).
 *
 * Ein **Objekt statt eines fertigen Satzes** (#818): Nur der Treffer-Fall ist anklickbar, und
 * seine Karte liegt bereits hier vor. Ein String zwänge die Anzeige, ihn wieder zu zerlegen oder
 * die Karte ein zweites Mal zu suchen.
 */
type StufenZustand =
  | { art: 'treffer'; nummer: number; karte: CardByNumber; abgerissen: boolean }
  | { art: 'nicht-gefunden'; nummer: number }
  | { art: 'ohne' }
  | { art: 'noch-nicht-erreicht' }
  | { art: 'entfaellt' }

/** Die Fälle, die eine Zeile ergeben — `entfaellt` erscheint gar nicht und ist deshalb ausgenommen. */
type SichtbarerStufenZustand = Exclude<StufenZustand, { art: 'entfaellt' }>

/**
 * <p><b>Warum der Modus mitkommt (#858):</b> {@link kette} läuft `derivedFrom` **aufwärts**. Bei
 * einem Ketten-Lauf ist die Wurzelkarte selbst die fachliche Anforderung, und das Plan-Dokument,
 * das die Kette erzeugt hat, hängt **unterhalb** von ihr (echter Stand: Plan #849 mit
 * `derivedFrom` auf Fachplan #842). Aufwärts gedeutet hieße das „Fachliche Anforderung: ohne",
 * obwohl sie die Karte selbst ist, und „Plan: noch nicht erreicht", obwohl der Vorgang gerade
 * einen Plan erzeugt hat. Die Richtung passt für diesen Modus nicht — und die Auskunft steht
 * dort ohnehin vollständig im Auszug (Plan-Nummer und Paketnummern, #855). Deshalb entfallen im
 * Modus `CHAIN` beide Stufenzeilen, statt eine geratene Aussage zu zeigen.
 */
function stufenZustand(
  praefix: string,
  glieder: readonly Kettenglied[],
  istRot: (nummer: number) => boolean,
  modus: NightRunMode,
): StufenZustand {
  if (modus === 'CHAIN') {
    return { art: 'entfaellt' }
  }
  const geladen = glieder.filter(
    (glied): glied is { nummer: number; karte: CardByNumber } => glied.karte !== null,
  )
  const treffer = geladen.find((glied) => glied.karte.title.startsWith(praefix))
  if (treffer !== undefined) {
    return {
      art: 'treffer',
      nummer: treffer.nummer,
      karte: treffer.karte,
      abgerissen: istRot(treffer.nummer),
    }
  }
  const unbekannt = glieder.find((glied) => glied.karte === null)
  if (unbekannt !== undefined) {
    return { art: 'nicht-gefunden', nummer: unbekannt.nummer }
  }
  return glieder.length === 0 ? { art: 'ohne' } : { art: 'noch-nicht-erreicht' }
}

/** Der Satzrest hinter „<Stufe>: " in den drei Fällen ohne Karte. */
function ohneTrefferText(zustand: Exclude<SichtbarerStufenZustand, { art: 'treffer' }>): string {
  if (zustand.art === 'nicht-gefunden') {
    return `Karte #${zustand.nummer} nicht gefunden`
  }
  return zustand.art === 'ohne' ? 'ohne' : 'noch nicht erreicht'
}

/**
 * Eine Stufe der Herkunftskette als Zeile. Im Treffer-Fall ist **nur Nummer und Titel** der Link,
 * nicht die ganze Zeile: Der zugängliche Name trägt zusätzlich die Stufe („Plan #718 …"), und der
 * sichtbare Text bleibt sein Teilstring (WCAG 2.5.3). Der Abriss-Vermerk steht außerhalb des
 * Links — er sagt etwas über den Lauf, nicht über das Ziel des Klicks.
 */
function Stufenzeile({
  label,
  zustand,
  onOeffnen,
}: Readonly<{
  label: string
  zustand: SichtbarerStufenZustand
  onOeffnen: (karte: CardByNumber) => void
}>) {
  if (zustand.art !== 'treffer') {
    return (
      <Typography variant="body2" color="text.secondary">
        {`${label}: ${ohneTrefferText(zustand)}`}
      </Typography>
    )
  }
  return (
    <Typography variant="body2" color="text.secondary">
      {`${label}: `}
      <Link
        component="button"
        type="button"
        variant="body2"
        aria-label={`${label} #${zustand.nummer} ${zustand.karte.title}`}
        onClick={() => onOeffnen(zustand.karte)}
      >
        {`#${zustand.nummer} ${zustand.karte.title}`}
      </Link>
      {zustand.abgerissen ? ' — abgerissen' : null}
    </Typography>
  )
}

/**
 * Das Vorhaben, dem das Arbeitspaket zugeordnet ist (#818) — die vier sichtbaren Fälle:
 *
 * - keine Zuordnung → „ohne"; das ist kein Mangel, viele Pakete hängen an keinem Vorhaben.
 * - Karte vorhanden → Link auf ihre **projektweite Nummer**, nicht auf die `parentId`, über die
 *   abgerufen wurde: Die ID steht nirgends im Board.
 * - Abruf gescheitert (`null`) → „nicht auflösbar"; die übrige Zeile bleibt bedienbar.
 * - Abruf läuft noch (`undefined`) → **keine Zeile**. Eine Aussage über ein Vorhaben, das gerade
 *   erst geladen wird, wäre für einen Wimpernschlag falsch.
 */
function Vorhabenzeile({
  parentId,
  vorhabenKarten,
  onOeffnen,
}: Readonly<{
  parentId: number | null
  vorhabenKarten: Vorhabenkatalog
  onOeffnen: (karte: CardByNumber) => void
}>) {
  if (parentId === null) {
    return (
      <Typography variant="body2" color="text.secondary">
        Vorhaben: ohne
      </Typography>
    )
  }
  const karte = vorhabenKarten.get(parentId)
  if (karte === undefined) {
    return null
  }
  if (karte === null) {
    return (
      <Typography variant="body2" color="text.secondary">
        Vorhaben: nicht auflösbar
      </Typography>
    )
  }
  return (
    <Typography variant="body2" color="text.secondary">
      {'Vorhaben: '}
      <Link
        component="button"
        type="button"
        variant="body2"
        aria-label={`Vorhaben #${karte.number} ${karte.title}`}
        onClick={() => onOeffnen(karte)}
      >
        {`#${karte.number} ${karte.title}`}
      </Link>
    </Typography>
  )
}

/**
 * Die Häufigkeitszeile eines Arbeitspakets — `null`, wenn keine erscheint (Issue #726).
 *
 * Sie steht nur an einem **gelben oder roten** Befund eines **aufbewahrten** Laufs: Ein graues
 * Arbeitspaket trägt zwar eine Fehlerklasse (`DEPENDENCY_UNMET`), ist aber kein Befund, und ein
 * noch nicht gespeicherter Lauf ist in der Zählung des Servers noch nicht enthalten.
 *
 * Gezählt wird **einschließlich** des angezeigten Laufs, ein erstes Vorkommen ergibt also `1` —
 * dafür steht der verbindliche Wortlaut „zum ersten Mal" statt „1 von M", der sonst so klänge, als
 * sei der angezeigte Lauf nicht mitgezählt.
 */
function haeufigkeitsText(
  item: AnzeigeItem,
  gespeichert: boolean,
  zaehler: Haeufigkeiten,
  aufbewahrteLaeufe: number,
): string | null {
  if (!gespeichert || item.errorClass === undefined) {
    return null
  }
  if (item.state !== 'YELLOW' && item.state !== 'RED') {
    return null
  }
  const beschriftung = NIGHT_RUN_ERROR_CLASS_TEXT[item.errorClass]
  if (zaehler === null) {
    return `${beschriftung}: Häufigkeit nicht abrufbar`
  }
  // Eine Klasse, die der Server nicht nennt, kam nie vor — hier also: er weiß von diesem Lauf noch
  // nichts. Eine „0" zu einem sichtbaren Befund wäre ein Widerspruch, „zum ersten Mal" eine
  // Behauptung über eine Zählung, die es nicht gibt.
  const anzahl = zaehler[item.errorClass]
  if (anzahl === undefined) {
    return null
  }
  return anzahl === 1
    ? `${beschriftung}: zum ersten Mal`
    : `${beschriftung}: ${anzahl} von ${aufbewahrteLaeufe} aufbewahrten Läufen`
}

/**
 * Legt den Übernahmetext in die Zwischenablage (#727).
 *
 * Schlägt der Zugriff fehl — Clipboard-API nicht verfügbar, Berechtigung verweigert, unsicherer
 * Kontext —, bleibt es dabei: Der Text steht sichtbar im Feld und ist von Hand markierbar. Eine
 * zusätzliche Fehlermeldung wäre Lärm über etwas, das der Betreiber vor sich sieht. Der `try`
 * umfasst auch den Zugriff auf `navigator.clipboard` selbst: In einem unsicheren Kontext fehlt die
 * Eigenschaft, und das wirft synchron statt abzulehnen.
 */
async function inDieZwischenablage(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // Absichtlich still — siehe oben.
  }
}

/** Ein Arbeitspaket samt Zustand, Dauer, Auszug, Häufigkeit, Herkunftskette und Übernahmetext. */
function Arbeitspaket({
  item,
  modus,
  gekuerzt,
  katalog,
  vorhabenKarten,
  haeufigkeit,
  istRot,
  onOeffnen,
}: Readonly<{
  item: AnzeigeItem
  /** Der Modus des Laufs — er entscheidet, ob die Stufenzeilen überhaupt erscheinen (#858). */
  modus: NightRunMode
  /**
   * `true` an einem Lauf, über dem die Übersicht steht (Issue #869): Dann trägt die Zeile nur noch
   * Vorhaben und Übernahmetext. Zustand, Dauer und der Auszug samt Stufenblock stünden sonst ein
   * zweites Mal auf derselben Seite (AK 15 aus #859).
   */
  gekuerzt: boolean
  katalog: Kartenkatalog
  vorhabenKarten: Vorhabenkatalog
  haeufigkeit: string | null
  istRot: (nummer: number) => boolean
  onOeffnen: (karte: CardByNumber) => void
}>) {
  // `undefined` = noch nicht aufgelöst (die Kette lädt), `null` = nicht auflösbar.
  const wurzel = katalog.get(item.cardNumber)
  const beschriftung = `#${item.cardNumber} ${item.title}`

  return (
    <Box sx={{ py: 1 }} data-testid={`paket-${item.cardNumber}`}>
      {!gekuerzt && (
        <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap' }}>
          <Stack
            direction="row"
            alignItems="center"
            spacing={0.5}
            data-testid={`zustand-${item.cardNumber}`}
          >
            {/* Ampel-Fläche (#738): fixe Größe, unabhängig von der Länge des Zustandstexts daneben —
                wirkt als Signal statt als weitere Textzeile. Dekorativ und redundant zum Text, deshalb
                `aria-hidden` (CLAUDE-react.md Zeile 142: Farbe trägt die Aussage nie allein). */}
            <Box
              aria-hidden="true"
              data-testid={`ampel-${item.cardNumber}`}
              sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: ZUSTAND_FARBE[item.state], flexShrink: 0 }}
            />
            <Typography component="span" variant="body2">
              {nightRunZustandsText(item.state, item.errorClass)}
            </Typography>
          </Stack>
          {wurzel === null && (
            <Typography component="span">Karte #{item.cardNumber} nicht gefunden</Typography>
          )}
          {wurzel === undefined && <Typography component="span">{beschriftung}</Typography>}
          {wurzel != null && (
            <Link component="button" type="button" onClick={() => onOeffnen(wurzel)}>
              {beschriftung}
            </Link>
          )}
          {item.durationMs !== undefined && (
            <Typography component="span" color="text.secondary">
              {formatDuration(item.durationMs / 1000)}
            </Typography>
          )}
        </Stack>
      )}

      {!gekuerzt && item.excerpt !== undefined && (
        // Auszüge sind Fremdtext (Claude-Ausgaben, Ergebnisse fremder Werkzeuge) und werden
        // deshalb als reiner Text gerendert, nie über den Markdown-Renderer (CLAUDE-security.md).
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}>
          {(item.state === 'GREY' ? 'Grund: ' : 'Auszug: ') + item.excerpt}
        </Typography>
      )}

      <Paketzusaetze
        item={item}
        modus={modus}
        mitStufenzeilen={!gekuerzt}
        katalog={katalog}
        vorhabenKarten={vorhabenKarten}
        haeufigkeit={haeufigkeit}
        istRot={istRot}
        onOeffnen={onOeffnen}
      />
    </Box>
  )
}

/**
 * Was ein Vorgang über seine Gestalt hinaus trägt: Häufigkeit eines Befunds über die aufbewahrten
 * Läufe, die Herkunftskette, das Vorhaben der Wurzelkarte und der Übernahmetext samt Kopierknopf.
 *
 * <p>Seit #916 teilen sich {@link Arbeitspaket} und der Vorgangsblock des Entwurfs diese Teile.
 * Der Entwurf sieht für sie kein Element vor; AK 10 verlangt, sie in ihrer Funktion zu erhalten
 * und einzupassen — im Block stehen sie deshalb als dessen Kinder unter der Ergebniszeile.
 */
function Paketzusaetze({
  item,
  modus,
  /** `false` an einem Lauf, über dem eine Übersicht steht — dieselbe Bedingung wie bisher. */
  mitStufenzeilen,
  katalog,
  vorhabenKarten,
  haeufigkeit,
  istRot,
  onOeffnen,
}: Readonly<{
  item: AnzeigeItem
  modus: NightRunMode
  mitStufenzeilen: boolean
  katalog: Kartenkatalog
  vorhabenKarten: Vorhabenkatalog
  haeufigkeit: string | null
  istRot: (nummer: number) => boolean
  onOeffnen: (karte: CardByNumber) => void
}>) {
  const wurzel = katalog.get(item.cardNumber)
  // `null` an einem grünen oder grauen Arbeitspaket — dort erscheint weder Feld noch Knopf.
  const uebernahme = buildHandoffText(item)

  return (
    <>
      {/* Der aufbewahrte Verbrauch des Arbeitspakets (Issue #949) — hier und nicht in einer der
          drei Vorgangsformen: Er gilt fuer alle drei gleichermassen, und dreimal geschrieben liefe
          er beim naechsten Wortwechsel auseinander. Er steht nur, wo einer aufbewahrt ist: An
          einem eben geparsten Lauf gaebe es vier Fehlanzeigen zu lesen, die nichts ueber den Lauf
          sagen, sondern nur darueber, dass er noch nicht eingeliefert ist. */}
      {item.verbrauch !== undefined && (
        <VerbrauchsZeile
          verbrauch={item.verbrauch}
          testId={`paket-verbrauch-${item.cardNumber}`}
        />
      )}

      {haeufigkeit !== null && (
        <Typography
          variant="body2"
          color="text.secondary"
          data-testid={`haeufigkeit-${item.cardNumber}`}
        >
          {haeufigkeit}
        </Typography>
      )}

      {wurzel != null && (
        <>
          {mitStufenzeilen &&
            STUFEN.map((stufe) => {
              const zustand = stufenZustand(
                stufe.praefix,
                kette(item.cardNumber, katalog),
                istRot,
                modus,
              )
              // `entfaellt` ergibt **keine Zeile** — die Herkunft steht in diesem Modus im Auszug
              // (#858, siehe `stufenZustand`).
              return zustand.art === 'entfaellt' ? null : (
                <Stufenzeile
                  key={stufe.label}
                  label={stufe.label}
                  zustand={zustand}
                  onOeffnen={onOeffnen}
                />
              )
            })}
          {/* Das Vorhaben hängt an der **Wurzelkarte**, nicht an der Kette: Fachliche Anforderung
              und Plan tragen ebenfalls eine `parentId`, und deren Vorhaben wäre hier eine andere
              Aussage als die gesuchte. */}
          <Vorhabenzeile
            parentId={wurzel.parentId}
            vorhabenKarten={vorhabenKarten}
            onOeffnen={onOeffnen}
          />
        </>
      )}

      {uebernahme !== null && (
        // Der Text steht **immer** offen da, nie in einem eingeklappten Bereich: Er speist sich aus
        // Protokollauszügen, also aus Fremdtext (Claude-Ausgaben, Ergebnisse fremder Werkzeuge).
        // Ein unsichtbar kopierter Text wäre ein Weg von fremdem Text in die eigene
        // Entwicklungssitzung. Als reiner Wert eines Textfelds, nie über den Markdown-Renderer
        // (CLAUDE-security.md). `maxRows` begrenzt nur die Höhe — das Feld scrollt, der Wert
        // bleibt ungekürzt, sonst wanderte ein halbes Rohprotokoll in die Zwischenablage.
        <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ mt: 1 }}>
          <TextField
            fullWidth
            multiline
            minRows={3}
            maxRows={12}
            size="small"
            value={uebernahme}
            slotProps={{
              htmlInput: {
                readOnly: true,
                'aria-label': `Übernahmetext zu Karte #${item.cardNumber}`,
              },
            }}
          />
          <Button
            size="small"
            variant="outlined"
            startIcon={<ContentCopyIcon fontSize="small" />}
            aria-label={`Übernahmetext zu Karte #${item.cardNumber} kopieren`}
            onClick={() => void inDieZwischenablage(uebernahme)}
          >
            Kopieren
          </Button>
        </Stack>
      )}
    </>
  )
}

/**
 * Die vier Arbeitsschritte einer Kette in der Reihenfolge, in der `stufenDerKette` sie läuft —
 * derselbe Schlüsselraum wie {@link NightRunKettenStufen} und {@link NightRunStufenvorgaben}, also
 * stehen Vorgabe und Verbrauch eines Schritts unter demselben Namen.
 *
 * <p>Als Liste mit Beschriftung und nicht über `Object.keys`: Die Reihenfolge ist Teil der Aussage,
 * und ein fünfter Schritt bräuchte hier eine deutsche Benennung, statt still als Schlüssel
 * durchzurutschen — dieselbe Absicherung wie bei {@link MODUS_TEXT}.
 */
const KETTEN_STUFEN: ReadonlyArray<{ schluessel: NightRunKettenStufe; label: string }> = [
  { schluessel: 'plan', label: 'Plan' },
  { schluessel: 'review', label: 'Prüfung' },
  { schluessel: 'pakete', label: 'Pakete' },
  { schluessel: 'abdeckung', label: 'Abdeckung' },
]

/**
 * Wie ein Lauf endete (Plan #863, E5). Eine fehlende Angabe heißt „noch nicht abgeschlossen" und
 * nicht „vorzeitig beendet": `night.mjs` legt den Lauf ohne Abschluss an, und der Parser nimmt ihn
 * als unvollständig an.
 */
const ABSCHLUSS_TEXT = new Map<string, string>([
  ['regulaer', 'regulär beendet'],
  ['harterStopp', 'vorzeitig beendet (harter Stopp)'],
])

/**
 * Der Abschluss in Worten — **kein Fall gibt einen Rohwert aus**. `abschluss` ist im Stand eine
 * freie Zeichenkette; ein fremdes Wort ungeprüft auf die Seite zu stellen hieße, dem Betreiber eine
 * Auskunft zu geben, die der Leitstand selbst nicht versteht.
 */
const abschlussText = (abschluss: string | undefined): string =>
  abschluss === undefined
    ? 'noch nicht abgeschlossen'
    : (ABSCHLUSS_TEXT.get(abschluss) ?? 'Abschluss nicht deutbar')

/** Eine Minute in Millisekunden — die Zeitvorgaben stehen im Stand in Minuten, die Zeiten in ms. */
const MINUTE_MS = 60_000

/**
 * Minuten mit einer Nachkommastelle — die Schreibweise des Entwurfs
 * `docs/mockup-leitstand-nachtlauf.html` („7,8 / 20 min"). Nicht {@link formatDuration}: Die rundet
 * grobkörnig auf ganze Minuten, und ein Stufenband, dessen Zahlen alle gleich aussehen, sagt über
 * das Verhältnis zur Vorgabe nichts mehr.
 */
const MINUTEN_FORMAT = new Intl.NumberFormat('de-DE', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
})

/** Die Zeitvorgaben stehen ohne Nachkommastelle da, aber in deutscher Schreibweise. */
const ZAHL_FORMAT = new Intl.NumberFormat('de-DE')


/**
 * Der Vermerk fehlender Kostenmeldungen (AK 3 und AK 11 aus #859); `null`, wo nichts fehlt. Er
 * steht als Einschränkung neben der Summe, nicht als eigene Fehlermeldung: Die Summe stimmt, sie
 * ist nur unvollständig.
 *
 * <p>`eins` und `viele` benennen, **was** fehlt: Am Ketten-Lauf sind es die Arbeitsschritte einer
 * Kette, an den drei übrigen Lauf-Arten die Vorgänge der Nacht (#874). Eine zweite Funktion mit
 * derselben Pluralregel liefe beim nächsten Wortwechsel auseinander.
 */
function ohneKostenmeldung(
  anzahl: number | undefined,
  eins: string,
  viele: string,
): string | null {
  if (anzahl === undefined || anzahl === 0) {
    return null
  }
  return anzahl === 1 ? `${eins} ohne Kostenmeldung` : `${anzahl} ${viele} ohne Kostenmeldung`
}

/** Die Dokumente eines Arbeitsschritts — leer, wo der Vorgang ihn nicht mehr erreicht hat. */
const dokumenteDerStufe = (
  stufen: NightRunKettenStufen | undefined,
  schluessel: NightRunKettenStufe,
): readonly string[] => stufen?.[schluessel]?.dokumente ?? []

/**
 * Die Karten, die in dieser Nacht entstanden sind — Pläne und Pakete zusammen und **jede einmal**
 * (AK 2). Ein Set und keine Summe der Längen: Derselbe Plan kann in zwei Schritten auftauchen, und
 * doppelt gezählt wäre die Zahl größer als das, was am Board steht.
 */
function entstandeneDokumente(items: readonly NightRunItem[]): ReadonlySet<string> {
  return new Set(items.flatMap((item) => dokumenteDesVorgangs(item)))
}

/** Die Dokumente eines einzelnen Vorgangs, in der Reihenfolge der Kette. */
const dokumenteDesVorgangs = (item: NightRunItem): readonly string[] =>
  KETTEN_STUFEN.flatMap(({ schluessel }) => dokumenteDerStufe(item.kettenStufen, schluessel))

/**
 * Die Kartennummer einer Dokumentangabe. Der Stand führt sie als Zeichenkette; `NaN` bei einer
 * Angabe, die keine Nummer ist — der Katalog kennt dazu nichts, und der Verweis bleibt beim
 * schwächsten seiner drei Zustände stehen, statt eine Karte zu behaupten.
 */
const alsNummer = (id: string): number => Number.parseInt(id, 10)

/**
 * Die Kartennummern aller in einem Lauf entstandenen Dokumente (#868) — je Nummer einmal und ohne
 * die, die keine Nummer sind: Eine Anfrage auf `NaN` wäre eine Anfrage, die nie eine Karte finden
 * kann. `undefined` heißt: zu diesem Lauf liegt kein Ergebnisstand dieser Sitzung vor, und dann
 * gibt es auch keine Dokumente zu laden.
 */
const dokumentNummern = (run: NightRun | undefined): number[] =>
  run === undefined
    ? []
    : [...new Set(run.items.flatMap(dokumenteDesVorgangs).map(alsNummer))].filter(Number.isInteger)

/**
 * Der letzte Arbeitsschritt, den ein Vorgang erreicht hat; `undefined`, wenn er gar keinen erreichte
 * — ein übersprungener Vorgang etwa. **An ihm endete die Kette**, und nur dort steht der Vermerk,
 * wie sie endete: `stufenDerKette` in `night.mjs` läuft die vier streng der Reihe nach und bricht
 * beim ersten nicht fertigen ab.
 */
const letzteErreichteStufe = (
  stufen: NightRunKettenStufen | undefined,
): NightRunKettenStufe | undefined =>
  KETTEN_STUFEN.filter(({ schluessel }) => stufen?.[schluessel] !== undefined).at(-1)?.schluessel

/**
 * Der Grund, mit dem ein Vorgang endete: die **erste Zeile** seines Auszugs. Im Modus `CHAIN` setzt
 * `stufenText` (`lib/nightRunErgebnisstand.ts`) den gedeuteten Ausgang als Kopf vor den
 * Stufenblock; der rohe `grund` des Stands erreicht `NightRunItem` dagegen nie.
 */
const grundDesVorgangs = (item: NightRunItem): string => item.excerpt.split('\n')[0]

/**
 * Der Satz über dem Band eines Vorgangs, der nicht regulär endete: sein Grund und die Nummern der
 * bis dahin entstandenen Dokumente. Dass keine entstanden, wird **benannt** statt weggelassen —
 * eine Aufzählung, der man die Leere nicht ansieht, ließe offen, ob nichts entstand oder nichts
 * nachgesehen wurde.
 */
function abbruchSatz(item: NightRunItem): string {
  const nummern = dokumenteDesVorgangs(item).map((id) => `#${id}`)
  const entstanden = nummern.length === 0 ? 'keine Karten' : nummern.join(', ')
  return `${grundDesVorgangs(item)} — bis dahin entstanden: ${entstanden}`
}

/**
 * Die Summe der Zeiten aller Arbeitsschritte aller Vorgänge, in Millisekunden (AK 2). Ein nicht
 * erreichter Schritt zählt als 0 — er hat keine Zeit verbraucht.
 */
function stufenZeitSumme(items: readonly NightRunItem[]): number {
  const jeVorgang = (item: NightRunItem) =>
    KETTEN_STUFEN.reduce((summe, { schluessel }) => summe + (item.kettenStufen?.[schluessel]?.dauerMs ?? 0), 0)
  return items.reduce((summe, item) => summe + jeVorgang(item), 0)
}

/** Der höchste Kostenverbrauch eines einzelnen Vorgangs (AK 12). */
function hoechsteKosten(items: readonly NightRunItem[]): string {
  const gemeldet = items.flatMap((item) =>
    item.kennzahlen?.kostenUsd === undefined ? [] : [item.kennzahlen.kostenUsd],
  )
  return gemeldet.length === 0 ? 'nicht angegeben' : betrag(Math.max(...gemeldet))
}

/** Die Zeitvorgaben je Arbeitsschritt, in der Reihenfolge der Kette (AK 12). */
function vorgabenText(vorgaben: NightRunStufenvorgaben | undefined): string {
  const teile = KETTEN_STUFEN.flatMap(({ schluessel, label }) => {
    const minuten = vorgaben?.[schluessel]
    return minuten === undefined ? [] : [`${label} ${minuten}`]
  })
  return teile.length === 0 ? 'nicht angegeben' : `${teile.join(' · ')} min`
}

/**
 * Der Anteil der Modellarbeit an der Dauer eines Vorgangs (Issue #872) — vier unterschiedene
 * Lagen, keine davon geraten:
 *
 * - **anteil** — Arbeitszeit und Dauer liegen vor, die Arbeitszeit ist die kleinere.
 * - **gleichzeitig** — die gemeldete Arbeitszeit **übersteigt** die Dauer. Sie zu kappen
 *   behauptete „hundert Prozent Modellarbeit", eine Aussage über die Aufteilung, die die Daten
 *   nicht hergeben; der Rest wäre negativ. Belegt an Vorgang #782 der Nacht vom 11. September:
 *   18,1 Minuten Arbeitszeit bei 14,7 Minuten Dauer, weil mehrere Prüfer zugleich liefen.
 * - **ohne-dauer** — beide sind null. Ein Anteil an einer Dauer von null ist keine Aussage,
 *   und „0 % Modellarbeit" behauptete eine Restzeit, die es nicht gibt.
 * - **fehlt** — eine der beiden Angaben führt der Stand gar nicht.
 */
type Modellzeit =
  | { art: 'anteil'; prozent: number }
  | { art: 'gleichzeitig' }
  | { art: 'ohne-dauer' }
  | { art: 'fehlt' }

function modellzeit(dauerMs: number | undefined, arbeitMs: number | undefined): Modellzeit {
  if (dauerMs === undefined || arbeitMs === undefined) {
    return { art: 'fehlt' }
  }
  if (arbeitMs > dauerMs) {
    return { art: 'gleichzeitig' }
  }
  // Hier ist die Arbeitszeit höchstens so groß wie die Dauer; eine Dauer von null heißt also
  // auch eine Arbeitszeit von null — die Teilung unten braucht keinen weiteren Schutz.
  return dauerMs === 0
    ? { art: 'ohne-dauer' }
    : { art: 'anteil', prozent: Math.round((arbeitMs / dauerMs) * 100) }
}

/**
 * Der Modellzeit-Anteil in Worten. **Die Restzeit wird nie beziffert und nie benannt**: Das
 * Protokoll misst ihre Aufteilung nicht — sie enthält neben der Wartezeit auf Bauen, Testen und
 * Versionsverwaltung auch die Prüfung durch den Nachtlauf selbst. „Werkzeugzeit: 26 %" wäre eine
 * Zahl über etwas, das nirgends gemessen wurde.
 */
function modellzeitText(dauerMs: number | undefined, kennzahlen: NightRunKennzahlen | undefined): string {
  const zeit = modellzeit(dauerMs, kennzahlen?.arbeitszeitMs)
  if (zeit.art === 'anteil') {
    return `Modellarbeit ${zeit.prozent} % der Dauer, der Rest außerhalb`
  }
  if (zeit.art === 'gleichzeitig') {
    return 'Modellarbeit: mehrere Arbeiten liefen gleichzeitig'
  }
  return zeit.art === 'ohne-dauer'
    ? 'Modellarbeit: ohne messbare Dauer kein Anteil'
    : 'Modellzeit nicht gemeldet'
}

/**
 * Dauer, Kosten, Züge und Modellzeit eines bearbeiteten Vorgangs (Issue #872). Jede der vier
 * Angaben steht entweder als Wert da oder benennt ihr Fehlen — ein ausgelassener Platz ließe
 * offen, ob nichts gemeldet wurde oder nichts nachgesehen.
 *
 * <p>Eine **gemessene Null** ist dabei ein Wert: Der fortgesetzte Vorgang #535 der Nacht vom
 * 9. September trägt die Dauer null, weil in dieser Nacht keine Sitzung mehr lief. „0 s" ist dort
 * die Wahrheit, „nicht gemeldet" wäre die Lüge in die andere Richtung.
 *
 * <p><b>`ohneKennzahlen`</b> sagt, dass der Lauf die Sitzungs-Kennzahlen gar nicht erst angefordert
 * hat (Issue #874, Plan #864 E8). Dann bleibt allein die Dauer stehen — sie stammt aus dem Lauf
 * selbst und ist davon unberührt. Die drei Fehlanzeigen entfielen sonst nicht, obwohl der Lauf
 * ihren Grund bereits einmal an seinem Kopf nennt.
 */
function vorgangszeile(item: NightRunItem, ohneKennzahlen: boolean): string {
  const dauer =
    item.durationMs === undefined ? 'Dauer nicht gemeldet' : formatDuration(item.durationMs / 1000)
  if (ohneKennzahlen) {
    return dauer
  }
  return [
    dauer,
    item.kennzahlen?.kostenUsd === undefined
      ? 'Kosten nicht gemeldet'
      : betrag(item.kennzahlen.kostenUsd),
    item.kennzahlen?.zuege === undefined ? 'Züge nicht gemeldet' : `${item.kennzahlen.zuege} Züge`,
    modellzeitText(item.durationMs, item.kennzahlen),
  ].join(' · ')
}

/**
 * Die Ergebniszeile eines Ketten-Vorgangs (#916): Kosten, Züge, der Vermerk fehlender
 * Kostenmeldungen — und der **Anteil der Modellarbeit** (AK 6, seit Issue #872). Der Entwurf führt
 * ihn in jeder Ergebniszeile; bis #916 stand er allein an den drei Nicht-Ketten-Arten.
 */
const vorgangsKennzahlenText = (
  item: AnzeigeItem,
  standItem: NightRunItem | undefined,
): string =>
  [
    // Die Dauer steht vorneweg. Der Entwurf führt sie in der Ergebniszeile nicht — sie steckt
    // dort im Band —, aber ein Lauf ohne Ergebnisstand hat kein Band, und dann wäre sie ganz
    // verloren (AK 10).
    item.durationMs === undefined ? 'Dauer nicht gemeldet' : formatDuration(item.durationMs / 1000),
    vorgangsKennzahlen(standItem?.kennzahlen),
    modellzeitText(item.durationMs, standItem?.kennzahlen),
  ].join(' · ')

/** Kosten und Züge eines Vorgangs, dazu der Vermerk fehlender Kostenmeldungen (AK 11). */
function vorgangsKennzahlen(kennzahlen: NightRunKennzahlen | undefined): string {
  const vermerk = ohneKostenmeldung(
    kennzahlen?.kostenUnbekannt,
    'ein Arbeitsschritt',
    'Arbeitsschritte',
  )
  return [
    kennzahlen?.kostenUsd === undefined ? 'Kosten nicht gemeldet' : betrag(kennzahlen.kostenUsd),
    ...(kennzahlen?.zuege === undefined ? [] : [`${kennzahlen.zuege} Züge`]),
    ...(vermerk === null ? [] : [vermerk]),
  ].join(' · ')
}

/**
 * Die Kosten der Nacht für einen Umsetzungs-, Erzeugungs- oder Prüf-Lauf (Issue #874): die Summe
 * über die bearbeiteten Vorgänge, dazu der Zusatz, mit dem sie gekennzeichnet ist.
 *
 * <p><b>Immer gerechnet</b> (Plan #864, E9): Eine ausgewiesene Summe (`stand.kostenSumme`) schreibt
 * allein die Kette; für diese drei Arten liegt nie eine vor. Der Zusatz steht deshalb beiläufig an
 * der Zahl und nicht als Warnung — es ist dieselbe Addition, die der Runner selbst vornähme.
 *
 * <p><b>Bezugsgröße sind die bearbeiteten Vorgänge</b>, nicht alle gesichteten: Ein Prüf-Lauf
 * sichtet 35 Karten und bearbeitet eine. Die 34 übergangenen zu den fehlenden Kostenmeldungen zu
 * zählen, machte aus der Auswahlregel des Laufs einen Mangel.
 *
 * <p><b>Ohne jede Meldung steht kein Betrag</b>: „0,00 $" behauptete eine Nacht ohne Kosten, wo nur
 * nichts gemeldet wurde.
 */
function laufkosten(bearbeitet: readonly NightRunItem[]): { wert: string; hinweis: string | null } {
  const gemeldet = bearbeitet.flatMap((item) =>
    item.kennzahlen?.kostenUsd === undefined ? [] : [item.kennzahlen.kostenUsd],
  )
  if (gemeldet.length === 0) {
    return { wert: 'Kosten unbekannt', hinweis: null }
  }
  const fehlend = ohneKostenmeldung(
    bearbeitet.length - gemeldet.length,
    'ein Vorgang',
    'Vorgänge',
  )
  return {
    wert: betrag(gemeldet.reduce((summe, wert) => summe + wert, 0)),
    hinweis:
      fehlend === null ? 'gerechnet, nicht im Protokoll' : `gerechnet, unvollständig — ${fehlend}`,
  }
}

/**
 * Die Züge des Modells über alle bearbeiteten Vorgänge — dieselbe Bezugsgröße und dieselbe
 * Unterscheidung wie bei {@link laufkosten}: Eine Null wäre eine Behauptung über eine Nacht, die
 * gar keine Zahl gemeldet hat.
 */
function laufZuege(bearbeitet: readonly NightRunItem[]): string {
  const gemeldet = bearbeitet.flatMap((item) =>
    item.kennzahlen?.zuege === undefined ? [] : [item.kennzahlen.zuege],
  )
  return gemeldet.length === 0
    ? 'Züge unbekannt'
    : ZAHL_FORMAT.format(gemeldet.reduce((summe, wert) => summe + wert, 0))
}

/**
 * Die Kennzahl, die allein zu dieser Lauf-Art gehört (Issue #874, AK 2): „Karten entstanden" ergibt
 * für einen Umsetzungs-Lauf keinen Sinn — er erledigt Vorgänge und erzeugt keine Karten —, und
 * „erledigt" ergibt für einen Erzeugungs-Lauf keinen Sinn. Eine Kennzahl ohne Sinn für ihre Art
 * erscheint deshalb gar nicht und bekommt auch keinen Platzhalter.
 *
 * <p><b>„Erledigt" ist grün oder gelb</b> (Plan #864, E6): Die Session hat das Paket abgeschlossen;
 * ob die Prüfung grün war, entscheidet über die Farbe, nicht über „erledigt". Die beiden
 * erfolgreichen Vorgänge der Nacht vom 7. September sind „ungeprüft" und damit gelb — „nur grün"
 * ergäbe dort null von fünf, obwohl zwei Pakete fertig wurden.
 *
 * <p><b>„Bearbeitet" ist jeder nicht graue Vorgang</b> — dieselbe Grenze, die `processedCount` in
 * der Kopfzeile des Laufs zieht und die die Aufschlüsselung darunter benutzt (E5).
 *
 * <p>Ein Ketten-Lauf erreicht diese Funktion nicht: Er trägt seine eigene Übersicht
 * — seine Kennzahlen stehen im Kopf des Entwurfs, nicht in dieser Zeile.
 */
function artKennzahl(
  run: NightRun,
  bearbeitet: readonly NightRunItem[],
): { wert: string; label: string } {
  if (run.mode === 'IMPLEMENTATION') {
    const erledigt = run.items.filter(
      (item) => item.state === 'GREEN' || item.state === 'YELLOW',
    ).length
    return { wert: `${erledigt} von ${run.items.length}`, label: 'Vorgänge erledigt' }
  }
  if (run.mode === 'NIGHTPLAN') {
    const dokumente = run.items.reduce((summe, item) => summe + (item.dokumenteAnzahl ?? 0), 0)
    return { wert: `${dokumente}`, label: 'Dokumente entstanden' }
  }
  return { wert: `${bearbeitet.length} von ${run.items.length}`, label: 'Karten bearbeitet' }
}

/** Der Kopf der Übersicht: Modell, Label und Abschluss — jede Angabe nur, wo der Stand sie führt. */
const kopfText = (stand: NightRunStand | undefined): string =>
  [
    ...(stand?.modell === undefined ? [] : [stand.modell]),
    ...(stand?.label === undefined ? [] : [`Label ${stand.label}`]),
    abschlussText(stand?.abschluss),
  ].join(' · ')

/**
 * Ein Abschnitt des Stufenbands. Die Form steht seit #916 an der Komponente, die ihn darstellt;
 * gerechnet wird er weiter hier ({@link bandabschnitt}).
 */
type Bandabschnitt = BandabschnittForm & { schluessel: NightRunKettenStufe }

/** Der Breitenanteil eines Schritts ohne Zeitvorgabe: derselbe wie der jedes anderen ohne Vorgabe. */
const ANTEIL_OHNE_VORGABE = 1

/**
 * Ein Prozentwert, **bei 100 gekappt** und auf eine Nachkommastelle gerundet. Die Kappung ist die
 * Aussage: Die Füllung endet bei voller Länge und läuft nie über ihren Abschnitt hinaus; ein
 * Überlauf bleibt an den Zahlen darunter ablesbar.
 */
const gekappt = (prozent: number): number => Math.round(Math.min(100, prozent) * 10) / 10

/**
 * Der Vermerk an dem Abschnitt, an dem ein Vorgang endete — `null` an jedem anderen.
 *
 * <p>Als **zeitbedingt** gilt er allein bei der Fehlerklasse des Zeitbudgets. Sie entsteht in
 * `deuteKettenAusgang` genau dann, wenn der Grund der Einheit das Präfix `Zeitbudget ` trägt; ein
 * hoher Verbrauch allein genügt nicht — eine Kette kann auch weit unter ihrer Vorgabe an einem
 * Fehler zerbrechen.
 */
function vermerkAmEnde(istEnde: boolean, item: NightRunItem): string | null {
  if (!istEnde) {
    return null
  }
  return item.errorClass === 'TIME_BUDGET_EXCEEDED' ? 'am Zeitbudget beendet' : 'hier abgebrochen'
}

/** Ein Abschnitt des Bands: sein Anteil an der Breite, seine Füllung, seine Zahlen, sein Vermerk. */
function bandabschnitt(
  { schluessel, label }: { schluessel: NightRunKettenStufe; label: string },
  item: NightRunItem,
  vorgaben: NightRunStufenvorgaben | undefined,
  letzte: NightRunKettenStufe | undefined,
): Bandabschnitt {
  const stufe = item.kettenStufen?.[schluessel]
  const erreicht = stufe !== undefined
  const verbrauchMs = stufe?.dauerMs ?? 0
  // Eine Vorgabe von null Minuten zählt wie gar keine: Sie ergäbe kein Verhältnis, sondern eine
  // Division durch null. Deshalb der Blick auf den Wert und nicht nur auf sein Vorhandensein.
  const vorgabeMin = vorgaben?.[schluessel] ?? 0
  const mitVorgabe = vorgabeMin > 0
  const istEnde = erreicht && schluessel === letzte && item.state !== 'GREEN'
  const verbrauch = erreicht ? MINUTEN_FORMAT.format(verbrauchMs / MINUTE_MS) : '–'

  return {
    schluessel,
    label,
    anteil: mitVorgabe ? vorgabeMin : ANTEIL_OHNE_VORGABE,
    fuellung: mitVorgabe ? gekappt((verbrauchMs / (vorgabeMin * MINUTE_MS)) * 100) : 0,
    erreicht,
    zahlen: mitVorgabe
      ? `${verbrauch} / ${ZAHL_FORMAT.format(vorgabeMin)} min`
      : `${verbrauch} min · ohne Vorgabe`,
    vermerk: erreicht ? vermerkAmEnde(istEnde, item) : 'nicht erreicht',
    farbe: istEnde ? ZUSTAND_FARBE[item.state] : 'primary.main',
  }
}

/**
 * Die Beschriftung des Bands für Vorlesewerkzeuge: alle vier Schritte mit Verbrauch, Vorgabe und
 * Vermerk. Sie ist der Grund, warum das Band `role="img"` trägt — die Zahlen darunter werden damit
 * nicht ein zweites Mal einzeln vorgelesen, sondern genau einmal in dieser Reihenfolge.
 */
const bandAnsage = (abschnitte: readonly Bandabschnitt[]): string =>
  `Stufenband: ${abschnitte
    .map((a) => `${a.label} ${a.zahlen}${a.vermerk === null ? '' : `, ${a.vermerk}`}`)
    .join(' · ')}`


/** Eine Kennzahl der Nacht: der Wert, darunter seine Benennung und ein etwaiger Vorbehalt. */
function Kennzahl({
  wert,
  label,
  hinweis,
}: Readonly<{ wert: string; label: string; hinweis: string | null }>) {
  return (
    <Box>
      <Typography variant="h6">{wert}</Typography>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      {hinweis !== null && (
        <Typography variant="body2" color="text.secondary">
          {hinweis}
        </Typography>
      )}
    </Box>
  )
}


/**
 * Die beiden Arbeitsschritte, in denen Karten entstehen (Issue #868), jeder mit der Auskunft für
 * den Fall, dass in ihm nichts entstand. Prüfung und Abdeckung hinterlassen nie ein Dokument
 * (`kettenStufenDerEinheit` in `lib/nightRunErgebnisstand.ts`) und stehen deshalb nicht hier — eine
 * Zeile „keine Karten" unter einem Schritt, der gar keine hinterlassen kann, wäre keine Aussage.
 *
 * <p>Die Beschriftung trägt **keinen Doppelpunkt**: „Plan: " ist im Panel bereits die Stufenzeile
 * der Herkunftskette ({@link Stufenzeile}), und zwei verschiedene Aussagen unter derselben
 * Satzform stünden im selben Lauf nebeneinander.
 */
const DOKUMENT_STUFEN: ReadonlyArray<{
  schluessel: NightRunKettenStufe
  label: string
  leer: string
}> = [
  { schluessel: 'plan', label: 'Plan', leer: 'kein Plan' },
  { schluessel: 'pakete', label: 'Pakete', leer: 'keine Pakete' },
]

/**
 * Der Zustand eines Verweises auf eine entstandene Karte (Issue #868) — die drei Fälle, die der
 * Kartenkatalog bereits unterscheidet (E7 aus Plan #863):
 *
 * - **geladen** — die Karte liegt vor; der Verweis öffnet den Kartendialog.
 * - **fort** — der Katalog hat sie ausdrücklich als nicht auflösbar vermerkt (404).
 * - **laedt** — zu dieser Nummer steht noch gar nichts im Katalog.
 *
 * „laedt" und „fort" auseinanderzuhalten ist der Kern: Eine Karte, die gerade geladen wird, als
 * „nicht mehr vorhanden" zu zeigen, wäre eine Falschaussage, die sich Sekunden später selbst
 * widerlegt.
 */
type VerweisZustand =
  | { art: 'geladen'; karte: CardByNumber }
  | { art: 'fort' }
  | { art: 'laedt' }

function verweisZustand(nummer: number, katalog: Kartenkatalog): VerweisZustand {
  const karte = katalog.get(nummer)
  if (karte === undefined) {
    return { art: 'laedt' }
  }
  return karte === null ? { art: 'fort' } : { art: 'geladen', karte }
}




/**
 * Die Kennzahlen der Nacht für einen Umsetzungs-, Erzeugungs- oder Prüf-Lauf (Issue #874) — eine
 * Zeile unter der Kopfzeile des Laufs, mit der art-eigenen Kennzahl vorneweg, dann Laufzeit, Kosten
 * und Züge.
 *
 * <p><b>Sie liest den Ergebnisstand dieser Sitzung</b>, dieselbe Linie wie der Kopf des Entwurfs
 * (Plan #863, E1) und {@link VorgangsKennzahlen}: Kosten und Züge verlassen den Browser nie
 * (Plan #718, A1), der Server bewahrt sie nicht auf. Nach einem Neuladen der Seite fällt der Lauf
 * auf Band bzw. Aufschlüsselung zurück — eine Zeile aus lauter Fehlanzeigen wäre dieselbe Wand,
 * die der Kennzahlen-Hinweis unten vermeidet.
 *
 * <p><b>Die Laufzeit steht mit einer Nachkommastelle</b> und damit feiner als die grobkörnige
 * Angabe der Kopfzeile darüber ({@link formatDuration} rundet auf ganze Minuten). Das ist die Form
 * der Vorlage `docs/mockup-leitstand-laufarten.html` und hält die beiden Angaben auseinander,
 * obwohl sie dieselbe Größe messen: Die Laufdauer eines Ergebnisstands **ist** die Summe der
 * Vorgangsdauern (`parseNightRunErgebnisstand`), eine eigene Addition wäre eine zweite Rechenstelle.
 *
 * <p><b>Der Kennzahlen-Hinweis des Lauf-Kopfs verdrängt Kosten und Züge</b> (Plan #864, E8): Ohne
 * die ausführliche Ausgabe fordert der Runner den Kennzahlen-Strom gar nicht erst an und schreibt
 * den Grund einmal an den Lauf — das ist der Regelfall eines Umsetzungs-Laufs. „Kosten unbekannt"
 * daneben behauptete ein Fehlen, wo nichts fehlt; es wurde nur nichts angefordert.
 */
function Laufkennzahlen({ run }: Readonly<{ run: NightRun }>) {
  const bearbeitet = run.items.filter((item) => item.state !== 'GREY')
  const art = artKennzahl(run, bearbeitet)
  const hinweis = run.stand?.kennzahlenHinweis
  const kosten = laufkosten(bearbeitet)

  return (
    <Box data-testid="lauf-kennzahlen" sx={{ mb: 2 }}>
      <Stack direction="row" spacing={3} sx={{ flexWrap: 'wrap' }}>
        <Kennzahl wert={art.wert} label={art.label} hinweis={null} />
        <Kennzahl
          wert={`${MINUTEN_FORMAT.format(run.durationMs / MINUTE_MS)} min`}
          label="Laufzeit über alle Vorgänge"
          hinweis={null}
        />
        {hinweis === undefined && (
          <>
            <Kennzahl wert={kosten.wert} label="Kosten der Nacht" hinweis={kosten.hinweis} />
            <Kennzahl wert={laufZuege(bearbeitet)} label="Züge des Modells" hinweis={null} />
          </>
        )}
      </Stack>
      {hinweis !== undefined && (
        <Typography
          variant="body2"
          color="text.secondary"
          data-testid="lauf-kennzahlen-hinweis"
          sx={{ mt: 1 }}
        >
          {hinweis}
        </Typography>
      )}
    </Box>
  )
}

/**
 * Die Kennzahlen je bearbeitetem Vorgang eines Umsetzungs-, Erzeugungs- oder Prüf-Laufs
 * (Issue #872): Dauer, Kosten, Züge und der Anteil der Modellarbeit an der Dauer.
 *
 * <p><b>Nur die bearbeiteten Vorgänge</b> — also die mit nicht-grauem Zustand, dieselbe Grenze,
 * die `processedCount` in der Kopfzeile zieht. Ein Prüf-Lauf sichtet 35 Karten und bearbeitet
 * eine; eine Zeile je übergangener Karte wäre eine Wand aus Fehlanzeigen für Sitzungen, die es
 * nie gab.
 *
 * <p><b>Sie liest den Ergebnisstand dieser Sitzung</b>, nicht den Anzeigelauf — dieselbe Linie
 * wie der Kopf des Entwurfs (Plan #863, E1) und aus demselben Grund: Kosten, Züge und
 * Arbeitszeit verlassen den Browser nie (Plan #718, A1), der Server bewahrt sie nicht auf. Nach
 * einem Neuladen der Seite ist der Speicher leer, und der Lauf zeigt seine Zeilenliste ohne
 * diesen Block. Deshalb steht er **für sich** und nicht in der Vorgangszeile: Eine Zuordnung
 * über die Position zwischen zwei Quellen ginge still schief, sobald ihre Reihenfolgen einmal
 * auseinanderlaufen, und zeigte dann die Kennzahlen des falschen Vorgangs.
 *
 * <p>Die Kette bekommt ihn nicht: Sie führt Kosten und Züge je Vorgang bereits in ihrer
 * Übersicht (#866), und ihre Zeiten stehen am Stufenband je Arbeitsschritt (#867).
 */
function VorgangsKennzahlen({ run }: Readonly<{ run: NightRun }>) {
  const bearbeitet = run.items.filter((item) => item.state !== 'GREY')
  if (bearbeitet.length === 0) {
    return null
  }
  // Hat der Lauf die Sitzungs-Kennzahlen gar nicht angefordert, nennt er den Grund einmal an
  // seinem Kopf ({@link Laufkennzahlen}); je Vorgang stünde dann dreimal „fehlt" (#874).
  const ohneKennzahlen = run.stand?.kennzahlenHinweis !== undefined

  return (
    <Stack spacing={1} data-testid="vorgangs-kennzahlen" sx={{ mb: 2 }}>
      {bearbeitet.map((item) => (
        <Box key={`${item.cardNumber}-${item.position}`}>
          <Typography variant="body2">{`#${item.cardNumber} ${item.title}`}</Typography>
          <Typography
            variant="body2"
            color="text.secondary"
            data-testid={`kennzahlen-${item.cardNumber}`}
          >
            {vorgangszeile(item, ohneKennzahlen)}
          </Typography>
        </Box>
      ))}
    </Stack>
  )
}

/** Ein Abschnitt des Laufbands — ein Vorgang, der eine Dauer verbraucht hat. */
interface Laufabschnitt {
  cardNumber: number
  /** Der Breitenanteil in Prozent an der Summe aller Vorgangsdauern, auf eine Nachkommastelle. */
  anteil: number
  /** Die Dauer in derselben Schreibweise wie in der Vorgangszeile darunter. */
  dauer: string
  /** Die Ansage für Vorlesewerkzeuge: Kartennummer, Zustand und Dauer. */
  ansage: string
  /** Der Ampelton des Vorgangs — derselbe, den seine Zeile trägt. */
  farbe: string
}

/**
 * Ein Vorgang, der im Band einen Abschnitt bekommt: einer mit **gemessener** Dauer. Ohne sie hätte
 * sein Abschnitt keine Breite und wäre unsichtbar, obwohl er einen Ausgang trägt — er steht
 * deshalb allein in der Zeilenliste. Eine gemeldete Dauer von null zählt wie keine: Sie ergäbe
 * ebenfalls keine Breite.
 */
const mitDauer = (item: AnzeigeItem): item is AnzeigeItem & { durationMs: number } =>
  (item.durationMs ?? 0) > 0

/** Ein Abschnitt des Bands: sein Anteil an der Summe, seine Dauer, seine Ansage, seine Farbe. */
function laufabschnitt(item: AnzeigeItem & { durationMs: number }, summe: number): Laufabschnitt {
  const dauer = formatDuration(item.durationMs / 1000)
  return {
    cardNumber: item.cardNumber,
    anteil: Math.round((item.durationMs / summe) * 1000) / 10,
    dauer,
    ansage: `Karte #${item.cardNumber}: ${nightRunZustandsText(item.state, item.errorClass)}, ${dauer}`,
    farbe: ZUSTAND_FARBE[item.state],
  }
}


/**
 * Eine gesichtete Karte, so weit die Aufschlüsselung sie braucht. Beide Quellen erfüllen diese
 * Form: der frisch gedeutete Lauf ({@link NightRunItem}, mit `ausgang`) und die Server-Sicht
 * ({@link AnzeigeItem}, ohne ihn).
 */
type Sichtung = Pick<NightRunItem, 'cardNumber' | 'title' | 'state' | 'errorClass' | 'ausgang'> & {
  excerpt?: string
}

/**
 * Wohin eine gesichtete Karte in der Aufschlüsselung zählt — die vier Ausgänge aus Issue #873.
 * **Jede Karte bekommt genau einen**, sodass die Teilmengen die Gesamtzahl ergeben.
 *
 * <p>„bearbeitet" ist jeder **nicht graue** Vorgang (Plan #864, E5) — dieselbe Grenze, die
 * `processedCount` zieht und die die Kopfzeile des Laufs zeigt. Eine zweite, abweichende
 * Zählung daneben wäre ein Widerspruch auf derselben Seite.
 */
type Sichtungsausgang =
  | { art: 'bearbeitet' }
  | { art: 'uebersprungen'; grund: string }
  | { art: 'liegengeblieben' }
  | { art: 'unbekannt' }

/** Der Ersatz für einen übersprungenen Vorgang, dessen Stand keinen Grund nennt. */
const OHNE_GENANNTEN_GRUND = 'ohne genannten Grund'

/**
 * Der Ausgang aus dem **Ausgangswort** des Ergebnisstands — der sichere Weg, den nur ein frisch
 * gedeuteter Lauf gehen kann. Jedes Wort außer den beiden benannten zählt unter „ohne bekannten
 * Ausgang": Ein grauer Vorgang, der weder übersprungen noch liegengeblieben ist (`offen`, ein
 * zurückgestelltes Paket), gehört in keine der Grund-Zeilen, und ihn einer zuzuschlagen wäre
 * genau das Raten, das E2 ausschließt.
 */
function ausgangAusWort(ausgang: string, auszug: string): Sichtungsausgang {
  if (ausgang === 'uebersprungen') {
    return { art: 'uebersprungen', grund: auszug === '' ? OHNE_GENANNTEN_GRUND : auszug }
  }
  return ausgang === 'liegengeblieben' ? { art: 'liegengeblieben' } : { art: 'unbekannt' }
}

/**
 * Die **Ableitungstabelle** für einen aufbewahrten Lauf (Plan #864, E2): Seine Sicht trägt kein
 * Ausgangswort, und von den grauen Ausgängen eines Prüf-Laufs bleiben nur zwei zu unterscheiden.
 * Die Tabelle liest in dieser Reihenfolge:
 *
 * - **eine Fehlerklasse** — weder ein übersprungener noch ein liegengebliebener Vorgang trägt
 *   eine; was hier steht, ist ein anderer Ausgang und bleibt unbenannt.
 * - **der Anfang des Auszugs** ist der feste Satz eines liegengebliebenen Vorgangs
 *   ({@link NIGHT_RUN_AUSZUG_LIEGENGEBLIEBEN}).
 * - **ein sonstiger Auszug** ist der Grundtext, mit dem der Lauf das Überspringen begründet hat.
 * - **kein Auszug** — dann sagt allein die graue Farbe noch etwas, und die sagt nicht, warum.
 *
 * <p><b>Sie ist unscharf, und das steht in der Anzeige</b>: Was sie nicht sicher zuordnen kann,
 * zählt unter „ohne bekannten Ausgang" statt geraten zu werden.
 */
function ausgangAusSicht(
  errorClass: NightRunErrorClass | undefined,
  auszug: string,
): Sichtungsausgang {
  if (errorClass !== undefined) {
    return { art: 'unbekannt' }
  }
  if (auszug.startsWith(NIGHT_RUN_AUSZUG_LIEGENGEBLIEBEN)) {
    return { art: 'liegengeblieben' }
  }
  return auszug === '' ? { art: 'unbekannt' } : { art: 'uebersprungen', grund: auszug }
}

/**
 * Der Ausgang einer gesichteten Karte — mit dem Ausgangswort, wo es vorliegt, sonst über die
 * Ableitung. Der fehlende Auszug wird **einmal** hier zur leeren Zeichenkette: Ein aufbewahrter
 * Vorgang kann ohne ihn ankommen, ein frisch gedeuteter trägt immer einen ({@link NightRunItem}
 * führt ihn als Pflichtfeld), und zwei Auffangstellen hätten eine davon als toten Zweig.
 */
function sichtungsausgang(eintrag: Sichtung): Sichtungsausgang {
  if (eintrag.state !== 'GREY') {
    return { art: 'bearbeitet' }
  }
  const auszug = eintrag.excerpt ?? ''
  return eintrag.ausgang === undefined
    ? ausgangAusSicht(eintrag.errorClass, auszug)
    : ausgangAusWort(eintrag.ausgang, auszug)
}

/** Eine Zeile der Aufschlüsselung: ihr Text und die Karten, die sie zusammenfasst. */
interface Ausgangszeile {
  testId: string
  text: string
  karten: readonly Sichtung[]
}

/** Eine Zeile, die es nur gibt, wo sie wenigstens eine Karte zusammenfasst. */
const zeileWennBesetzt = (
  testId: string,
  label: string,
  karten: readonly Sichtung[],
): Ausgangszeile[] =>
  karten.length === 0 ? [] : [{ testId, text: `${karten.length} ${label}`, karten }]

/**
 * Die Aufschlüsselung eines Laufs: die Zahl der gesichteten und der bearbeiteten Karten und die
 * Zeilen der aussortierten. Die Überspringgründe stehen **nach Häufigkeit** — die Aussage ist,
 * welche Regel am meisten weggefiltert hat; `sort` ist stabil, gleich häufige Gründe behalten
 * also die Reihenfolge des Laufs.
 */
function aufschluesselung(items: readonly Sichtung[]): {
  bearbeitet: number
  zeilen: Ausgangszeile[]
} {
  let bearbeitet = 0
  const gruende = new Map<string, Sichtung[]>()
  const liegengeblieben: Sichtung[] = []
  const unbekannt: Sichtung[] = []

  for (const eintrag of items) {
    const ausgang = sichtungsausgang(eintrag)
    if (ausgang.art === 'bearbeitet') {
      bearbeitet += 1
    } else if (ausgang.art === 'liegengeblieben') {
      liegengeblieben.push(eintrag)
    } else if (ausgang.art === 'unbekannt') {
      unbekannt.push(eintrag)
    } else {
      const bisher = gruende.get(ausgang.grund)
      if (bisher === undefined) {
        gruende.set(ausgang.grund, [eintrag])
      } else {
        bisher.push(eintrag)
      }
    }
  }

  return {
    bearbeitet,
    zeilen: [
      ...[...gruende]
        .sort(([, a], [, b]) => b.length - a.length)
        .map(([grund, karten], position) => ({
          testId: `aufschluesselung-grund-${position}`,
          text: `${karten.length} übersprungen: ${grund}`,
          karten,
        })),
      ...zeileWennBesetzt('aufschluesselung-liegengeblieben', 'liegengeblieben', liegengeblieben),
      ...zeileWennBesetzt('aufschluesselung-unbekannt', 'ohne bekannten Ausgang', unbekannt),
    ],
  }
}

/**
 * Eine Zeile der Aufschlüsselung, aufklappbar zu den Karten, die sie zusammenfasst (#873). Der
 * Knopf trägt den ganzen Zeilentext als zugänglichen Namen, und `aria-expanded` sagt, ob die
 * Karten darunter stehen — die Liste wird erst beim Aufklappen gerendert, wie beim Lauf-Panel
 * darüber. `aria-controls` steht nur dann, wenn es auch ein Ziel gibt.
 */
function Ausgangsgruppe({ zeile }: Readonly<{ zeile: Ausgangszeile }>) {
  const [offen, setOffen] = useState(false)
  const bereichId = useId()

  return (
    <Box data-testid={zeile.testId}>
      <Button
        size="small"
        color="inherit"
        aria-expanded={offen}
        aria-controls={offen ? bereichId : undefined}
        onClick={() => setOffen((wert) => !wert)}
        startIcon={
          <ExpandMoreIcon
            fontSize="small"
            sx={{ transform: offen ? 'rotate(180deg)' : 'none' }}
          />
        }
        sx={{ textTransform: 'none', justifyContent: 'flex-start' }}
      >
        {zeile.text}
      </Button>
      {offen && (
        <Box id={bereichId} sx={{ pl: 4 }}>
          {zeile.karten.map((karte, position) => (
            // Zwei Vorgänge können dieselbe Karte betreffen — wie in der Zeilenliste trägt der
            // Schlüssel deshalb die Position dazu.
            <Typography
              key={`${karte.cardNumber}-${position}`}
              variant="body2"
              color="text.secondary"
            >
              {`#${karte.cardNumber} ${karte.title}`}
            </Typography>
          ))}
        </Box>
      )}
    </Box>
  )
}

/**
 * Die Aufschlüsselung der gesichteten Karten eines Erzeugungs- oder Prüf-Laufs (Issue #873).
 *
 * <p><b>Warum hier kein Band steht:</b> In diesen beiden Lauf-Arten werden neun von zehn Karten
 * nur angesehen und aussortiert — der echte Prüf-Lauf vom 11. September sichtet 35 und bearbeitet
 * **eine**. Ein Balken je Vorgang wäre dort eine Wand aus leeren Zeilen; die Aufschlüsselung
 * dreht die Aussage um und zeigt, **warum** so wenig übrig blieb. Daran erkennt der Betreiber, ob
 * die Auswahlregel greift oder zu eng steht.
 *
 * <p><b>Sie bedient Altbestand</b> (Plan #864, E1): `laufArt` in `night.mjs` liefert seit Kit
 * 1.53.0 nur noch `kette` oder `implementierung`, und die Routing-Labels der beiden anderen
 * Betriebsarten sind dort als entfallen vermerkt. Manne hat am 2026-09-14 entschieden, die
 * Darstellung trotzdem zu bauen: Aufbewahrte Läufe dieser Arten liegen weiterhin im Leitstand und
 * sollen lesbar bleiben. Wer diese Komponente später für toten Code hält, findet hier den Grund,
 * warum sie steht.
 *
 * <p><b>Gesichtete und bearbeitete Zahl stehen immer da</b>, auch mit dem Wert null: Ein Lauf,
 * der nichts fand, ist eine Auskunft — ein leerer Platz ließe offen, ob nichts gesichtet wurde
 * oder nichts nachgesehen. Gründe, die nicht vorkamen, erscheinen dagegen nicht; sie zählten
 * nichts zur Summe bei.
 */
function Aufschluesselung({ items }: Readonly<{ items: readonly Sichtung[] }>) {
  const { bearbeitet, zeilen } = aufschluesselung(items)

  return (
    <Box data-testid="aufschluesselung" sx={{ mb: 2 }}>
      <Typography variant="subtitle2" data-testid="aufschluesselung-gesichtet">
        {`${items.length} Karten gesichtet`}
      </Typography>
      <Typography
        variant="body2"
        color="text.secondary"
        data-testid="aufschluesselung-bearbeitet"
        sx={{ pl: 1 }}
      >
        {`${bearbeitet} bearbeitet`}
      </Typography>
      {zeilen.map((zeile) => (
        <Ausgangsgruppe key={zeile.testId} zeile={zeile} />
      ))}
    </Box>
  )
}

/**
 * Die beiden Lauf-Arten, die die Gestaltung des Entwurfs tragen (#915, E5). `REVIEW` und
 * `NIGHTPLAN` sind Altbestand: `laufArt` in `.claude/kit/night.mjs` erzeugt seit Kit 1.53.0 nur
 * noch `kette` und `implementierung` (Nicht-Ziel 5). Sie behalten ihre heutige Darstellung.
 */
const ENTWURFS_ARTEN = new Set<NightRunMode>(['CHAIN', 'IMPLEMENTATION'])

const traegtEntwurf = (modus: NightRunMode): modus is 'CHAIN' | 'IMPLEMENTATION' =>
  ENTWURFS_ARTEN.has(modus)

/**
 * Die Kennzahlen der Nacht für den Kopf des Entwurfs — **dieselben Werte wie vor diesem Paket**
 * (Nicht-Ziel 3): für die Kette die vier der bisherigen Übersicht, für den Umsetzungs-Lauf die
 * aus {@link Laufkennzahlen}. Hier steht nur, wie sie zusammengestellt werden, nicht wie sie
 * entstehen.
 *
 * <p>`null` heißt: Zu diesem Lauf liegt kein Ergebnisstand dieser Sitzung vor. Kosten und Züge
 * bewahrt der Server nicht auf (Plan #718, A1) — eine Reihe aus lauter Fehlanzeigen wäre dieselbe
 * Wand, die der Bestand schon vermeidet (E6).
 */
function nachtKennzahlen(
  stand: NightRun | undefined,
): { kennzahlen: NachtlaufKennzahl[]; hinweis: string | undefined } | null {
  if (stand === undefined) {
    return null
  }
  if (stand.mode === 'CHAIN') {
    const gruen = stand.items.filter((item) => item.state === 'GREEN').length
    return {
      kennzahlen: [
        { wert: `${gruen} von ${stand.items.length}`, label: 'Ketten durchgelaufen', hinweis: null },
        {
          wert: `${entstandeneDokumente(stand.items).size}`,
          label: 'Karten entstanden',
          hinweis: null,
        },
        {
          wert: formatDuration(stufenZeitSumme(stand.items) / 1000),
          label: 'Laufzeit über alle Stufen',
          hinweis: null,
        },
        {
          wert: betrag(stand.stand?.kostenSumme),
          label: 'Kosten der Nacht',
          hinweis: ohneKostenmeldung(
            stand.stand?.kostenUnbekannt,
            'ein Arbeitsschritt',
            'Arbeitsschritte',
          ),
        },
      ],
      hinweis: undefined,
    }
  }

  const bearbeitet = stand.items.filter((item) => item.state !== 'GREY')
  const art = artKennzahl(stand, bearbeitet)
  const kennzahlenHinweis = stand.stand?.kennzahlenHinweis
  const kosten = laufkosten(bearbeitet)
  return {
    kennzahlen: [
      { wert: art.wert, label: art.label, hinweis: null },
      {
        wert: `${MINUTEN_FORMAT.format(stand.durationMs / MINUTE_MS)} min`,
        label: 'Laufzeit über alle Vorgänge',
        hinweis: null,
      },
      // Der Kennzahlen-Hinweis verdrängt Kosten und Züge (Plan #864, E8): Ohne die ausführliche
      // Ausgabe fordert der Runner den Kennzahlen-Strom gar nicht erst an.
      ...(kennzahlenHinweis === undefined
        ? [
            { wert: kosten.wert, label: 'Kosten der Nacht', hinweis: kosten.hinweis },
            { wert: laufZuege(bearbeitet), label: 'Züge des Modells', hinweis: null },
          ]
        : []),
    ],
    hinweis: kennzahlenHinweis,
  }
}

/**
 * Die Arbeitsschritte, aus denen ein Ketten-Vorgang Karten hinterlässt, als Chipgruppen für seinen
 * Block (#916). Nur `plan` und `pakete`: Die beiden anderen Schritte erzeugen gar keine Karten, und
 * eine Zeile „keine Karten" unter einem Schritt, der keine hinterlassen kann, wäre keine Aussage.
 */
function chipgruppen(item: NightRunItem, katalog: Kartenkatalog): Chipgruppe[] {
  return DOKUMENT_STUFEN.map(({ schluessel, label, leer }) => ({
    label,
    leer,
    testId: `dokumente-${item.cardNumber}-${schluessel}`,
    chips: dokumenteDerStufe(item.kettenStufen, schluessel).map(
      (id): Kartenchip => ({ id, art: label, zustand: verweisZustand(alsNummer(id), katalog) }),
    ),
  }))
}

/**
 * Das Stufenband eines Ketten-Vorgangs: die gerechneten Abschnitte und ihre Ansage. `null`, wo der
 * Vorgang gar keine Arbeitsschritte führt — ein aufbewahrter Lauf ohne Sitzungsstand.
 */
function vorgangsband(
  item: NightRunItem,
  vorgaben: NightRunStufenvorgaben | undefined,
): { abschnitte: readonly Bandabschnitt[]; ansage: string } | null {
  if (item.kettenStufen === undefined) {
    return null
  }
  const letzte = letzteErreichteStufe(item.kettenStufen)
  const abschnitte = KETTEN_STUFEN.map((stufe) => bandabschnitt(stufe, item, vorgaben, letzte))
  return { abschnitte, ansage: bandAnsage(abschnitte) }
}

/**
 * Der Grund, mit dem ein Vorgang endete (AK 7) — `null` an einem regulär beendeten. Derselbe Satz,
 * der bis #916 über dem Band stand: der Grund und die bis dahin entstandenen Karten.
 */
function vorgangsgrund(item: NightRunItem): string | null {
  const letzte = letzteErreichteStufe(item.kettenStufen)
  return letzte !== undefined && item.state !== 'GREEN' ? abbruchSatz(item) : null
}

/**
 * Ein Vorgang eines Ketten-Laufs (#916): der Block des Entwurfs, gefüllt mit den Werten des
 * Bestands, darunter die Fähigkeiten, die der Entwurf nicht vorsieht (AK 10).
 *
 * <p>Hier wird zusammengestellt, nicht gerechnet: Band, Grund, Chips und Kennzahlenzeile kommen aus
 * denselben Funktionen wie zuvor, damit die Werte nachweislich dieselben bleiben.
 */
function KettenVorgang({
  item,
  standItem,
  vorgaben,
  modus,
  katalog,
  vorhabenKarten,
  haeufigkeit,
  istRot,
  onOeffnen,
}: Readonly<{
  item: AnzeigeItem
  /**
   * Derselbe Vorgang im Ergebnisstand dieser Sitzung; `undefined` an einem aufbewahrten Lauf.
   * Arbeitsschritte, Kosten und Züge stehen allein dort — der Server bewahrt sie nicht auf.
   */
  standItem: NightRunItem | undefined
  vorgaben: NightRunStufenvorgaben | undefined
  modus: NightRunMode
  katalog: Kartenkatalog
  vorhabenKarten: Vorhabenkatalog
  haeufigkeit: string | null
  istRot: (nummer: number) => boolean
  onOeffnen: (karte: CardByNumber) => void
}>) {
  const band = standItem === undefined ? null : vorgangsband(standItem, vorgaben)
  return (
    <Box sx={{ mb: 2 }}>
      <NachtlaufVorgang
        nummer={item.cardNumber}
        titel={item.title}
        wurzel={katalog.get(item.cardNumber)}
        zustand={item.state}
        ausgangswort={nightRunZustandsText(item.state, item.errorClass)}
        grund={standItem === undefined ? null : vorgangsgrund(standItem)}
        verlauf={
          band === null ? undefined : (
            <NachtlaufStufenband
              abschnitte={band.abschnitte}
              ansage={band.ansage}
              testId={`stufenband-${item.cardNumber}`}
              abschnittTestId={`stufe-${item.cardNumber}`}
            />
          )
        }
        chipgruppen={standItem === undefined ? [] : chipgruppen(standItem, katalog)}
        kennzahlen={vorgangsKennzahlenText(item, standItem)}
        onOeffnen={onOeffnen}
      >
        {standItem === undefined && item.excerpt !== undefined && (
          // Auszüge sind Fremdtext (Claude-Ausgaben, Ergebnisse fremder Werkzeuge) und werden
          // deshalb als reiner Text gerendert, nie über den Markdown-Renderer
          // (CLAUDE-security.md). Mit Ergebnisstand sagen Band und Grund dasselbe genauer.
          <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
            {(item.state === 'GREY' ? 'Grund: ' : 'Auszug: ') + item.excerpt}
          </Typography>
        )}
        <Paketzusaetze
          item={item}
          modus={modus}
          // Die Entscheidung trifft `stufenZustand` an der Lauf-Art: Im Modus `CHAIN` entfällt
          // jede Stufenzeile, weil die Herkunft dort im Auszug steht (#858). Sie hier ein zweites
          // Mal zu treffen hieße, dieselbe Regel an zwei Stellen zu führen.
          mitStufenzeilen
          katalog={katalog}
          vorhabenKarten={vorhabenKarten}
          haeufigkeit={haeufigkeit}
          istRot={istRot}
          onOeffnen={onOeffnen}
        />
      </NachtlaufVorgang>
    </Box>
  )
}

/**
 * Die Ergebniszeile eines Umsetzungs-Vorgangs (#917) — dieselbe, die {@link VorgangsKennzahlen}
 * bildet. Kosten, Züge und Modellzeit stehen allein im Ergebnisstand; ohne ihn bleibt die Dauer
 * des Anzeigemodells, und die drei Fehlanzeigen entfallen: Der Lauf hat sie nicht verschwiegen,
 * der Server bewahrt sie nur nicht auf.
 */
const umsetzungsKennzahlen = (
  item: AnzeigeItem,
  standItem: NightRunItem | undefined,
  ohneKennzahlen: boolean,
): string => {
  // Ein grauer Vorgang lief nie; seine Zeile bestünde aus lauter Fehlanzeigen. Dieselbe Grenze,
  // die {@link VorgangsKennzahlen} zieht.
  if (item.state === 'GREY') {
    return ''
  }
  return standItem === undefined
    ? item.durationMs === undefined
      ? 'Dauer nicht gemeldet'
      : formatDuration(item.durationMs / 1000)
    : vorgangszeile(standItem, ohneKennzahlen)
}

/**
 * Die Anteile der Vorgänge eines Umsetzungs-Laufs an der Laufzeit der Nacht (#917), je Vorgang
 * einer. **Die Rechnung ist die des Laufbands aus #871** und hat sich nicht geändert, nur ihr Ort:
 * Bezugsgröße ist die Summe der Vorgangsdauern, und Abschnitte entstehen erst ab zwei gemessenen
 * Vorgängen — ein Balken mit einem einzigen Abschnitt behauptete ein Verhältnis, das es nicht gibt.
 *
 * <p>Ein Vorgang ohne gemessene Dauer bekommt keinen Eintrag: Sein Balken hätte keine Breite und
 * wäre unsichtbar, obwohl er einen Ausgang trägt. Seine Angaben stehen davon unberührt in seinem
 * Block.
 */
function laufanteile(items: readonly AnzeigeItem[]): ReadonlyMap<number, Laufabschnitt> {
  const gemessen = items.filter(mitDauer)
  if (gemessen.length < 2) {
    return new Map()
  }
  const summe = gemessen.reduce((wert, item) => wert + item.durationMs, 0)
  return new Map(gemessen.map((item) => [item.cardNumber, laufabschnitt(item, summe)]))
}

/**
 * Ein Vorgang eines Umsetzungs-Laufs (#917): derselbe Block wie bei der Kette, nur mit dem
 * Anteilsbalken an der Stelle des Stufenbands — ein Umsetzungs-Vorgang kennt keine Arbeitsschritte,
 * sondern eine Strecke.
 */
function UmsetzungsVorgang({
  item,
  anteil,
  kennzahlen,
  modus,
  katalog,
  vorhabenKarten,
  haeufigkeit,
  istRot,
  onOeffnen,
}: Readonly<{
  item: AnzeigeItem
  /** Der Anteil dieses Vorgangs; `undefined`, wo der Lauf keine Bezugsgröße hergibt. */
  anteil: Laufabschnitt | undefined
  /** Dauer, Kosten, Züge und Modellzeit — dieselbe Zeile, die `VorgangsKennzahlen` bildet. */
  kennzahlen: string
  modus: NightRunMode
  katalog: Kartenkatalog
  vorhabenKarten: Vorhabenkatalog
  haeufigkeit: string | null
  istRot: (nummer: number) => boolean
  onOeffnen: (karte: CardByNumber) => void
}>) {
  return (
    <Box sx={{ mb: 2 }}>
      <NachtlaufVorgang
        nummer={item.cardNumber}
        titel={item.title}
        wurzel={katalog.get(item.cardNumber)}
        zustand={item.state}
        ausgangswort={nightRunZustandsText(item.state, item.errorClass)}
        // Der Grund steht bei dieser Lauf-Art im Auszug und nicht als eigener Satz: Sie kennt
        // keine Arbeitsschritte, an denen eine Kette reißen könnte.
        grund={null}
        verlauf={
          anteil === undefined ? undefined : (
            <NachtlaufAnteilsbalken
              anteil={anteil.anteil}
              beschriftung={`${anteil.dauer} · ${anteil.anteil} % der Nacht`}
              ansage={`${anteil.ansage}, ${anteil.anteil} % der Nacht`}
              farbe={NACHTLAUF_TON[item.state]}
              testId={`laufband-abschnitt-${item.cardNumber}`}
              fuellungTestId={`laufband-balken-${item.cardNumber}`}
            />
          )
        }
        // Ein Umsetzungs-Vorgang hinterlässt keine Dokumente je Arbeitsschritt.
        chipgruppen={[]}
        kennzahlen={kennzahlen}
        onOeffnen={onOeffnen}
      >
        {item.excerpt !== undefined && (
          // Auszüge sind Fremdtext und werden als reiner Text gerendert, nie über den
          // Markdown-Renderer (CLAUDE-security.md).
          <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
            {(item.state === 'GREY' ? 'Grund: ' : 'Auszug: ') + item.excerpt}
          </Typography>
        )}
        <Paketzusaetze
          item={item}
          modus={modus}
          mitStufenzeilen
          katalog={katalog}
          vorhabenKarten={vorhabenKarten}
          haeufigkeit={haeufigkeit}
          istRot={istRot}
          onOeffnen={onOeffnen}
        />
      </NachtlaufVorgang>
    </Box>
  )
}

/**
 * Die Angaben der Fußzeile je Lauf-Art (#918). **Dieselben Werte wie vor diesem Paket**
 * (Nicht-Ziel 3): die drei der Ketten-Übersicht und die drei, die der Entwurf für den
 * Umsetzungs-Lauf führt.
 *
 * <p><b>Die Herkunft der Budgets sagt „nicht angegeben"</b> (AK 9, Fall 2, E12): Der Ergebnisstand
 * führt das Feld heute nicht — der Leitstand kann deshalb nicht sagen, ob eine Vorgabe eingestellt
 * oder voreingestellt war. Weggelassen ließe die Zeile offen, ob nichts vorlag oder nichts
 * nachgesehen wurde; das Feld selbst kommt aus einem Folge-Vorhaben im Kit-Repository, und dann
 * trägt die Zeile dessen Wert. Die Warnfarbe bleibt bis dahin aus: Eine fehlende Angabe ist keine
 * Warnung.
 */
function fussangaben(lauf: AnzeigeLauf, stand: NightRun | undefined): FussangabeForm[] {
  if (lauf.mode === 'CHAIN') {
    return [
      { label: 'Zeitvorgaben je Kette', wert: vorgabenText(stand?.stand?.vorgabenMin) },
      { label: 'Kostenbudget je Kette', wert: betrag(stand?.stand?.kostenBudgetUsd) },
      {
        label: 'Höchste Kosten eines Vorgangs',
        wert: stand === undefined ? 'nicht angegeben' : hoechsteKosten(stand.items),
      },
      { label: 'Herkunft der Budgets', wert: 'nicht angegeben' },
    ]
  }
  return [
    {
      label: 'Ergebnis der Nacht',
      wert: `${lauf.processedCount} bearbeitet · ${lauf.skippedCount} übergangen`,
    },
    {
      label: 'Teuerster Vorgang',
      wert: stand === undefined ? 'nicht angegeben' : teuersterVorgang(stand.items),
    },
    { label: 'Herkunft des Stands', wert: stand === undefined ? 'nicht angegeben' : 'Ergebnisstand' },
  ]
}

/**
 * Der Vorgang mit den höchsten Kosten, mit seiner Nummer (#918). {@link hoechsteKosten} nennt nur
 * den Betrag; der Entwurf führt hier beides („#872 mit 12,53 $"), und ohne die Nummer müsste man
 * die Vorgänge durchsehen, um den teuersten zu finden.
 */
function teuersterVorgang(items: readonly NightRunItem[]): string {
  const gemeldet = items.flatMap((item) =>
    item.kennzahlen?.kostenUsd === undefined
      ? []
      : [{ nummer: item.cardNumber, kosten: item.kennzahlen.kostenUsd }],
  )
  if (gemeldet.length === 0) {
    return 'nicht angegeben'
  }
  const teuerster = gemeldet.reduce((hoechster, kandidat) =>
    kandidat.kosten > hoechster.kosten ? kandidat : hoechster,
  )
  return `#${teuerster.nummer} mit ${betrag(teuerster.kosten)}`
}

/** Ein Zeitpunkt in der Schreibweise, die die Seite ueberall fuer Zeitpunkte fuehrt. */
const zeitpunkt = (iso: string): string => new Date(iso).toLocaleString('de-DE')

/**
 * Was der Kopf ueber die Einlieferung eines Laufs sagt (Issue #949): woher er kam, wann er zuletzt
 * gemeldet wurde und ob er abgeschlossen ist.
 *
 * <p>Ein eben geparster Lauf sagt dazu **nichts** — er ist noch nicht eingeliefert, und
 * „hochgeladen am" waere dort eine Behauptung ueber die Zukunft. Die Unvollstaendigkeit steht
 * trotzdem da: Sie gilt dem Lauf selbst, nicht seiner Speicherung.
 *
 * <p>Der Zeitpunkt der letzten Meldung erscheint nur, wenn er vom Anlegen abweicht. Eine Kette
 * meldet denselben Lauf mehrfach; stehen beide Zeitpunkte gleich, gab es genau eine Meldung, und
 * zwei gleiche Zeiten nebeneinander liessen den Leser nach einem Unterschied suchen, den es nicht
 * gibt.
 */
function einlieferungsangaben(lauf: AnzeigeLauf): string[] {
  const herkunft: string[] = []
  if (lauf.herkunft === 'TOKEN' && lauf.eingeliefertAm !== undefined) {
    herkunft.push(
      `maschinell eingeliefert am ${zeitpunkt(lauf.eingeliefertAm)}${
        lauf.tokenName === undefined ? '' : ` (Token: ${lauf.tokenName})`
      }`,
    )
  } else if (lauf.herkunft === 'UPLOAD' && lauf.eingeliefertAm !== undefined) {
    herkunft.push(`hochgeladen am ${zeitpunkt(lauf.eingeliefertAm)}`)
  }
  if (
    lauf.zuletztGemeldetAm !== undefined &&
    lauf.zuletztGemeldetAm !== lauf.eingeliefertAm
  ) {
    herkunft.push(`zuletzt gemeldet am ${zeitpunkt(lauf.zuletztGemeldetAm)}`)
  }
  return lauf.vollstaendig ? herkunft : [...herkunft, 'unvollständig gemeldet']
}


/**
 * Der aufbewahrte Verbrauch eines Laufs oder eines Arbeitspakets (Issue #949).
 *
 * <p><b>Die Lauf-Summe wird angezeigt, nicht gerechnet.</b> Sie kommt aus dem Lauf selbst und
 * liegt ueber der Summe seiner Arbeitspakete, wo Sitzungen keinem Paket zuzuordnen waren. Aus den
 * Paketen gerechnet waere dieser Rest per Konstruktion null — und damit unsichtbar.
 */
function VerbrauchsZeile({
  verbrauch,
  testId,
}: Readonly<{
  verbrauch: Verbrauch | undefined
  testId: string
}>) {
  const angaben = [
    `Kosten: ${kosten(verbrauch?.kostenUsd)}`,
    `Eingabe: ${menge(verbrauch?.eingabe)}`,
    `Ausgabe: ${menge(verbrauch?.ausgabe)}`,
    `Zwischenspeicher: ${menge(verbrauch?.zwischenspeicher)}`,
  ]
  return (
    <Typography data-testid={testId} variant="body2" color="text.secondary">
      {angaben.join(' · ')}
    </Typography>
  )
}

/** Ein Lauf als aufklappbares Panel; die Kette wird erst beim Aufklappen geladen (A8). */
function LaufPanel({
  lauf,
  ergebnis,
  ausErgebnisstand,
  stand,
  katalog,
  vorhabenKarten,
  zaehler,
  aufbewahrteLaeufe,
  zuerst,
  onAufklappen,
  onOeffnen,
}: Readonly<{
  lauf: AnzeigeLauf
  /** `true` = in dieser Sitzung neu angelegt, `false` = lag schon vor, `undefined` = nicht gesendet. */
  ergebnis: boolean | undefined
  /** Die Startzeitpunkte der Läufe, die in dieser Sitzung aus einem Ergebnisstand entstanden sind. */
  ausErgebnisstand: ReadonlySet<string>
  /**
   * Der in dieser Sitzung gedeutete Lauf; `undefined` heißt: kein Ergebnisstand, also weder
   * Übersicht (E1) noch Kennzahlen je Vorgang (#872) — beide stehen allein im Stand.
   */
  stand: NightRun | undefined
  katalog: Kartenkatalog
  vorhabenKarten: Vorhabenkatalog
  zaehler: Haeufigkeiten
  /** Das „M" in „N von M aufbewahrten Läufen" — die Länge der zuletzt geladenen Liste. */
  aufbewahrteLaeufe: number
  /**
   * Der oberste Lauf der Liste steht beim Öffnen der Seite offen (#914, E7). AK 2 verlangt Kopf,
   * Kennzahlenreihe und ersten Vorgangsblock ohne Scrollen — genau dieser eine, nicht alle: Bis
   * zu 30 aufgeklappte Läufe lösten die Anfragelawine aus, die Plan #718 (A8) vermeidet.
   */
  zuerst: boolean
  onAufklappen: () => void
  onOeffnen: (karte: CardByNumber) => void
}>) {
  const rot = new Set(lauf.items.filter((item) => item.state === 'RED').map((item) => item.cardNumber))
  // Die Weiche hängt an der Lauf-Art, nicht am Vorliegen eines Stands (#915, E6): Ein aufbewahrter
  // Ketten-Lauf ohne Sitzungsstand bekommt denselben Kopf, nur ohne die Kennzahlen, die allein der
  // Stand hergibt.
  // Die Lauf-Art in einer eigenen Konstante, damit die Prüfung unten den Typ verengt: Ein
  // Eigenschaftszugriff verengt sich nicht über die Verzweigung hinweg mit.
  const modus = lauf.mode
  // Eine Fassung für beide Zweige: Zwei gleichlautende Abfragen nebeneinander hießen zwei Stellen,
  // an denen dieselbe Frage beantwortet wird.
  const istRot = (nummer: number) => rot.has(nummer)
  const entwurf = traegtEntwurf(modus)
  const kennzahlen = entwurf ? nachtKennzahlen(stand) : null
  // Einmal je Lauf gerechnet: Die Bezugsgröße ist die Summe **aller** Vorgangsdauern, also eine
  // Größe des Laufs und nicht eines Vorgangs.
  const anteile = modus === 'IMPLEMENTATION' ? laufanteile(lauf.items) : new Map<number, Laufabschnitt>()

  return (
    <Accordion
      data-testid={`lauf-${lauf.startedAt}`}
      component={Paper}
      variant="outlined"
      defaultExpanded={zuerst}
      // Bleibt erhalten: Es ist der Grund, warum bis zu 190 aufbewahrte Läufe nicht alle ihre
      // Inhalte rendern.
      slotProps={{ transition: { unmountOnExit: true } }}
      onChange={(_, offen) => offen && onAufklappen()}
    >
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        {entwurf ? (
          <NachtlaufKopf
            startedAt={lauf.startedAt}
            mode={modus}
            angaben={[
              kopfText(stand?.stand),
              // Die Herkunft wird **hier** aus dem Zwischenspeicher gelesen, nicht in
              // `AnzeigeLauf` mitgeführt: Der Server kennt die Unterscheidung nicht, ein Feld am
              // Anzeigemodell müsste also in jedem Ladepfad einzeln gesetzt werden — und der
              // nächste vergessene Pfad zeigte still die falsche Herkunft (AK 9, Fall 3).
              ausErgebnisstand.has(lauf.startedAt) ? 'Ergebnisstand' : 'Herkunft unbekannt',
              // Alles Weitere stand bis #915 als Chip-Zeile im Kopf des Laufs. Der Entwurf sieht
              // dafür kein Element vor; nach AK 10 bleibt es in seiner Funktion erhalten und wird
              // eingepasst, statt fallen gelassen zu werden.
              MODUS_TEXT[lauf.mode],
              formatDuration(lauf.durationMs / 1000),
              `${lauf.processedCount} bearbeitet, ${lauf.skippedCount} übergangen`,
              ...(lauf.unparsedCount > 0 ? [`Ungedeutete Zeilen: ${lauf.unparsedCount}`] : []),
              ...(ergebnis === undefined ? [] : [ergebnis ? 'neu angelegt' : 'lag schon vor']),
              // Herkunft, letzte Meldung und Vollstaendigkeit (Issue #949) — sie stehen im Kopf
              // und nicht im Inhalt: Ein unvollstaendig gemeldeter Lauf soll auffallen, ohne dass
              // man ihn erst aufklappt.
              ...einlieferungsangaben(lauf),
            ]}
          />
        ) : (
        <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap' }}>
          <Typography variant="subtitle1">{new Date(lauf.startedAt).toLocaleString('de-DE')}</Typography>
          <Chip size="small" label={MODUS_TEXT[lauf.mode]} variant="outlined" />
          {/* Die Herkunft wird **hier** aus dem Zwischenspeicher gelesen, nicht in `AnzeigeLauf`
              mitgeführt: Der Server kennt die Unterscheidung nicht, ein Feld am Anzeigemodell
              müsste also in jedem Ladepfad einzeln gesetzt werden — und der nächste vergessene
              Pfad zeigte still die falsche Herkunft. */}
          <Chip
            size="small"
            variant="outlined"
            label={ausErgebnisstand.has(lauf.startedAt) ? 'Ergebnisstand' : 'Herkunft unbekannt'}
          />
          <Typography component="span" color="text.secondary">
            {formatDuration(lauf.durationMs / 1000)}
          </Typography>
          <Typography component="span" color="text.secondary">
            {`${lauf.processedCount} bearbeitet, ${lauf.skippedCount} übergangen`}
          </Typography>
          {lauf.unparsedCount > 0 && (
            <Chip size="small" variant="outlined" label={`Ungedeutete Zeilen: ${lauf.unparsedCount}`} />
          )}
          {ergebnis !== undefined && (
            <Chip size="small" label={ergebnis ? 'neu angelegt' : 'lag schon vor'} variant="outlined" />
          )}
          {/* Dieselben Angaben wie im Entwurfs-Kopf (Issue #949), in der Chip-Form der beiden
              Altbestand-Arten. */}
          {einlieferungsangaben(lauf).map((angabe) => (
            <Typography key={angabe} component="span" color="text.secondary">
              {angabe}
            </Typography>
          ))}
        </Stack>
        )}
      </AccordionSummary>
      <AccordionDetails>
        {/* Der aufbewahrte Verbrauch des Laufs (Issue #949) — anders als die Kennzahlen aus dem
            Ergebnisstand steht er auch dann da, wenn diese Sitzung den Stand nie gesehen hat. Nur
            an einem gespeicherten Lauf: Ein eben geparster hat noch keinen aufbewahrten Wert. */}
        {lauf.verbrauch !== undefined && (
          <VerbrauchsZeile verbrauch={lauf.verbrauch} testId="lauf-verbrauch" />
        )}
        {/* Die Kennzahlenreihe des Entwurfs gehört zum Kopf, steht aber im Inhalt: Der
            `AccordionSummary` ist ein `<button>`, und die Reihe trägt zu viel für einen Knopf. */}
        {kennzahlen !== null && (
          <NachtlaufKennzahlen kennzahlen={kennzahlen.kennzahlen} hinweis={kennzahlen.hinweis} />
        )}
        {/* Die Kennzahlen der Nacht stehen unter der Kopfzeile des Laufs und über allen
            Einzelangaben (#874). Seit #915 nur noch an den beiden Altbestand-Arten: Kette und
            Umsetzungs-Lauf tragen sie im Kopf des Entwurfs. */}
        {stand !== undefined && !entwurf && <Laufkennzahlen run={stand} />}
        {/* Die drei Nicht-Ketten-Arten bekommen ihre Kennzahlen je Vorgang; die Kette führt sie
            bereits in ihrer Übersicht (#872, siehe `VorgangsKennzahlen`). */}
        {stand !== undefined && !entwurf && <VorgangsKennzahlen run={stand} />}
        {/* Das Band steht unter der Kopfzeile des Laufs und über seiner Zeilenliste — die Kopfzeile
            selbst („N bearbeitet, M übergangen") bleibt unverändert. Nur am Umsetzungs-Lauf: Der
            Ketten-Lauf hat mit dem Stufenband je Vorgang bereits ein Band, und ein zweites daneben
            bezöge sich auf eine andere Größe. */}

        {/* Statt eines Bandes (#873): Der Erzeugungs- und der Prüf-Lauf sortieren die große
            Mehrheit ihrer Karten aus, und die Aufschlüsselung sagt, warum. Sie liest den
            Ergebnisstand dieser Sitzung, wo er vorliegt — allein er trägt das Ausgangswort;
            sonst leitet sie aus der Server-Sicht ab (Plan #864, E2). */}
        {(lauf.mode === 'REVIEW' || lauf.mode === 'NIGHTPLAN') && (
          <Aufschluesselung items={stand?.items ?? lauf.items} />
        )}
        {lauf.unparsedSample.length > 0 && (
          <Box sx={{ mb: 1 }}>
            <Typography variant="subtitle2">Nicht gedeutete Zeilen (Auszug)</Typography>
            {lauf.unparsedSample.map((zeile, position) => (
              // Der Index als Schlüssel: Zwei ungedeutete Zeilen können wörtlich gleich sein, und
              // die Liste ist unveränderlich — sie wird weder sortiert noch gefiltert.
              // Sonar S6479 ist deshalb an der Schlüssel-Zeile unterdrückt (Plan #776, Entscheidung 3).
              <Typography key={position /* NOSONAR */} variant="body2" color="text.secondary">
                {zeile}
              </Typography>
            ))}
          </Box>
        )}
        <Divider />
        {/* Der Ketten-Lauf trägt seit #916 den Vorgangsblock des Entwurfs; er vereint, was bis
            dahin auf die Zeile des Arbeitspakets und die Vorgangsliste der Übersicht verteilt war.
            Die drei übrigen Arten behalten die Zeilendarstellung — der Umsetzungs-Lauf bis zum
            Folgepaket, `REVIEW` und `NIGHTPLAN` auf Dauer (E5). */}
        {entwurf
          ? lauf.items.map((item, position) =>
              lauf.mode === 'CHAIN' ? (
              <KettenVorgang
                key={`${item.cardNumber}-${position}`}
                item={item}
                standItem={stand?.items.find((eintrag) => eintrag.cardNumber === item.cardNumber)}
                vorgaben={stand?.stand?.vorgabenMin}
                modus={lauf.mode}
                katalog={katalog}
                vorhabenKarten={vorhabenKarten}
                haeufigkeit={haeufigkeitsText(item, lauf.gespeichert, zaehler, aufbewahrteLaeufe)}
                istRot={istRot}
                onOeffnen={onOeffnen}
              />
              ) : (
                <UmsetzungsVorgang
                  key={`${item.cardNumber}-${position}`}
                  item={item}
                  anteil={anteile.get(item.cardNumber)}
                  kennzahlen={umsetzungsKennzahlen(
                    item,
                    stand?.items.find((eintrag) => eintrag.cardNumber === item.cardNumber),
                    stand?.stand?.kennzahlenHinweis !== undefined,
                  )}
                  modus={lauf.mode}
                  katalog={katalog}
                  vorhabenKarten={vorhabenKarten}
                  haeufigkeit={haeufigkeitsText(item, lauf.gespeichert, zaehler, aufbewahrteLaeufe)}
                  istRot={istRot}
                  onOeffnen={onOeffnen}
                />
              ),
            )
          : lauf.items.map((item, position) => (
              <Arbeitspaket
                key={`${item.cardNumber}-${position}`}
                item={item}
                modus={lauf.mode}
                // Dieselbe Bedingung, die über die Übersicht entscheidet, und nicht die Lauf-Art
                // (#869): Ein aufbewahrter Ketten-Lauf ohne Sitzungsstand bekommt keine Übersicht
                // und verlöre sonst seine Zeilenangaben, ohne etwas dafür zu bekommen.
                gekuerzt={stand !== undefined && lauf.mode === 'CHAIN'}
                katalog={katalog}
                vorhabenKarten={vorhabenKarten}
                haeufigkeit={haeufigkeitsText(item, lauf.gespeichert, zaehler, aufbewahrteLaeufe)}
                istRot={istRot}
                onOeffnen={onOeffnen}
              />
            ))}
        {entwurf && <NachtlaufFuss angaben={fussangaben(lauf, stand)} testId="uebersicht-fuss" />}
      </AccordionDetails>
    </Accordion>
  )
}

export function NightRunPage() {
  const { projectId } = useParams()
  const id = Number.parseInt(projectId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const projectName = useProjectName(validId ? id : null)
  const notify = useSnackbar()

  const [laeufe, setLaeufe] = useState<AnzeigeLauf[]>([])
  const [ergebnisse, setErgebnisse] = useState<ReadonlyMap<string, boolean>>(() => new Map())
  /**
   * Die Startzeitpunkte der Läufe, die in **dieser Sitzung** aus einem Ergebnisstand gedeutet
   * wurden (#775). Der Server trägt kein Herkunftsfeld, und die Seite lädt nach jedem Einliefern
   * sofort neu — ohne diesen Vermerk wäre die Kennzeichnung Sekunden nach dem Hochladen falsch.
   *
   * Sitzungslokal und nicht dauerhaft (Plan #772, Entscheidung 6): Nach einem Neuladen der Seite
   * ist der Speicher leer, auch ein eben erst eingelieferter Lauf zeigt dann „Herkunft unbekannt".
   *
   * State statt Ref — wie das benachbarte {@link ergebnisse}: Der Wert wird **zur Renderzeit**
   * gelesen, und ein Ref darf das nicht (`react-hooks/refs`, ein Ref-Wert löst kein Rendern aus).
   */
  const [ausErgebnisstand, setAusErgebnisstand] = useState<ReadonlySet<string>>(() => new Set())
  /**
   * Die in **dieser Sitzung** gedeuteten Läufe, je Startzeitpunkt (Plan #863, E1). Aus ihnen
   * allein entstehen die Ketten-Übersicht und die Kennzahlen je Vorgang (#872): Zeitvorgaben,
   * Kostenbudget, Modell, Label, Abschlussart, Kosten, Züge und die Arbeitszeit des Modells
   * stehen nur im Ergebnisstand — der Server bewahrt sie nicht auf, und Manne hat am 2026-09-14
   * entschieden, daran nichts zu ändern (#859, Frage 1).
   *
   * <p>Nicht an das Kennzeichen `gespeichert` gebunden, und das ist der Kern der Entscheidung:
   * `protokollLesen` liefert den Lauf ein und ersetzt danach das **ganze** Lauf-Array durch die
   * Server-Sicht, in der `gespeichert` auf `true` steht. Eine so gebundene Übersicht hätte nur
   * zwischen Parsen und Server-Antwort existiert.
   *
   * <p>Sitzungslokal wie das benachbarte {@link ausErgebnisstand}: Nach einem Neuladen der Seite
   * ist der Speicher leer, und der Lauf fällt auf die Zeilendarstellung zurück.
   */
  const [staende, setStaende] = useState<ReadonlyMap<string, NightRun>>(() => new Map())
  const [katalog, setKatalog] = useState<Kartenkatalog>(() => new Map())
  const [vorhabenKarten, setVorhabenKarten] = useState<Vorhabenkatalog>(() => new Map())
  // Leer heißt „zu keiner Klasse ist etwas bekannt" — der Zustand vor dem ersten Abruf und der
  // eines leeren Ringpuffers sind derselbe. `null` heißt dagegen: der Abruf ist gescheitert.
  const [zaehler, setZaehler] = useState<Haeufigkeiten>({})
  const [aufbewahrteLaeufe, setAufbewahrteLaeufe] = useState(0)
  const [meldung, setMeldung] = useState<string | null>(null)
  const [detail, setDetail] = useState<CardByNumber | null>(null)

  // Aufgelöste Karten und bereits aufgeklappte Läufe überdauern das Rendern, ohne es auszulösen:
  // So wird jede Kartennummer je Seitenaufruf genau einmal geladen.
  const katalogRef = useRef(new Map<number, CardByNumber | null>())
  const geladeneLaeufe = useRef(new Set<string>())
  /**
   * Die laufenden Vorhaben-Abrufe je `parentId` (#818). Der Promise wird **vor** dem Warten
   * eingetragen, ohne ein `await` dazwischen: Zwei gleichzeitig aufgeklappte Läufe mit demselben
   * Vorhaben warten so auf denselben Abruf, statt zwei Anfragen auszulösen.
   */
  const vorhabenAbrufeRef = useRef(new Map<number, Promise<Card | null>>())
  const vorhabenKartenRef = useRef(new Map<number, Card | null>())

  useEffect(() => {
    if (!validId) {
      return
    }
    let aktiv = true
    void nightRunsApi
      .list(id)
      .then((views) => {
        if (aktiv) {
          setLaeufe(views.map(ausSicht).sort(nachStartAbsteigend))
          setAufbewahrteLaeufe(views.length)
        }
      })
      .catch((error_: Error) => {
        if (aktiv) notify(error_.message, 'error')
      })
    // Ein Abruf beim Öffnen der Seite, ein weiterer nach erfolgreichem Senden — die Zahl ist
    // projektweit, ein Abruf je Lauf oder je Arbeitspaket wäre die Anfragelawine aus A8.
    void zaehlerLaden(id).then((stand) => {
      if (aktiv) setZaehler(stand)
    })
    return () => {
      aktiv = false
    }
  }, [id, validId, notify])

  /**
   * Löst die Herkunftsketten eines Laufs auf — Stufe für Stufe, jede Nummer nur einmal.
   *
   * <p>`dokumente` sind die Karten, die in der Nacht **aus** den Vorgängen entstanden sind (#868).
   * Sie kommen in dieselbe erste Abrufrunde wie die Wurzelkarten und nicht in einen eigenen Weg:
   * Die Entdoppelung gegen `bekannt` und untereinander wirkt so über beide Quellen zugleich, und
   * eine Nummer, die zugleich Wurzel und Dokument ist, geht genau einmal hinaus.
   */
  const ladeKetten = async (items: readonly AnzeigeItem[], dokumente: readonly number[]) => {
    const bekannt = katalogRef.current
    let offen = [...new Set([...items.map((item) => item.cardNumber), ...dokumente])].filter(
      (n) => !bekannt.has(n),
    )

    while (offen.length > 0) {
      const geladen = await Promise.all(
        // `apiFetch` wirft bei 404; eine nicht auflösbare Nummer ist hier kein Fehler, sondern
        // ein Ergebnis — sie erscheint als „Karte #N nicht gefunden".
        offen.map(async (n) => [n, await cardsApi.byNumber(id, n).catch(() => null)] as const),
      )
      for (const [nummer, karte] of geladen) bekannt.set(nummer, karte)
      offen = [
        ...new Set(
          geladen.flatMap(([, karte]) => (karte?.derivedFrom == null ? [] : [karte.derivedFrom])),
        ),
      ].filter((n) => !bekannt.has(n))
    }

    // Nachgeladen wird allein das Vorhaben der **Wurzelkarte**: Auch fachliche Anforderung, Plan
    // und Idee tragen eine `parentId`, deren Vorhaben aber eine andere Aussage wäre.
    const eltern = [
      ...new Set(
        [...new Set(items.map((item) => item.cardNumber))].flatMap((nummer) => {
          const parentId = bekannt.get(nummer)?.parentId
          return parentId == null ? [] : [parentId]
        }),
      ),
    ]
    await Promise.all(
      eltern.map(async (parentId) => {
        let abruf = vorhabenAbrufeRef.current.get(parentId)
        if (abruf === undefined) {
          // Ein nicht abrufbares Vorhaben (404, fehlendes Leserecht) ist hier kein Fehler, sondern
          // ein Ergebnis — es erscheint als „Vorhaben: nicht auflösbar".
          abruf = cardsApi.get(parentId).catch(() => null)
          vorhabenAbrufeRef.current.set(parentId, abruf)
        }
        vorhabenKartenRef.current.set(parentId, await abruf)
      }),
    )

    // Beide Stände im selben Durchgang: Sonst zeigte die Seite für einen Wimpernschlag eine
    // aufgelöste Wurzelkarte, deren Vorhaben-Zeile noch „nicht auflösbar" hieße.
    setKatalog(new Map(bekannt))
    setVorhabenKarten(new Map(vorhabenKartenRef.current))
  }

  const aufklappen = useCallback(
    (lauf: AnzeigeLauf) => {
      if (geladeneLaeufe.current.has(lauf.startedAt)) {
        return
      }
      geladeneLaeufe.current.add(lauf.startedAt)
      void ladeKetten(lauf.items, dokumentNummern(staende.get(lauf.startedAt)))
    },
    // `ladeKetten` wird bei jedem Rendern neu erzeugt und ließe sich nicht als Abhängigkeit
    // führen, ohne die ganze Funktion selbst einzupacken — ein Umbau, den dieses Paket nicht
    // verlangt. Was sie an Veränderlichem liest, steht dafür vollständig in der Liste: `staende`
    // entscheidet über die Dokumentnummern, `id` über das Projekt, alles Übrige sind Refs.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- ladeKetten liest nur staende, id und Refs
    [staende, id],
  )

  /**
   * Der oberste Lauf steht beim Öffnen der Seite offen (#914, E7), und damit muss auch seine
   * Herkunftskette geladen werden: Ein `defaultExpanded`-Aufklappfeld löst kein `onChange` aus.
   *
   * Hier und nicht in den drei Ladepfaden: Sonst müsste jeder von ihnen einzeln daran denken, und
   * der nächste vergessene Pfad zeigte einen offenen Lauf ohne aufgelöste Kette. `aufklappen`
   * bleibt die Stelle, die ein zweites Laden verhindert — der Effekt darf also mehrfach laufen.
   */
  useEffect(() => {
    const oberster = laeufe[0]
    if (oberster !== undefined) {
      aufklappen(oberster)
    }
  }, [laeufe, aufklappen])

  /**
   * Liest den Ergebnisstand im Browser, zeigt die Auswertung und liefert sie ein. Die gedeutete
   * Auswertung steht **vor** dem Senden auf der Seite: Scheitert das Einliefern, bleibt sie
   * sichtbar, und die Meldung nennt den Grund.
   */
  const protokollLesen = async (datei: File) => {
    setMeldung(null)
    let text: string
    try {
      text = await readTextFile(datei)
    } catch {
      notify('Die Datei konnte nicht gelesen werden.', 'error')
      return
    }

    const ergebnis = parseNightRunErgebnisstand(text)
    if (!ergebnis.ok) {
      setMeldung(nichtDeutbar(ergebnis))
      return
    }

    // Ein Ergebnisstand ist genau ein Lauf; ein zweiter Stand desselben Laufs ersetzt den ersten.
    const run = ergebnis.run
    // Vermerkt **vor** dem Senden: Der Lauf ist aus dem Ergebnisstand entstanden, unabhängig davon,
    // ob die Einlieferung gleich gelingt.
    setAusErgebnisstand((bisher) => new Set(bisher).add(run.startedAt))
    // Ebenfalls **vor** dem Einliefern vermerkt (E1): Gleich ersetzt die Server-Sicht das ganze
    // Lauf-Array, und ein erst danach gefüllter Speicher trüge die Angaben des Stands nicht mehr.
    // Seit #872 für **jede** Lauf-Art: Nicht nur die Ketten-Übersicht liest den Stand, sondern
    // auch die Kennzahlen je Vorgang der drei anderen Arten.
    setStaende((bisher) => new Map(bisher).set(run.startedAt, run))
    setLaeufe((bisher) =>
      [ausParser(run), ...bisher.filter((alt) => alt.startedAt !== run.startedAt)].sort(
        nachStartAbsteigend,
      ),
    )

    if (run.incomplete) {
      setMeldung(UNVOLLSTAENDIG)
      return
    }

    // Nachtplan-Läufe bleiben browser-only (Plan #803, Entscheidung 8): kein Einliefern, keine
    // Kennzeichnung „neu angelegt"/„lag schon vor", kein Neuladen der Liste.
    if (!istEinlieferbar(run)) {
      return
    }

    let antwort: Awaited<ReturnType<typeof nightRunsApi.submit>>
    try {
      antwort = await nightRunsApi.submit(id, [zurEinlieferung(run)])
    } catch (error_) {
      notify(apiErrorMessage(error_, 'Einliefern fehlgeschlagen.'), 'error')
      return
    }
    setErgebnisse(new Map(antwort.map((eintrag) => [eintrag.startedAt, eintrag.created])))
    // Eingeliefert ist eingeliefert — scheitert nur das Nachladen der Liste, bleibt die
    // Einlieferung bestehen; ein erneuter Versuch waere hier ein unnoetiges Duplikat.
    try {
      const views = await nightRunsApi.list(id)
      setLaeufe(views.map(ausSicht).sort(nachStartAbsteigend))
      setAufbewahrteLaeufe(views.length)
      setZaehler(await zaehlerLaden(id))
    } catch (error_) {
      notify(apiErrorMessage(error_, 'Eingeliefert, Liste konnte nicht aktualisiert werden.'), 'error')
    }
  }

  if (!validId) {
    return <Alert severity="error">Ungültige Projekt-ID.</Alert>
  }

  return (
    // Das Theme des Entwurfs liegt über dem Inhaltsbereich und nur über ihm (#914, E2): keine
    // globalen CSS-Regeln, die die übrige Anwendung mitfärbten. Die `AppShell` mit AppBar und
    // Drawer bleibt außen vor (E8) — der Entwurf beschreibt allein den Inhaltsbereich.
    <>
      <ThemeProvider theme={nachtlaufTheme}>
        <Box>
          {/* Brotkrumenpfad und „Protokoll einlesen" kennt der Entwurf nicht. AK 10: Wo er kein
              Element vorsieht, bleibt es in seiner Funktion erhalten und wird eingepasst — als
              schmale Zeile über dem Entwurfskopf, in dessen Schriftbild und Farben (E9). */}
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Breadcrumbs
              items={[
                { label: 'Projekte', to: '/' },
                { label: projectName ?? 'Projekt', to: `/projects/${id}` },
                { label: 'Nachtlauf' },
              ]}
            />
            {/* Dateiauswahl wie in der Ideen-Seite: Button als <label> mit verstecktem Input. */}
            <Button variant="contained" component="label">
              Protokoll einlesen<input
                hidden
                type="file"
                accept=".json,application/json"
                aria-label="Protokolldatei auswählen"
                onChange={(e) => {
                  const datei = e.target.files?.[0]
                  // Zurücksetzen, damit dieselbe Datei erneut gewählt werden kann.
                  e.target.value = ''
                  if (datei) void protokollLesen(datei)
                }}
              />
            </Button>
          </Stack>

          {meldung !== null && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {meldung}
            </Alert>
          )}

          {/* Der Verbrauchs-Bereich (Issue #941) liegt im selben Theme-Teilbaum und auf derselben
              Route (Plan #933 E13): `CLAUDE-design.md` erlaubt die Gestaltung des Entwurfs nur
              für den Inhaltsbereich dieser einen Seite. */}
          <NachtlaufVerbrauchBereich projectId={id} />

          {laeufe.length === 0 && <Typography color="text.secondary">Noch keine Auswertung vorhanden.</Typography>}

          {laeufe.map((lauf, position) => (
            <LaufPanel
              key={lauf.startedAt}
              lauf={lauf}
              zuerst={position === 0}
              ergebnis={ergebnisse.get(lauf.startedAt)}
              ausErgebnisstand={ausErgebnisstand}
              // Der Speicher entscheidet, ob zu genau diesem Lauf ein Ergebnisstand dieser Sitzung
              // vorliegt; welche Darstellung daraus entsteht, entscheidet der Modus am Anzeigelauf —
              // er steht auch am neu geladenen.
              stand={staende.get(lauf.startedAt)}
              katalog={katalog}
              vorhabenKarten={vorhabenKarten}
              zaehler={zaehler}
              aufbewahrteLaeufe={aufbewahrteLaeufe}
              onAufklappen={() => aufklappen(lauf)}
              onOeffnen={setDetail}
            />
          ))}

        </Box>
      </ThemeProvider>

      {/* Außerhalb des Theme-Teilbaums: Nicht-Ziel 2 des Fachplans verbietet, die Gestaltung auf
          die übrige Anwendung zu übertragen, und der Kartendialog gehört zu ihr — auch wenn er
          von dieser Seite aus geöffnet wird. Maßgeblich ist die Stellung im React-Baum, nicht das
          Portal, in dem der Dialog im DOM landet. */}
      {detail !== null && (
        <CardDetailModal
          card={detail}
          canEdit={false}
          projectId={id}
          onClose={() => setDetail(null)}
        />
      )}
    </>
  )
}
