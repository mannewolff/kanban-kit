// Schriften des Entwurfs `docs/mockup-leitstand-nachtlauf.html`, mit der Anwendung ausgeliefert.
// AK 11 des Fachplans #903 verbietet den Bezug von einem fremden Server: Eine Instanz ohne
// Internetzugang soll dasselbe Schriftbild zeigen wie der Entwurf.
//
// Chivo steht hier und nicht in `main.tsx`: Diese Seite ist ein lazy geladener Route-Chunk
// (`App.tsx`), ein Import in `main.tsx` lüde die Schnitte in jede Seite der Anwendung — entgegen dem
// Performance-Budget aus `CLAUDE-react.md`. Plex Sans und Plex Mono lädt seit #978 auch `main.tsx`;
// die Importe hier bleiben, damit die Seite ihre Schnitte selbst benennt — Vite bündelt sie einmal.
//
// Je Datei ein Gewicht aus dem Latin-Subset, keine Sammelimporte der Pakete.
import '@fontsource/chivo/latin-600.css'
import '@fontsource/chivo/latin-800.css'
import '@fontsource/ibm-plex-sans/latin-400.css'
import '@fontsource/ibm-plex-sans/latin-500.css'
import '@fontsource/ibm-plex-sans/latin-600.css'
import '@fontsource/ibm-plex-mono/latin-400.css'
import '@fontsource/ibm-plex-mono/latin-600.css'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { ThemeProvider } from '@mui/material/styles'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { cardsApi, type Card, type CardByNumber } from '../api/cards'
import { apiErrorMessage } from '../api/client'
import {
  nightRunsApi,
  type NightRunBudgetOrigin,
  type NightRunBudgetView,
  type NightRunErrorClassCounts,
  type NightRunItemStageView,
  type NightRunOutcomeView,
  type NightRunServerMode,
  type NightRunStage,
  type NightRunSubmission,
  type NightRunUsage,
  type NightRunUsageView,
  type NightRunView,
} from '../api/nightRuns'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { CardDetailModal } from '../components/CardDetailModal'
import { Led, melderFarbe } from '../components/leitstand/LeitstandBausteine'
import {
  NachtlaufKartenchips,
  type Kartenchip,
} from '../components/nachtlauf/NachtlaufKartenchips'
import { NachtlaufBefund } from '../components/nachtlauf/NachtlaufBefund'
import { KupferwarteBereich } from '../components/nachtlauf/KupferwarteBereich'
import {
  NachtlaufLaufInstrumente,
  type Kostenaufteilung,
} from '../components/nachtlauf/NachtlaufLaufInstrumente'
import { LaufMarke, NachtlaufLaufPlatte } from '../components/nachtlauf/NachtlaufLaufPlatte'
import { LaufArtSymbol, type LaufArt } from '../components/leitstand/LaufArtSymbol'
import { NachtlaufVorgangszeile } from '../components/nachtlauf/NachtlaufVorgangszeile'
import {
  NachtlaufStufenband,
  type Bandabschnitt as BandabschnittForm,
} from '../components/nachtlauf/NachtlaufStufenband'
import { NachtlaufAnteilsbalken } from '../components/nachtlauf/NachtlaufAnteilsbalken'
import { NachtlaufFuss, type Fussangabe as FussangabeForm } from '../components/nachtlauf/NachtlaufFuss'
import { NACHTLAUF_WURZEL_SX, nachtlaufTheme } from '../nachtlaufDesign'
import { KUPFER, NUT, RAND, TEXT_SCHWACH, ZAHL } from '../theme'
import { useSnackbar } from '../components/SnackbarProvider'
import { epicColor } from '../lib/epicMeta'
import { formatDuration } from '../lib/formatDuration'
import type { Melder } from '../lib/leitstand'
import {
  auskunftOhneArbeit,
  ersteZeile,
  kostenText,
  kurzHash,
  laeuftNoch,
  laufDauer,
  laufMelder,
  paketDauer,
  paketZaehlung,
  tagZeit,
  MELDER_JE_FEHLERKLASSE,
  MELDER_JE_ZUSTAND,
} from '../lib/leitstand'
import { betrag, menge } from '../lib/nachtlaufFormat'
import {
  buildHandoffText,
  kurzGrund,
  nightRunZustandsText,
  NIGHT_RUN_ERROR_CLASS_TEXT,
  NIGHT_RUN_VERDICT_TEXT,
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
  type NightRunKettenStufe,
  type NightRunKettenStufen,
  type NightRunState,
  type NightRunStufenvorgaben,
} from '../lib/nightRunLog'
import { ermittleErzeugnisse, type Erzeugnisse } from '../lib/kettenErzeugnisse'
import { readTextFile } from '../lib/readTextFile'
import { zyklusBeschriftung, zyklusDavor, zyklusDesStarts } from '../lib/verbrauchZeitraum'
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
   * Der Commit, den der Vorgang hinterlassen hat (#988) — in der Zeile die letzte Spalte;
   * `undefined`, wo der Lauf keinen gemeldet hat.
   */
  commitHash: string | undefined
  /**
   * Der aufbewahrte Verbrauch (Issue #949); `undefined` allein am eben geparsten Lauf — dort gibt
   * es noch keinen aufbewahrten Stand. Ein aufbewahrter ohne Messung traegt vier leere Felder.
   */
  verbrauch: Verbrauch | undefined
  /**
   * Die Arbeitsschritte, die dieser Vorgang durchlaufen hat (Issue #1116) — aus der Server-Antwort
   * (`stages`, seit #1113). `undefined`, wo der Lauf keine fuehrt: am eben geparsten Lauf und an
   * jedem Vorgang mit leerer Stufenliste. Aus ihnen entsteht das Stufenband eines Laufs, den
   * niemand eingelesen hat.
   */
  stufen: Bandstufen | undefined
}

/**
 * Die Arbeitsschritte eines Ketten-Vorgangs, so weit das Stufenband sie braucht: je **erreichtem**
 * Schritt seine Dauer; ein nicht erreichter fehlt.
 *
 * <p>Beide Quellen erfuellen diese Form — die Schritte des eingelesenen Stands
 * ({@link NightRunKettenStufen}) und die der Server-Antwort ({@link AnzeigeItem.stufen}). Ein
 * gemeinsamer Typ statt zweier Zweige in {@link bandabschnitt}: Das Band rechnet an beiden
 * dieselbe Rechnung, und zwei Zweige liefen beim naechsten Feld auseinander.
 */
type Bandstufen = Partial<Record<NightRunKettenStufe, { dauerMs?: number; kostenUsd?: number }>>

/**
 * Der aufbewahrte Verbrauch in der Anzeigeform (Issue #949) — `undefined` statt `null`, wie
 * ueberall im Anzeigemodell (Issue #734). Jedes Feld fehlt einzeln: Ein hochgeladener Lauf traegt
 * einen Kostenbetrag ohne Mengen, ein gemeldeter beides.
 *
 * <p>Modellzeit und Zuege kamen mit Issue #1115 dazu — sie stehen seit #1113 in derselben Antwort
 * und gehoeren deshalb in denselben Typ; ein zweites Buendel liefe beim naechsten Feld auseinander.
 */
interface Verbrauch {
  kostenUsd: number | undefined
  eingabe: number | undefined
  ausgabe: number | undefined
  zwischenspeicher: number | undefined
  modellzeitMs: number | undefined
  zuege: number | undefined
}

/**
 * Die Vorgaben eines Ketten-Laufs in der Anzeigeform (Issue #1115).
 *
 * <p>Die Zeitvorgaben stehen unter den Schlüsseln von {@link NightRunKettenStufen}, nicht unter den
 * Feldnamen des Servers: Damit liest {@link vorgabenText} sie unverändert weiter — Vorgabe und
 * Verbrauch eines Arbeitsschritts finden sich seit #859 unter demselben Namen.
 */
interface Budget {
  vorgabenMin: NightRunStufenvorgaben
  kostenUsd: number | undefined
  /** Woher die Vorgaben stammen; `undefined` ist die dritte Aussage aus AK 3: „nicht angegeben". */
  herkunft: NightRunBudgetOrigin | undefined
  /**
   * Die Feldnamen, die aus den Voreinstellungen kamen — **roh, wie der Lauf sie meldet**. Die
   * Übersetzung in die Worte der Fußzeile steht in {@link herkunftText}: Was das Board nicht kennt,
   * lässt es dort aus, statt es hier abzuweisen (Plan #1110, E4 und E11).
   */
  voreingestellteFelder: readonly string[]
}

/**
 * Die Lauf-Arten, die in der Anzeige vorkommen können: die des Browser-Parsers **und** die, die der
 * Server melden darf. Seit Issue #1016 kennt `NightRunServerMode` zusätzlich `INTERACTIVE`.
 *
 * <p>Auf der Seite steht das Wort heute nicht: Die Laufliste ist serverseitig auf Nachtläufe
 * beschränkt (`NightRunService.list`, Issue #1012), eine Sitzung erreicht sie also gar nicht. Der
 * Typ lässt sie trotzdem zu — ein Wort dafür ist billiger als ein Cast, der den Schutz aushebelte.
 */
type AnzeigeArt = LaufArt

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
  mode: AnzeigeArt
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
  /**
   * Grund, warum der Lauf nichts abgearbeitet hat (Issue #1068); `undefined`, wenn er gearbeitet
   * hat, aus der Zeit vor der Umstellung stammt oder eben erst im Browser geparst wurde.
   */
  ohneArbeit: string | undefined
  /**
   * Grund eines harten Abbruchs (Issue #1145) — **vollständig**, wie der Runner ihn gemeldet hat;
   * `undefined` an jedem Lauf, der nicht abbrach, aus der Zeit vor der Umstellung stammt oder eben
   * erst im Browser geparst wurde. Gekürzt wird er allein dort, wo er neben anderem in einer Zeile
   * steht — die Kopfmarke nimmt {@link kurzGrund}, die aufgeklappte Platte den ganzen Text (AK 4).
   */
  abbruchGrund: string | undefined
  /**
   * Der Befund des Servers (Issue #1078); `undefined` beim eben geparsten Lauf — der ist noch bei
   * keinem Server gewesen und wird deshalb weiterhin lokal beurteilt (Plan #1072 E28).
   */
  befund: NightRunOutcomeView | undefined
  /**
   * Die technische Lauf-Id (Issue #1085); `undefined` beim eben geparsten Lauf — der ist noch bei
   * keinem Server gewesen und hat deshalb keine. Sie ist die Kennung, ueber die eine Stoerzeile des
   * Plattform-Leitstands auf genau diesen Lauf zeigt (AK 7, Plan #1072 E10).
   */
  laufId: number | undefined
  verbrauch: Verbrauch | undefined
  /**
   * Die Vorgaben, unter denen der Lauf antrat (Issue #1115); `undefined`, wo er keine mitbrachte —
   * ein Lauf vor der Umstellung, ein eben geparster oder einer, den der Browser hochgeladen hat.
   * Der Upload-Weg führt sie nicht (Plan #1110, E14), und für diese Läufe tritt in
   * {@link kettenBudget} der eingelesene Ergebnisstand ein.
   */
  budget: Budget | undefined
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
 * Die Zustandsfarbe eines Vorgangs seit #988: der Melder des Leitstands. Die eigenen Palettenpfade
 * `nightRun.*` der Nachtlauf-Ausnahme sind damit aus den Laufblöcken verschwunden — sie folgen
 * Kupferwarte, und dort trägt der Melder diese Aussage (`CLAUDE-design.md`).
 */
