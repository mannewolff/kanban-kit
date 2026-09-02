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
import Typography from '@mui/material/Typography'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { cardsApi, type CardByNumber } from '../api/cards'
import {
  nightRunsApi,
  type NightRunErrorClassCounts,
  type NightRunSubmission,
  type NightRunView,
} from '../api/nightRuns'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { CardDetailModal } from '../components/CardDetailModal'
import { useSnackbar } from '../components/SnackbarProvider'
import { formatDuration } from '../lib/formatDuration'
import {
  parseNightRunLog,
  type NightRun,
  type NightRunErrorClass,
  type NightRunState,
} from '../lib/nightRunLog'
import { readTextFile } from '../lib/readTextFile'
import { useProjectName } from '../lib/useProjectName'

/**
 * Auswertung der Nachtläufe eines Projekts (Issue #725, Plan #718).
 *
 * **Das Protokoll verlässt den Browser nicht** (Entscheidung A1, Präzedenzfall `lib/specImport.ts`):
 * Die Datei wird über {@link readTextFile} eingelesen, mit {@link parseNightRunLog} hier geparst,
 * und an den Server geht allein die verdichtete Auswertung. Protokolle sind mehrere Megabyte groß
 * und tragen Projekt-Quelltext, Sitzungs-IDs und Pfade.
 *
 * **Die Herkunftskette wird erst beim Aufklappen eines Laufs aufgelöst** (A8). `cardsApi.byNumber`
 * liefert genau eine Karte; 30 aufbewahrte Läufe mit je 10 bis 15 Arbeitspaketen und zwei
 * Kettenschritten wären mehrere hundert Anfragen bei jedem Seitenaufruf.
 */

