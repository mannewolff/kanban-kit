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
import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { cardsApi, type CardByNumber } from '../api/cards'
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
  NIGHT_RUN_ERROR_CLASS_TEXT,
  NIGHT_RUN_STATE_TEXT,
  type NightRunHandoffItem,
} from '../lib/nightRunHandoff'
import {
  parseNightRunErgebnisstand,
  type NightRunErgebnisstandGrund,
} from '../lib/nightRunErgebnisstand'
import { type NightRun, type NightRunMode, type NightRunState } from '../lib/nightRunLog'
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
 * Die Chip-Beschriftung je Lauf-Modus (Plan #803). Als `Record` über alle drei Werte, nicht als
 * Inline-Bedingung: Ein vierter Modus bricht den Build, statt still auf „Umsetzungs-Lauf" zu
 * fallen — dieselbe Absicherung wie bei {@link ZUSTAND_FARBE}.
 */
const MODUS_TEXT: Record<NightRunMode, string> = {
  IMPLEMENTATION: 'Umsetzungs-Lauf',
  REVIEW: 'Prüf-Lauf',
  NIGHTPLAN: 'Nachtplan-Lauf',
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
 * Was die Seite zu einem nicht deutbaren Stand sagt — eine Zeile je Grund aus
 * {@link parseNightRunErgebnisstand}. Die drei sind bewusst unterschieden: Sie verlangen vom
 * Betreiber verschiedene Handgriffe (die falsche Datei gewählt, ein neueres Kit, oder ein Lauf,
 * den der Leitstand nicht auswertet).
 */
const NICHT_DEUTBAR: Record<NightRunErgebnisstandGrund, string> = {
  'kein-json': 'Nicht auswertbar',
  'unbekannte-fassung': 'Fassung nicht unterstützt',
  'nicht-unterstuetzt': 'Lauf-Art oder Vokabular nicht unterstützt',
}

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
 * Der Text einer Kettenstufe. Die vier Fälle sind bewusst unterschieden (#715, A8):
 *
 * - **vorhanden** — die Stufe existiert; meldet der Lauf zu ihrer Karte einen roten Zustand, ist
 *   der Weg dort **abgerissen** (gescheitert oder auf eine Entscheidung wartend).
 * - **ohne** — das Arbeitspaket hat gar keinen Vorfahren. Kein Abriss, sondern ein legitim kurzer
 *   Weg (etwa ein Sonar-Befund).
 * - **noch nicht erreicht** — die Kette ist begonnen, aber diese Stufe fehlt. Sie zu erzeugen ist
 *   genau der Schritt, den der Nachtlauf heute nicht fährt.
 * - **Karte #N nicht gefunden** — eine Nummer der Kette ließ sich nicht auflösen; das ist weder
 *   „ohne" noch ein Abriss.
 */
function stufenText(
  stufe: { label: string; praefix: string },
  glieder: readonly Kettenglied[],
  istRot: (nummer: number) => boolean,
): string {
  const geladen = glieder.filter(
    (glied): glied is { nummer: number; karte: CardByNumber } => glied.karte !== null,
  )
  const treffer = geladen.find((glied) => glied.karte.title.startsWith(stufe.praefix))
  if (treffer !== undefined) {
    const abriss = istRot(treffer.nummer) ? ' — abgerissen' : ''
    return `${stufe.label}: #${treffer.nummer} ${treffer.karte.title}${abriss}`
  }
  const unbekannt = glieder.find((glied) => glied.karte === null)
  if (unbekannt !== undefined) {
    return `${stufe.label}: Karte #${unbekannt.nummer} nicht gefunden`
  }
  return glieder.length === 0 ? `${stufe.label}: ohne` : `${stufe.label}: noch nicht erreicht`
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
  katalog,
  haeufigkeit,
  istRot,
  onOeffnen,
}: Readonly<{
  item: AnzeigeItem
  katalog: Kartenkatalog
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
    <Box sx={{ py: 1 }}>
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
            {NIGHT_RUN_STATE_TEXT[item.state]}
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

      {item.excerpt !== undefined && (
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

      {wurzel != null &&
        STUFEN.map((stufe) => (
          <Typography key={stufe.label} variant="body2" color="text.secondary">
            {stufenText(stufe, kette(item.cardNumber, katalog), istRot)}
          </Typography>
        ))}

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

/** Ein Lauf als aufklappbares Panel; die Kette wird erst beim Aufklappen geladen (A8). */
function LaufPanel({
  lauf,
  ergebnis,
  ausErgebnisstand,
  katalog,
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
  katalog: Kartenkatalog
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
            katalog={katalog}
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
  const [katalog, setKatalog] = useState<Kartenkatalog>(() => new Map())
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

  /** Löst die Herkunftsketten eines Laufs auf — Stufe für Stufe, jede Nummer nur einmal. */
  const ladeKetten = async (items: readonly AnzeigeItem[]) => {
    const bekannt = katalogRef.current
    let offen = [...new Set(items.map((item) => item.cardNumber))].filter((n) => !bekannt.has(n))

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
    setKatalog(new Map(bekannt))
  }

  const aufklappen = (lauf: AnzeigeLauf) => {
    if (geladeneLaeufe.current.has(lauf.startedAt)) {
      return
    }
    geladeneLaeufe.current.add(lauf.startedAt)
    void ladeKetten(lauf.items)
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
      setMeldung(NICHT_DEUTBAR[ergebnis.grund])
      return
    }

    // Ein Ergebnisstand ist genau ein Lauf; ein zweiter Stand desselben Laufs ersetzt den ersten.
    const run = ergebnis.run
    // Vermerkt **vor** dem Senden: Der Lauf ist aus dem Ergebnisstand entstanden, unabhängig davon,
    // ob die Einlieferung gleich gelingt.
    setAusErgebnisstand((bisher) => new Set(bisher).add(run.startedAt))
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
          katalog={katalog}
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
