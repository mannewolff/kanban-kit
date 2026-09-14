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
import { Fragment, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { cardsApi, type Card, type CardByNumber } from '../api/cards'
import { apiErrorMessage } from '../api/client'
import {
  nightRunsApi,
  type NightRunErrorClassCounts,
  type NightRunServerMode,
  type NightRunSubmission,
  type NightRunView,
} from '../api/nightRuns'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { CardDetailModal } from '../components/CardDetailModal'
import { useSnackbar } from '../components/SnackbarProvider'
import { formatDuration } from '../lib/formatDuration'
import {
  buildHandoffText,
  nightRunZustandsText,
  NIGHT_RUN_ERROR_CLASS_TEXT,
  type NightRunHandoffItem,
} from '../lib/nightRunHandoff'
import {
  parseNightRunErgebnisstand,
  type NightRunErgebnisstandGrund,
} from '../lib/nightRunErgebnisstand'
import {
  type NightRun,
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
import { SURFACE_TINT } from '../theme'

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
 * liefert genau eine Karte; 30 aufbewahrte Läufe mit je 10 bis 15 Arbeitspaketen und zwei
 * Kettenschritten wären mehrere hundert Anfragen bei jedem Seitenaufruf.
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
  items: run.items.map((item) => ({
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    errorClass: item.errorClass,
    durationMs: item.durationMs,
    excerpt: item.excerpt,
  })),
})

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
  items: view.items.map((item) => ({
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    errorClass: item.errorClass ?? undefined,
    durationMs: item.durationMs ?? undefined,
    excerpt: item.excerpt ?? undefined,
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

const zurEinlieferung = (run: NightRun & { mode: NightRunServerMode }): NightRunSubmission => ({
  startedAt: run.startedAt,
  mode: run.mode,
  durationMs: run.durationMs,
  processedCount: run.processedCount,
  skippedCount: run.skippedCount,
  unparsedCount: run.unparsedCount,
  items: run.items.map((item) => ({
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    ...(item.errorClass === undefined ? {} : { errorClass: item.errorClass }),
    ...(item.durationMs === undefined ? {} : { durationMs: item.durationMs }),
    ...(item.commit === undefined ? {} : { commitHash: item.commit }),
    excerpt: item.excerpt,
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
  // `null` an einem grünen oder grauen Arbeitspaket — dort erscheint weder Feld noch Knopf.
  const uebernahme = buildHandoffText(item)

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
          {!gekuerzt &&
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
    </Box>
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

/** Die Kosten des Nachtlaufs stehen im Ergebnisstand in US-Dollar. */
const KOSTEN_FORMAT = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'USD' })

/**
 * Ein Betrag oder die ausdrückliche Auskunft, dass der Stand keinen führt. „0 $" wäre eine
 * Behauptung über etwas, das gar nicht gemeldet wurde.
 */
const betrag = (wert: number | undefined): string =>
  wert === undefined ? 'nicht angegeben' : KOSTEN_FORMAT.format(wert)

/**
 * Der Vermerk fehlender Kostenmeldungen (AK 3 und AK 11 aus #859); `null`, wo nichts fehlt. Er
 * steht als Einschränkung neben der Summe, nicht als eigene Fehlermeldung: Die Summe stimmt, sie
 * ist nur unvollständig.
 */
function ohneKostenmeldung(anzahl: number | undefined): string | null {
  if (anzahl === undefined || anzahl === 0) {
    return null
  }
  return anzahl === 1
    ? 'ein Arbeitsschritt ohne Kostenmeldung'
    : `${anzahl} Arbeitsschritte ohne Kostenmeldung`
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

/** Kosten und Züge eines Vorgangs, dazu der Vermerk fehlender Kostenmeldungen (AK 11). */
function vorgangsKennzahlen(kennzahlen: NightRunKennzahlen | undefined): string {
  const vermerk = ohneKostenmeldung(kennzahlen?.kostenUnbekannt)
  return [
    kennzahlen?.kostenUsd === undefined ? 'Kosten nicht gemeldet' : betrag(kennzahlen.kostenUsd),
    ...(kennzahlen?.zuege === undefined ? [] : [`${kennzahlen.zuege} Züge`]),
    ...(vermerk === null ? [] : [vermerk]),
  ].join(' · ')
}

/** Der Kopf der Übersicht: Modell, Label und Abschluss — jede Angabe nur, wo der Stand sie führt. */
const kopfText = (stand: NightRunStand | undefined): string =>
  [
    ...(stand?.modell === undefined ? [] : [stand.modell]),
    ...(stand?.label === undefined ? [] : [`Label ${stand.label}`]),
    abschlussText(stand?.abschluss),
  ].join(' · ')

/** Ein Abschnitt des Stufenbands — alles, was seine Darstellung und seine Ansage brauchen. */
interface Bandabschnitt {
  schluessel: NightRunKettenStufe
  label: string
  /** Der Breitenanteil: die Zeitvorgabe in Minuten; ohne Vorgabe {@link ANTEIL_OHNE_VORGABE}. */
  anteil: number
  /** Die Füllung in Prozent, **bei 100 gekappt** — der tatsächliche Wert steht in {@link zahlen}. */
  fuellung: number
  /** Ob der Vorgang diesen Arbeitsschritt überhaupt erreicht hat. */
  erreicht: boolean
  /** Verbrauch und Vorgabe als Zahlen. */
  zahlen: string
  /** Der Vermerk unter dem Abschnitt; `null`, wo keiner steht. */
  vermerk: string | null
  /** Die Füllfarbe — der Ampelton nur an dem Abschnitt, an dem der Vorgang endete. */
  farbe: string
}

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

/**
 * Das Stufenband eines Ketten-Vorgangs (Issue #867): die vier Arbeitsschritte in der Breite ihrer
 * Zeitvorgaben, jeder gefüllt, soweit er seine Zeit verbraucht hat, darunter Verbrauch und Vorgabe
 * als Zahlen. Wer hinsieht, erkennt ohne Zahlenlesen, an welchem Schritt eine Kette riss.
 *
 * <p><b>Die Aussage hängt nie an der Farbe</b> (`CLAUDE-react.md`, Zeile 142): „nicht erreicht",
 * „am Zeitbudget beendet" und „hier abgebrochen" stehen unter dem betroffenen Abschnitt in Worten,
 * und die Beschriftung des Bands nennt alle vier Schritte. Die Schraffur für „nicht erreicht" ist
 * die zweite, nicht die einzige Unterscheidung von einem leeren Balken.
 *
 * <p><b>Alle Töne kommen aus dem Theme</b> (E9 aus Plan #863): Schiene und Schraffur aus der
 * Trennlinienfarbe und der hellsten getönten Fläche, die Füllung aus `primary` — und am Abschnitt,
 * an dem der Vorgang endete, aus {@link ZUSTAND_FARBE}, also demselben Ampelton, den die Zeile des
 * Arbeitspakets trägt. Neue Töne in `palette.nightRun` hätten den dortigen Kopfkommentar falsch
 * gemacht.
 */
function Vorgangsband({
  item,
  vorgaben,
}: Readonly<{ item: NightRunItem; vorgaben: NightRunStufenvorgaben | undefined }>) {
  const letzte = letzteErreichteStufe(item.kettenStufen)
  const abschnitte = KETTEN_STUFEN.map((stufe) => bandabschnitt(stufe, item, vorgaben, letzte))
  // Ein Vorgang, der keinen Schritt erreicht hat, endete an keinem — über ihm steht kein Satz.
  const abgebrochen = letzte !== undefined && item.state !== 'GREEN'

  return (
    <>
      {abgebrochen && (
        <Typography
          variant="body2"
          color="text.secondary"
          data-testid={`abbruch-${item.cardNumber}`}
        >
          {abbruchSatz(item)}
        </Typography>
      )}
      <Stack
        direction="row"
        spacing={0.5}
        role="img"
        aria-label={bandAnsage(abschnitte)}
        data-testid={`stufenband-${item.cardNumber}`}
        sx={{ mt: 0.5 }}
      >
        {abschnitte.map((abschnitt) => (
          <Box
            key={abschnitt.schluessel}
            data-testid={`stufe-${item.cardNumber}-${abschnitt.schluessel}`}
            data-anteil={abschnitt.anteil}
            data-fuellung={abschnitt.fuellung}
            data-erreicht={abschnitt.erreicht ? 'ja' : 'nein'}
            sx={{ flexGrow: abschnitt.anteil, flexBasis: 0, minWidth: 0 }}
          >
            <Box
              sx={(t) => ({
                height: 8,
                borderRadius: 1,
                overflow: 'hidden',
                bgcolor: t.palette.divider,
                ...(abschnitt.erreicht
                  ? {}
                  : {
                      backgroundImage: `repeating-linear-gradient(45deg, ${t.palette.divider} 0 3px, ${SURFACE_TINT} 3px 6px)`,
                    }),
              })}
            >
              {abschnitt.erreicht && (
                <Box sx={{ height: '100%', width: `${abschnitt.fuellung}%`, bgcolor: abschnitt.farbe }} />
              )}
            </Box>
            <Typography variant="caption" component="div">
              {abschnitt.label}
            </Typography>
            <Typography variant="caption" component="div" color="text.secondary">
              {abschnitt.zahlen}
            </Typography>
            {abschnitt.vermerk !== null && (
              <Typography variant="caption" component="div" color="text.secondary">
                {abschnitt.vermerk}
              </Typography>
            )}
          </Box>
        ))}
      </Stack>
    </>
  )
}

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

/** Eine Angabe der Fußzeile: die Vorgabe der Nacht und ihr Wert. */
function Fussangabe({ label, wert }: Readonly<{ label: string; wert: string }>) {
  return (
    <Box>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2">{wert}</Typography>
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
 * Eine entstandene Karte als Verweis. Sichtbar steht allein ihre Nummer — die Übersicht führt drei
 * Vorgänge mit bis zu drei Karten, und volle Titel machten daraus eine Textwand. Der zugängliche
 * Name trägt Stufe und Titel dazu, und die sichtbare Nummer bleibt sein Teilstring (WCAG 2.5.3).
 */
function Kartenverweis({
  id,
  label,
  katalog,
  onOeffnen,
}: Readonly<{
  id: string
  label: string
  katalog: Kartenkatalog
  onOeffnen: (karte: CardByNumber) => void
}>) {
  const zustand = verweisZustand(alsNummer(id), katalog)
  if (zustand.art === 'laedt') {
    return <Typography component="span" variant="body2">{`#${id}`}</Typography>
  }
  if (zustand.art === 'fort') {
    return (
      <Typography component="span" variant="body2">{`#${id} nicht mehr vorhanden`}</Typography>
    )
  }
  return (
    <Link
      component="button"
      type="button"
      variant="body2"
      aria-label={`${label} #${id} ${zustand.karte.title}`}
      onClick={() => onOeffnen(zustand.karte)}
    >
      {`#${id}`}
    </Link>
  )
}

/**
 * Die Karten, die ein Vorgang in einem Arbeitsschritt hinterlassen hat — oder die ausdrückliche
 * Auskunft, dass dort keine entstand. Ein leerer Platz ließe offen, ob nichts entstand oder nichts
 * nachgesehen wurde; dieselbe Begründung wie beim Abbruchsatz über dem Band.
 */
function Dokumentzeile({
  label,
  leer,
  ids,
  testId,
  katalog,
  onOeffnen,
}: Readonly<{
  label: string
  leer: string
  ids: readonly string[]
  testId: string
  katalog: Kartenkatalog
  onOeffnen: (karte: CardByNumber) => void
}>) {
  if (ids.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" data-testid={testId}>
        {leer}
      </Typography>
    )
  }
  return (
    <Typography variant="body2" color="text.secondary" data-testid={testId}>
      {`${label} `}
      {ids.map((id, position) => (
        <Fragment key={id}>
          {position > 0 && ', '}
          <Kartenverweis id={id} label={label} katalog={katalog} onOeffnen={onOeffnen} />
        </Fragment>
      ))}
    </Typography>
  )
}

/**
 * Die Übersicht eines Ketten-Laufs (Plan #863, Issue #866): Kopf, die Kennzahlen der Nacht, je
 * Vorgang Kosten und Züge, dazu die Vorgaben in der Fußzeile.
 *
 * <p>Sie liest den **gedeuteten Lauf aus dem sitzungslokalen Speicher**, nicht den Anzeigelauf
 * (E1): Zeitvorgaben, Kostenbudget, Modell und die Kennzahlen je Arbeitsschritt stehen allein im
 * Ergebnisstand und werden nicht aufbewahrt. Nach einem Neuladen der Seite ist der Speicher leer,
 * und der Lauf fällt auf die Zeilendarstellung zurück.
 *
 * <p>Die eigene Kennzeichnung als Wurzel ist Absicht: Die Bestandstests zum Ketten-Lauf (#854,
 * #856, #858) suchen mit `getByText` im Panel, und ohne eine eigene Wurzel müsste jeder von ihnen
 * angefasst werden, sobald hier ein Text zweimal auf der Seite steht.
 */
function KettenUebersicht({
  run,
  katalog,
  onOeffnen,
}: Readonly<{
  run: NightRun
  /** Derselbe Katalog wie in den Arbeitspaket-Zeilen — er trägt seit #868 auch die Dokumente. */
  katalog: Kartenkatalog
  onOeffnen: (karte: CardByNumber) => void
}>) {
  const stand = run.stand
  const start = new Date(run.startedAt)
  const datum = start.toLocaleDateString('de-DE', { dateStyle: 'full' })
  const uhrzeit = start.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
  const gruen = run.items.filter((item) => item.state === 'GREEN').length

  return (
    <Box
      data-testid="ketten-uebersicht"
      sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 2, mb: 2 }}
    >
      <Typography variant="subtitle1">{`Nacht vom ${datum}, ${uhrzeit} Uhr`}</Typography>
      <Typography variant="body2" color="text.secondary">
        {kopfText(stand)}
      </Typography>

      <Stack direction="row" spacing={3} sx={{ mt: 2, flexWrap: 'wrap' }}>
        {/* „Vollständig durchlaufen" ist der grüne Zustand: Im Modus `CHAIN` wird nach
            `KETTEN_AUSGAENGE` ausschließlich `fertig` grün, und der rohe Ausgang erreicht die
            Seite gar nicht (E4). */}
        <Kennzahl
          wert={`${gruen} von ${run.items.length}`}
          label="Ketten durchgelaufen"
          hinweis={null}
        />
        <Kennzahl
          wert={`${entstandeneDokumente(run.items).size}`}
          label="Karten entstanden"
          hinweis={null}
        />
        <Kennzahl
          wert={formatDuration(stufenZeitSumme(run.items) / 1000)}
          label="Laufzeit über alle Stufen"
          hinweis={null}
        />
        <Kennzahl
          wert={betrag(stand?.kostenSumme)}
          label="Kosten der Nacht"
          hinweis={ohneKostenmeldung(stand?.kostenUnbekannt)}
        />
      </Stack>

      <Stack spacing={1} sx={{ mt: 2 }}>
        {run.items.map((item) => (
          <Box
            key={`${item.cardNumber}-${item.position}`}
            data-testid={`uebersicht-vorgang-${item.cardNumber}`}
          >
            <Typography variant="body2">{`#${item.cardNumber} ${item.title}`}</Typography>
            <Typography variant="body2" color="text.secondary">
              {vorgangsKennzahlen(item.kennzahlen)}
            </Typography>
            <Vorgangsband item={item} vorgaben={stand?.vorgabenMin} />
            {DOKUMENT_STUFEN.map(({ schluessel, label, leer }) => (
              <Dokumentzeile
                key={schluessel}
                label={label}
                leer={leer}
                ids={dokumenteDerStufe(item.kettenStufen, schluessel)}
                testId={`dokumente-${item.cardNumber}-${schluessel}`}
                katalog={katalog}
                onOeffnen={onOeffnen}
              />
            ))}
          </Box>
        ))}
      </Stack>

      <Divider sx={{ my: 2 }} />
      {/* Die Zeile „Herkunft der Budgets" aus `docs/mockup-leitstand-nachtlauf.html` fehlt hier
          bewusst: Der Leitstand kennt die Einstellungen des Betreibers nicht und kann deshalb
          nicht sagen, ob eine Vorgabe eingestellt oder voreingestellt war (#859, Nicht-Ziel 2). */}
      <Stack direction="row" spacing={3} sx={{ flexWrap: 'wrap' }} data-testid="uebersicht-fuss">
        <Fussangabe label="Zeitvorgaben je Kette" wert={vorgabenText(stand?.vorgabenMin)} />
        <Fussangabe label="Kostenbudget je Kette" wert={betrag(stand?.kostenBudgetUsd)} />
        <Fussangabe label="Höchste Kosten eines Vorgangs" wert={hoechsteKosten(run.items)} />
      </Stack>
    </Box>
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
 * Das Laufband eines Umsetzungs-Laufs (Issue #871): **ein** Band über alle Vorgänge der Nacht,
 * jeder Abschnitt so breit, wie sein Vorgang an der Gesamtzeit verbrauchte. Wer hinsieht, erkennt
 * ohne Zahlenlesen, woran die Nacht ihre Zeit verbrauchte — an der echten Nacht vom 7. September
 * verbrauchten drei von fünf Vorgängen zusammen 86,7 Prozent, und alle drei scheiterten.
 *
 * <p><b>Bezugsgröße ist die Summe der Vorgangsdauern</b>, nicht die verstrichene Zeit von der
 * Startzeit bis zum Abschluss: Der Ergebnisstand weist die Zeit zwischen den Vorgängen nicht aus
 * und trägt gar keinen Endzeitstempel. Ein Band gegen die verstrichene Zeit hätte einen
 * unbenannten Rest, und der wäre eine erfundene Größe.
 *
 * <p><b>Die Aussage hängt nie an der Farbe</b> (`CLAUDE-react.md`, Zeile 142): Jeder Abschnitt
 * führt eine Ansage mit Kartennummer, Zustand und Dauer, und die Dauer steht zusätzlich sichtbar
 * unter ihm. Die Töne kommen aus {@link ZUSTAND_FARBE}, also derselben Palette wie die Ampel der
 * Vorgangszeile — kein neuer Ton, kein Hex-Literal.
 *
 * <p><b>Unter zwei Abschnitten erscheint kein Band</b>: Ein Balken mit einem einzigen Abschnitt
 * behauptet ein Verhältnis, das es nicht gibt. Die Angaben der Vorgänge stehen davon unberührt in
 * der Zeilenliste. Aus derselben Prüfung folgt, dass die Summe unten nachweislich über null liegt
 * — die Teilung braucht keinen weiteren Schutz.
 */
function Laufband({ items }: Readonly<{ items: readonly AnzeigeItem[] }>) {
  const gemessen = items.filter(mitDauer)
  if (gemessen.length < 2) {
    return null
  }
  const summe = gemessen.reduce((wert, item) => wert + item.durationMs, 0)

  return (
    <Stack direction="row" spacing={0.5} data-testid="laufband" sx={{ mb: 2 }}>
      {gemessen.map((item, position) => {
        const abschnitt = laufabschnitt(item, summe)
        return (
          <Box
            // Zwei Vorgänge können dieselbe Karte betreffen — wie in der Zeilenliste trägt der
            // Schlüssel deshalb die Position dazu.
            key={`${abschnitt.cardNumber}-${position}`}
            role="img"
            aria-label={abschnitt.ansage}
            data-testid={`laufband-abschnitt-${abschnitt.cardNumber}`}
            data-anteil={abschnitt.anteil}
            sx={{ flexGrow: abschnitt.anteil, flexBasis: 0, minWidth: 0 }}
          >
            <Box
              data-testid={`laufband-balken-${abschnitt.cardNumber}`}
              sx={{ height: 8, borderRadius: 1, bgcolor: abschnitt.farbe }}
            />
            <Typography variant="caption" component="div" color="text.secondary">
              {abschnitt.dauer}
            </Typography>
          </Box>
        )
      })}
    </Stack>
  )
}

/** Ein Lauf als aufklappbares Panel; die Kette wird erst beim Aufklappen geladen (A8). */
function LaufPanel({
  lauf,
  ergebnis,
  ausErgebnisstand,
  kettenStand,
  katalog,
  vorhabenKarten,
  zaehler,
  aufbewahrteLaeufe,
  onAufklappen,
  onOeffnen,
}: Readonly<{
  lauf: AnzeigeLauf
  /** `true` = in dieser Sitzung neu angelegt, `false` = lag schon vor, `undefined` = nicht gesendet. */
  ergebnis: boolean | undefined
  /** Die Startzeitpunkte der Läufe, die in dieser Sitzung aus einem Ergebnisstand entstanden sind. */
  ausErgebnisstand: ReadonlySet<string>
  /** Der gedeutete Ketten-Lauf dieser Sitzung; `undefined` heißt: keine Übersicht (E1). */
  kettenStand: NightRun | undefined
  katalog: Kartenkatalog
  vorhabenKarten: Vorhabenkatalog
  zaehler: Haeufigkeiten
  /** Das „M" in „N von M aufbewahrten Läufen" — die Länge der zuletzt geladenen Liste. */
  aufbewahrteLaeufe: number
  onAufklappen: () => void
  onOeffnen: (karte: CardByNumber) => void
}>) {
  const rot = new Set(lauf.items.filter((item) => item.state === 'RED').map((item) => item.cardNumber))

  return (
    <Accordion
      data-testid={`lauf-${lauf.startedAt}`}
      component={Paper}
      variant="outlined"
      slotProps={{ transition: { unmountOnExit: true } }}
      onChange={(_, offen) => offen && onAufklappen()}
    >
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
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
        </Stack>
      </AccordionSummary>
      <AccordionDetails>
        {kettenStand !== undefined && (
          <KettenUebersicht run={kettenStand} katalog={katalog} onOeffnen={onOeffnen} />
        )}
        {/* Das Band steht unter der Kopfzeile des Laufs und über seiner Zeilenliste — die Kopfzeile
            selbst („N bearbeitet, M übergangen") bleibt unverändert. Nur am Umsetzungs-Lauf: Der
            Ketten-Lauf hat mit dem Stufenband je Vorgang bereits ein Band, und ein zweites daneben
            bezöge sich auf eine andere Größe. */}
        {lauf.mode === 'IMPLEMENTATION' && <Laufband items={lauf.items} />}
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
        {lauf.items.map((item, position) => (
          <Arbeitspaket
            key={`${item.cardNumber}-${position}`}
            item={item}
            modus={lauf.mode}
            // Dieselbe Bedingung, die über die Übersicht entscheidet, und nicht die Lauf-Art
            // (#869): Ein aufbewahrter Ketten-Lauf ohne Sitzungsstand bekommt keine Übersicht und
            // verlöre sonst seine Zeilenangaben, ohne etwas dafür zu bekommen.
            gekuerzt={kettenStand !== undefined}
            katalog={katalog}
            vorhabenKarten={vorhabenKarten}
            haeufigkeit={haeufigkeitsText(item, lauf.gespeichert, zaehler, aufbewahrteLaeufe)}
            istRot={(nummer) => rot.has(nummer)}
            onOeffnen={onOeffnen}
          />
        ))}
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
   * Die in **dieser Sitzung** gedeuteten Ketten-Läufe, je Startzeitpunkt (Plan #863, E1). Aus
   * ihnen allein entsteht die Übersicht: Zeitvorgaben, Kostenbudget, Modell, Label, Abschlussart
   * und die Kennzahlen je Arbeitsschritt stehen nur im Ergebnisstand — der Server bewahrt sie
   * nicht auf, und Manne hat am 2026-09-14 entschieden, daran nichts zu ändern (#859, Frage 1).
   *
   * <p>Nicht an das Kennzeichen `gespeichert` gebunden, und das ist der Kern der Entscheidung:
   * `protokollLesen` liefert den Lauf ein und ersetzt danach das **ganze** Lauf-Array durch die
   * Server-Sicht, in der `gespeichert` auf `true` steht. Eine so gebundene Übersicht hätte nur
   * zwischen Parsen und Server-Antwort existiert.
   *
   * <p>Sitzungslokal wie das benachbarte {@link ausErgebnisstand}: Nach einem Neuladen der Seite
   * ist der Speicher leer, und der Lauf fällt auf die Zeilendarstellung zurück.
   */
  const [kettenStaende, setKettenStaende] = useState<ReadonlyMap<string, NightRun>>(() => new Map())
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

  const aufklappen = (lauf: AnzeigeLauf) => {
    if (geladeneLaeufe.current.has(lauf.startedAt)) {
      return
    }
    geladeneLaeufe.current.add(lauf.startedAt)
    void ladeKetten(lauf.items, dokumentNummern(kettenStaende.get(lauf.startedAt)))
  }

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
    if (run.mode === 'CHAIN') {
      setKettenStaende((bisher) => new Map(bisher).set(run.startedAt, run))
    }
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
    <Box>
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

      {laeufe.length === 0 && <Typography color="text.secondary">Noch keine Auswertung vorhanden.</Typography>}

      {laeufe.map((lauf) => (
        <LaufPanel
          key={lauf.startedAt}
          lauf={lauf}
          ergebnis={ergebnisse.get(lauf.startedAt)}
          ausErgebnisstand={ausErgebnisstand}
          // Der Modus steht am Anzeigelauf und damit auch am neu geladenen; der Speicher entscheidet
          // danach, ob zu genau diesem Lauf ein Ergebnisstand dieser Sitzung vorliegt.
          kettenStand={lauf.mode === 'CHAIN' ? kettenStaende.get(lauf.startedAt) : undefined}
          katalog={katalog}
          vorhabenKarten={vorhabenKarten}
          zaehler={zaehler}
          aufbewahrteLaeufe={aufbewahrteLaeufe}
          onAufklappen={() => aufklappen(lauf)}
          onOeffnen={setDetail}
        />
      ))}

      {detail !== null && (
        <CardDetailModal
          card={detail}
          canEdit={false}
          projectId={id}
          onClose={() => setDetail(null)}
        />
      )}
    </Box>
  )
}