/** Ein Arbeitspaket in der Anzeigeform — frisch geparst und aufbewahrt sehen gleich aus. */
interface AnzeigeItem {
  cardNumber: number
  title: string
  state: NightRunState
  errorClass: NightRunErrorClass | undefined
  durationMs: number | undefined
  excerpt: string | undefined
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

const ZUSTAND_TEXT: Record<NightRunState, string> = {
  GREEN: 'Erfolg',
  YELLOW: 'Erfolg, Prüfung rot',
  RED: 'gescheitert',
  GREY: 'nicht bearbeitet',
}

/**
 * Die Beschriftung je Fehlerklasse. Der Typ ist `Record<NightRunErrorClass, string>` und **nicht**
 * `Partial`: Die Liste der Klassen ist abgeschlossen und lebt in `lib/nightRunLog.ts` (A13); käme
 * dort eine hinzu, bräche hier der Build, statt dass eine Häufigkeit stumm ohne Beschriftung
 * erschiene. Deshalb zählt diese Datei die Klassen auch nirgends selbst auf.
 */
const FEHLERKLASSE_TEXT: Record<NightRunErrorClass, string> = {
  CHECKS_RED: 'Prüfungen rot',
  CHECKS_NOT_STARTED: 'Prüfungen nicht gelaufen',
  DEPENDENCY_UNMET: 'Abhängigkeit offen',
  UNEXPECTED_STATE: 'Unerwarteter Zustand',
  HARD_ABORT: 'Harter Abbruch',
  AWAITING_DECISION: 'Wartet auf Entscheidung',
  REVIEWER_FAILED: 'Prüf-Session gescheitert',
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
 * Die Stufen des Wegs von der fachlichen Anforderung über den Plan zum Arbeitspaket (#715). Die
 * Zuordnung läuft über die **Titel-Präfixe**, die der Workflow ohnehin vergibt (`/fachplan`,
 * `/plan`) — nicht über die Position in der `derivedFrom`-Kette: Ein Vorhaben oder eine Idee kann
 * dazwischenliegen, und dann bezeichnete die Position die falsche Stufe.
 */
const STUFEN: ReadonlyArray<{ label: string; praefix: string }> = [
  { label: 'Fachliche Anforderung', praefix: '[Fachlich]' },
  { label: 'Plan', praefix: '[Plan]' },
]

const KEIN_PROTOKOLL = 'Kein Nachtlauf-Protokoll erkannt'

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

const ausSicht = (view: NightRunView): AnzeigeLauf => ({
  gespeichert: true,
  startedAt: view.startedAt,
  mode: view.mode,
  durationMs: view.durationMs,
  processedCount: view.processedCount,
  skippedCount: view.skippedCount,
  unparsedCount: view.unparsedCount,
  unparsedSample: view.unparsedSample === undefined ? [] : view.unparsedSample.split('\n'),
  items: view.items.map((item) => ({
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    errorClass: item.errorClass,
    durationMs: item.durationMs,
    excerpt: item.excerpt,
  })),
})

/**
 * Ein Lauf, wie er an den Server geht: Kennzahlen, Zustände, Kartennummern, Fehlerklassen und die
 * kurzen Auszüge — nie das Protokoll. Optionale Felder werden weggelassen statt auf `undefined`
 * gesetzt, damit der Request-Body keine leeren Schlüssel trägt.
 */
const zurEinlieferung = (run: NightRun): NightRunSubmission => ({
  startedAt: run.startedAt,
  mode: run.mode,
  durationMs: run.durationMs,
  processedCount: run.processedCount,
  skippedCount: run.skippedCount,
  unparsedCount: run.unparsedCount,
  ...(run.unparsedSample.length === 0 ? {} : { unparsedSample: run.unparsedSample.join('\n') }),
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
  const beschriftung = FEHLERKLASSE_TEXT[item.errorClass]
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

/** Ein Arbeitspaket samt Zustand, Dauer, Auszug, Häufigkeit und Herkunftskette. */
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

  return (
    <Box sx={{ py: 1 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap' }}>
        <Chip
          size="small"
          variant="outlined"
          data-testid={`zustand-${item.cardNumber}`}
          label={ZUSTAND_TEXT[item.state]}
          sx={{ color: ZUSTAND_FARBE[item.state], borderColor: ZUSTAND_FARBE[item.state] }}
        />
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
    </Box>
  )
}

/** Ein Lauf als aufklappbares Panel; die Kette wird erst beim Aufklappen geladen (A8). */
function LaufPanel({
  lauf,
  ergebnis,
  katalog,
  zaehler,
  aufbewahrteLaeufe,
  onAufklappen,
  onOeffnen,
}: Readonly<{
  lauf: AnzeigeLauf
  /** `true` = in dieser Sitzung neu angelegt, `false` = lag schon vor, `undefined` = nicht gesendet. */
  ergebnis: boolean | undefined
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
          <Chip
            size="small"
            label={lauf.mode === 'REVIEW' ? 'Prüf-Lauf' : 'Umsetzungs-Lauf'}
            variant="outlined"
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
              <Typography key={position} variant="body2" color="text.secondary">
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
      .catch((fehler: Error) => {
        if (aktiv) notify(fehler.message, 'error')
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
   * Liest das Protokoll im Browser, zeigt die Auswertung und liefert sie ein. Die geparste
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

    const { runs, dryRunCount } = parseNightRunLog(text)
    if (runs.length === 0) {
      setMeldung(
        dryRunCount === 0
          ? KEIN_PROTOKOLL
          : `Das Protokoll enthält nur Probeläufe (${dryRunCount}) — keine Auswertung.`,
      )
      return
    }

    const geparst = runs.map(ausParser)
    setLaeufe((bisher) =>
      [...geparst, ...bisher.filter((alt) => !geparst.some((neu) => neu.startedAt === alt.startedAt))].sort(
        nachStartAbsteigend,
      ),
    )

    try {
      const antwort = await nightRunsApi.submit(id, runs.map(zurEinlieferung))
      setErgebnisse(new Map(antwort.map((eintrag) => [eintrag.startedAt, eintrag.created])))
      const views = await nightRunsApi.list(id)
      setLaeufe(views.map(ausSicht).sort(nachStartAbsteigend))
      setAufbewahrteLaeufe(views.length)
      setZaehler(await zaehlerLaden(id))
    } catch (fehler) {
      // `apiFetch` wirft `ApiError`, ein Netzwerkabbruch einen `TypeError` — beides `Error`.
      notify((fehler as Error).message, 'error')
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
            accept=".log,.txt,text/plain"
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