const zustandsFarbe = (zustand: NightRunState): string => melderFarbe(MELDER_JE_ZUSTAND[zustand])

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
  'nicht-unterstuetzt': 'Art des Runs oder Vokabular nicht unterstützt',
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
const UNVOLLSTAENDIG = 'Run noch nicht abgeschlossen — nicht gespeichert'

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

/** Dieselbe Menge ohne einen Eintrag — die Rücknahme eines Vermerks (Issue #1116). */
function ohneEintrag(vermerke: ReadonlySet<string>, eintrag: string): ReadonlySet<string> {
  const rest = new Set(vermerke)
  rest.delete(eintrag)
  return rest
}

/** Dieselbe Abbildung ohne einen Schlüssel — die Schwester von {@link ohneEintrag}. */
function ohneSchluessel<T>(
  abbildung: ReadonlyMap<string, T>,
  schluessel: string,
): ReadonlyMap<string, T> {
  const rest = new Map(abbildung)
  rest.delete(schluessel)
  return rest
}

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
  // Wie die Herkunftsfelder leer: Den Grund kennt nur der Server, ein eben geparster Lauf war
  // noch bei keinem.
  ohneArbeit: undefined,
  // Wie der Grund ohne Arbeit leer: Ein eben geparster Lauf war bei keinem Server, und ein
  // Abbruchgrund entsteht erst dort (Plan #1139, E7).
  abbruchGrund: undefined,
  befund: undefined,
  laufId: undefined,
  verbrauch: undefined,
  // Wie die Herkunftsfelder leer: Die Vorgaben **des Laufs** meldet allein der Runner ueber den
  // Token-Weg. Was der eingelesene Stand dazu fuehrt, holt `kettenBudget` von dort (Issue #1115).
  budget: undefined,
  items: run.items.map((item) => ({
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    errorClass: item.errorClass,
    durationMs: item.durationMs,
    commitHash: item.commit,
    excerpt: item.excerpt,
    verbrauch: undefined,
    // Wie der Verbrauch leer: Die Arbeitsschritte eines eben geparsten Laufs stehen in seinem
    // Ergebnisstand, und von dort holt sie {@link bandstufen} (Issue #1116).
    stufen: undefined,
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
    ? {
        kostenUsd: undefined,
        eingabe: undefined,
        ausgabe: undefined,
        zwischenspeicher: undefined,
        modellzeitMs: undefined,
        zuege: undefined,
      }
    : {
        kostenUsd: view.costUsd ?? undefined,
        eingabe: view.inputTokens ?? undefined,
        ausgabe: view.outputTokens ?? undefined,
        zwischenspeicher: view.cachedInputTokens ?? undefined,
        modellzeitMs: view.modelDurationMs ?? undefined,
        zuege: view.turns ?? undefined,
      }

/**
 * Die Vorgaben vom Server in der Anzeigeform (Issue #1115).
 *
 * <p>Ein `null` des Servers bleibt `undefined` und wird **nicht** zum leeren Budget — anders als
 * bei {@link ausVerbrauch}: „dieser Lauf brachte keine Vorgaben mit" ist hier kein Endzustand,
 * sondern die Bedingung, unter der der eingelesene Stand einspringt (E14). Ein leeres Budget
 * verdeckte ihn und zeigte am Rückfallweg „nicht angegeben", wo Zahlen vorliegen.
 */
const ausBudget = (view: NightRunBudgetView | null): Budget | undefined =>
  view === null
    ? undefined
    : {
        // Ein fehlender Schlüssel statt eines `undefined`-Werts: `vorgabenText` zählt die Schritte,
        // die eine Vorgabe **führen**, und ein Schlüssel mit `undefined` wäre dort einer zu viel.
        vorgabenMin: vorgabenAusSicht(view),
        kostenUsd: view.kostenUsd ?? undefined,
        herkunft: view.origin ?? undefined,
        voreingestellteFelder: view.defaultFields,
      }

/**
 * Die Vorgaben, die die Fußzeile eines Ketten-Laufs zeigt (Issue #1115): **die des Laufs**, und wo
 * er keine mitbrachte, die des eingelesenen Ergebnisstands.
 *
 * <p>Der Rückfall ist keine Vermischung zweier Quellen, sondern die zweite Lage desselben Wegs:
 * Der Upload liefert Budgets nicht mit (Plan #1110, E14), also führt der Server zu einem
 * eingelesenen Lauf keine — und ohne diesen Zweig zeigte der Rückfallweg weniger als vor diesem
 * Paket, während die fachliche Quelle (#993) gerade das Gegenteil verlangt. Der Stand trägt keine
 * Herkunft; sie bleibt dort „nicht angegeben".
 */
const kettenBudget = (lauf: AnzeigeLauf, stand: NightRun | undefined): Budget =>
  lauf.budget ?? {
    vorgabenMin: stand?.stand?.vorgabenMin ?? {},
    kostenUsd: stand?.stand?.kostenBudgetUsd,
    herkunft: undefined,
    voreingestellteFelder: [],
  }

/** Die Zeitvorgaben der Antwort unter den Schlüsseln der Arbeitsschritte; ein Schritt ohne Vorgabe fehlt. */
function vorgabenAusSicht(view: NightRunBudgetView): NightRunStufenvorgaben {
  const minuten: Record<NightRunKettenStufe, number | null> = {
    plan: view.planMin,
    review: view.reviewMin,
    pakete: view.paketeMin,
    abdeckung: view.abdeckungMin,
  }
  return Object.fromEntries(
    KETTEN_STUFEN.flatMap(({ schluessel }) =>
      minuten[schluessel] === null ? [] : [[schluessel, minuten[schluessel]]],
    ),
  )
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
  // Die Auskunft kommt aus dem **Befund** und nicht aus `noWorkReason` am Datensatz (Issue #1190,
  // E10): Ein Lauf, der alle Pakete zurueckstellte, traegt den Rueckfalltext, ist aber „mit
  // Vorbehalt" — er bekaeme sonst die Marke „nichts zu tun". Uebersetzt wird hier, an der einen
  // Stelle zwischen Server-Sicht und Anzeigetyp; die Marke kennt nur den Anzeigetyp, und der im
  // Browser geparste Lauf setzt `ohneArbeit` ohnehin nie.
  ohneArbeit: auskunftOhneArbeit(view) ?? undefined,
  abbruchGrund: view.abortReason ?? undefined,
  befund: view.outcome,
  laufId: view.id,
  verbrauch: ausVerbrauch(view.usage),
  budget: ausBudget(view.budget),
  items: view.items.map((item) => {
    const stufen = ausStufen(item.stages)
    return {
      cardNumber: item.cardNumber,
      title: item.title,
      state: item.state,
      errorClass: item.errorClass ?? undefined,
      durationMs: stufen === undefined ? (item.durationMs ?? undefined) : stufenDauer(stufen),
      commitHash: item.commitHash ?? undefined,
      excerpt: item.excerpt ?? undefined,
      verbrauch: ausVerbrauch(item.usage),
      stufen,
    }
  }),
})

/**
 * Die Dauer eines Kettenvorgangs als Summe seiner Arbeitsschritte (Issue #1106, AK 4) — dieselbe
 * Rechnung wie {@link stufenZeitSumme} am eingelesenen Stand. Die gemeldete Dauer des Vorgangs
 * reicht bis zum Ende der Umsetzung seiner Pakete, die darunter noch einmal mit eigener Dauer
 * stehen; sie zählte diese Zeit doppelt. `undefined`, wo kein Schritt eine Dauer meldet.
 */
function stufenDauer(stufen: Bandstufen): number | undefined {
  const dauern = KETTEN_STUFEN.flatMap(({ schluessel }) => {
    const dauer = stufen[schluessel]?.dauerMs
    return dauer === undefined ? [] : [dauer]
  })
  return dauern.length === 0 ? undefined : dauern.reduce((summe, dauer) => summe + dauer, 0)
}

/**
 * Die Kosten eines Kettenlaufs, aufgeteilt in Planung und Umsetzung (Issue #1106, AK 3).
 *
 * <p><b>Planung</b> sind die Kosten der Arbeitsschritte der Kettenvorgänge, <b>Umsetzung</b> der
 * Rest der Laufkosten. Der Rest und nicht die Summe der Paketzeilen: Die Laufsumme liegt über der
 * Summe der Vorgänge, wo Sitzungen keinem Vorgang zuzuordnen waren (siehe
 * `NachtlaufLaufInstrumente`) — so ergeben beide Anteile zusammen immer das Gesamt.
 *
 * <p>`undefined` am Lauf, der keine eingelieferte Kette ist. Ein einzelner Anteil fehlt, wo er sich
 * nicht bilden lässt — kein Schritt meldet Kosten, oder der Lauf meldet kein Gesamt.
 */
function kostenaufteilung(lauf: AnzeigeLauf): Kostenaufteilung | undefined {
  const kettenvorgaenge = lauf.items.filter((item) => item.stufen !== undefined)
  if (lauf.mode !== 'CHAIN' || kettenvorgaenge.length === 0) {
    return undefined
  }
  const stufenkosten = kettenvorgaenge.flatMap((item) =>
    KETTEN_STUFEN.flatMap(({ schluessel }) => {
      const kosten = item.stufen?.[schluessel]?.kostenUsd
      return kosten === undefined ? [] : [kosten]
    }),
  )
  const planungUsd =
    stufenkosten.length === 0 ? undefined : stufenkosten.reduce((summe, kosten) => summe + kosten, 0)
  const gesamtUsd = lauf.verbrauch?.kostenUsd
  const umsetzungUsd =
    planungUsd === undefined || gesamtUsd === undefined ? undefined : gesamtUsd - planungUsd
  return { planungUsd, umsetzungUsd }
}

/** Die Schlüssel der Anzeige zu den Stufennamen des Servers (Issue #1113). */
const STUFE_JE_NAME: Readonly<Record<NightRunStage, NightRunKettenStufe>> = {
  PLAN: 'plan',
  REVIEW: 'review',
  PAKETE: 'pakete',
  ABDECKUNG: 'abdeckung',
}

/**
 * Die Arbeitsschritte der Server-Antwort in der Form des Bands (Issue #1116).
 *
 * <p>Eine **leere** Liste ergibt `undefined` und nicht `{}`: Der Server schickt sie an jedem
 * Vorgang, der keine Kette ist, und ein Band aus vier nie erreichten Schritten behauptete dort
 * eine Kette, die es nicht gab.
 */
function ausStufen(stages: readonly NightRunItemStageView[]): Bandstufen | undefined {
  if (stages.length === 0) {
    return undefined
  }
  const stufen: Bandstufen = {}
  for (const stage of stages) {
    stufen[STUFE_JE_NAME[stage.stage]] = {
      dauerMs: stage.durationMs ?? undefined,
      kostenUsd: stage.usage?.costUsd ?? undefined,
    }
  }
  return stufen
}

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
  modus: AnzeigeArt,
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
    : `${beschriftung}: ${anzahl} von ${aufbewahrteLaeufe} aufbewahrten Runs`
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

/**
 * Die vier Arbeitsschritte einer Kette in der Reihenfolge, in der `stufenDerKette` sie läuft —
 * derselbe Schlüsselraum wie {@link NightRunKettenStufen} und {@link NightRunStufenvorgaben}, also
 * stehen Vorgabe und Verbrauch eines Schritts unter demselben Namen.
 *
 * <p>Als Liste mit Beschriftung und nicht über `Object.keys`: Die Reihenfolge ist Teil der Aussage,
 * und ein fünfter Schritt bräuchte hier eine deutsche Benennung, statt still als Schlüssel
 * durchzurutschen — dieselbe Absicherung, die das Symbol der Laufart über seinen `Record` hat.
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
 *
 * <p><b>Das Fehlen eines Ergebnisstands ist keine Aussage über den Abschluss</b> (Issue #1070).
 * Der `undefined`-Fall hier gilt einem <em>vorliegenden</em> Stand ohne `abschluss` — dort sagt die
 * Datei selbst, dass der Lauf nicht zu Ende lief. Ob die Angabe überhaupt in die Metazeile kommt,
 * entscheidet deshalb {@link kopfText} am Vorhandensein des Stands, nicht diese Funktion. Was über
 * den Abschluss des Laufs auszusagen ist, trägt allein die Kopfmarke {@link
 * UNVOLLSTAENDIG_GEMELDET}, und die hängt am gemeldeten Zustand.
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
  stufen: Bandstufen | undefined,
): NightRunKettenStufe | undefined =>
  KETTEN_STUFEN.findLast(({ schluessel }) => stufen?.[schluessel] !== undefined)?.schluessel

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

/**
 * Der höchste Kostenverbrauch eines einzelnen Vorgangs (AK 12), gerechnet über die **Vorgänge des
 * Laufs** — nicht mehr über den eingelesenen Stand (Issue #1115, Plan #1110 E10). Gerechnet wird
 * im Browser und nicht im Server: Die Vorgänge kommen ohnehin vollständig mit, und ein Antwortfeld
 * `maxItemCostUsd` wäre eine zweite Wahrheit über dieselbe Liste.
 *
 * <p>Je Vorgang gilt derselbe Betrag wie in seiner Kostenspalte ({@link vorgangskosten}) — sonst
 * stünde in der Fußzeile eine Zahl, die die Zeile darüber nicht kennt (AK 4, letzter Satz).
 *
 * <p>Der Zusatz „aus n von m Vorgängen" steht, sobald **nicht alle** Vorgänge Kosten tragen: Ohne
 * ihn läse sich der Höchstwert einer halb gemeldeten Nacht wie der einer ganzen.
 */
function hoechsteKosten(items: readonly AnzeigeItem[], stand: NightRun | undefined): string {
  const gemeldet = items.flatMap((item) => {
    const wert = vorgangskosten(item, standVorgang(stand, item.cardNumber))
    return wert === null ? [] : [wert]
  })
  if (gemeldet.length === 0) {
    return 'nicht angegeben'
  }
  const hoechste = betrag(Math.max(...gemeldet))
  return gemeldet.length === items.length
    ? hoechste
    : `${hoechste} (aus ${gemeldet.length} von ${items.length} Vorgängen)`
}

/**
 * Die fünf Budgetfelder in den Worten aus AK 3 der fachlichen Quelle (#993).
 *
 * <p>Die Übersetzung liegt **hier** und nicht am Server (Plan #1110, E4): Er nimmt die Feldnamen
 * an, wie der Lauf sie meldet, und `ketteBudgetDefaults` führt mehr davon, als die Fußzeile zeigt —
 * `varianteBLabel`, `umsetzungMin`, `kostenUsdB`. Als `Map` und nicht als `Record`: Ein Name, den
 * das Board nicht kennt, soll `undefined` ergeben und ausgelassen werden, statt den Build zu
 * binden oder als roher Feldname auf der Seite zu erscheinen (E11).
 */
const BUDGETFELD_TEXT = new Map<string, string>([
  ['planMin', 'Zeitvorgabe Plan'],
  ['reviewMin', 'Zeitvorgabe Prüfung'],
  ['paketeMin', 'Zeitvorgabe Pakete'],
  ['abdeckungMin', 'Zeitvorgabe Abdeckung'],
  ['kostenUsd', 'Kostenbudget'],
])

/**
 * Die Herkunft der Budgets in einer der drei Aussagen aus AK 3 — „eingestellt", „aus
 * Voreinstellungen: <Angaben>" oder „nicht angegeben".
 *
 * <p>Die dritte ist die fehlende Herkunft selbst: ein Lauf ohne Budgets, ein Lauf des Upload-Wegs
 * (E14) oder ein Kit-Stand, der die Herkunft noch nicht meldet. Geraten wird sie nicht —
 * „eingestellt" wäre dort eine Behauptung über die Konfiguration einer fremden Maschine.
 *
 * <p>Bleibt von den gemeldeten Feldern keines übrig, das das Board kennt, steht „aus
 * Voreinstellungen" **ohne** Aufzählung: Ein Doppelpunkt mit nichts dahinter sähe aus wie ein
 * Fehler, und der Lauf hat die Aussage ja getroffen — nur über Felder, die hier nicht stehen.
 */
function herkunftText(budget: Budget): string {
  if (budget.herkunft === undefined) {
    return 'nicht angegeben'
  }
  if (budget.herkunft === 'CONFIGURED') {
    return 'eingestellt'
  }
  const namen = budget.voreingestellteFelder.flatMap((feld) => {
    const wort = BUDGETFELD_TEXT.get(feld)
    return wort === undefined ? [] : [wort]
  })
  return namen.length === 0 ? 'aus Voreinstellungen' : `aus Voreinstellungen: ${namen.join(', ')}`
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
function modellzeitText(dauerMs: number | undefined, modellzeitMs: number | undefined): string {
  const zeit = modellzeit(dauerMs, modellzeitMs)
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
function vorgangszeile(
  item: AnzeigeItem,
  verbrauch: Vorgangsverbrauch | undefined,
  ohneKennzahlen: boolean,
): string {
  const dauer =
    item.durationMs === undefined ? 'Dauer nicht gemeldet' : formatDuration(item.durationMs / 1000)
  // Ohne jede Quelle bleibt die Dauer allein: Sie stammt aus dem Lauf selbst. Die drei
  // Fehlanzeigen stünden sonst an einem Vorgang, zu dem nie etwas aufzubewahren war.
  if (ohneKennzahlen || verbrauch === undefined) {
    return dauer
  }
  return [
    dauer,
    verbrauch.kostenUsd === undefined ? 'Kosten nicht gemeldet' : betrag(verbrauch.kostenUsd),
    verbrauch.zuege === undefined ? 'Züge nicht gemeldet' : `${verbrauch.zuege} Züge`,
    modellzeitText(item.durationMs, verbrauch.modellzeitMs),
    ...tokenmengen(verbrauch),
  ].join(' · ')
}

/**
 * Die Ergebniszeile eines Ketten-Vorgangs (#916): Kosten, Züge, der Vermerk fehlender
 * Kostenmeldungen — und der **Anteil der Modellarbeit** (AK 6, seit Issue #872). Der Entwurf führt
 * ihn in jeder Ergebniszeile; bis #916 stand er allein an den drei Nicht-Ketten-Arten.
 */
const vorgangsKennzahlenText = (
  item: AnzeigeItem,
  verbrauch: Vorgangsverbrauch | undefined,
): string =>
  [
    // Die Dauer steht vorneweg. Der Entwurf führt sie in der Ergebniszeile nicht — sie steckt
    // dort im Band —, aber ein Lauf ohne Arbeitsschritte hat kein Band, und dann wäre sie ganz
    // verloren (AK 10).
    item.durationMs === undefined ? 'Dauer nicht gemeldet' : formatDuration(item.durationMs / 1000),
    vorgangsKennzahlen(verbrauch),
    modellzeitText(item.durationMs, verbrauch?.modellzeitMs),
    ...tokenmengen(verbrauch),
  ].join(' · ')

/** Kosten und Züge eines Vorgangs, dazu der Vermerk fehlender Kostenmeldungen (AK 11). */
function vorgangsKennzahlen(verbrauch: Vorgangsverbrauch | undefined): string {
  const vermerk = ohneKostenmeldung(
    verbrauch?.kostenUnbekannt,
    'ein Arbeitsschritt',
    'Arbeitsschritte',
  )
  return [
    verbrauch?.kostenUsd === undefined ? 'Kosten nicht gemeldet' : betrag(verbrauch.kostenUsd),
    ...(verbrauch?.zuege === undefined ? [] : [`${verbrauch.zuege} Züge`]),
    ...(vermerk === null ? [] : [vermerk]),
  ].join(' · ')
}

/**
 * Die Tokenmengen eines Vorgangs (AK 4 aus #993) — oder **gar keine Angabe**, wo weder Ein- noch
 * Ausgabe gemessen wurde.
 *
 * <p>Der Unterschied zu den Fehlanzeigen daneben ist Absicht: Kosten, Züge und Modellzeit führt
 * auch der eingelesene Ergebnisstand, ihr Fehlen ist dort eine Aussage. Tokenmengen führt er
 * **nie** (`NightRunKennzahlen` in `lib/nightRunLog.ts`) — ein „nicht gemessen" auf dem
 * Rückfallweg meldete eine Lücke, die diese Quelle gar nicht haben kann.
 */
const tokenmengen = (verbrauch: Vorgangsverbrauch | undefined): string[] =>
  verbrauch?.eingabe === undefined && verbrauch?.ausgabe === undefined
    ? []
    : [`Eingabe ${menge(verbrauch.eingabe)}`, `Ausgabe ${menge(verbrauch.ausgabe)}`]

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

/**
 * Der Kopf der Übersicht: Modell, Label und Abschluss — jede Angabe nur, wo der Stand sie führt.
 *
 * <p><b>Der Abschluss hängt am Vorliegen eines Ergebnisstands</b> (Issue #1070), nicht daran, ob
 * dieser ein `abschluss`-Feld trägt. Vorher stand der Vermerk bedingungslos da und las sich an
 * einem per Token gemeldeten Lauf — der nie einen hochgeladenen Stand hat — als „noch nicht
 * abgeschlossen", obwohl der Lauf fertig war. Die Prüfung gilt deshalb dem <em>Ergebnisstand</em>
 * und nicht seinen Kopfangaben: Eine hochgeladene Datei ohne Kopfangaben hat hier `stand`
 * `undefined`, sagt mit ihrem fehlenden Abschluss aber sehr wohl etwas aus.
 */
const kopfText = (ergebnisstand: NightRun | undefined): string => {
  if (ergebnisstand === undefined) {
    return ''
  }
  const stand = ergebnisstand.stand
  return [
    ...(stand?.modell === undefined ? [] : [stand.modell]),
    ...(stand?.label === undefined ? [] : [`Label ${stand.label}`]),
    abschlussText(stand?.abschluss),
  ].join(' · ')
}

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
function vermerkAmEnde(istEnde: boolean, item: Bandvorgang): string | null {
  if (!istEnde) {
    return null
  }
  return item.errorClass === 'TIME_BUDGET_EXCEEDED' ? 'am Zeitbudget beendet' : 'hier abgebrochen'
}

/**
 * Was das Band vom Vorgang selbst braucht: seinen Ausgang und seine Fehlerklasse. Beide Quellen
 * führen sie — der eingelesene Stand wie die Server-Antwort —, und der Vermerk am Abschnitt, an
 * dem die Kette riss, hängt an beiden.
 */
type Bandvorgang = Pick<NightRunItem, 'state' | 'errorClass'>

/** Ein Abschnitt des Bands: sein Anteil an der Breite, seine Füllung, seine Zahlen, sein Vermerk. */
function bandabschnitt(
  { schluessel, label }: { schluessel: NightRunKettenStufe; label: string },
  stufen: Bandstufen,
  item: Bandvorgang,
  vorgaben: NightRunStufenvorgaben | undefined,
  letzte: NightRunKettenStufe | undefined,
  mitKosten: boolean,
): Bandabschnitt {
  const stufe = stufen[schluessel]
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
    kosten: erreicht && mitKosten ? kostenText(stufe.kostenUsd ?? null) : null,
    vermerk: erreicht ? vermerkAmEnde(istEnde, item) : 'nicht erreicht',
    farbe: istEnde ? zustandsFarbe(item.state) : KUPFER,
  }
}

/**
 * Die Beschriftung des Bands für Vorlesewerkzeuge: alle vier Schritte mit Verbrauch, Vorgabe und
 * Vermerk. Sie ist der Grund, warum das Band `role="img"` trägt — die Zahlen darunter werden damit
 * nicht ein zweites Mal einzeln vorgelesen, sondern genau einmal in dieser Reihenfolge.
 */
const bandAnsage = (abschnitte: readonly Bandabschnitt[]): string => {
  const schritte = abschnitte.map((a) => {
    const kosten = a.kosten === null ? '' : `, ${a.kosten}`
    const vermerk = a.vermerk === null ? '' : `, ${a.vermerk}`
    return `${a.label} ${a.zahlen}${kosten}${vermerk}`
  })
  return `Stufenband: ${schritte.join(' · ')}`
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
    farbe: zustandsFarbe(item.state),
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
 * Die Arbeitsschritte, aus denen ein Ketten-Vorgang Karten hinterlässt, als Chipgruppen für seinen
 * Block (#916). Nur `plan` und `pakete`: Die beiden anderen Schritte erzeugen gar keine Karten, und
 * eine Zeile „keine Karten" unter einem Schritt, der keine hinterlassen kann, wäre keine Aussage.
 */
interface Chipgruppe {
  label: string
  /** Was steht, wenn in diesem Schritt keine Karte entstand. */
  leer: string
  testId: string
  chips: readonly Kartenchip[]
}

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
 * Die Kartenchips eines Kettenvorgangs (Issue #868, für eingelieferte Läufe #1106). Der eingelesene
 * Stand bleibt die erste Quelle; ohne ihn tragen die Erzeugnisse aus der Herkunft am Board die
 * Chips. Solange sie nicht ermittelt sind, steht **keine** Chipgruppe — „kein Plan" wäre für einen
 * Plan, der nur noch nicht geladen ist, eine Falschaussage.
 */
function kettenchips(
  modus: AnzeigeArt,
  item: AnzeigeItem,
  standItem: NightRunItem | undefined,
  erzeugt: Erzeugnisse | undefined,
  katalog: Kartenkatalog,
): Chipgruppe[] {
  if (modus !== 'CHAIN') {
    return []
  }
  if (standItem !== undefined) {
    return chipgruppen(standItem, katalog)
  }
  if (erzeugt === undefined) {
    return []
  }
  // `DOKUMENT_STUFEN` führt genau die beiden Schritte, in denen Karten entstehen: Plan und Pakete.
  return DOKUMENT_STUFEN.map(({ schluessel, label, leer }) => ({
    label,
    leer,
    testId: `dokumente-${item.cardNumber}-${schluessel}`,
    chips: (schluessel === 'plan' ? erzeugt.plaene : erzeugt.pakete).map(
      (nummer): Kartenchip => ({ id: String(nummer), art: label, zustand: verweisZustand(nummer, katalog) }),
    ),
  }))
}

/**
 * Welche Läufe die Liste zeigt, solange niemand die älteren aufgeklappt hat (Issue #1134): die der
 * letzten zwei Zyklen — Startzeit in der Zone des Lesers, dieselbe Zuordnung wie im Titel (#1127).
 *
 * <p>Drei ältere bleiben trotzdem stehen, weil der Mensch sie gerade im Blick hat: ein Lauf, der noch
 * läuft (er kann über Mittag weiterlaufen, #1109), der über `?lauf=<id>` angesteuerte und ein in
 * dieser Sitzung eingelesener. Begrenzt wird allein die Anzeige — Auswertungen über den Bestand
 * zählen weiter alle aufbewahrten Läufe.
 */
function imBlick(
  lauf: AnzeigeLauf,
  abZyklus: string,
  gesuchteLaufId: number | null,
  ausErgebnisstand: ReadonlySet<string>,
): boolean {
  return (
    zyklusDesStarts(lauf.startedAt) >= abZyklus ||
    laeuftNoch({ complete: lauf.vollstaendig, outcome: lauf.befund }) ||
    (gesuchteLaufId !== null && lauf.laufId === gesuchteLaufId) ||
    ausErgebnisstand.has(lauf.startedAt)
  )
}

/** Die Erzeugnisse eines Laufs, zu dem (noch) keine ermittelt sind — eine Instanz für alle. */
const KEINE_ERZEUGNISSE: ReadonlyMap<number, Erzeugnisse> = new Map()

/** Der Plan, unter dem ein Paket in diesem Lauf entstand — gesucht über alle Kettenvorgänge. */
function planDesPakets(
  nummer: number,
  erzeugnisse: ReadonlyMap<number, Erzeugnisse>,
): number | undefined {
  for (const erzeugt of erzeugnisse.values()) {
    const plan = erzeugt.planJePaket[nummer]
    if (plan !== undefined) {
      return plan
    }
  }
  return undefined
}

/**
 * Die Zeile „Paket aus Plan" im Zustand ihres Verweises. Die Erzeugnisse stehen erst fest, wenn
 * ihre Karten im Katalog liegen (`ladeErzeugnisse`); eine fehlende ist deshalb eine nicht
 * auflösbare, keine, die noch lädt.
 */
function planZustand(nummer: number, katalog: Kartenkatalog): SichtbarerStufenZustand {
  const karte = katalog.get(nummer)
  return karte == null
    ? { art: 'nicht-gefunden', nummer }
    : { art: 'treffer', nummer, karte, abgerissen: false }
}

/**
 * Das Stufenband eines Ketten-Vorgangs: die gerechneten Abschnitte und ihre Ansage. `null`, wo
 * weder der eingelesene Stand noch die Server-Antwort Arbeitsschritte führt.
 *
 * <p>Die Quellen stehen in derselben Rangfolge wie bei {@link vorgangsverbrauch} und aus demselben
 * Grund: Zu einem servergeführten Lauf gibt es keinen Stand mehr, und wo einer vorliegt, ist er
 * die richtige Quelle. Seit Issue #1116 zeigt damit auch ein Lauf sein Band, den niemand
 * eingelesen hat.
 */
function vorgangsband(
  item: AnzeigeItem,
  standItem: NightRunItem | undefined,
  vorgaben: NightRunStufenvorgaben | undefined,
): { abschnitte: readonly Bandabschnitt[]; ansage: string } | null {
  const stufen = standItem?.kettenStufen ?? item.stufen
  if (stufen === undefined) {
    return null
  }
  // Die Kosten je Schritt kommen allein aus der Server-Antwort (Issue #1106): Ein eingelesener
  // Stand zeigt sein Band wie bisher (dort AK 6).
  const mitKosten = standItem?.kettenStufen === undefined
  const letzte = letzteErreichteStufe(stufen)
  const abschnitte = KETTEN_STUFEN.map((stufe) =>
    bandabschnitt(stufe, stufen, item, vorgaben, letzte, mitKosten),
  )
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
 * Die Ergebniszeile eines Umsetzungs-Vorgangs (#917) — dieselbe, die {@link VorgangsKennzahlen}
 * bildet. Kosten, Züge und Modellzeit stehen allein im Ergebnisstand; ohne ihn bleibt die Dauer
 * des Anzeigemodells, und die drei Fehlanzeigen entfallen: Der Lauf hat sie nicht verschwiegen,
 * der Server bewahrt sie nur nicht auf.
 */
const umsetzungsKennzahlen = (
  item: AnzeigeItem,
  verbrauch: Vorgangsverbrauch | undefined,
  ohneKennzahlen: boolean,
): string =>
  // Ein grauer Vorgang lief nie; seine Zeile bestünde aus lauter Fehlanzeigen. Dieselbe Grenze,
  // die {@link VorgangsKennzahlen} zieht.
  item.state === 'GREY' ? '' : vorgangszeile(item, verbrauch, ohneKennzahlen)

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

function fussangaben(lauf: AnzeigeLauf, stand: NightRun | undefined): FussangabeForm[] {
  if (lauf.mode === 'CHAIN') {
    // Seit Issue #1115 aus dem Lauf selbst und nicht mehr allein aus dem eingelesenen Stand: Ein
    // Lauf, den der Runner eingeliefert hat, bringt seine Vorgaben mit (AK 1) — bis dahin stand
    // hier viermal „nicht angegeben", obwohl der Server die Angaben führte.
    const budget = kettenBudget(lauf, stand)
    return [
      ...kettenAngaben(stand),
      { label: 'Zeitvorgaben je Kette', wert: vorgabenText(budget.vorgabenMin) },
      { label: 'Kostenbudget je Kette', wert: betrag(budget.kostenUsd) },
      { label: 'Höchste Kosten eines Vorgangs', wert: hoechsteKosten(lauf.items, stand) },
      { label: 'Herkunft der Budgets', wert: herkunftText(budget) },
    ]
  }
  return [
    ...standAngaben(stand),
    {
      label: 'Ergebnis der Schicht',
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
 * Die Angaben, die allein der Ergebnisstand eines Ketten-Laufs hergibt — bis #988 die
 * Kennzahlenreihe der Übersicht (#866). Die Werte sind dieselben; nur ihr Ort ist neu: Über den
 * Vorgängen stehen seit #988 die sechs Instrumente der Vorlage, und die tragen den Verbrauch, nicht
 * die Auskünfte des Stands.
 *
 * <p>Leer ohne Stand: Kosten und Zeiten der Arbeitsschritte verlassen den Browser nie (Plan #718,
 * A1), der Server bewahrt sie nicht auf. Eine Reihe aus lauter Fehlanzeigen wäre dieselbe Wand, die
 * der Bestand schon vermeidet.
 */
function kettenAngaben(stand: NightRun | undefined): FussangabeForm[] {
  if (stand === undefined) {
    return []
  }
  const gruen = stand.items.filter((item) => item.state === 'GREEN').length
  const vermerk = ohneKostenmeldung(
    stand.stand?.kostenUnbekannt,
    'ein Arbeitsschritt',
    'Arbeitsschritte',
  )
  return [
    { label: 'Ketten durchgelaufen', wert: `${gruen} von ${stand.items.length}` },
    { label: 'Karten entstanden', wert: `${entstandeneDokumente(stand.items).size}` },
    {
      label: 'Laufzeit über alle Stufen',
      wert: formatDuration(stufenZeitSumme(stand.items) / 1000),
    },
    { label: 'Kosten der Schicht', wert: betrag(stand.stand?.kostenSumme) },
    ...(vermerk === null ? [] : [{ label: 'Zur Kostensumme', wert: vermerk, vorbehalt: true }]),
  ]
}

/**
 * Dasselbe für die drei übrigen Lauf-Arten — bis #988 die Zeile `Laufkennzahlen` (#874): die
 * art-eigene Kennzahl, die Laufzeit, Kosten und Züge.
 *
 * <p><b>Der Kennzahlen-Hinweis verdrängt Kosten und Züge</b> (Plan #864, E8): Ohne die ausführliche
 * Ausgabe fordert der Runner den Kennzahlen-Strom gar nicht erst an und schreibt den Grund einmal
 * an den Lauf. „Kosten unbekannt“ daneben behauptete ein Fehlen, wo nichts fehlt; es wurde nur
 * nichts angefordert.
 *
 * <p><b>Die Laufzeit steht mit einer Nachkommastelle</b> und damit feiner als die grobkörnige
 * Angabe der Metazeile darüber ({@link laufDauer} rundet auf ganze Minuten). Das ist die Form der
 * Vorlage `docs/mockup-leitstand-laufarten.html` und hält die beiden Angaben auseinander, obwohl
 * sie dieselbe Größe messen.
 */
function standAngaben(stand: NightRun | undefined): FussangabeForm[] {
  if (stand === undefined) {
    return []
  }
  const bearbeitet = stand.items.filter((item) => item.state !== 'GREY')
  const art = artKennzahl(stand, bearbeitet)
  const hinweis = stand.stand?.kennzahlenHinweis
  const kosten = laufkosten(bearbeitet)
  return [
    { label: art.label, wert: art.wert },
    {
      label: 'Laufzeit über alle Vorgänge',
      wert: `${MINUTEN_FORMAT.format(stand.durationMs / MINUTE_MS)} min`,
    },
    ...(hinweis === undefined
      ? [
          { label: 'Kosten der Schicht', wert: kosten.wert },
          ...(kosten.hinweis === null
            ? []
            : [{ label: 'Zur Kostensumme', wert: kosten.hinweis, vorbehalt: true }]),
          { label: 'Züge des Modells', wert: laufZuege(bearbeitet) },
        ]
      : [{ label: 'Kennzahlen', wert: hinweis, vorbehalt: true }]),
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
  const teuerster = gemeldet.reduce(
    (hoechster, kandidat) => (kandidat.kosten > hoechster.kosten ? kandidat : hoechster),
    gemeldet[0],
  )
  return `#${teuerster.nummer} mit ${betrag(teuerster.kosten)}`
}

/** Ein Zeitpunkt in der Schreibweise, die die Seite ueberall fuer Zeitpunkte fuehrt. */
const zeitpunkt = (iso: string): string => new Date(iso).toLocaleString('de-DE')

/**
 * Was ein unvollstaendig gemeldeter Lauf im Kopf sagt. Er wird **angezeigt, aber nicht
 * eingeliefert** — der Satz gilt dem Lauf selbst, nicht seiner Speicherung, und steht seit #988 als
 * Zustandsmarke mit LED im Kopf (Vorlage Z. 387), damit er ohne Aufklappen auffaellt.
 */
const UNVOLLSTAENDIG_GEMELDET = 'unvollständig gemeldet'

/**
 * Was der Kopf ueber die Einlieferung eines Laufs sagt (Issue #949): woher er kam und wann er
 * zuletzt gemeldet wurde.
 *
 * <p>Ein eben geparster Lauf sagt dazu **nichts** — er ist noch nicht eingeliefert, und
 * „hochgeladen am" waere dort eine Behauptung ueber die Zukunft. Die Unvollstaendigkeit steht
 * dagegen unabhaengig davon da ({@link UNVOLLSTAENDIG_GEMELDET}): Sie gilt dem Lauf selbst.
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
  return herkunft
}

/**
 * Die Marken rechts im Kopf eines Laufs (Vorlage `docs/mockup-nachtlauf-lauf.html` Z. 386–389):
 * Zustand, Kosten und Herkunft.
 *
 * <p><b>Die Kosten stehen nur am zugeklappten Lauf.</b> Aufgeklappt trägt sie das Instrument
 * „Kosten“, und zweimal dieselbe Zahl im Blick ließe den Leser nach einem Unterschied suchen, den
 * es nicht gibt — so führt es die Vorlage (Z. 452 gegen Z. 393).
 */
function Kopfmarken({
  lauf,
  melder,
  ergebnis,
  ausErgebnisstand,
  offen,
}: Readonly<{
  lauf: AnzeigeLauf
  /** Der Melder des ganzen Laufs — die Marke „ohne Arbeit" trägt ihn (Issue #1121). */
  melder: Melder
  ergebnis: boolean | undefined
  ausErgebnisstand: ReadonlySet<string>
  offen: boolean
}>) {
  const kosten = kostenText(lauf.verbrauch?.kostenUsd ?? null)
  return (
    <>
      {/* Die Marke haengt am Befund und nicht an `vollstaendig` (#1092): Ein verstummter Lauf
          traegt fuer immer `complete = false`, ist aber nicht „unvollstaendig gemeldet" — er ist
          nicht gelungen, und das sagt bereits die rote LED der Platte. */}
      {laeuftNoch({ complete: lauf.vollstaendig, outcome: lauf.befund }) && (
        <LaufMarke testId="lauf-zustand" led={<Led melder="stahl" pulsiert />}>
          {UNVOLLSTAENDIG_GEMELDET}
        </LaufMarke>
      )}
      {/* Die dritte Zustandsmarke (Issue #1145): der Grund des harten Abbruchs, **gekuerzt** auf
          eine Zeile — vollstaendig steht er in der aufgeklappten Platte (AK 4, E14). Sie schliesst
          die Marke „ohne Arbeit" aus: Der Server setzt beim abgebrochenen Lauf allein den
          Abbruchgrund (E6), und zwei Zustandsmarken waeren ein Widerspruch im selben Kopf. */}
      {lauf.abbruchGrund !== undefined && (
        <LaufMarke testId="lauf-zustand" led={<Led melder={melder} />}>
          {kurzGrund(lauf.abbruchGrund)}
        </LaufMarke>
      )}
      {/* Die Zustandsmarken schliessen einander aus: Ein Lauf ist entweder noch nicht
          abgeschlossen, abgebrochen oder ohne Arbeit beendet. Mehrere zugleich waeren ein
          Widerspruch im Kopf derselben Platte (Issue #1069). */}
      {/* Der Melder kommt vom Lauf und steht nicht fest auf zinnober (Issue #1121): Ein Lauf, der
          nichts zu tun fand, ist grau — auch mit dem Rueckfalltext „Grund unbekannt" (#1185). */}
      {/* Das Wort vor der Auskunft kommt aus der Woertertabelle des Befunds (Issue #1190, E8): Der
          Grund allein sagte nicht, dass es nichts zu tun gab. Die Auskunft bleibt **ungekuerzt** —
          die Marke steht allein in ihrer Zeile, und einen zweiten Ort fuer den vollen Text gibt es
          an der Laufplatte nicht (anders als beim Abbruchgrund daruber). */}
      {lauf.vollstaendig && lauf.abbruchGrund === undefined && lauf.ohneArbeit !== undefined && (
        <LaufMarke testId="lauf-zustand" led={<Led melder={melder} />}>
          {`${NIGHT_RUN_VERDICT_TEXT.NO_WORK} — ${lauf.ohneArbeit}`}
        </LaufMarke>
      )}
      {!offen && kosten !== null && <LaufMarke testId="lauf-kosten">{kosten}</LaufMarke>}
      {/* Die Herkunft wird **hier** aus dem Zwischenspeicher gelesen, nicht in `AnzeigeLauf`
          mitgeführt: Der Server kennt die Unterscheidung nicht, ein Feld am Anzeigemodell müsste
          also in jedem Ladepfad einzeln gesetzt werden — und der nächste vergessene Pfad zeigte
          still die falsche Herkunft (AK 9, Fall 3). */}
      <LaufMarke testId="lauf-stand">
        {ausErgebnisstand.has(lauf.startedAt) ? 'Ergebnisstand' : 'Herkunft unbekannt'}
      </LaufMarke>
      {einlieferungsangaben(lauf).map((angabe) => (
        <LaufMarke key={angabe}>{angabe}</LaufMarke>
      ))}
      {ergebnis !== undefined && <LaufMarke>{ergebnis ? 'neu angelegt' : 'lag schon vor'}</LaufMarke>}
    </>
  )
}

/**
 * Die Metazeile im Kopf eines Laufs (Vorlage Z. 385): Beginn, Dauer und die Stückzahlen — dazu,
 * was der Ergebnisstand über Modell, Label und Abschluss weiß.
 *
 * <p>Die Zeile ist lang, und das ist Absicht: Die Vorlage zeigt dort vier Angaben, ein Lauf hat
 * aber mehr zu sagen. Weggelassen wäre die Auskunft verloren; in der Metazeile steht sie in der
 * Gestalt, die die Vorlage dafür kennt.
 */
const metazeile = (lauf: AnzeigeLauf, stand: NightRun | undefined): string =>
  [
    tagZeit(lauf.startedAt),
    laufDauer(lauf.durationMs),
    `${lauf.processedCount} bearbeitet`,
    `${lauf.skippedCount} übergangen`,
    kopfText(stand),
    ...(lauf.unparsedCount > 0 ? [`Ungedeutete Zeilen: ${lauf.unparsedCount}`] : []),
  ]
    .filter((eintrag) => eintrag !== '')
    .join(' · ')

/**
 * Der Titel eines Laufs (Issue #1127, #1151): „Run #412 · 14. September, 22:05" — Nummer,
 * Startdatum und Startzeit. Datum und Uhrzeit machen zwei Läufe derselben Schicht unterscheidbar.
 * Ohne Nummer (ein eben eingelesener Lauf war bei keinem Server) „Run · 14. September, 22:05".
 */
const laufTitel = (startedAt: string, laufId: number | undefined): string => {
  const start = new Date(startedAt)
  const datum = start.toLocaleDateString('de-DE', { day: 'numeric', month: 'long' })
  const zeit = start.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
  const lauf = laufId === undefined ? 'Run' : `Run #${laufId}`
  return `${lauf} · ${datum}, ${zeit}`
}

/**
 * Die Ergebniszeile eines Vorgangs: Dauer, Kosten, Züge und der Anteil der Modellarbeit. Die Kette
 * führt sie in ihrer eigenen Form ({@link vorgangsKennzahlenText}), die drei übrigen Arten in der
 * des Umsetzungs-Laufs — beides unverändert gegenüber #916 und #917, nur ihr Ort ist neu: Sie steht
 * seit #988 in der aufgeklappten Zeile ihres Vorgangs.
 */
const ergebniszeile = (
  modus: AnzeigeArt,
  item: AnzeigeItem,
  standItem: NightRunItem | undefined,
  ohneKennzahlen: boolean,
): string => {
  const verbrauch = vorgangsverbrauch(item, standItem)
  return modus === 'CHAIN'
    ? vorgangsKennzahlenText(item, verbrauch)
    : umsetzungsKennzahlen(item, verbrauch, ohneKennzahlen)
}

/**
 * Die Kennzahlen eines Vorgangs in **einer** Form, gleich aus welcher der beiden Quellen sie
 * stammen (Issue #1116, Plan #1110 E9): dem eingelesenen Ergebnisstand oder der Server-Antwort.
 * `undefined`, wo es beide nicht gibt.
 *
 * <p><b>Der Stand gewinnt, wo es ihn gibt</b> — und das ist keine Umkehr des Vorrangs aus AK 6,
 * sondern seine Folge: Zu einem servergeführten Lauf entsteht seit diesem Paket gar kein Stand
 * mehr ({@link protokollLesen}, E8). Ein Stand liegt danach nur noch vor, wo er die richtige
 * Quelle ist — an einem Lauf, den erst das Einlesen angelegt hat, und am Nachtplan-Lauf, den der
 * Server nie führt.
 *
 * <p><b>Ganze Bündel statt Feld für Feld</b>: Ein feldweises Auffüllen mischte die Angaben zweier
 * Quellen zu einer Zeile, die keine von beiden je so gemeldet hat (AK 6).
 *
 * <p>Sie ist zugleich die Quelle der Kostenspalte ({@link vorgangskosten}). Genau das war der
 * Anlassfall: Zeile und Spalte lasen verschieden, und die Seite widersprach sich selbst.
 */
interface Vorgangsverbrauch {
  kostenUsd: number | undefined
  zuege: number | undefined
  /** Die Zeit, in der das Modell selbst arbeitete — im Stand `arbeitszeitMs`, am Server `modelDurationMs`. */
  modellzeitMs: number | undefined
  eingabe: number | undefined
  ausgabe: number | undefined
  /** Zahl der Arbeitsschritte ohne Kostenmeldung; allein der Ergebnisstand zählt sie. */
  kostenUnbekannt: number | undefined
}

function vorgangsverbrauch(
  item: AnzeigeItem,
  standItem: NightRunItem | undefined,
): Vorgangsverbrauch | undefined {
  if (standItem !== undefined) {
    return {
      kostenUsd: standItem.kennzahlen?.kostenUsd,
      zuege: standItem.kennzahlen?.zuege,
      modellzeitMs: standItem.kennzahlen?.arbeitszeitMs,
      // Der Ergebnisstand führt keine Tokenmengen — siehe {@link tokenmengen}.
      eingabe: undefined,
      ausgabe: undefined,
      kostenUnbekannt: standItem.kennzahlen?.kostenUnbekannt,
    }
  }
  if (item.verbrauch === undefined) {
    return undefined
  }
  return {
    kostenUsd: item.verbrauch.kostenUsd,
    zuege: item.verbrauch.zuege,
    modellzeitMs: item.verbrauch.modellzeitMs,
    eingabe: item.verbrauch.eingabe,
    ausgabe: item.verbrauch.ausgabe,
    // Der Server zählt sie nicht: Er nimmt je Vorgang eine Kostenangabe an, nicht die Sitzungen
    // dahinter. Eine 0 behauptete hier „alle haben gemeldet".
    kostenUnbekannt: undefined,
  }
}

/**
 * Die Kosten eines Vorgangs für seine Spalte (#988); `null` heißt „nicht gemessen“ und erscheint
 * als „—“. Sie kommen aus derselben Auflösung wie seine Ergebniszeile ({@link vorgangsverbrauch})
 * — sonst stünde in der Zeile eine Fehlanzeige für den Wert, der einen Zentimeter daneben steht.
 */
const vorgangskosten = (item: AnzeigeItem, standItem: NightRunItem | undefined): number | null =>
  vorgangsverbrauch(item, standItem)?.kostenUsd ?? null

/**
 * Derselbe Vorgang im Ergebnisstand dieser Sitzung; `undefined` ohne Stand oder wo der Stand ihn
 * nicht führt. Eine benannte Funktion, weil seit Issue #1115 zwei Stellen dieselbe Zuordnung
 * brauchen — die Vorgangszeile und die höchsten Kosten der Fußzeile.
 */
const standVorgang = (stand: NightRun | undefined, nummer: number): NightRunItem | undefined =>
  stand?.items.find((eintrag) => eintrag.cardNumber === nummer)

/**
 * Ein Vorgang eines Laufs als kompakte Zeile (#988) — und aufgeklappt alles, was die Vorlage in
 * der Zeile nicht zeigt: Zustand in Worten, Abbruchgrund, Auszug, Verlauf, entstandene Karten,
 * Ergebniszeile, Herkunftskette, Vorhaben und der Befund mit „Kopieren“.
 *
 * <p><b>Hier wird zusammengestellt, nicht gerechnet.</b> Band, Grund, Chips, Ergebniszeile und
 * Häufigkeit kommen aus denselben Funktionen wie vor diesem Paket — die Werte bleiben nachweislich
 * dieselben, nur ihre Gestalt ist neu.
 */
function Vorgangszeile({
  item,
  standItem,
  modus,
  vorgaben,
  anteil,
  ohneKennzahlen,
  katalog,
  vorhabenKarten,
  haeufigkeit,
  erzeugt,
  ausPlan,
  istRot,
  onOeffnen,
}: Readonly<{
  item: AnzeigeItem
  /**
   * Derselbe Vorgang im Ergebnisstand dieser Sitzung; `undefined` an einem aufbewahrten Lauf.
   * Arbeitsschritte, Züge und Modellzeit stehen allein dort — der Server bewahrt sie nicht auf.
   */
  standItem: NightRunItem | undefined
  modus: AnzeigeArt
  vorgaben: NightRunStufenvorgaben | undefined
  /** Der Anteil dieses Vorgangs an der Nacht; `undefined`, wo der Lauf keine Bezugsgröße hergibt. */
  anteil: Laufabschnitt | undefined
  ohneKennzahlen: boolean
  katalog: Kartenkatalog
  vorhabenKarten: Vorhabenkatalog
  haeufigkeit: string | null
  /**
   * Was dieser Kettenvorgang eines eingelieferten Laufs angelegt hat (Issue #1106); `undefined`,
   * solange es nicht ermittelt ist, und an jedem Vorgang, der keine Kette ist.
   */
  erzeugt: Erzeugnisse | undefined
  /** Der Plan, aus dem dieses Paket im selben Lauf hervorging (Issue #1106); sonst `undefined`. */
  ausPlan: number | undefined
  istRot: (nummer: number) => boolean
  onOeffnen: (karte: CardByNumber) => void
}>) {
  const [offen, setOffen] = useState(false)
  // `undefined` = noch nicht aufgelöst (die Kette lädt), `null` = nicht auflösbar.
  const wurzel = katalog.get(item.cardNumber)
  // `null` an einem grünen oder grauen Vorgang — dort erscheint kein Befund.
  const uebernahme = buildHandoffText(item)
  const band = modus === 'CHAIN' ? vorgangsband(item, standItem, vorgaben) : null
  const grund = modus === 'CHAIN' && standItem !== undefined ? vorgangsgrund(standItem) : null
  const chips = kettenchips(modus, item, standItem, erzeugt, katalog)
  const kennzahlen = ergebniszeile(modus, item, standItem, ohneKennzahlen)
  const zustandswort = nightRunZustandsText(item.state, item.errorClass)
  // Das Vorhaben hängt an der **Wurzelkarte**, nicht an der Kette: Fachliche Anforderung und Plan
  // tragen ebenfalls eine `parentId`, und deren Vorhaben wäre hier eine andere Aussage.
  const vorhabenKarte = wurzel?.parentId == null ? undefined : vorhabenKarten.get(wurzel.parentId)

  return (
    <NachtlaufVorgangszeile
      nummer={item.cardNumber}
      titel={item.title}
      wurzel={wurzel}
      melder={MELDER_JE_ZUSTAND[item.state]}
      zustandswort={zustandswort}
      klasse={
        item.errorClass === undefined
          ? null
          : { marke: item.errorClass, melder: MELDER_JE_FEHLERKLASSE[item.errorClass] }
      }
      auszug={ersteZeile(item.excerpt ?? null)}
      haeufigkeit={haeufigkeit}
      vorhaben={
        vorhabenKarte == null
          ? null
          : { titel: vorhabenKarte.title, farbe: epicColor(vorhabenKarte.id) }
      }
      dauer={paketDauer(item.durationMs ?? null)}
      kosten={kostenText(vorgangskosten(item, standItem))}
      commit={kurzHash(item.commitHash ?? null)}
      offen={offen}
      onUmschalten={() => setOffen((wert) => !wert)}
      onOeffnen={onOeffnen}
    >
      {grund !== null && (
        <Typography
          variant="body2"
          data-testid={`abbruch-${item.cardNumber}`}
          sx={{ color: 'text.secondary', whiteSpace: 'pre-wrap' }}
        >
          {grund}
        </Typography>
      )}

      {/* Auszüge sind Fremdtext (Claude-Ausgaben, Ergebnisse fremder Werkzeuge) und werden
          deshalb als reiner Text gerendert, nie über den Markdown-Renderer (CLAUDE-security.md).
          An einer Kette **mit** Ergebnisstand entfällt er: Dort sagen Band und Grund dasselbe
          genauer, und der rohe Stufenblock stünde ein zweites Mal daneben (#916, AK 15 aus #859). */}
      {item.excerpt !== undefined && (modus !== 'CHAIN' || standItem === undefined) && (
        <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
          {(item.state === 'GREY' ? 'Grund: ' : 'Auszug: ') + item.excerpt}
        </Typography>
      )}

      {band !== null && (
        <NachtlaufStufenband
          abschnitte={band.abschnitte}
          ansage={band.ansage}
          testId={`stufenband-${item.cardNumber}`}
          abschnittTestId={`stufe-${item.cardNumber}`}
        />
      )}

      {anteil !== undefined && (
        <NachtlaufAnteilsbalken
          anteil={anteil.anteil}
          beschriftung={`${anteil.dauer} · ${anteil.anteil} % der Schicht`}
          ansage={`${anteil.ansage}, ${anteil.anteil} % der Schicht`}
          farbe={anteil.farbe}
          schiene={NUT}
          testId={`laufband-abschnitt-${item.cardNumber}`}
          fuellungTestId={`laufband-balken-${item.cardNumber}`}
        />
      )}

      {(chips.length > 0 || kennzahlen !== '') && (
        <Box
          data-testid={`ergebnis-${item.cardNumber}`}
          sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '8px' }}
        >
          {chips.length > 0 && (
            <Typography component="span" sx={{ fontSize: 12, color: TEXT_SCHWACH, mr: '2px' }}>
              Entstanden
            </Typography>
          )}
          {chips.map((gruppe) => (
            <NachtlaufKartenchips
              key={gruppe.testId}
              chips={gruppe.chips}
              leer={gruppe.leer}
              testId={gruppe.testId}
              onOeffnen={onOeffnen}
            />
          ))}
          {kennzahlen !== '' && (
            <Typography
              component="span"
              data-testid={`kennzahlen-${item.cardNumber}`}
              sx={{ ml: 'auto', ...ZAHL, fontSize: 12.5, color: 'text.secondary' }}
            >
              {kennzahlen}
            </Typography>
          )}
        </Box>
      )}

      {ausPlan !== undefined && (
        <Stufenzeile
          label="Paket aus Plan"
          zustand={planZustand(ausPlan, katalog)}
          onOeffnen={onOeffnen}
        />
      )}

      {wurzel != null && (
        <>
          {STUFEN.map((stufe) => {
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
          <Vorhabenzeile
            parentId={wurzel.parentId}
            vorhabenKarten={vorhabenKarten}
            onOeffnen={onOeffnen}
          />
        </>
      )}

      {uebernahme !== null && (
        <NachtlaufBefund
          cardNumber={item.cardNumber}
          text={uebernahme}
          onKopieren={() => void inDieZwischenablage(uebernahme)}
        />
      )}
    </NachtlaufVorgangszeile>
  )
}

/**
 * Ein Lauf als aufklappbare Platte (#988) — Kopf, sechs Instrumente, die Vorgänge als kompakte
 * Zeilen und die Fußzeile. Die Kette wird erst beim Aufklappen geladen (Plan #718, A8).
 */
function LaufPanel({
  lauf,
  ergebnis,
  ausErgebnisstand,
  stand,
  katalog,
  vorhabenKarten,
  zaehler,
  aufbewahrteLaeufe,
  erzeugnisse,
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
   * Arbeitsschritte noch Züge und Modellzeit — die stehen allein dort.
   */
  stand: NightRun | undefined
  katalog: Kartenkatalog
  vorhabenKarten: Vorhabenkatalog
  zaehler: Haeufigkeiten
  /** Das „M“ in „N von M aufbewahrten Runs“ — die Länge der zuletzt geladenen Liste. */
  aufbewahrteLaeufe: number
  /**
   * Was die Kettenvorgänge dieses Laufs angelegt haben, je Anforderung (Issue #1106) — leer, bis es
   * beim Aufklappen ermittelt ist, und an jedem Lauf, der keine eingelieferte Kette ist.
   */
  erzeugnisse: ReadonlyMap<number, Erzeugnisse>
  /**
   * Der oberste Lauf der Liste steht beim Öffnen der Seite offen (#914, E7). AK 2 verlangt Kopf,
   * Instrumente und erste Vorgangszeile ohne Scrollen — genau dieser eine, nicht alle: Bis zu 30
   * aufgeklappte Läufe lösten die Anfragelawine aus, die Plan #718 (A8) vermeidet.
   */
  zuerst: boolean
  onAufklappen: () => void
  onOeffnen: (karte: CardByNumber) => void
}>) {
  const [offen, setOffen] = useState(zuerst)
  const rot = new Set(lauf.items.filter((item) => item.state === 'RED').map((item) => item.cardNumber))
  // Eine Fassung für alle Vorgänge: Zwei gleichlautende Abfragen nebeneinander hießen zwei Stellen,
  // an denen dieselbe Frage beantwortet wird.
  const istRot = (nummer: number) => rot.has(nummer)
  // Einmal je Lauf gerechnet: Die Bezugsgröße ist die Summe **aller** Vorgangsdauern, also eine
  // Größe des Laufs und nicht eines Vorgangs.
  const anteile =
    lauf.mode === 'IMPLEMENTATION' ? laufanteile(lauf.items) : new Map<number, Laufabschnitt>()
  // Hat der Lauf die Sitzungs-Kennzahlen gar nicht angefordert, nennt er den Grund einmal in seiner
  // Fußzeile; je Vorgang stünde sonst dreimal „fehlt“ (#874).
  const ohneKennzahlen = stand?.stand?.kennzahlenHinweis !== undefined

  const umschalten = () => {
    const neu = !offen
    setOffen(neu)
    if (neu) {
      onAufklappen()
    }
  }

  // Einmal gerechnet und zweimal gezeigt: Die Platte trägt ihn, und die Marke „ohne Arbeit" nimmt
  // ihn von hier (Issue #1121). Zwei Aufrufe nebeneinander wären zwei Stellen, an denen dieselbe
  // Frage beantwortet wird — und die Marke stand vorher fest auf zinnober.
  //
  // Den Grund bekommt er seit Issue #1186 nicht mehr: Er steht im Befund als `NO_WORK`, und der
  // eben im Browser geparste Lauf trägt ohnehin keinen.
  const melder = laufMelder({ complete: lauf.vollstaendig, items: lauf.items, outcome: lauf.befund })

  return (
    <NachtlaufLaufPlatte
      testId={`lauf-${lauf.startedAt}`}
      titel={laufTitel(lauf.startedAt, lauf.laufId)}
      zyklus={zyklusBeschriftung(zyklusDesStarts(lauf.startedAt))}
      artSymbol={<LaufArtSymbol art={lauf.mode} />}
      meta={metazeile(lauf, stand)}
      melder={melder}
      abbruchGrund={lauf.abbruchGrund}
      pulsiert={laeuftNoch({ complete: lauf.vollstaendig, outcome: lauf.befund })}
      offen={offen}
      onUmschalten={umschalten}
      marken={
        <Kopfmarken
          lauf={lauf}
          melder={melder}
          ergebnis={ergebnis}
          ausErgebnisstand={ausErgebnisstand}
          offen={offen}
        />
      }
    >
      {/* Die sechs Instrumente ersetzen die Textzeile der Laufsumme (Entscheidung Manne
          2026-09-17). Der Verbrauch kommt aus dem Lauf selbst, nicht aus seinen Vorgängen. */}
      <NachtlaufLaufInstrumente
        verbrauch={lauf.verbrauch}
        dauerMs={lauf.durationMs}
        pakete={paketZaehlung(lauf.items)}
        aufteilung={kostenaufteilung(lauf)}
      />

      {/* Statt eines Bandes (#873): Der Erzeugungs- und der Prüf-Lauf sortieren die große Mehrheit
          ihrer Karten aus, und die Aufschlüsselung sagt, warum. Sie liest den Ergebnisstand dieser
          Sitzung, wo er vorliegt — allein er trägt das Ausgangswort; sonst leitet sie aus der
          Server-Sicht ab (Plan #864, E2). */}
      {(lauf.mode === 'REVIEW' || lauf.mode === 'NIGHTPLAN') && (
        <Box sx={{ px: '16px', pt: '14px' }}>
          <Aufschluesselung items={stand?.items ?? lauf.items} />
        </Box>
      )}

      {lauf.unparsedSample.length > 0 && (
        <Box sx={{ px: '16px', pt: '14px' }}>
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

      <Box
        component="ul"
        sx={{ listStyle: 'none', m: 0, p: 0, borderTop: `1px solid ${RAND}` }}
      >
        {lauf.items.map((item, position) => (
          <Vorgangszeile
            // Zwei Vorgänge können dieselbe Karte betreffen — der Schlüssel trägt deshalb die
            // Position dazu.
            key={`${item.cardNumber}-${position}`}
            item={item}
            standItem={standVorgang(stand, item.cardNumber)}
            modus={lauf.mode}
            // Dieselben Vorgaben, die die Fußzeile zeigt (Issue #1116): Ein servergeführter Lauf
            // bringt sie mit, und das Band darunter stünde sonst als „ohne Vorgabe" da, während
            // die Zeile darunter die Minuten nennt — genau das Nebeneinander, das dieses Paket
            // beseitigt.
            vorgaben={kettenBudget(lauf, stand).vorgabenMin}
            anteil={anteile.get(item.cardNumber)}
            ohneKennzahlen={ohneKennzahlen}
            katalog={katalog}
            vorhabenKarten={vorhabenKarten}
            haeufigkeit={haeufigkeitsText(item, lauf.gespeichert, zaehler, aufbewahrteLaeufe)}
            erzeugt={item.stufen === undefined ? undefined : erzeugnisse.get(item.cardNumber)}
            ausPlan={planDesPakets(item.cardNumber, erzeugnisse)}
            istRot={istRot}
            onOeffnen={onOeffnen}
          />
        ))}
      </Box>

      <Box sx={{ borderTop: `1px solid ${RAND}` }}>
        <NachtlaufFuss angaben={fussangaben(lauf, stand)} testId="uebersicht-fuss" />
      </Box>
    </NachtlaufLaufPlatte>
  )
}


export function NightRunPage() {
  const { projectId } = useParams()
  const [suchparameter] = useSearchParams()
  /**
   * Der angesteuerte Lauf aus `?lauf=<id>` (Issue #1085, AK 7).
   *
   * Eine Stoerzeile des Plattform-Leitstands zeigt hierher. Steht kein Parameter oder etwas
   * Ungueltiges darin, ist das Ergebnis `null` und die Seite verhaelt sich wie ohne ihn — ein
   * Verweis, den das System selbst ausgegeben hat, soll nicht auf eine Fehlerseite fuehren, nur
   * weil der Ringpuffer den Lauf inzwischen verdraengt hat.
   */
  const angesteuerterLauf = Number(suchparameter.get('lauf'))
  const gesuchteLaufId = Number.isInteger(angesteuerterLauf) && angesteuerterLauf > 0 ? angesteuerterLauf : null
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
  /** Die Erzeugnisse der Kettenläufe je Startzeitpunkt (Issue #1106), ermittelt beim Aufklappen. */
  /** Ob die Liste auch die älteren Läufe zeigt (Issue #1134) — nur für diesen Seitenbesuch. */
  const [alleLaeufe, setAlleLaeufe] = useState(false)
  const [erzeugnisse, setErzeugnisse] = useState<ReadonlyMap<string, ReadonlyMap<number, Erzeugnisse>>>(
    () => new Map(),
  )
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

  /**
   * Ermittelt, was die Kettenvorgänge eines eingelieferten Laufs angelegt haben (Issue #1106), und
   * lädt die gefundenen Karten in den Katalog. Ein eingelesener Stand bringt seine Dokumente selbst
   * mit und braucht das nicht.
   *
   * <p>Scheitert ein Abruf, bleibt der Lauf ohne Erzeugnisse — und damit ohne Chipgruppen, statt
   * „kein Plan" zu behaupten. Eine Meldung gibt es dafür nicht: Die übrige Seite ist vollständig.
   */
  const ladeErzeugnisse = async (lauf: AnzeigeLauf) => {
    const anforderungen = lauf.items.filter((item) => item.stufen !== undefined).map((item) => item.cardNumber)
    // Stufen trägt allein die Server-Antwort: Ein eben geparster Lauf hat keine, und zu ihm liegt
    // ohnehin ein Stand vor.
    if (lauf.mode !== 'CHAIN' || anforderungen.length === 0) {
      return
    }
    const von = Date.parse(lauf.startedAt)
    // Ein Lauf, der noch arbeitet, hat kein Ende — was er bis jetzt anlegte, gehört dazu.
    const bis = lauf.vollstaendig ? von + lauf.durationMs : Number.POSITIVE_INFINITY
    const gefunden = await ermittleErzeugnisse(id, anforderungen, { von, bis }).catch(() => undefined)
    if (gefunden === undefined) {
      return
    }
    await ladeKetten([], [...gefunden.values()].flatMap(({ plaene, pakete }) => [...plaene, ...pakete]))
    setErzeugnisse((vorher) => new Map(vorher).set(lauf.startedAt, gefunden))
  }

  const aufklappen = useCallback(
    (lauf: AnzeigeLauf) => {
      if (geladeneLaeufe.current.has(lauf.startedAt)) {
        return
      }
      geladeneLaeufe.current.add(lauf.startedAt)
      const stand = staende.get(lauf.startedAt)
      void ladeKetten(lauf.items, dokumentNummern(stand)).then(() =>
        stand === undefined ? ladeErzeugnisse(lauf) : undefined,
      )
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
  // Der vorige Zyklus beginnt einen Tag vor dem laufenden; alles ab ihm ist „die letzten zwei".
  const abZyklus = zyklusDavor(zyklusDesStarts(new Date().toISOString()))
  const sichtbareLaeufe = alleLaeufe
    ? laeufe
    : laeufe.filter((lauf) => imBlick(lauf, abZyklus, gesuchteLaufId, ausErgebnisstand))
  const ausgeblendet = laeufe.length - sichtbareLaeufe.length
  const obersterSichtbarer = sichtbareLaeufe[0]

  useEffect(() => {
    if (obersterSichtbarer !== undefined) {
      aufklappen(obersterSichtbarer)
    }
  }, [obersterSichtbarer, aufklappen])

  /**
   * Springt zum angesteuerten Lauf (Issue #1085, AK 7).
   *
   * Aufgeklappt ist er schon über `zuerst` — ohne den Sprung stünde er aber möglicherweise weit
   * unten, und der Verweis aus der Störzeile führte auf eine Seite, auf der man erst suchen muss.
   * Der Effekt läuft, sobald die Läufe geladen sind; ein Ziel, das der Ringpuffer verdrängt hat,
   * findet kein Element und tut nichts.
   */
  useEffect(() => {
    if (gesuchteLaufId === null) {
      return
    }
    const ziel = laeufe.find((lauf) => lauf.laufId === gesuchteLaufId)
    if (ziel === undefined) {
      return
    }
    document.querySelector(`[data-testid="lauf-${ziel.startedAt}"]`)?.scrollIntoView({ block: 'start' })
  }, [gesuchteLaufId, laeufe])


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
    /**
     * Ob der Server diesen Lauf schon führt (Issue #1116, Plan #1110 E8). Der Blick geht **vor**
     * die drei Speicher unten: Danach stünde der geparste Lauf bereits an seiner Stelle, und die
     * Frage wäre nicht mehr zu beantworten.
     *
     * <p>Für einen servergeführten Lauf bleibt alles drei unberührt — seine Anzeige speist sich
     * aus der Server-Antwort, und das Einlesen fügt ihm nichts hinzu (AK 6). Auch nicht, wenn
     * `submit` oder das Neuladen der Liste danach scheitert: Was nie gesetzt wurde, kann kein
     * Fehlschlag stehen lassen.
     */
    const servergefuehrt = laeufe.some((alt) => alt.startedAt === run.startedAt)
    if (!servergefuehrt) {
      // Vermerkt **vor** dem Senden: Der Lauf ist aus dem Ergebnisstand entstanden, unabhängig
      // davon, ob die Einlieferung gleich gelingt.
      setAusErgebnisstand((bisher) => new Set(bisher).add(run.startedAt))
      // Ebenfalls **vor** dem Einliefern vermerkt (E1): Gleich ersetzt die Server-Sicht das ganze
      // Lauf-Array, und ein erst danach gefüllter Speicher trüge die Angaben des Stands nicht
      // mehr. Seit #872 für **jede** Lauf-Art: Nicht nur die Ketten-Übersicht liest den Stand,
      // sondern auch die Kennzahlen je Vorgang der drei anderen Arten.
      setStaende((bisher) => new Map(bisher).set(run.startedAt, run))
      setLaeufe((bisher) =>
        [ausParser(run), ...bisher.filter((alt) => alt.startedAt !== run.startedAt)].sort(
          nachStartAbsteigend,
        ),
      )
    }

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
    // Die zweite Hälfte von E8: Auch ein Lauf, den die Liste nicht zeigte, ist servergeführt,
    // wenn `submit` ihn nicht neu angelegt hat. Der eben vermerkte Stand wird dann wieder
    // zurückgenommen — er ist nicht die Quelle dieses Laufs. Meldet `submit` dagegen
    // `created: true`, hat erst das Einlesen ihn angelegt, und der Stand bleibt seine Quelle.
    if (antwort.some((eintrag) => eintrag.startedAt === run.startedAt && !eintrag.created)) {
      setAusErgebnisstand((bisher) => ohneEintrag(bisher, run.startedAt))
      setStaende((bisher) => ohneSchluessel(bisher, run.startedAt))
    }
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
        {/* Die Ausnahme bleibt auch bei dunklem System hell (#954): Der Wurzelknoten setzt die
            Variablen des Leitstands für seinen Teilbaum auf die Hellwerte und malt den Grund selbst. */}
        <Box data-testid="nachtlauf-wurzel" sx={NACHTLAUF_WURZEL_SX}>
          {/* Brotkrumenpfad und „Protokoll einlesen" kennt der Entwurf nicht. AK 10: Wo er kein
              Element vorsieht, bleibt es in seiner Funktion erhalten und wird eingepasst — als
              schmale Zeile über dem Entwurfskopf, in dessen Schriftbild und Farben (E9). */}
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Breadcrumbs
              items={[
                { label: 'Projekte', to: '/projects' },
                { label: projectName ?? 'Projekt', to: `/projects/${id}` },
                { label: 'Runner' },
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

          {laeufe.length === 0 &&<Typography color="text.secondary">Noch keine Auswertung vorhanden.</Typography>}
          {laeufe.length > 0 && sichtbareLaeufe.length === 0 && (
            <Typography color="text.secondary">In den letzten zwei Schichten gab es keinen Run.</Typography>
          )}

          {/* Die Laufblöcke haben mit #988 die Nachtlauf-Ausnahme verlassen und folgen Kupferwarte
              (`CLAUDE-design.md`) — deshalb stehen sie im `KupferwarteBereich`, der Theme und
              Variablen für seinen Teilbaum zurückstellt. Was sonst auf dieser Seite steht, bleibt
              in der Ausnahme. */}
          {sichtbareLaeufe.length > 0 && (
            <KupferwarteBereich>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                {sichtbareLaeufe.map((lauf, position) => (
                  <LaufPanel
                    key={lauf.startedAt}
                    lauf={lauf}
                    // Mit `?lauf=<id>` steht genau dieser Lauf offen statt des obersten; zeigt der
                    // Parameter ins Leere, bleibt es beim obersten (Issue #1085).
                    zuerst={
                      gesuchteLaufId === null
                        ? position === 0
                        : lauf.laufId === gesuchteLaufId
                    }
                    ergebnis={ergebnisse.get(lauf.startedAt)}
                    ausErgebnisstand={ausErgebnisstand}
                    // Der Speicher entscheidet, ob zu genau diesem Lauf ein Ergebnisstand dieser
                    // Sitzung vorliegt; welche Darstellung daraus entsteht, entscheidet der Modus
                    // am Anzeigelauf — er steht auch am neu geladenen.
                    stand={staende.get(lauf.startedAt)}
                    katalog={katalog}
                    vorhabenKarten={vorhabenKarten}
                    zaehler={zaehler}
                    erzeugnisse={erzeugnisse.get(lauf.startedAt) ?? KEINE_ERZEUGNISSE}
                    aufbewahrteLaeufe={aufbewahrteLaeufe}
                    onAufklappen={() => aufklappen(lauf)}
                    onOeffnen={setDetail}
                  />
                ))}
              </Box>
            </KupferwarteBereich>
          )}

          {(alleLaeufe || ausgeblendet > 0) && (
            <Box>
              <Button variant="text" onClick={() => setAlleLaeufe((wert) => !wert)}>
                {alleLaeufe ? 'Nur die letzten zwei Schichten zeigen' : `Ältere Runs anzeigen (${ausgeblendet})`}
              </Button>
            </Box>
          )}

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
