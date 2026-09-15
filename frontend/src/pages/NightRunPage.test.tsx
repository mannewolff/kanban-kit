import { ThemeProvider } from '@mui/material/styles'
import {
  fireEvent,
  getDefaultNormalizer,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Card, CardByNumber } from '../api/cards'
import type {
  NightRunErrorClassCounts,
  NightRunItemView,
  NightRunResult,
  NightRunView,
} from '../api/nightRuns'
import { SnackbarProvider } from '../components/SnackbarProvider'
import echterLauf from '../lib/__fixtures__/night-run-2026-09-07-085229.json'
import echterNachtplanRegulaer from '../lib/__fixtures__/night-run-2026-09-09-125621.json'
import echterNachtplanHarterStopp from '../lib/__fixtures__/night-run-2026-09-09-141506.json'
import echterPrueflauf from '../lib/__fixtures__/night-run-2026-09-11-103116.json'
import echteKette from '../lib/__fixtures__/night-run-2026-09-14-131200.json'
import { parseNightRunErgebnisstand } from '../lib/nightRunErgebnisstand'
import { buildHandoffText, type NightRunHandoffItem } from '../lib/nightRunHandoff'
import { theme } from '../theme'
import { NightRunPage } from './NightRunPage'

/**
 * Die Seite wird gegen einen **`fetch`-Stub** getestet, nicht gegen gemockte API-Module. Nur so
 * ist das Kriterium aus Issue #725 überhaupt prüfbar, dass beim Hineingeben eines Ergebnisstands
 * **kein** Request seinen Inhalt trägt: Ein gemocktes `nightRunsApi` erzeugte gar keine Requests,
 * und der Test wäre grün, ohne etwas zu belegen.
 *
 * Eingelesen wird seit Issue #774 ausschließlich der **Ergebnisstand** (`night-run-<datum>.json`);
 * das Textprotokoll ist kein Weg mehr. Die selbstgebauten Stände sind anonymisiert (Titel nach dem
 * Schema `Paket N`, keine Pfade) — daneben steht der echte Lauf vom 2026-09-07 aus
 * `lib/__fixtures__/`, damit die Seite mindestens einmal gegen echte Daten läuft.
 */

// Das Karten-Detail ist separat getestet (CardDetailModal.test.tsx) — hier ein Stub, der die
// geöffnete Karte sichtbar macht und den Schließen-Pfad auslöst.
//
// Der Stub gibt zusätzlich aus, **welches Theme** er sieht (#914): Nicht-Ziel 2 des Fachplans
// verbietet, die Gestaltung der Nachtlauf-Auswertung auf die übrige Anwendung zu übertragen, und
// der Dialog gehört zu ihr, obwohl er von dieser Seite aus geöffnet wird. Ob er im Theme-Teilbaum
// steht, entscheidet allein seine Stellung im React-Baum — das Portal, in dem er im DOM landet,
// sagt darüber nichts.
vi.mock('../components/CardDetailModal', async () => {
  const { useTheme } = await import('@mui/material/styles')
  return {
    CardDetailModal: ({
      card,
      onClose,
    }: {
      card: { number: number | null }
      onClose: () => void
    }) => {
      const gesehenesTheme = useTheme()
      return (
        <div data-testid="karten-detail" data-schrift={String(gesehenesTheme.typography.fontFamily)}>
          Karte {card.number}
          <button type="button" onClick={onClose}>
            detail-schliessen
          </button>
        </div>
      )
    },
  }
})

/** Startzeitpunkt eines Laufs — der Schlüssel der Auswertung (Plan #718, A4). */
const startedAt = (minute: number) => `2026-09-01T22:${String(minute).padStart(2, '0')}:00.000Z`

/** Die Dauer einer Session im Ergebnisstand; die Laufdauer ist ihre Summe. */
const SIEBEN_MIN = 7 * 60_000

/**
 * Ein Ergebnisstand der Fassung 1, wie `night.mjs` ihn schreibt. Jedes Feld ist einzeln
 * überschreibbar — dieselbe Bauform wie in `lib/nightRunErgebnisstand.test.ts`.
 */
const stand = (felder: Record<string, unknown> = {}): string =>
  JSON.stringify({
    schemaFassung: 1,
    erzeugtVon: '1.47.0',
    start: startedAt(0),
    art: 'implementierung',
    modell: 'claude-opus-5',
    max: 5,
    label: null,
    einheiten: [],
    abschluss: 'regulaer',
    ...felder,
  })

/** Eine Einheit des Ergebnisstands — ein Arbeitspaket der Runde. */
const einheit = (felder: Record<string, unknown>): Record<string, unknown> => ({
  id: '700',
  titel: 'Paket A',
  dauerMs: SIEBEN_MIN,
  ...felder,
})

/**
 * Markierungstext in einem Feld, das der Parser nicht deutet. Der Ergebnisstand führt in
 * `pruefung.laufen[].grund` die Pfade der geänderten Dateien — sie dürfen in keinem Request
 * auftauchen, weder in einer URL noch in einem Body.
 */
const GEHEIM = 'GEHEIM-NICHT-SENDEN'

/** Ein grüner Nachweis, samt der Felder, die die Auswertung nicht braucht. */
const GEPRUEFT = {
  zustand: 'geprueft',
  laufen: [{ cmd: 'npm test', grund: GEHEIM, ergebnis: 'gruen' }],
  ausgelassen: [],
}

/** Ein roter Nachweis — der Fall, für den es die Auswertung gibt. */
const NACHWEIS_ROT = {
  zustand: 'rot',
  rotesKommando: 'npm test',
  rotesErgebnis: 'rot',
  laufen: [{ cmd: 'npm test', grund: GEHEIM, ergebnis: 'rot' }],
  ausgelassen: [],
}

const EIN_LAUF = stand({
  einheiten: [einheit({ ausgang: 'erfolg', commit: 'a1b2c3d', pruefung: GEPRUEFT })],
})

/** Erfolgreiche Session, aber roter Nachweis → gelb. */
const GELB = stand({
  einheiten: [einheit({ ausgang: 'erfolg', commit: 'a1b2c3d', pruefung: NACHWEIS_ROT })],
})

/** Der Grund eines zurückgestellten Pakets, wie `night.mjs` ihn schreibt. */
const GRUND_ZURUECKGESTELLT = 'Abhaengigkeit #999 liegt nicht in Done.'

/** Alle vier Zustände in einem Lauf. */
const VIER_ZUSTAENDE = stand({
  einheiten: [
    einheit({ ausgang: 'erfolg', commit: 'a1b2c3d', pruefung: GEPRUEFT }),
    einheit({ id: '701', titel: 'Paket B', ausgang: 'erfolg', commit: 'b2c3d4e', pruefung: NACHWEIS_ROT }),
    einheit({ id: '702', titel: 'Paket C', ausgang: 'fehlschlag', pruefung: { zustand: 'ungeprueft' } }),
    // Ein zurückgestelltes Paket lief nie, also trägt es auch keine Dauer.
    einheit({ id: '703', titel: 'Paket D', ausgang: 'zurueckgestellt', grund: GRUND_ZURUECKGESTELLT, dauerMs: undefined }),
  ],
})

/** Der echte Lauf vom 2026-09-07 (Issue #773) — unverändert, wie der Runner ihn schrieb. */
const ECHTER_STAND = JSON.stringify(echterLauf)
const ECHTER_START = '2026-09-07T08:52:29.532Z'

/**
 * Der echte, hart gestoppte Nachtplan-Lauf vom 2026-09-09 (Issue #805/#806) — unverändert, aus
 * `claude-workflow-kit`. 33 übersprungene Pakete (kein Label `kit:nightplan`), zwei abgebrochene
 * (#479, #549).
 */
const ECHTER_NACHTPLAN_STAND = JSON.stringify(echterNachtplanHarterStopp)
const ECHTER_NACHTPLAN_START = '2026-09-09T14:15:06.165Z'

/**
 * Der echte, regulär beendete Erzeugungs-Lauf vom 2026-09-09 (Issue #872). Drei bearbeitete
 * Vorgänge, darunter der fortgesetzte #535 mit **gemessener** Dauer null und ohne jede
 * Sitzungs-Kennzahl — der Fall, an dem sich die Null von der fehlenden Angabe trennt.
 */
const ECHTE_ERZEUGUNG_STAND = JSON.stringify(echterNachtplanRegulaer)
const ECHTE_ERZEUGUNG_START = '2026-09-09T12:56:21.983Z'

/**
 * Der echte Prüf-Lauf vom 2026-09-11 (Issue #872). Sein einziger bearbeiteter Vorgang #782
 * meldet 18,1 Minuten Arbeitszeit bei 14,7 Minuten Dauer, weil mehrere Prüfer gleichzeitig
 * liefen — der Beleg dafür, dass die Arbeitszeit die Dauer übersteigen kann.
 */
const ECHTER_PRUEFLAUF_STAND = JSON.stringify(echterPrueflauf)
const ECHTER_PRUEFLAUF_START = '2026-09-11T10:31:16.379Z'

/**
 * Der echte Ketten-Lauf vom 2026-09-14 (Issue #854) — unverändert, wie der Runner ihn schrieb.
 * Drei Vorgänge: zwei fertige Ketten (#791, #814) und ein Zeitbudget-Abbruch mit erzeugtem Plan
 * (#842). Anders als der Nachtplan-Lauf geht er an den Server.
 */
const ECHTE_KETTE_STAND = JSON.stringify(echteKette)
const ECHTE_KETTE_START = '2026-09-14T13:12:00.574Z'

/** Eine Runner-Zeile, die kein Muster deutete — mit Markdown-Zeichen im Text. */
const UNGEDEUTET = 'Voellig unbekannte Runner-Zeile mit *Sternchen* und `Backticks`'

function karte(
  partial: Partial<CardByNumber> & { id: number; number: number; title: string },
): CardByNumber {
  return {
    description: null,
    type: 'CARD',
    dependencies: [],
    assignees: [],
    labels: [],
    parentId: null,
    shortcode: null,
    dueDate: null,
    archived: false,
    ideaStored: false,
    derivedFrom: null,
    boardId: 1,
    columnId: 2,
    ...partial,
  }
}

/**
 * Ein Vorhaben, wie `GET /api/cards/{id}` es liefert — die volle `Card`, nicht die `CardByNumber`
 * des Nummer-Lookups. `id` und `number` sind in den Tests bewusst verschieden: Der Abruf läuft über
 * die `parentId` (eine Karten-ID), angezeigt wird die projektweite Nummer.
 */
function vorhaben(partial: Partial<Card> & { id: number; number: number; title: string }): Card {
  return {
    boardId: 1,
    columnId: 2,
    description: null,
    excerpt: null,
    positionInColumn: 0,
    archived: false,
    ideaStored: false,
    movedToDoneAt: null,
    dependencies: [],
    type: 'EPIC',
    parentId: null,
    shortcode: null,
    assignees: [],
    dueDate: null,
    labels: [],
    derivedFrom: null,
    ...partial,
  }
}

/** Der Lauf eines Ergebnisstands; wirft, wenn er gar nicht deutbar ist — dann taugt das Fixture nicht. */
function gedeutet(ergebnisstand: string) {
  const ergebnis = parseNightRunErgebnisstand(ergebnisstand)
  if (!ergebnis.ok) throw new Error(`Fixture nicht deutbar: ${ergebnis.grund}`)
  return ergebnis.run
}

/**
 * Engt einen Modus auf das ein, was der Server kennt — `wieAufbewahrt`/`aufbewahrt` bilden nur
 * seine Antwort nach, und ein Nachtplan-Lauf kommt dort nie an (Plan #803, Entscheidung 8). Ein
 * Wurf statt eines Casts: Ein Test, der versehentlich einen Nachtplan-Ergebnisstand hier hineingibt,
 * soll das laut sagen, nicht still eine falsche Server-Antwort simulieren.
 */
function alsServerModus(mode: NightRunView['mode'] | 'NIGHTPLAN'): NightRunView['mode'] {
  if (mode === 'NIGHTPLAN') throw new Error('Der Server liefert nie NIGHTPLAN — falsches Fixture?')
  return mode
}

/**
 * Der Lauf eines Ergebnisstands so, wie der Server ihn nach dem Einliefern zurückgibt. Die Seite
 * lädt nach erfolgreichem Senden die Liste neu — ohne diese Nachbildung zeigte jeder Sendetest
 * danach eine leere Seite. Der Helfer bildet nur die Feldabbildung des Servers nach; **welchen**
 * Zustand ein Arbeitspaket trägt, prüfen die Tests weiterhin am sichtbaren Text.
 */
function wieAufbewahrt(ergebnisstand: string): NightRunView[] {
  const run = gedeutet(ergebnisstand)
  return [
    {
      id: 1,
      startedAt: run.startedAt,
      mode: alsServerModus(run.mode),
      durationMs: run.durationMs,
      processedCount: run.processedCount,
      skippedCount: run.skippedCount,
      unparsedCount: run.unparsedCount,
      // Der Ergebnisstand kennt keine ungedeuteten Zeilen (#773), also schickt der Server auch
      // keinen Auszug zurück. Ein aufbewahrter Lauf aus der Zeit der Protokolldeutung schon —
      // dafür steht {@link aufbewahrt} mit ausdrücklicher Vorgabe.
      unparsedSample: null,
      createdAt: '2026-09-02T06:00:00.000Z',
      items: run.items.map((item, position) => wieAufbewahrtesItem({ id: position + 1, ...item })),
    },
  ]
}

/** Ein Arbeitspaket in der Kurzform der Tests: Was nichts zur Sache tut, bleibt weg. */
type ItemVorgabe = Partial<NightRunItemView> &
  Pick<NightRunItemView, 'id' | 'cardNumber' | 'title' | 'state'>

/**
 * Ein Arbeitspaket so, wie der Server es schickt: Was der Lauf nicht wusste, steht als `null` im
 * JSON — nicht als fehlender Schlüssel (Issue #734). Die Helfer nehmen die Angaben in der
 * Frontend-Schreibweise (`undefined`) entgegen und übersetzen sie hier einmal, damit kein Test
 * versehentlich eine Antwortform nachbildet, die es nicht gibt.
 */
function wieAufbewahrtesItem(item: ItemVorgabe): NightRunItemView {
  return {
    id: item.id,
    cardNumber: item.cardNumber,
    title: item.title,
    state: item.state,
    errorClass: item.errorClass ?? null,
    durationMs: item.durationMs ?? null,
    commitHash: item.commitHash ?? null,
    excerpt: item.excerpt ?? null,
  }
}

/** Die Einlieferungs-Antwort zu einem Ergebnisstand, dessen Lauf neu ist. */
const alleNeu = (ergebnisstand: string): NightRunResult[] => [
  { startedAt: gedeutet(ergebnisstand).startedAt, created: true },
]

function aufbewahrt(
  partial: Omit<Partial<NightRunView>, 'items'> & {
    id: number
    startedAt: string
    items?: ItemVorgabe[]
  },
): NightRunView {
  const { items, ...rest } = partial
  return {
    mode: 'IMPLEMENTATION',
    durationMs: 10 * 60_000,
    processedCount: 1,
    skippedCount: 0,
    unparsedCount: 0,
    unparsedSample: null,
    createdAt: '2026-09-02T06:00:00.000Z',
    ...rest,
    items: (items ?? []).map(wieAufbewahrtesItem),
  }
}

interface Antworten {
  /** Je `GET /night-runs` eine Antwort; die letzte gilt für alle weiteren Aufrufe. */
  listen?: NightRunView[][]
  /** Statt einer Liste eine Fehlerantwort — der Ladepfad beim Öffnen der Seite. */
  listenFehler?: string
  /**
   * Ergebnis des `POST`; `fehler` erzeugt stattdessen eine 400-Antwort mit `detail`.
   *
   * `rohText` antwortet mit einem Body **ohne** RFC-9457-`detail` (etwa die HTML-Fehlerseite eines
   * Reverse-Proxys), `netzfehler` lässt `fetch` selbst scheitern — beides Fälle, in denen es keine
   * für den Nutzer formulierte Server-Meldung gibt.
   */
  submit?: { ergebnis?: NightRunResult[]; fehler?: string; rohText?: string; netzfehler?: boolean }
  /** Je `GET /night-runs/error-class-counts` eine Antwort; die letzte gilt für alle weiteren Aufrufe. */
  zaehler?: NightRunErrorClassCounts[]
  /** Statt der Häufigkeiten eine Fehlerantwort — der Fehlerpfad aus Issue #726. */
  zaehlerFehler?: string
  /** Karten je projektweiter Nummer; ein fehlender Eintrag antwortet mit 404. */
  karten?: Record<number, CardByNumber>
  /**
   * Hält den Nummer-Lookup an, bis dieses Promise auflöst — das Gegenstück zu
   * {@link Antworten.kartenNachIdVerzoegert} für `GET /cards/by-number/{n}`. Nur damit ist der
   * Verweis-Zustand „die Karte lädt noch" prüfbar, ohne ihn aus dem Zeitverhalten des Stubs zu
   * erraten (Issue #868, Punkt 8).
   */
  kartenVerzoegert?: Promise<void>
  /** Karten je Karten-ID (`GET /api/cards/{id}`) — die Vorhaben; fehlender Eintrag = 404. */
  kartenNachId?: Record<number, Card>
  /**
   * Hält den Einzelabruf einer Karte an, bis dieses Promise auflöst. So ist der Zustand „der
   * Vorhaben-Abruf läuft noch" prüfbar, den zwei nebenläufige `ladeKetten()`-Aufrufe erzeugen.
   */
  kartenNachIdVerzoegert?: Promise<void>
}

/** Alle Anfragen dieses Tests, in Reihenfolge — Grundlage der Sende- und Ladepfad-Prüfungen. */
let anfragen: Array<{ url: string; method: string; body: string }> = []

const antwortOk = (daten: unknown) => ({
  ok: true,
  status: 200,
  statusText: 'OK',
  text: () => Promise.resolve(JSON.stringify(daten)),
})

const antwortFehler = (detail: string, status = 400) => ({
  ok: false,
  status,
  statusText: 'Bad Request',
  text: () => Promise.resolve(JSON.stringify({ detail })),
})

/** Fehlerantwort ohne RFC-9457-Problem im Body — der Roh-Body gehört nicht vor den Nutzer. */
const antwortOhneDetail = (body: string, status = 502) => ({
  ok: false,
  status,
  statusText: 'Bad Gateway',
  text: () => Promise.resolve(body),
})

function stubFetch(antworten: Antworten) {
  let listenIndex = 0
  let zaehlerIndex = 0
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      anfragen.push({ url, method, body: String(init?.body ?? '') })

      if (url === '/api/projects') {
        return Promise.resolve(antwortOk([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }]))
      }
      if (url === '/api/projects/5/night-runs' && method === 'GET') {
        if (antworten.listenFehler !== undefined) {
          return Promise.resolve(antwortFehler(antworten.listenFehler, 403))
        }
        const listen = antworten.listen ?? [[]]
        const daten = listen[Math.min(listenIndex, listen.length - 1)]
        listenIndex += 1
        return Promise.resolve(antwortOk(daten))
      }
      if (url === '/api/projects/5/night-runs/error-class-counts') {
        if (antworten.zaehlerFehler !== undefined) {
          return Promise.resolve(antwortFehler(antworten.zaehlerFehler, 403))
        }
        const staende = antworten.zaehler ?? [{}]
        const daten = staende[Math.min(zaehlerIndex, staende.length - 1)]
        zaehlerIndex += 1
        return Promise.resolve(antwortOk(daten))
      }
      if (url === '/api/projects/5/night-runs' && method === 'POST') {
        if (antworten.submit?.netzfehler === true) {
          return Promise.reject(new TypeError('Failed to fetch'))
        }
        if (antworten.submit?.rohText !== undefined) {
          return Promise.resolve(antwortOhneDetail(antworten.submit.rohText))
        }
        return Promise.resolve(
          antworten.submit?.fehler === undefined
            ? antwortOk(antworten.submit?.ergebnis ?? [])
            : antwortFehler(antworten.submit.fehler),
        )
      }
      const nummer = /^\/api\/projects\/5\/cards\/by-number\/(\d+)$/.exec(url)
      if (nummer) {
        const gefunden = antworten.karten?.[Number(nummer[1])]
        const antwort = () =>
          gefunden === undefined ? antwortFehler('Karte nicht gefunden', 404) : antwortOk(gefunden)
        return antworten.kartenVerzoegert === undefined
          ? Promise.resolve(antwort())
          : antworten.kartenVerzoegert.then(antwort)
      }
      const kartenId = /^\/api\/cards\/(\d+)$/.exec(url)
      if (kartenId) {
        const gefunden = antworten.kartenNachId?.[Number(kartenId[1])]
        const antwort = () =>
          gefunden === undefined ? antwortFehler('Karte nicht gefunden', 404) : antwortOk(gefunden)
        return antworten.kartenNachIdVerzoegert === undefined
          ? Promise.resolve(antwort())
          : antworten.kartenNachIdVerzoegert.then(antwort)
      }
      return Promise.reject(new Error(`unerwartete Anfrage: ${method} ${url}`))
    }),
  )
}

function renderPage(antworten: Antworten = {}, pfad = '/projects/5/nachtlauf') {
  stubFetch(antworten)
  return render(
    <ThemeProvider theme={theme}>
      <SnackbarProvider>
        <MemoryRouter initialEntries={[pfad]}>
          <Routes>
            <Route path="/projects/:projectId/nachtlauf" element={<NightRunPage />} />
          </Routes>
        </MemoryRouter>
      </SnackbarProvider>
    </ThemeProvider>,
  )
}

/** Wählt eine Datei im versteckten Datei-Input aus — im Regelfall den Ergebnisstand eines Laufs. */
function protokollWaehlen(inhalt: string, name = 'night-run.json', typ = 'application/json') {
  const input = screen.getByLabelText('Protokolldatei auswählen')
  const datei = new File([inhalt], name, { type: typ })
  Object.defineProperty(input, 'files', { value: [datei], configurable: true })
  fireEvent.change(input)
}

/** Das Panel eines Laufs, identifiziert über seinen Startzeitpunkt. */
const lauf = (minute: number) => screen.getByTestId(`lauf-${startedAt(minute)}`)

/**
 * Die Kopfzeile eines Laufs. Bis #915 war sie eine Reihe eigener Chips; Ketten- und
 * Umsetzungs-Lauf tragen seither die Metazeile des Entwurfs, in der dieselben Angaben stehen
 * (AK 10) — nur nicht mehr in je eigenen Knoten. Die beiden Altbestand-Arten behalten ihre Chips,
 * deshalb der Rückfall auf den Aufklappknopf, der beide Formen umschließt.
 */
const laufKopfzeile = (panelEl: HTMLElement): HTMLElement =>
  within(panelEl).queryByTestId('nachtlauf-meta') ?? within(panelEl).getAllByRole('button')[0]

/** Die Metazeile des einzigen Laufs auf der Seite — dort steht seit #915 seine Kennzeichnung. */
const metazeile = () => screen.getByTestId('nachtlauf-meta')

/**
 * Die Kennzahlenreihe eines Laufs, in welcher Form auch immer: Ketten- und Umsetzungs-Lauf tragen
 * seit #915 die Reihe des Entwurfs, die beiden Altbestand-Arten weiter die bisherige Zeile.
 */
const KENNZAHLEN_REIHE = /^(nachtlauf|lauf)-kennzahlen$/

/**
 * Klappt den Lauf auf; erst dabei wird die Herkunftskette aufgelöst (Plan #718, A8).
 *
 * Der **oberste** Lauf steht seit #914 beim Öffnen der Seite bereits offen (E7) — für ihn ist
 * nichts zu tun. Der Helfer bleibt deshalb still, statt auf einen zugeklappten Knopf zu warten,
 * den es nicht mehr gibt.
 */
function aufklappen(minute: number) {
  panelAufklappen(lauf(minute))
}

/**
 * Dasselbe für ein Panel, das der Test bereits in der Hand hat.
 *
 * Genommen wird der **erste** Knopf des Panels: Die Kopfzeile des Laufs steht im DOM vor seinem
 * Inhalt. Ein offener Lauf trägt weitere zugeklappte Aufklappfelder in sich (etwa die Grund-Zeilen
 * der Aufschlüsselung) — eine Suche nach „irgendeinem zugeklappten Knopf" träfe die.
 */
function panelAufklappen(panelEl: HTMLElement) {
  const knopf = within(panelEl).getAllByRole('button')[0]
  if (knopf.getAttribute('aria-expanded') === 'false') {
    fireEvent.click(knopf)
  }
}

/** Der Wert des schreibgeschützten Übernahmetext-Feldes eines Arbeitspakets im Panel eines Laufs. */
const uebernahmetext = (panel: HTMLElement, cardNumber: number) =>
  (within(panel).getByLabelText(`Übernahmetext zu Karte #${cardNumber}`) as HTMLTextAreaElement)
    .value

const byNumberAufrufe = () => anfragen.filter((a) => a.url.includes('/cards/by-number/'))

/** Die Einzelabrufe einer Karte über ihre ID — in dieser Seite ausschließlich die Vorhaben. */
const vorhabenAufrufe = () => anfragen.filter((a) => /^\/api\/cards\/\d+$/.test(a.url))

const zaehlerAufrufe = () => anfragen.filter((a) => a.url.endsWith('/night-runs/error-class-counts'))

beforeEach(() => {
  anfragen = []
  vi.clearAllMocks()
})

afterEach(() => vi.unstubAllGlobals())

describe('NightRunPage — ungültige Projekt-ID', () => {
  it('meldet eine ungültige Projekt-ID und lädt nichts', async () => {
    renderPage({}, '/projects/abc/nachtlauf')

    expect(await screen.findByText('Ungültige Projekt-ID.')).toBeInTheDocument()
    expect(anfragen).toHaveLength(0)
  })

  it('behandelt einen fehlenden Projekt-Parameter als ungültig', () => {
    stubFetch({})
    render(
      <MemoryRouter initialEntries={['/nachtlauf']}>
        <Routes>
          <Route path="/nachtlauf" element={<NightRunPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Ungültige Projekt-ID.')).toBeInTheDocument()
  })
})

describe('NightRunPage — aufbewahrte Läufe beim Öffnen', () => {
  it('zeigt die aufbewahrten Läufe, neueste zuerst', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({ id: 1, startedAt: startedAt(0) }),
          aufbewahrt({ id: 2, startedAt: startedAt(30) }),
        ],
      ],
    })

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    const reihenfolge = screen.getAllByTestId(/^lauf-\d/).map((el) => el.dataset.testid)
    expect(reihenfolge).toEqual([`lauf-${startedAt(30)}`, `lauf-${startedAt(0)}`])
  })

  it('löst beim Öffnen der Seite nur die Kette des obersten Laufs auf (A8, E7)', async () => {
    // A8 hält die Anfragelawine von bis zu 30 aufbewahrten Läufen fern. Seit #914 steht der
    // oberste Lauf offen (AK 2 verlangt den ersten Vorgangsblock ohne Scrollen) — genau er, und
    // deshalb bleibt es bei einer Kette statt dreißig.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
          }),
          aufbewahrt({
            id: 2,
            startedAt: startedAt(30),
            items: [{ id: 21, cardNumber: 701, title: 'Paket B', state: 'GREEN' }],
          }),
        ],
      ],
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A' }),
        701: karte({ id: 2, number: 701, title: 'Paket B' }),
      },
    })

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    await waitFor(() => expect(byNumberAufrufe()).toHaveLength(1))
    expect(byNumberAufrufe()[0].url).toContain('/cards/by-number/701')
  })

  it('sagt es, solange keine Auswertung vorliegt', async () => {
    renderPage()

    expect(await screen.findByText('Noch keine Auswertung vorhanden.')).toBeInTheDocument()
  })

  it('meldet einen Fehler beim Laden der aufbewahrten Läufe', async () => {
    renderPage({ listenFehler: 'Nur der Owner darf die Auswertung sehen.' })

    expect(await screen.findByText('Nur der Owner darf die Auswertung sehen.')).toBeInTheDocument()
  })
})

describe('NightRunPage — Ergebnisstand hineingeben', () => {
  it('sendet die Auswertung und stellt den neuen Lauf nach oben', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(EIN_LAUF) },
      listen: [
        [aufbewahrt({ id: 9, startedAt: '2026-08-30T22:00:00.000Z' })],
        [aufbewahrt({ id: 9, startedAt: '2026-08-30T22:00:00.000Z' }), ...wieAufbewahrt(EIN_LAUF)],
      ],
    })
    await screen.findByTestId('lauf-2026-08-30T22:00:00.000Z')

    protokollWaehlen(EIN_LAUF)

    await waitFor(() => expect(screen.getAllByTestId(/^lauf-\d/)).toHaveLength(2))
    expect(screen.getAllByTestId(/^lauf-\d/)[0].dataset.testid).toBe(`lauf-${startedAt(0)}`)
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('neu angelegt')
  })

  it('schickt die Auswertung hinaus, nie den Ergebnisstand selbst', async () => {
    renderPage({ submit: { ergebnis: [{ startedAt: startedAt(0), created: true }] } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    await waitFor(() => expect(anfragen.some((a) => a.method === 'POST')).toBe(true))
    for (const anfrage of anfragen) {
      expect(anfrage.url).not.toContain(GEHEIM)
      expect(anfrage.body).not.toContain(GEHEIM)
    }
    const gesendet = anfragen.find((a) => a.method === 'POST')
    expect(gesendet?.body).toContain('"cardNumber":700')
  })

  it('stellt einen bereits bekannten Lauf vollständig dar und kennzeichnet ihn', async () => {
    renderPage({
      submit: { ergebnis: [{ startedAt: startedAt(0), created: false }] },
      listen: [[], wieAufbewahrt(EIN_LAUF)],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('lag schon vor')
    aufklappen(0)
    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
  })

  it('deutet den echten Ergebnisstand eines Laufs Paket für Paket', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_STAND, 'night-run-2026-09-07-085229.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_START}`)
    const panel = within(panelEl)
    panelAufklappen(panelEl)
    await panel.findByTestId('zustand-767')
    // Zwei erfolgreiche, aber ungeprüfte Sessions (gelb) und drei mit rotem Nachweis (rot) —
    // Zustand und Fehlerklasse stehen beide sichtbar auf der Seite.
    for (const nummer of [767, 770]) {
      expect(within(panel.getByTestId(`zustand-${nummer}`)).getByText('Erfolg, Prüfung rot')).toBeInTheDocument()
      expect(uebernahmetext(panelEl, nummer)).toContain('Fehlerklasse: Prüfungen nicht gelaufen')
    }
    for (const nummer of [768, 769, 771]) {
      expect(within(panel.getByTestId(`zustand-${nummer}`)).getByText('gescheitert')).toBeInTheDocument()
      expect(uebernahmetext(panelEl, nummer)).toContain('Fehlerklasse: Prüfungen rot')
    }
  })

  it('meldet eine Textdatei als nicht auswertbar, statt sie zu deuten', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(
      '[2026-09-01T22:00:00.000Z] Nacht-Runner startet (Modus Implementierung)',
      'nacht.log',
      'text/plain',
    )

    // Ohne geparstes Objekt gibt es weder ein Wort noch eine Herkunft — der Grundsatz steht
    // allein, die fehlende Herkunft wird benannt statt verschwiegen.
    expect(
      await screen.findByText('Nicht auswertbar — Herkunft nicht angegeben'),
    ).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-\d/)).toHaveLength(0)
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('meldet eine unbekannte Fassung, statt sie zu deuten', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(stand({ schemaFassung: 2 }))

    // „Fassung" bezeichnet hier den Aufbau des Protokolls; die Herkunft steht daneben als
    // „erzeugt von" und übernimmt das Wort nicht (AK 10 aus #842, E11 aus Plan #849).
    expect(
      await screen.findByText('Fassung nicht unterstützt — erzeugt von 1.47.0'),
    ).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-\d/)).toHaveLength(0)
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  // Der Prüf-Lauf selbst ist seit Issue #816 deutbar; nicht unterstützt bleibt eine
  // Kombination, die der Runner nie schreibt — hier eine unbekannte Prüfstufe.
  it('meldet eine unbekannte Lauf-Art/Stufe als nicht unterstützt', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(stand({ art: 'review', stufe: 'sonstwas' }))

    expect(
      await screen.findByText(
        'Lauf-Art oder Vokabular nicht unterstützt — nicht gedeutet: art=review/stufe=sonstwas'
          + ' — erzeugt von 1.47.0',
      ),
    ).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-\d/)).toHaveLength(0)
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  // AK 10 aus #842: Die Meldung nennt das unbekannte Wort selbst — hier den Ausgang einer
  // Einheit, nicht die Lauf-Art.
  it('nennt den nicht gedeuteten Ausgang einer Einheit in der Meldung', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(stand({ einheiten: [einheit({ ausgang: 'halbfertig' })] }))

    expect(
      await screen.findByText(
        'Lauf-Art oder Vokabular nicht unterstützt — nicht gedeutet: halbfertig'
          + ' — erzeugt von 1.47.0',
      ),
    ).toBeInTheDocument()
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('sagt „Herkunft nicht angegeben", wenn der Stand kein erzeugtVon führt', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    const ohneHerkunft = JSON.parse(stand({ art: 'review', stufe: 'sonstwas' })) as Record<
      string,
      unknown
    >
    delete ohneHerkunft.erzeugtVon
    protokollWaehlen(JSON.stringify(ohneHerkunft))

    expect(
      await screen.findByText(
        'Lauf-Art oder Vokabular nicht unterstützt — nicht gedeutet: art=review/stufe=sonstwas'
          + ' — Herkunft nicht angegeben',
      ),
    ).toBeInTheDocument()
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('zeigt einen noch nicht abgeschlossenen Lauf an, liefert ihn aber nicht ein', async () => {
    // Der Server legt je (Projekt, Startzeit) nur einmal an — ein unvollständiger Stand blockierte
    // den späteren vollständigen dauerhaft.
    renderPage({ karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(stand({ abschluss: null, einheiten: [einheit({ ausgang: 'erfolg', pruefung: GEPRUEFT })] }))

    expect(
      await screen.findByText('Lauf noch nicht abgeschlossen — nicht gespeichert'),
    ).toBeInTheDocument()
    expect(lauf(0)).toBeInTheDocument()
    aufklappen(0)
    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('meldet eine unlesbare Datei, ohne abzustürzen', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')
    class FailingReader {
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      result: string | null = null
      readAsText(): void {
        this.onerror?.()
      }
    }
    vi.stubGlobal('FileReader', FailingReader)

    protokollWaehlen(EIN_LAUF)

    expect(await screen.findByText('Die Datei konnte nicht gelesen werden.')).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-\d/)).toHaveLength(0)
  })

  it('öffnet nichts, wenn die Auswahl abgebrochen wurde', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')
    const input = screen.getByLabelText('Protokolldatei auswählen')
    Object.defineProperty(input, 'files', { value: null, configurable: true })

    fireEvent.change(input)

    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('setzt den Datei-Input zurück, damit derselbe Stand erneut gewählt werden kann', async () => {
    renderPage({ submit: { ergebnis: [{ startedAt: startedAt(0), created: true }] } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect((screen.getByLabelText('Protokolldatei auswählen') as HTMLInputElement).value).toBe('')
  })

  it('lässt die Auswertung sichtbar, wenn das Senden scheitert, und nennt den Grund', async () => {
    renderPage({
      submit: { fehler: 'Auszug zu lang' },
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect(await screen.findByText('Auszug zu lang')).toBeInTheDocument()
    expect(lauf(0)).toBeInTheDocument()
    // Der Zwischenspeicher wird **vor** dem Senden gefüllt (#775): Auch ein Lauf, dessen
    // Einlieferung scheitert, ist aus dem Ergebnisstand entstanden und sagt das.
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('Ergebnisstand')
    aufklappen(0)
    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
  })

  it('zeigt statt eines Roh-Bodys ohne Server-Meldung den eigenen Text', async () => {
    // 502 vom Reverse-Proxy: Der Body ist eine HTML-Seite, keine für den Nutzer formulierte
    // Meldung — sie gehört nicht in den Toast (Issue #812).
    renderPage({ submit: { rohText: '<html><body>502 Bad Gateway</body></html>' } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect(await screen.findByText('Einliefern fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.queryByText(/502 Bad Gateway/)).not.toBeInTheDocument()
  })

  it('nennt bei einem Netzwerkabbruch den eigenen Text', async () => {
    renderPage({ submit: { netzfehler: true } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect(await screen.findByText('Einliefern fehlgeschlagen.')).toBeInTheDocument()
  })

  /**
   * Review-Fund (Code-Review Schritt 7, #807-#812-Batch): Einliefern und das anschließende
   * Nachladen der Liste (plus Zähler) standen im selben try/catch. Scheitert nur das Nachladen,
   * ist die Einlieferung trotzdem durch — „Einliefern fehlgeschlagen" wäre dann falsch.
   */
  it('meldet ein gescheitertes Nachladen getrennt vom Einliefern, wenn die Einlieferung erfolgreich war', async () => {
    let listRufe = 0
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init?: RequestInit) => {
        const method = init?.method ?? 'GET'
        anfragen.push({ url, method, body: String(init?.body ?? '') })
        if (url === '/api/projects') {
          return Promise.resolve(antwortOk([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }]))
        }
        if (url === '/api/projects/5/night-runs' && method === 'GET') {
          listRufe += 1
          // Erster Aufruf (Erstladen) klappt, der zweite (Nachladen nach dem Einliefern) scheitert.
          if (listRufe === 1) return Promise.resolve(antwortOk([]))
          return Promise.reject(new TypeError('Failed to fetch'))
        }
        if (url === '/api/projects/5/night-runs' && method === 'POST') {
          return Promise.resolve(antwortOk(alleNeu(EIN_LAUF)))
        }
        return Promise.reject(new Error(`unerwartete Anfrage: ${method} ${url}`))
      }),
    )

    render(
      <ThemeProvider theme={theme}>
        <SnackbarProvider>
          <MemoryRouter initialEntries={['/projects/5/nachtlauf']}>
            <Routes>
              <Route path="/projects/:projectId/nachtlauf" element={<NightRunPage />} />
            </Routes>
          </MemoryRouter>
        </SnackbarProvider>
      </ThemeProvider>,
    )
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    expect(
      await screen.findByText('Eingeliefert, Liste konnte nicht aktualisiert werden.'),
    ).toBeInTheDocument()
    // Der lokal aus dem Ergebnisstand erzeugte Lauf-Eintrag bleibt trotzdem sichtbar.
    expect(lauf(0)).toBeInTheDocument()
  })
})

describe('NightRunPage — Nachtplan-Lauf (#806)', () => {
  it('zeigt den echten Nachtplan-Lauf mit eigenem Chip und den erwarteten Zuständen, liefert ihn aber nicht ein', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_NACHTPLAN_STAND, 'night-run-2026-09-09-141506.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_NACHTPLAN_START}`)
    expect(within(panelEl).getByText('Nachtplan-Lauf')).toBeInTheDocument()
    expect(within(panelEl).getByText('2 bearbeitet, 33 übergangen')).toBeInTheDocument()

    panelAufklappen(panelEl)
    const panel = within(panelEl)
    await panel.findByTestId('zustand-479')
    for (const nummer of [479, 549]) {
      expect(within(panel.getByTestId(`zustand-${nummer}`)).getByText('gescheitert')).toBeInTheDocument()
    }
    expect(panel.getAllByText("Grund: kein Label 'kit:nightplan'")).toHaveLength(33)

    // Kein Einliefern: weder ein POST noch die Kennzeichnung "neu angelegt"/"lag schon vor".
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
    expect(within(panelEl).queryByText('neu angelegt')).not.toBeInTheDocument()
    expect(within(panelEl).queryByText('lag schon vor')).not.toBeInTheDocument()
  })

  it('zeigt einen unvollständigen Nachtplan-Lauf an, liefert ihn aber ebenfalls nicht ein', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(
      stand({
        art: 'erzeugung',
        stufe: 'plan',
        abschluss: null,
        einheiten: [einheit({ ausgang: 'uebersprungen', grund: "kein Label 'kit:nightplan'" })],
      }),
    )

    expect(
      await screen.findByText('Lauf noch nicht abgeschlossen — nicht gespeichert'),
    ).toBeInTheDocument()
    expect(lauf(0)).toBeInTheDocument()
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })
})

describe('NightRunPage — Ketten-Lauf (#854)', () => {
  it('zeigt den echten Ketten-Lauf mit eigenem Chip und liefert ihn ein', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    expect(laufKopfzeile(panelEl)).toHaveTextContent('Ketten-Lauf')
    expect(laufKopfzeile(panelEl)).toHaveTextContent('3 bearbeitet, 0 übergangen')

    // Anders als der Nachtplan-Lauf geht die Kette an den Server (AK 8 aus #842):
    // `istEinlieferbar` schließt weiterhin allein `NIGHTPLAN` aus.
    await waitFor(() => expect(anfragen.some((a) => a.method === 'POST')).toBe(true))
    expect(anfragen.find((a) => a.method === 'POST')?.body).toContain('"mode":"CHAIN"')
    expect(laufKopfzeile(panelEl)).toHaveTextContent('neu angelegt')
  })
})

describe('NightRunPage — Herkunftskette am Ketten-Vorgang (#858)', () => {
  /**
   * Die Wurzelkarte eines Ketten-Vorgangs **ist** die fachliche Anforderung, und das erzeugte
   * Plan-Dokument hängt unterhalb von ihr. Aufwärts gedeutet stünde an #791 und #814 zweimal
   * „ohne" und an #842 zweimal „noch nicht erreicht" — obwohl #842 den Plan #849 gerade erzeugt
   * hat. Beides sind geratene Aussagen (zweites Nicht-Ziel aus #842); die Karten hier tragen
   * genau die Vorbedingungen dafür, damit der Test ohne die Modus-Weitergabe rot wird.
   */
  const KETTEN_KARTEN = {
    791: karte({ id: 1, number: 791, title: '[Fachlich] Zugriff und Konten' }),
    814: karte({ id: 2, number: 814, title: '[Fachlich] Vorhaben ein- und ausblenden' }),
    842: karte({ id: 3, number: 842, title: '[Fachlich] Leitstand mehrstufig', derivedFrom: 700 }),
    700: karte({ id: 4, number: 700, title: 'Leitstand' }),
  }

  it('lässt an jedem Vorgang des echten Ketten-Laufs beide Stufenzeilen weg', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
      karten: KETTEN_KARTEN,
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    await waitFor(() => expect(laufKopfzeile(panelEl)).toHaveTextContent('neu angelegt'))
    panelAufklappen(panelEl)

    // Erst wenn die Wurzelkarten aufgelöst sind, steht der Block mit Stufen- und Vorhabenzeile
    // überhaupt da — sonst wäre die Erwartung darunter schon vor dem Laden erfüllt.
    expect(await within(panelEl).findAllByText('Vorhaben: ohne')).toHaveLength(3)
    expect(within(panelEl).queryAllByText(/^Fachliche Anforderung: /)).toHaveLength(0)
    expect(within(panelEl).queryAllByText(/^Plan: /)).toHaveLength(0)
  })

  it('lässt beide Stufenzeilen an einem Lauf bisheriger Art unverändert stehen (AK 9 aus #842)', async () => {
    // Der Bestandsfall: Nur der Ketten-Modus verliert die Zeilen. Dieser Test sichert die
    // Nicht-Änderung — ein Umsetzung, die die Zeilen überall wegließe, fiele hier auf.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
          }),
        ],
      ],
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(
      await within(lauf(0)).findByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    ).toBeInTheDocument()
    expect(
      within(lauf(0)).getByRole('button', {
        name: 'Fachliche Anforderung #715 [Fachlich] Nachtlauf',
      }),
    ).toBeInTheDocument()
  })
})

describe('NightRunPage — Zeitbudget als eigener Grund (#856)', () => {
  /**
   * Der Vorgang #842 der echten Fixture endete am Zeitbudget der Prüf-Stufe und hat trotzdem einen
   * Plan hinterlassen — gelb mit `TIME_BUDGET_EXCEEDED`. Er ist der Fall, für den AK 3 und AK 5 aus
   * #842 geschrieben sind: Die Ampel allein sagte bisher „Erfolg, Prüfung rot", und die Prüfung war
   * nicht rot, sie kam gar nicht dran.
   */
  /**
   * Der Lauf wird **vom Server geladen**, nicht als Ergebnisstand hineingegeben: Seit Issue #869
   * kürzt die Seite die Vorgangszeile an jedem Lauf, zu dem ein Sitzungsstand vorliegt — und beide
   * Aussagen hier hängen an dieser Zeile. Ohne Sitzungsstand steht sie vollständig da, und das ist
   * genau der Fall, für den AK 3 und AK 5 aus #842 geschrieben sind. `gespeichert` ist an einem
   * geladenen Lauf `true`, die Häufigkeitszeile erscheint also weiterhin.
   */
  async function ketteAufgeklappt(zaehler: NightRunErrorClassCounts) {
    renderPage({ listen: [wieAufbewahrt(ECHTE_KETTE_STAND)], zaehler: [zaehler] })

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('zustand-842')
    return panelEl
  }

  it('nennt neben der Ampel des Zeitbudget-Vorgangs das Zeitbudget, nicht „Erfolg, Prüfung rot" (AK 3)', async () => {
    const panelEl = await ketteAufgeklappt({ TIME_BUDGET_EXCEEDED: 1 })
    const panel = within(panelEl)

    expect(
      within(panel.getByTestId('zustand-842')).getByText('Am Zeitbudget beendet, Ergebnis liegt vor'),
    ).toBeInTheDocument()
    expect(panel.queryByText('Erfolg, Prüfung rot')).not.toBeInTheDocument()
  })

  it('führt das Zeitbudget in Häufigkeitszeile und Übernahmetext als eigenen Grund (AK 5)', async () => {
    const panelEl = await ketteAufgeklappt({ TIME_BUDGET_EXCEEDED: 1, CHECKS_NOT_STARTED: 3 })

    const zeile = await within(panelEl).findByTestId('haeufigkeit-842')
    expect(zeile).toHaveTextContent('Zeitbudget erschöpft: zum ersten Mal')
    expect(zeile).not.toHaveTextContent('Prüfungen nicht gelaufen')

    const text = uebernahmetext(panelEl, 842)
    expect(text).toContain('Zustand: Am Zeitbudget beendet, Ergebnis liegt vor')
    expect(text).toContain('Fehlerklasse: Zeitbudget erschöpft')
    expect(text).not.toContain('Prüfungen nicht gelaufen')
  })
})

/**
 * Ein Ketten-Ergebnisstand ohne jede Kopfangabe — kein Modell, kein Label, kein Budget, keine
 * Kostensumme, ein Vorgang ohne Arbeitsschritte. Er ist die Gegenprobe zur echten Fixture: Jede
 * Angabe der Übersicht muss auch dann eine Auskunft ergeben, wenn der Stand sie nicht führt.
 */
const kettenStand = (felder: Record<string, unknown> = {}): string =>
  JSON.stringify({
    schemaFassung: 1,
    erzeugtVon: '1.53.0',
    start: startedAt(0),
    art: 'kette',
    stufe: null,
    einheiten: [{ id: '900', titel: 'Anforderung ohne Angaben', ausgang: 'fertig' }],
    abschluss: 'regulaer',
    ...felder,
  })

/**
 * Liest einen Ketten-Stand ein, wartet auf `warten` und klappt den Lauf auf.
 *
 * `extra` legt weitere Stub-Antworten darüber — seit Issue #868 braucht die Übersicht auch den
 * Kartenkatalog, und die Verweise auf die entstandenen Karten hängen an ihm.
 */
async function uebersichtZu(
  ergebnisstand: string,
  warten: string,
  start = startedAt(0),
  extra: Partial<Antworten> = {},
) {
  renderPage({
    submit: { ergebnis: alleNeu(ergebnisstand) },
    listen: [[], wieAufbewahrt(ergebnisstand)],
    ...extra,
  })
  await screen.findByText('Noch keine Auswertung vorhanden.')

  protokollWaehlen(ergebnisstand)

  const panelEl = await screen.findByTestId(`lauf-${start}`)
  // Zwei Orte, ein Warteschritt: Der Hinweis auf einen unabgeschlossenen Lauf steht als Meldung
  // über der Liste, die Kennzeichnung „neu angelegt" seit #915 in der Metazeile des Kopfes.
  await waitFor(() =>
    expect(screen.queryByText(warten) ?? laufKopfzeile(panelEl)).toHaveTextContent(warten),
  )
  panelAufklappen(panelEl)
  return within(panelEl).findByTestId('ketten-uebersicht')
}

/** Die Übersicht des echten Ketten-Laufs, nach dem Einliefern und dem Neuladen der Liste. */
const echteUebersicht = () => uebersichtZu(ECHTE_KETTE_STAND, 'neu angelegt', ECHTE_KETTE_START)

describe('NightRunPage — Ketten-Übersicht (#866)', () => {
  /**
   * Der Kopf nennt Datum und Uhrzeit des Starts. Beide werden hier mit denselben `Intl`-Angaben
   * erwartet, mit denen die Seite sie schreibt: Die Zeitzone des Prüfrechners steht nicht fest,
   * und ein fest eingetragener Text wäre anderswo rot, ohne dass etwas kaputt wäre.
   */
  const kopfzeile = (iso: string) =>
    `Nacht vom ${new Date(iso).toLocaleDateString('de-DE', { day: 'numeric', month: 'long' })}`

  it('zeigt die Übersicht auch nach dem Einliefern und dem Neuladen der Liste (Gegenprobe zu E1)', async () => {
    // Die abweichende Stückzahl belegt, dass der sichtbare Lauf der **neu geladene** ist. Erst
    // danach sagt die Übersicht etwas darüber aus, dass sie den Austausch des Lauf-Arrays
    // überlebt — an `gespeichert` gebunden wäre sie hier bereits verschwunden.
    const vomServer = wieAufbewahrt(ECHTE_KETTE_STAND).map((view) => ({
      ...view,
      processedCount: 42,
    }))
    renderPage({ submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) }, listen: [[], vomServer] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    await waitFor(() => expect(laufKopfzeile(panelEl)).toHaveTextContent('42 bearbeitet, 0 übergangen'))
    panelAufklappen(panelEl)

    expect(await within(panelEl).findByTestId('ketten-uebersicht')).toBeInTheDocument()
  })

  it('zeigt sie an einem vom Server geladenen Ketten-Lauf ohne Sitzungsstand nicht', async () => {
    renderPage({ listen: [wieAufbewahrt(ECHTE_KETTE_STAND)] })
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)

    panelAufklappen(panelEl)

    expect(await within(panelEl).findByTestId('zustand-791')).toBeInTheDocument()
    expect(within(panelEl).queryByTestId('ketten-uebersicht')).not.toBeInTheDocument()
  })

  it('trägt im Kopf und in der Kennzahlenreihe die Werte des Protokolls (AK 1 bis 3)', async () => {
    // Kopf und Kennzahlen stehen seit #915 über der Übersicht statt in ihr — dieselben Werte,
    // ein anderer Ort.
    await echteUebersicht()

    expect(screen.getByTestId('nachtlauf-ueberschrift')).toHaveTextContent(kopfzeile(ECHTE_KETTE_START))
    expect(metazeile()).toHaveTextContent('claude-opus-5 · Label kit:night · regulär beendet')

    // Zwei der drei Vorgänge sind grün; entstanden sind #844 bis #849, also sechs Karten; die
    // Summe der zehn Stufenzeiten ergibt 4 148 331 ms; die Kostensumme steht im Kopf des Stands.
    const reihe = within(screen.getByTestId('nachtlauf-kennzahlen'))
    expect(reihe.getByText('2 von 3')).toBeInTheDocument()
    expect(reihe.getByText('6')).toBeInTheDocument()
    expect(reihe.getByText('1 Std 9 Min')).toBeInTheDocument()
    expect(reihe.getByText('25,98 $')).toBeInTheDocument()
    expect(reihe.getByText('ein Arbeitsschritt ohne Kostenmeldung')).toBeInTheDocument()
  })

  it('nennt je Vorgang Kosten, Züge und fehlende Kostenmeldungen (AK 11)', async () => {
    const uebersicht = within(await echteUebersicht())

    expect(uebersicht.getByTestId('uebersicht-vorgang-791')).toHaveTextContent('11,52 $ · 89 Züge')
    expect(uebersicht.getByTestId('uebersicht-vorgang-814')).toHaveTextContent('10,37 $ · 87 Züge')
    expect(uebersicht.getByTestId('uebersicht-vorgang-842')).toHaveTextContent(
      '4,09 $ · 37 Züge · ein Arbeitsschritt ohne Kostenmeldung',
    )
  })

  it('führt in der Fußzeile Zeitvorgaben, Kostenbudget und die höchsten Kosten (AK 12)', async () => {
    const fuss = within(within(await echteUebersicht()).getByTestId('uebersicht-fuss'))

    expect(
      fuss.getByText('Plan 20 · Prüfung 15 · Pakete 15 · Abdeckung 10 min'),
    ).toBeInTheDocument()
    expect(fuss.getByText('50,00 $')).toBeInTheDocument()
    expect(fuss.getByText('11,52 $')).toBeInTheDocument()
  })

  it('nennt einen Lauf ohne Abschlussangabe „noch nicht abgeschlossen" und keinen Rohwert', async () => {
    const uebersicht = within(
      await uebersichtZu(
        kettenStand({ abschluss: null }),
        'Lauf noch nicht abgeschlossen — nicht gespeichert',
      ),
    )

    expect(metazeile()).toHaveTextContent('noch nicht abgeschlossen')
    expect(uebersicht.queryByText(/null/)).not.toBeInTheDocument()
    // Ohne Vorgaben, Budget und Kostenmeldung steht überall die ausdrückliche Auskunft statt
    // einer Zahl — eine „0 $" wäre eine Behauptung über etwas, das der Stand nicht führt.
    expect(
      within(uebersicht.getByTestId('uebersicht-fuss')).getAllByText('nicht angegeben'),
    ).toHaveLength(3)
    expect(uebersicht.getByText('Kosten nicht gemeldet')).toBeInTheDocument()
  })

  it('nennt einen hart gestoppten Lauf vorzeitig beendet und zählt fehlende Kostenmeldungen', async () => {
    // Abschluss, Kostensumme und der Vermerk stehen seit #915 alle drei im Kopf über der
    // Übersicht; sie selbst wird hier nur noch abgewartet.
    await uebersichtZu(
      kettenStand({ abschluss: 'harterStopp', kostenSumme: 3.5, kostenUnbekannt: 2 }),
      'neu angelegt',
    )

    expect(metazeile()).toHaveTextContent('vorzeitig beendet (harter Stopp)')
    expect(screen.getByTestId('nachtlauf-kennzahlen')).toHaveTextContent('3,50 $')
    expect(screen.getByTestId('nachtlauf-kennzahlen')).toHaveTextContent('2 Arbeitsschritte ohne Kostenmeldung')
  })

  it('gibt eine unbekannte Abschlussangabe nicht im Rohwert aus', async () => {
    const uebersicht = within(
      await uebersichtZu(kettenStand({ abschluss: 'sonntagsruhe' }), 'neu angelegt'),
    )

    expect(metazeile()).toHaveTextContent('Abschluss nicht deutbar')
    expect(uebersicht.queryByText(/sonntagsruhe/)).not.toBeInTheDocument()
  })

  it('zeigt an einem Lauf anderer Art unverändert die Zeilendarstellung (AK 16)', async () => {
    renderPage({ submit: { ergebnis: alleNeu(EIN_LAUF) }, listen: [[], wieAufbewahrt(EIN_LAUF)] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    const panelEl = await screen.findByTestId(`lauf-${startedAt(0)}`)
    await waitFor(() => expect(laufKopfzeile(panelEl)).toHaveTextContent('neu angelegt'))
    panelAufklappen(panelEl)

    expect(await within(panelEl).findByTestId('zustand-700')).toBeInTheDocument()
    expect(within(panelEl).queryByTestId('ketten-uebersicht')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Stufenband je Vorgang (#867)', () => {
  /** Die vier Arbeitsschritte in der Reihenfolge, in der die Kette sie läuft. */
  const SCHRITTE = ['plan', 'review', 'pakete', 'abdeckung'] as const

  const abschnitt = (uebersicht: HTMLElement, cardNumber: number, schritt: string) =>
    within(uebersicht).getByTestId(`stufe-${cardNumber}-${schritt}`)

  /** Ein Datenfeld aller vier Abschnitte eines Vorgangs, in der Reihenfolge der Kette. */
  const bandwerte = (
    uebersicht: HTMLElement,
    cardNumber: number,
    feld: 'anteil' | 'fuellung' | 'erreicht',
  ) => SCHRITTE.map((schritt) => abschnitt(uebersicht, cardNumber, schritt).dataset[feld])

  /** Der Grund eines Abbruchs, der **nicht** am Zeitbudget lag. */
  const GRUND_KOSTEN = 'Kostenbudget erschöpft: 52,10 $ über 50,00 $'

  it('zeichnet das Band der Einheit 791 in den Verhältnissen der Zeitvorgaben (Punkt 8)', async () => {
    const uebersicht = await echteUebersicht()

    // Geprüft an der Beschriftung und an den Datenfeldern, nicht an berechneten Stilwerten: In
    // jsdom rechnet kein Browser ein Layout aus, ein Test auf `flex`-Anteile prüfte dort die
    // Zeichenkette, die man selbst geschrieben hat.
    expect(within(uebersicht).getByTestId('stufenband-791')).toHaveAttribute(
      'aria-label',
      'Stufenband: Plan 7,8 / 20 min · Prüfung 11,8 / 15 min · Pakete 3,7 / 15 min · Abdeckung 2,2 / 10 min',
    )
    expect(bandwerte(uebersicht, 791, 'anteil')).toEqual(['20', '15', '15', '10'])
    expect(bandwerte(uebersicht, 791, 'fuellung')).toEqual(['39.1', '78.6', '24.6', '22.4'])
    expect(bandwerte(uebersicht, 791, 'erreicht')).toEqual(['ja', 'ja', 'ja', 'ja'])
  })

  it('kennzeichnet Pakete und Abdeckung der Einheit 842 als nicht erreicht (Punkt 9)', async () => {
    const uebersicht = await echteUebersicht()

    expect(bandwerte(uebersicht, 842, 'erreicht')).toEqual(['ja', 'ja', 'nein', 'nein'])
    expect(abschnitt(uebersicht, 842, 'pakete')).toHaveTextContent('nicht erreicht')
    expect(abschnitt(uebersicht, 842, 'abdeckung')).toHaveTextContent('nicht erreicht')

    // Der Plan-Schritt lief mit 36,6 % seiner Vorgabe — wenig, aber gelaufen. Er trägt den Vermerk
    // nicht, und daran ist „nicht erreicht" von „wenig verbraucht" unterscheidbar.
    const plan = abschnitt(uebersicht, 842, 'plan')
    expect(plan).not.toHaveTextContent('nicht erreicht')
    expect(plan.dataset.fuellung).toBe('36.6')
  })

  it('kappt die übergelaufene Prüfstufe der Einheit 842 bei voller Länge (Punkt 10)', async () => {
    const uebersicht = await echteUebersicht()
    const pruefung = abschnitt(uebersicht, 842, 'review')

    // 900 484 ms gegen eine Vorgabe von 900 000 ms: Ohne Kappung stünde hier 100.1.
    expect(pruefung.dataset.fuellung).toBe('100')
    expect(pruefung).toHaveTextContent('15,0 / 15 min')
  })

  it('kappt auch einen Schritt mit doppelter Vorgabe (Punkt 11)', async () => {
    const uebersicht = await uebersichtZu(
      kettenStand({
        budget: { planMin: 10 },
        einheiten: [
          {
            id: '900',
            titel: 'Doppelte Vorgabe',
            ausgang: 'fertig',
            stufen: { plan: { id: '901', dauerMs: 20 * 60_000 } },
          },
        ],
      }),
      'neu angelegt',
    )
    const plan = abschnitt(uebersicht, 900, 'plan')

    expect(plan.dataset.fuellung).toBe('100')
    expect(plan).toHaveTextContent('20,0 / 10 min')
  })

  it('nennt über dem Band der Einheit 842 den Zeitbudget-Grund samt Dokumentnummer (Punkt 12)', async () => {
    const uebersicht = await echteUebersicht()

    const satz = within(uebersicht).getByTestId('abbruch-842')
    expect(satz).toHaveTextContent(
      'Zeitbudget review: die Session wurde nach 15.0 min am Limit beendet',
    )
    expect(satz).toHaveTextContent('#849')
    expect(abschnitt(uebersicht, 842, 'review')).toHaveTextContent('am Zeitbudget beendet')

    // Ein regulär durchgelaufener Vorgang trägt weder Satz noch Vermerk.
    expect(within(uebersicht).queryByTestId('abbruch-791')).not.toBeInTheDocument()
    expect(abschnitt(uebersicht, 791, 'abdeckung')).not.toHaveTextContent('beendet')
  })

  it('trägt an einem anders begründeten Abbruch „hier abgebrochen" (Punkt 13)', async () => {
    const uebersicht = await uebersichtZu(
      kettenStand({
        budget: { planMin: 20, reviewMin: 15, paketeMin: 15, abdeckungMin: 10 },
        einheiten: [
          {
            id: '900',
            titel: 'Abbruch ohne Zeitbezug',
            ausgang: 'abgebrochen',
            grund: GRUND_KOSTEN,
            // `id: null` heißt: Die Stufe hat begonnen, ein Plan entstand aber nicht.
            stufen: { plan: { id: null, dauerMs: 5 * 60_000 } },
          },
        ],
      }),
      'neu angelegt',
    )

    expect(abschnitt(uebersicht, 900, 'plan')).toHaveTextContent('hier abgebrochen')
    expect(uebersicht).not.toHaveTextContent('am Zeitbudget beendet')

    const satz = within(uebersicht).getByTestId('abbruch-900')
    expect(satz).toHaveTextContent(GRUND_KOSTEN)
    expect(satz).toHaveTextContent('keine Karten')
  })

  it('sagt an einem Schritt ohne Zeitvorgabe ausdrücklich „ohne Vorgabe"', async () => {
    const uebersicht = await uebersichtZu(
      kettenStand({
        einheiten: [
          {
            id: '900',
            titel: 'Ohne Budget',
            ausgang: 'fertig',
            stufen: { plan: { id: '901' } },
          },
        ],
      }),
      'neu angelegt',
    )
    const plan = abschnitt(uebersicht, 900, 'plan')

    // Ohne Vorgabe gibt es kein Verhältnis: Der Schritt bekommt denselben Anteil wie die übrigen,
    // und die Füllung bleibt leer statt eine Quote zu behaupten, die niemand gemeldet hat.
    expect(plan).toHaveTextContent('0,0 min · ohne Vorgabe')
    expect(plan.dataset.anteil).toBe('1')
    expect(plan.dataset.fuellung).toBe('0')
  })
})

describe('NightRunPage — Entstandene Karten je Vorgang (#868)', () => {
  /**
   * Die Karten der echten Nacht: die drei Wurzeln und die sechs, die aus ihnen entstanden sind.
   * Sie hängen **unterhalb** der Wurzel — ohne die Erweiterung von `ladeKetten` fragt die Seite
   * keine von ihnen ab, und jeder Verweis bliebe ohne Karte.
   */
  const ENTSTANDENE = {
    791: karte({ id: 1, number: 791, title: '[Fachlich] Zugriff und Konten' }),
    814: karte({ id: 2, number: 814, title: '[Fachlich] Vorhaben ein- und ausblenden' }),
    842: karte({ id: 3, number: 842, title: '[Fachlich] Leitstand mehrstufig' }),
    844: karte({ id: 11, number: 844, title: '[Plan] Sicherheits-Bauform' }),
    845: karte({ id: 12, number: 845, title: 'Bauform binden' }),
    846: karte({ id: 13, number: 846, title: '[Plan] Vorhaben-Liste' }),
    847: karte({ id: 14, number: 847, title: 'Liste als Komponente' }),
    848: karte({ id: 15, number: 848, title: 'Liste statt Kachelraster' }),
    849: karte({ id: 16, number: 849, title: '[Plan] Leitstand mehrstufig' }),
  }

  const echteUebersichtMitKarten = () =>
    uebersichtZu(ECHTE_KETTE_STAND, 'neu angelegt', ECHTE_KETTE_START, { karten: ENTSTANDENE })

  /** Ein Ketten-Stand mit genau einem Vorgang, dessen Plan-Stufe das Dokument `id` hinterließ. */
  const mitPlanDokument = (id: string) =>
    kettenStand({
      einheiten: [
        { id: '900', titel: 'Vorgang mit Plan', ausgang: 'fertig', stufen: { plan: { id } } },
      ],
    })

  /**
   * Das Element eines Vorgangs — nicht dessen `within`-Objekt. Der Aufrufer legt `within` selbst
   * um das Ergebnis: `testing-library/prefer-screen-queries` erkennt eine Teilbaum-Suche nur, wenn
   * `within(...)` an der Abfragestelle steht, und hielte eine Zwischenvariable sonst für ein
   * destrukturiertes `render`-Ergebnis.
   */
  const vorgangIn = (uebersicht: HTMLElement, cardNumber: number) =>
    within(uebersicht).getByTestId(`uebersicht-vorgang-${cardNumber}`)

  /**
   * Die Zeile eines Arbeitsschritts mit den dort entstandenen Karten. Über die Test-ID und nicht
   * über den Text: Die Zeile setzt sich aus Beschriftung und Verweisen zusammen, und `getByText`
   * sucht je Element nur dessen eigene Textknoten.
   */
  const dokumentzeile = (uebersicht: HTMLElement, cardNumber: number, schritt: string) =>
    within(uebersicht).getByTestId(`dokumente-${cardNumber}-${schritt}`)

  it('führt an der Einheit 791 die entstandenen Karten und öffnet die angeklickte (Punkt 5)', async () => {
    const uebersicht = await echteUebersichtMitKarten()
    const vorgang = vorgangIn(uebersicht, 791)

    expect(
      await within(vorgang).findByRole('button', { name: 'Plan #844 [Plan] Sicherheits-Bauform' }),
    ).toBeInTheDocument()
    expect(
      within(vorgang).getByRole('button', { name: 'Pakete #845 Bauform binden' }),
    ).toBeInTheDocument()

    // Zwei Pakete an einem Vorgang stehen in **einer** Zeile, durch Komma getrennt.
    expect(dokumentzeile(uebersicht, 814, 'pakete')).toHaveTextContent('Pakete #847, #848')

    fireEvent.click(within(vorgang).getByRole('button', { name: /^Plan #844/ }))

    expect(screen.getByTestId('karten-detail')).toHaveTextContent('Karte 844')
  })

  it('nennt an der Einheit 842 den Plan #849 und ausdrücklich „keine Pakete" (Punkt 6)', async () => {
    const vorgang = vorgangIn(await echteUebersichtMitKarten(), 842)

    expect(
      await within(vorgang).findByRole('button', { name: 'Plan #849 [Plan] Leitstand mehrstufig' }),
    ).toBeInTheDocument()
    // Die Pakete-Stufe hat der Vorgang nie erreicht — das steht in Worten da, nicht als Leerstelle.
    expect(within(vorgang).getByText('keine Pakete')).toBeInTheDocument()
  })

  it('nennt einen Vorgang ohne jede Stufe „kein Plan" und „keine Pakete" (Punkt 4)', async () => {
    const vorgang = vorgangIn(
      await uebersichtZu(kettenStand(), 'neu angelegt', startedAt(0), { karten: {} }),
      900,
    )

    expect(within(vorgang).getByText('kein Plan')).toBeInTheDocument()
    expect(within(vorgang).getByText('keine Pakete')).toBeInTheDocument()
  })

  it('kennzeichnet eine nicht auflösbare Dokumentnummer als nicht mehr vorhanden (Punkt 7)', async () => {
    // Kein Eintrag im Katalog-Stub: Der Nummer-Lookup antwortet mit 404, und das ist ein
    // Ergebnis — nicht mehr vorhanden —, kein noch offener Abruf.
    const uebersicht = await uebersichtZu(mitPlanDokument('901'), 'neu angelegt', startedAt(0), {
      karten: {},
    })
    await waitFor(() =>
      expect(dokumentzeile(uebersicht, 900, 'plan')).toHaveTextContent(
        'Plan #901 nicht mehr vorhanden',
      ),
    )
    expect(
      within(vorgangIn(uebersicht, 900)).queryByRole('button', { name: /901/ }),
    ).not.toBeInTheDocument()
  })

  it('zeigt eine noch ladende Dokumentnummer ohne Verweis und nicht als entfallen (Punkt 8)', async () => {
    let freigeben = () => {}
    const laedt = new Promise<void>((aufloesen) => {
      freigeben = aufloesen
    })
    const uebersicht = await uebersichtZu(mitPlanDokument('901'), 'neu angelegt', startedAt(0), {
      karten: { 901: karte({ id: 11, number: 901, title: '[Plan] Gleich da' }) },
      kartenVerzoegert: laedt,
    })

    // Solange der Abruf läuft, steht die Nummer da — ohne Verweis und ohne die Falschaussage,
    // die Karte sei fort.
    const zeile = dokumentzeile(uebersicht, 900, 'plan')
    expect(zeile).toHaveTextContent('Plan #901')
    expect(zeile).not.toHaveTextContent('nicht mehr vorhanden')
    expect(
      within(vorgangIn(uebersicht, 900)).queryByRole('button', { name: /901/ }),
    ).not.toBeInTheDocument()

    freigeben()

    expect(
      await within(vorgangIn(uebersicht, 900)).findByRole('button', {
        name: 'Plan #901 [Plan] Gleich da',
      }),
    ).toBeInTheDocument()
  })

  it('ruft eine von zwei Vorgängen genannte Dokumentnummer nur einmal ab (Punkt 9)', async () => {
    const uebersicht = await uebersichtZu(
      kettenStand({
        einheiten: [
          { id: '900', titel: 'Erster', ausgang: 'fertig', stufen: { plan: { id: '901' } } },
          { id: '902', titel: 'Zweiter', ausgang: 'fertig', stufen: { pakete: { ids: ['901'] } } },
        ],
      }),
      'neu angelegt',
      startedAt(0),
      { karten: { 901: karte({ id: 11, number: 901, title: '[Plan] Gemeinsam' }) } },
    )

    // Beide Vorgänge zeigen den Verweis — die Karte ist also aufgelöst …
    expect(
      await within(vorgangIn(uebersicht, 900)).findByRole('button', { name: /#901/ }),
    ).toBeInTheDocument()
    expect(
      within(vorgangIn(uebersicht, 902)).getByRole('button', { name: /#901/ }),
    ).toBeInTheDocument()
    // … und dafür ging genau eine Anfrage hinaus.
    expect(byNumberAufrufe().filter((a) => a.url.endsWith('/901'))).toHaveLength(1)
  })
})

describe('NightRunPage — gekürzte Vorgangszeile neben der Übersicht (#869)', () => {
  /**
   * Die drei Wurzelkarten der echten Nacht. #842 hängt an einem Vorhaben, damit die Zeile eine
   * Angabe trägt, die die Übersicht gar nicht führt — genau die, die stehen bleiben soll.
   */
  const KARTEN = {
    791: karte({ id: 1, number: 791, title: '[Fachlich] Zugriff und Konten' }),
    814: karte({ id: 2, number: 814, title: '[Fachlich] Vorhaben ein- und ausblenden' }),
    842: karte({ id: 3, number: 842, title: '[Fachlich] Leitstand mehrstufig', parentId: 42 }),
  }

  const VORHABEN = { 42: vorhaben({ id: 42, number: 9, title: 'Leitstand ausbauen' }) }

  /**
   * Die Plan-Zeile des Stufenblocks im Auszug von #842. Sie steht seit #855 im `excerpt` und ist
   * damit der sichtbare Beleg dafür, dass die Zeile den Block nicht ein zweites Mal zeigt — die
   * Übersicht schreibt dieselbe Karte als „Plan #849 …" und nie in dieser Form.
   */
  const STUFENBLOCK_ZEILE = /plan #849: gelungen/

  /** Die Dauer von #842: 439 741 ms Plan plus 900 484 ms Prüfung, gerundet 22 Minuten. */
  const DAUER_842 = '22 Min'

  /**
   * Gesucht wird ausschließlich im Absatz beziehungsweise in der Inline-Zeile — **nicht** im
   * Übernahmetext: Dessen `textarea` trägt Zustand und Auszug absichtlich weiter (#856, AK 5), und
   * eine Suche über den ganzen Teilbaum fände sie dort wieder. Die Aussage lautet aber, dass der
   * Auszug nicht mehr als **Zeilentext** dasteht.
   */
  const ALS_ABSATZ = { selector: 'p' } as const
  const ALS_ZEILE = { selector: 'span' } as const

  /** Der echte Ketten-Lauf aus dem Ergebnisstand — mit Sitzungsstand, also mit Übersicht. */
  async function mitUebersicht() {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
      karten: KARTEN,
      kartenNachId: VORHABEN,
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    await waitFor(() => expect(laufKopfzeile(panelEl)).toHaveTextContent('neu angelegt'))
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('ketten-uebersicht')
    return panelEl
  }

  it('lässt am Lauf mit Übersicht Ampel, Dauer und den Auszug samt Stufenblock weg (Punkt 5)', async () => {
    const panelEl = await mitUebersicht()
    const zeile = await within(panelEl).findByTestId('paket-842')

    expect(within(zeile).queryByText(STUFENBLOCK_ZEILE, ALS_ABSATZ)).toBeNull()
    expect(within(zeile).queryByText(/^Auszug: /, ALS_ABSATZ)).toBeNull()
    // Ampel und Zustandstext stehen gemeinsam unter dieser Test-ID.
    expect(within(zeile).queryByTestId('zustand-842')).toBeNull()
    expect(within(zeile).queryByText(DAUER_842, ALS_ZEILE)).toBeNull()
  })

  it('lässt Vorhaben und Übernahmetext am Lauf mit Übersicht stehen und bedienbar (Punkt 6)', async () => {
    const panelEl = await mitUebersicht()
    const zeile = await within(panelEl).findByTestId('paket-842')

    fireEvent.click(await within(zeile).findByRole('button', { name: /^Vorhaben #9 / }))
    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 9')

    expect(uebernahmetext(panelEl, 842)).toContain(
      'Zustand: Am Zeitbudget beendet, Ergebnis liegt vor',
    )
    expect(
      within(zeile).getByRole('button', { name: 'Übernahmetext zu Karte #842 kopieren' }),
    ).toBeInTheDocument()
  })

  it('lässt die Zeile an einem aufbewahrten Ketten-Lauf ohne Sitzungsstand vollständig (Punkt 7)', async () => {
    // Die Gegenprobe zu AK 2: Derselbe Lauf, dieselbe Lauf-Art `CHAIN` — nur ohne Sitzungsstand.
    // Eine an `mode === 'CHAIN'` gebundene Kürzung nähme ihm hier alles und gäbe nichts zurück.
    renderPage({ listen: [wieAufbewahrt(ECHTE_KETTE_STAND)], karten: KARTEN, kartenNachId: VORHABEN })
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)

    panelAufklappen(panelEl)

    expect(within(panelEl).queryByTestId('ketten-uebersicht')).toBeNull()
    const zeile = await within(panelEl).findByTestId('paket-842')
    // Abgewartet, bis die Wurzelkarte und ihr Vorhaben aufgelöst sind — erst dann steht die Zeile
    // fertig da, und der Rest des Tests prüft nicht bloß ihren Zwischenstand.
    await within(zeile).findByRole('button', { name: /^Vorhaben #9 / })
    expect(within(zeile).getByTestId('zustand-842')).toHaveTextContent(
      'Am Zeitbudget beendet, Ergebnis liegt vor',
    )
    expect(within(zeile).getByText(DAUER_842, ALS_ZEILE)).toBeInTheDocument()
    expect(within(zeile).getByText(STUFENBLOCK_ZEILE, ALS_ABSATZ)).toBeInTheDocument()
  })

  it('lässt die Zeile an einem Lauf anderer Art vollständig (Punkt 8)', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              {
                id: 11,
                cardNumber: 700,
                title: 'Paket A',
                state: 'YELLOW',
                errorClass: 'CHECKS_RED',
                durationMs: SIEBEN_MIN,
                excerpt: 'Issue #700: npm test -> rot',
              },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    const zeile = within(lauf(0)).getByTestId('paket-700')
    expect(within(zeile).getByTestId('zustand-700')).toHaveTextContent('Erfolg, Prüfung rot')
    expect(within(zeile).getByText('7 Min', ALS_ZEILE)).toBeInTheDocument()
    expect(
      within(zeile).getByText('Auszug: Issue #700: npm test -> rot', ALS_ABSATZ),
    ).toBeInTheDocument()
    expect(within(zeile).getByLabelText('Übernahmetext zu Karte #700')).toBeInTheDocument()
  })
})

describe('NightRunPage — Laufband für Umsetzungs-Läufe (#871)', () => {
  /** Ein Abschnitt des Laufbands, über die Kartennummer seines Vorgangs. */
  const bandabschnitt = (panelEl: HTMLElement, cardNumber: number) =>
    within(panelEl).getByTestId(`laufband-abschnitt-${cardNumber}`)

  /**
   * Die fünf Vorgänge der echten Nacht vom 7. September, in der Reihenfolge des Laufs — zwei
   * erfolgreiche und drei gescheiterte.
   */
  const ECHTE_VORGAENGE = [767, 768, 769, 770, 771] as const

  /** Das aufgeklappte Panel des echten Umsetzungs-Laufs, nach Einliefern und Neuladen der Liste. */
  async function echterUmsetzungslauf() {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_STAND, 'night-run-2026-09-07-085229.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('zustand-767')
    return panelEl
  }

  it('teilt das Band der echten Nacht in den Verhältnissen der Vorgangsdauern (Punkt 9)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // Geprüft an der Beschriftung und am Datenfeld, nicht an berechneten Stilwerten: In jsdom
    // rechnet kein Browser ein Layout aus, ein Test auf `flex`-Anteile prüfte dort die
    // Zeichenkette, die man selbst geschrieben hat.
    expect(within(panelEl).getAllByTestId(/^laufband-abschnitt-/)).toHaveLength(5)
    expect(ECHTE_VORGAENGE.map((n) => bandabschnitt(panelEl, n).dataset.anteil)).toEqual([
      '7.9',
      '24.9',
      '31.8',
      '5.4',
      '30',
    ])
    // Die Aussage der Nacht: Die drei gescheiterten Vorgänge verbrauchten zusammen 86,7 Prozent
    // der Zeit — die beiden erfolgreichen zusammen 13,3.
    expect(bandabschnitt(panelEl, 767)).toHaveAttribute(
      'aria-label',
      'Karte #767: Erfolg, Prüfung rot, 4 Min',
    )
    expect(bandabschnitt(panelEl, 769)).toHaveAttribute(
      'aria-label',
      'Karte #769: gescheitert, 16 Min',
    )
    // Die Dauer steht auch sichtbar unter ihrem Abschnitt, nicht nur in der Ansage (Punkt 4).
    expect(bandabschnitt(panelEl, 769)).toHaveTextContent('16 Min')
  })

  it('färbt allein die drei gescheiterten Abschnitte rot (Punkt 10)', async () => {
    const panelEl = await echterUmsetzungslauf()
    const farbe = (cardNumber: number) =>
      within(panelEl).getByTestId(`laufband-balken-${cardNumber}`)

    for (const nummer of [768, 769, 771]) {
      expect(farbe(nummer)).toHaveStyle({ backgroundColor: theme.palette.nightRun.red })
    }
    for (const nummer of [767, 770]) {
      expect(farbe(nummer)).toHaveStyle({ backgroundColor: theme.palette.nightRun.yellow })
    }
  })

  it('zeigt an einem Lauf mit einem einzigen Vorgang kein Band, aber dessen Angaben (Punkt 11)', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              {
                id: 11,
                cardNumber: 700,
                title: 'Paket A',
                state: 'RED',
                errorClass: 'CHECKS_RED',
                durationMs: SIEBEN_MIN,
                excerpt: 'Issue #700: npm test -> rot',
              },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    // Ein Balken mit einem Abschnitt behauptet ein Verhältnis, das es nicht gibt.
    expect(within(lauf(0)).queryByTestId('laufband')).not.toBeInTheDocument()
    const zeile = within(lauf(0)).getByTestId('paket-700')
    expect(within(zeile).getByTestId('zustand-700')).toHaveTextContent('gescheitert')
    expect(within(zeile).getByText('7 Min')).toBeInTheDocument()
  })

  it('lässt einen Vorgang ohne Dauer in der Liste, aber nicht im Band (Punkt 12)', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
              { id: 12, cardNumber: 701, title: 'Paket B', state: 'GREEN', durationMs: SIEBEN_MIN },
              // Ein zurückgestelltes Paket lief nie, also trägt es auch keine Dauer.
              { id: 13, cardNumber: 702, title: 'Paket C', state: 'GREY' },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('laufband')

    expect(within(lauf(0)).getAllByTestId(/^laufband-abschnitt-/)).toHaveLength(2)
    expect(within(lauf(0)).queryByTestId('laufband-abschnitt-702')).not.toBeInTheDocument()
    // In der Zeilenliste steht er weiterhin — er trägt einen Ausgang, nur keine Breite.
    expect(within(lauf(0)).getByTestId('paket-702')).toHaveTextContent('nicht bearbeitet')
  })

  it('zeigt an einem Ketten-Lauf kein Laufband, sondern die Übersicht (Punkt 13)', async () => {
    const uebersicht = await echteUebersicht()

    expect(uebersicht).toBeInTheDocument()
    expect(screen.queryByTestId('laufband')).not.toBeInTheDocument()
  })

  it('misst die Anteile an der Summe der Vorgangsdauern, nicht an der Laufdauer (AK 2)', async () => {
    // Die Laufdauer weicht hier absichtlich von der Summe ab: Bezöge sich die Breite auf sie,
    // stünden hier 7 und 21 statt 25 und 75. Am echten Ergebnisstand ist der Unterschied nicht
    // messbar — dort **ist** die Laufdauer die Summe der Vorgangsdauern, und einen Endzeitstempel
    // führt der Stand gar nicht (Entscheidung des Issues).
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            durationMs: 100 * 60_000,
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
              { id: 12, cardNumber: 701, title: 'Paket B', state: 'GREEN', durationMs: 3 * SIEBEN_MIN },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('laufband')

    expect(bandabschnitt(lauf(0), 700).dataset.anteil).toBe('25')
    expect(bandabschnitt(lauf(0), 701).dataset.anteil).toBe('75')
  })
})

describe('NightRunPage — Modellzeit-Anteil je Vorgang (#872)', () => {
  /** Die Kennzahlenzeile eines Vorgangs, über die Kartennummer. */
  const kennzahlen = (panelEl: HTMLElement, cardNumber: number) =>
    within(panelEl).getByTestId(`kennzahlen-${cardNumber}`)

  /**
   * Klappt einen frisch eingelesenen Lauf auf und gibt sein Panel zurück. Die Kennzahlen stehen
   * **nur** im Ergebnisstand dieser Sitzung — der Server bewahrt sie nicht auf —, deshalb führt
   * jeder dieser Tests über das Einlesen einer Datei und nicht über eine Server-Antwort.
   */
  async function eingelesen(
    ergebnisstand: string,
    start: string,
    dateiname: string,
    antworten: Antworten,
  ) {
    renderPage(antworten)
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ergebnisstand, dateiname)

    const panelEl = await screen.findByTestId(`lauf-${start}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('vorgangs-kennzahlen')
    return panelEl
  }

  const echterUmsetzungslauf = () =>
    eingelesen(ECHTER_STAND, ECHTER_START, 'night-run-2026-09-07-085229.json', {
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })

  it('nennt je Vorgang Dauer, Kosten, Züge und den Anteil der Modellarbeit (Punkt 6)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // 180 563 ms Arbeitszeit auf 244 427 ms Dauer — 73,9 Prozent, gerundet 74.
    expect(kennzahlen(panelEl, 767)).toHaveTextContent(
      '4 Min · 2,14 $ · 38 Züge · Modellarbeit 74 % der Dauer, der Rest außerhalb',
    )
    // Der zweite Vorgang belegt, dass der Anteil je Vorgang gerechnet wird: 380 034 auf
    // 769 668 ms sind 49 Prozent, nicht noch einmal 74.
    expect(kennzahlen(panelEl, 768)).toHaveTextContent(
      'Modellarbeit 49 % der Dauer, der Rest außerhalb',
    )
  })

  it('beziffert die Restzeit nicht und nennt sie nicht Werkzeugzeit (AK 3)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // Das Protokoll misst die Aufteilung der Restzeit nicht — sie enthält auch die Prüfung
    // durch den Nachtlauf selbst und Wartezeiten.
    expect(within(panelEl).queryByText(/Werkzeugzeit/)).not.toBeInTheDocument()
    expect(kennzahlen(panelEl, 767)).not.toHaveTextContent('26 %')
  })

  it('zeigt bei gleichzeitigen Arbeiten keinen Anteil, sondern den Hinweis (Punkt 7)', async () => {
    const panelEl = await eingelesen(
      ECHTER_PRUEFLAUF_STAND,
      ECHTER_PRUEFLAUF_START,
      'night-run-2026-09-11-103116.json',
      {
        submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
        listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
      },
    )

    const zeile = kennzahlen(panelEl, 782)
    expect(zeile).toHaveTextContent(
      '14 Min · 21,95 $ · 252 Züge · Modellarbeit: mehrere Arbeiten liefen gleichzeitig',
    )
    // Weder ein Anteil über hundert Prozent noch ein negativer Rest: 1 084 627 auf 879 763 ms
    // wären gerundet 123 Prozent und ein Rest von minus 23.
    expect(zeile.textContent).not.toMatch(/\d+ %/)
    expect(zeile.textContent).not.toContain('−')
    expect(zeile.textContent).not.toContain('-')
  })

  it('führt einen Prüf-Lauf nur mit seinem einen bearbeiteten Vorgang (Punkt 1)', async () => {
    const panelEl = await eingelesen(
      ECHTER_PRUEFLAUF_STAND,
      ECHTER_PRUEFLAUF_START,
      'night-run-2026-09-11-103116.json',
      {
        submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
        listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
      },
    )

    // 34 der 35 Karten wurden nur angesehen und aussortiert — eine Kennzahlenzeile je
    // übergangener Karte wäre eine Wand aus Fehlanzeigen.
    expect(within(panelEl).getAllByTestId(/^kennzahlen-\d+$/)).toHaveLength(1)
  })

  it('zeigt eine gemessene Dauer von null als „0 s", fehlende Angaben als Hinweis (Punkt 8)', async () => {
    const panelEl = await eingelesen(
      ECHTE_ERZEUGUNG_STAND,
      ECHTE_ERZEUGUNG_START,
      'night-run-2026-09-09-125621.json',
      { listen: [[]] },
    )

    // #535 wurde in einer früheren Nacht fortgesetzt: In dieser lief keine Sitzung mehr, also
    // ist die Null gemessen — und die Kennzahlen fehlen tatsächlich.
    expect(kennzahlen(panelEl, 535)).toHaveTextContent(
      '0 s · Kosten nicht gemeldet · Züge nicht gemeldet · Modellzeit nicht gemeldet',
    )
    // Der Nachbarvorgang derselben Nacht zeigt, dass die Fehlanzeige nicht am Lauf hängt.
    expect(kennzahlen(panelEl, 533)).toHaveTextContent(
      '32 Min · 6,88 $ · 54 Züge · Modellarbeit 54 % der Dauer, der Rest außerhalb',
    )
  })

  it('zeigt ohne Arbeitszeit die Fehlanzeige und nicht den Hinweis auf Gleichzeitigkeit (Punkt 9)', async () => {
    const OHNE_ARBEITSZEIT = stand({
      einheiten: [
        einheit({
          ausgang: 'erfolg',
          pruefung: GEPRUEFT,
          kennzahlen: { kostenUsd: 1.5, zuege: 7 },
        }),
      ],
    })
    const panelEl = await eingelesen(OHNE_ARBEITSZEIT, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(OHNE_ARBEITSZEIT) },
      listen: [[], wieAufbewahrt(OHNE_ARBEITSZEIT)],
    })

    const zeile = kennzahlen(panelEl, 700)
    expect(zeile).toHaveTextContent('7 Min · 1,50 $ · 7 Züge · Modellzeit nicht gemeldet')
    expect(zeile).not.toHaveTextContent('gleichzeitig')
  })

  it('nennt ohne messbare Dauer keinen Anteil, obwohl beide Angaben vorliegen', async () => {
    // Konstruiert: Keines der fünf Protokolle trägt eine Sitzung, die neben der Dauer auch die
    // Arbeitszeit mit null meldet. Ein Anteil an einer Dauer von null ist keine Aussage, und
    // „0 % Modellarbeit" behauptete eine Restzeit, die es nicht gibt.
    const OHNE_MESSBARE_DAUER = stand({
      einheiten: [
        einheit({
          ausgang: 'erfolg',
          pruefung: GEPRUEFT,
          dauerMs: 0,
          kennzahlen: { kostenUsd: 1.5, zuege: 7, apiDauerMs: 0 },
        }),
      ],
    })
    const panelEl = await eingelesen(OHNE_MESSBARE_DAUER, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(OHNE_MESSBARE_DAUER) },
      listen: [[], wieAufbewahrt(OHNE_MESSBARE_DAUER)],
    })

    expect(kennzahlen(panelEl, 700)).toHaveTextContent(
      '0 s · 1,50 $ · 7 Züge · Modellarbeit: ohne messbare Dauer kein Anteil',
    )
  })

  it('zeigt an einem Lauf ohne bearbeiteten Vorgang gar keinen Block', async () => {
    // Ein Lauf, der jede Karte aussortiert hat, hat nichts zu berichten — ein leerer Block
    // behauptete eine Sitzung, die es nie gab.
    const NUR_UEBERGANGEN = stand({
      einheiten: [einheit({ ausgang: 'uebersprungen', grund: 'kein Label', dauerMs: undefined })],
    })
    renderPage({
      submit: { ergebnis: alleNeu(NUR_UEBERGANGEN) },
      listen: [[], wieAufbewahrt(NUR_UEBERGANGEN)],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(NUR_UEBERGANGEN)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    expect(within(lauf(0)).queryByTestId('vorgangs-kennzahlen')).not.toBeInTheDocument()
  })

  it('zeigt an einem Ketten-Lauf keine Kennzahlenzeilen, sondern die Übersicht', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('ketten-uebersicht')

    // Die Kette führt ihre Kosten und Züge bereits je Vorgang in der Übersicht (#866); eine
    // zweite Zeile daneben bezöge sich auf dieselben Zahlen.
    expect(within(panelEl).queryByTestId('vorgangs-kennzahlen')).not.toBeInTheDocument()
  })

  it('zeigt an einem aufbewahrten Lauf ohne Ergebnisstand keine Kennzahlenzeile', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    // Kosten, Züge und Arbeitszeit verlassen den Browser nie (Plan #718, A1) — ohne den Stand
    // dieser Sitzung gibt es sie nicht, und ein Block aus lauter Fehlanzeigen wäre kein Gewinn.
    expect(within(lauf(0)).queryByTestId('vorgangs-kennzahlen')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Herkunft eines Laufs (#775)', () => {
  /**
   * Derselbe Lauf, wie der Server ihn nach dem Einliefern zurückgibt — nur mit abweichender
   * Stückzahl. Sie ist der Beleg dafür, dass der sichtbare Lauf wirklich der **neu geladene** ist
   * und nicht mehr der eben im Browser gedeutete: Ohne diesen Unterschied stünde nach dem Upload
   * derselbe Text auf der Seite, und der Test wäre schon vor dem Neuladen grün.
   */
  const VOM_SERVER = wieAufbewahrt(EIN_LAUF).map((view) => ({ ...view, processedCount: 42 }))

  it('kennzeichnet einen aus dem Ergebnisstand eingelieferten Lauf auch nach dem Neuladen', async () => {
    renderPage({ submit: { ergebnis: alleNeu(EIN_LAUF) }, listen: [[], VOM_SERVER] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    // Erst wenn die Stückzahl des Servers dasteht, ist das Neuladen durch.
    await waitFor(() => expect(laufKopfzeile(lauf(0))).toHaveTextContent('42 bearbeitet, 0 übergangen'))
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('Ergebnisstand')
    expect(within(lauf(0)).queryByText('Herkunft unbekannt')).not.toBeInTheDocument()
  })

  it('nennt einen aufbewahrten Lauf ohne Upload in dieser Sitzung „Herkunft unbekannt"', async () => {
    // Der Server kennt die Unterscheidung nicht; die Kennzeichnung ist sitzungslokal (Plan #772,
    // Entscheidung 6). Nach einem Neuladen der Seite gilt das auch für einen eben erst
    // eingelieferten Lauf.
    renderPage({ listen: [VOM_SERVER] })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('Herkunft unbekannt')
    expect(within(lauf(0)).queryByText('Ergebnisstand')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Zustände, Kennzahlen und Auszüge', () => {
  it('macht jeden der vier Zustände am Text erkennbar, nicht nur an der Farbe', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    const panel = within(lauf(0))
    expect(await panel.findByText('Erfolg')).toBeInTheDocument()
    expect(panel.getByText('Erfolg, Prüfung rot')).toBeInTheDocument()
    expect(panel.getByText('gescheitert')).toBeInTheDocument()
    expect(panel.getByText('nicht bearbeitet')).toBeInTheDocument()
  })

  it('macht eine Erfolgsmeldung mit roter Prüfung gelb', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(GELB) },
      listen: [[], wieAufbewahrt(GELB)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(GELB)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    expect(await within(lauf(0)).findByText('Erfolg, Prüfung rot')).toBeInTheDocument()
    // Text **und** Farbe tragen die Aussage (CLAUDE-react.md Zeile 142); die Farbe kommt aus der
    // Palette, nicht aus `success`/`warning`/`error` (A15) — als ausgefüllte Ampel-Fläche (#738),
    // nicht mehr als Textfarbe.
    expect(within(lauf(0)).getByTestId('ampel-700')).toHaveStyle({
      backgroundColor: theme.palette.nightRun.yellow,
    })
  })

  it('zeigt den Zustand als ausgefuellte, gleich grosse Flaeche — unabhaengig von der Textlaenge (#738)', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    const panel = within(lauf(0))
    await panel.findByText('Erfolg')

    // Vier unterschiedlich lange Zustandstexte ("Erfolg" bis "Erfolg, Prüfung rot"), dieselbe
    // Flächengröße — die Fläche wirkt als Signal, nicht als weitere Textzeile (AC3).
    const groesse = { width: '8px', height: '8px' }
    expect(panel.getByTestId('ampel-700')).toHaveStyle({ ...groesse, backgroundColor: theme.palette.nightRun.green })
    expect(panel.getByTestId('ampel-701')).toHaveStyle({ ...groesse, backgroundColor: theme.palette.nightRun.yellow })
    expect(panel.getByTestId('ampel-702')).toHaveStyle({ ...groesse, backgroundColor: theme.palette.nightRun.red })
    expect(panel.getByTestId('ampel-703')).toHaveStyle({ ...groesse, backgroundColor: theme.palette.nightRun.grey })
  })

  it('zeigt bei einem grauen Arbeitspaket seinen Grund', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    // Der Ergebnisstand nennt den Grund wörtlich (#773) — die Anzeige stellt ihn als solchen voran.
    expect(
      await within(lauf(0)).findByText(`Grund: ${GRUND_ZURUECKGESTELLT}`),
    ).toBeInTheDocument()
  })

  it('kennzeichnet einen Prüf-Lauf als solchen', async () => {
    // Prüf-Läufe kommen nicht mehr über den Upload herein (der Ergebnisstand-Parser lehnt sie ab),
    // wohl aber vom Server: aus einem Lauf, den ein früherer Stand eingeliefert hat.
    renderPage({ listen: [[aufbewahrt({ id: 1, startedAt: startedAt(0), mode: 'REVIEW' })]] })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(within(lauf(0)).getByText('Prüf-Lauf')).toBeInTheDocument()
    expect(within(lauf(0)).queryByText('Umsetzungs-Lauf')).not.toBeInTheDocument()
  })

  it('kennzeichnet einen Umsetzungs-Lauf als solchen', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(EIN_LAUF) },
      listen: [[], wieAufbewahrt(EIN_LAUF)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(EIN_LAUF)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('Umsetzungs-Lauf')
  })

  // **Umgekehrt mit Issue #872**: Der Test hielt bis dahin fest, dass in der Auswertung
  // überhaupt keine Kostenangabe erscheint — ein Nicht-Ziel aus #715, das die fachliche Quelle
  // #861 ausdrücklich aufhebt. Die Kennzahlenzeile je Vorgang nennt Kosten jetzt, und wo der
  // Stand keine führt, benennt sie das Fehlen, statt es zu verschweigen.
  it('zeigt Dauer und Stückzahlen je Lauf sowie die Dauer je Arbeitspaket, dazu die Kosten', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    const panel = within(lauf(0))
    // Die Laufdauer ist die Summe der drei gelaufenen Sessions; das zurückgestellte Paket trägt keine.
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('21 Min')
    expect(laufKopfzeile(lauf(0))).toHaveTextContent('3 bearbeitet, 1 übergangen')
    expect((await panel.findAllByText('7 Min')).length).toBeGreaterThan(0)
    // Die drei bearbeiteten Vorgänge tragen eine Kennzahlenzeile; das zurückgestellte Paket
    // ist grau und bekommt keine.
    expect(panel.getAllByTestId(/^kennzahlen-\d+$/)).toHaveLength(3)
    // Dieser Stand führt keine Sitzungs-Kennzahlen — das Fehlen wird benannt, nicht als Null
    // dargestellt.
    expect(panel.getByTestId('kennzahlen-700')).toHaveTextContent('Kosten nicht gemeldet')
    expect(panel.queryByText(/0,00 \$/)).not.toBeInTheDocument()
  })

  it('weist ungedeutete Zeilen mit Anzahl und Auszug aus, wörtlich statt gerendert', async () => {
    // Ein Ergebnisstand kennt keine ungedeuteten Zeilen (#773) — ein vom Server geladener Lauf aus
    // der Zeit der Protokolldeutung schon.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            unparsedCount: 1,
            unparsedSample: UNGEDEUTET,
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
          }),
        ],
      ],
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    expect(laufKopfzeile(lauf(0))).toHaveTextContent('Ungedeutete Zeilen: 1')
    aufklappen(0)
    // `findByText` mit exaktem Matcher greift nur, wenn der ganze Text **ein** Textknoten ist —
    // ein Markdown-Renderer hätte ihn in Elemente zerteilt.
    expect(await within(lauf(0)).findByText(UNGEDEUTET)).toBeInTheDocument()
    expect(within(lauf(0)).queryByText('Sternchen', { selector: 'em' })).toBeNull()
    expect(within(lauf(0)).queryByText('Backticks', { selector: 'code' })).toBeNull()
  })
})

describe('NightRunPage — Herkunftskette', () => {
  const lauf700 = (items: ItemVorgabe[]) => [
    [aufbewahrt({ id: 1, startedAt: startedAt(0), items })],
  ]

  it('löst die Kette des offenen Laufs auf und lädt jede Nummer nur einmal', async () => {
    renderPage({
      listen: lauf700([
        { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' },
        { id: 12, cardNumber: 700, title: 'Paket A', state: 'GREY' },
      ]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    // Beide Arbeitspakete nennen dieselbe Karte, also erscheint die Kette zweimal. Die Stufe ist
    // seit #818 ein Knopf; ihr Name trägt die Stufe, ihr sichtbarer Text nur Nummer und Titel.
    expect(
      await within(lauf(0)).findAllByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    ).toHaveLength(2)
    expect(
      within(lauf(0)).getAllByRole('button', {
        name: 'Fachliche Anforderung #715 [Fachlich] Nachtlauf',
      }),
    ).toHaveLength(2)
    expect(within(lauf(0)).getAllByText('#718 [Plan] Nachtlauf')).toHaveLength(2)
    // Die doppelt genannte Karte 700 wird genau einmal geladen.
    expect(byNumberAufrufe().filter((a) => a.url.endsWith('/700'))).toHaveLength(1)
  })

  it('zeigt ein Arbeitspaket ohne Plan und ohne fachliche Anforderung als „ohne", nicht als Abriss', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Plan: ohne')).toBeInTheDocument()
    expect(within(lauf(0)).getByText('Fachliche Anforderung: ohne')).toBeInTheDocument()
    expect(within(lauf(0)).queryByText(/abgerissen/)).not.toBeInTheDocument()
  })

  it('zeigt eine Stufe, die der Nachtlauf nicht fährt, als „noch nicht erreicht"', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Plan: noch nicht erreicht')).toBeInTheDocument()
    expect(
      within(lauf(0)).getByRole('button', { name: 'Fachliche Anforderung #715 [Fachlich] Nachtlauf' }),
    ).toBeInTheDocument()
  })

  it('kennzeichnet eine Stufe als abgerissen, wenn der Lauf sie rot meldet', async () => {
    renderPage({
      listen: lauf700([
        { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' },
        { id: 12, cardNumber: 718, title: '[Plan] Nachtlauf', state: 'RED', errorClass: 'AWAITING_DECISION' },
      ]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    // Der Abriss-Vermerk steht außerhalb des Links: Ein Klick öffnet die Karte, der Zusatz bleibt
    // Fließtext (WCAG 2.5.3 — der sichtbare Linktext ist Teilstring des Namens).
    expect(
      await within(lauf(0)).findByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    ).toBeInTheDocument()
    expect(within(lauf(0)).getByText(/— abgerissen/)).toBeInTheDocument()
  })

  it('meldet eine nicht auflösbare Kartennummer und bleibt bedienbar', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {},
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Karte #700 nicht gefunden')).toBeInTheDocument()
    expect(within(lauf(0)).queryByText('Plan: ohne')).not.toBeInTheDocument()
    // Die Seite bleibt bedienbar: der Lauf lässt sich wieder zuklappen.
    fireEvent.click(within(lauf(0)).getByRole('button', { expanded: true }))
    expect(within(lauf(0)).getByRole('button', { expanded: false })).toBeInTheDocument()
  })

  it('meldet eine nicht auflösbare Stufe der Kette, ohne sie „ohne" zu nennen', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 999 }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Plan: Karte #999 nicht gefunden')).toBeInTheDocument()
    expect(within(lauf(0)).queryByText('Plan: ohne')).not.toBeInTheDocument()
  })

  it('lädt die Kette eines Laufs nur beim ersten Aufklappen', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)
    await within(lauf(0)).findByText('Plan: ohne')
    fireEvent.click(within(lauf(0)).getByRole('button', { expanded: true }))
    panelAufklappen(lauf(0))

    await waitFor(() => expect(within(lauf(0)).getByText('Plan: ohne')).toBeInTheDocument())
    expect(byNumberAufrufe()).toHaveLength(1)
  })

  it('bricht einen Herkunftsring ab, statt endlos zu laden', async () => {
    // Ein Ring kann nur an der API vorbei entstehen (siehe `DerivationNode.broken`) — ohne
    // Abbruch liefe sowohl das Laden als auch das Aufbauen der Kette endlos.
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 700 }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(
      await within(lauf(0)).findByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    ).toBeInTheDocument()
    expect(within(lauf(0)).getByText('Fachliche Anforderung: noch nicht erreicht')).toBeInTheDocument()
    expect(byNumberAufrufe()).toHaveLength(2)
  })

  it('öffnet zu einem Arbeitspaket seine Karte', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    fireEvent.click(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ }))

    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 700')
  })

  it('öffnet die Karte des Plans aus der Stufenzeile (#818)', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    fireEvent.click(
      await within(lauf(0)).findByRole('button', { name: 'Plan #718 [Plan] Nachtlauf' }),
    )

    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 718')
  })

  it('öffnet die Karte der fachlichen Anforderung aus der Stufenzeile (#818)', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', derivedFrom: 718 }),
        718: karte({ id: 2, number: 718, title: '[Plan] Nachtlauf', derivedFrom: 715 }),
        715: karte({ id: 3, number: 715, title: '[Fachlich] Nachtlauf' }),
      },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    fireEvent.click(
      await within(lauf(0)).findByRole('button', {
        name: 'Fachliche Anforderung #715 [Fachlich] Nachtlauf',
      }),
    )

    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 715')
  })

  it('schließt die Karte wieder', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    fireEvent.click(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ }))
    await screen.findByTestId('karten-detail')

    fireEvent.click(screen.getByText('detail-schliessen'))

    expect(screen.queryByTestId('karten-detail')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Vorhaben eines Arbeitspakets (#818)', () => {
  const lauf700 = (items: ItemVorgabe[]) => [
    [aufbewahrt({ id: 1, startedAt: startedAt(0), items })],
  ]

  /** Zwei aufbewahrte Läufe — die Grundlage der Tests zu nebenläufigen `ladeKetten()`-Aufrufen. */
  const zweiLaeufe = (itemsA: ItemVorgabe[], itemsB: ItemVorgabe[]) => [
    [
      aufbewahrt({ id: 1, startedAt: startedAt(0), items: itemsA }),
      aufbewahrt({ id: 2, startedAt: startedAt(30), items: itemsB }),
    ],
  ]

  /** Ein Stub, der die Vorhaben-Abrufe anhält, bis {@link freigeben} gerufen wird. */
  function angehalten() {
    let freigeben = () => {}
    const verzoegert = new Promise<void>((resolve) => {
      freigeben = resolve
    })
    return { verzoegert, freigeben: () => freigeben() }
  }

  it('zeigt „ohne", wenn das Arbeitspaket keinem Vorhaben zugeordnet ist', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Vorhaben: ohne')).toBeInTheDocument()
    // Ohne Zuordnung wird auch nichts nachgeladen.
    expect(vorhabenAufrufe()).toHaveLength(0)
  })

  it('öffnet das zugeordnete Vorhaben über seine projektweite Nummer', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A', parentId: 42 }) },
      kartenNachId: { 42: vorhaben({ id: 42, number: 5, title: 'Leitstand ausbauen' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    // Abgerufen wird über die Karten-ID, angezeigt die projektweite Nummer.
    fireEvent.click(await within(lauf(0)).findByRole('button', { name: /^Vorhaben #5 / }))
    expect(await screen.findByTestId('karten-detail')).toHaveTextContent('Karte 5')
    expect(vorhabenAufrufe().map((a) => a.url)).toEqual(['/api/cards/42'])
  })

  it('meldet ein nicht abrufbares Vorhaben und bleibt bedienbar', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A', parentId: 42 }) },
      kartenNachId: {},
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Vorhaben: nicht auflösbar')).toBeInTheDocument()
    // Die Wurzelkarte bleibt öffenbar, und der Lauf lässt sich weiterhin zu- und aufklappen.
    expect(within(lauf(0)).getByRole('button', { name: '#700 Paket A' })).toBeInTheDocument()
    fireEvent.click(within(lauf(0)).getByRole('button', { expanded: true }))
    panelAufklappen(lauf(0))
    await waitFor(() =>
      expect(within(lauf(0)).getByText('Vorhaben: nicht auflösbar')).toBeInTheDocument(),
    )
  })

  it('zeigt keine Vorhaben-Zeile, wenn schon die Wurzelkarte nicht auflösbar ist', async () => {
    renderPage({
      listen: lauf700([{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }]),
      karten: {},
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)

    expect(await within(lauf(0)).findByText('Karte #700 nicht gefunden')).toBeInTheDocument()
    expect(within(lauf(0)).queryByText(/^Vorhaben:/)).toBeNull()
    expect(vorhabenAufrufe()).toHaveLength(0)
  })

  it('zeigt keine Vorhaben-Zeile, solange der Abruf des Vorhabens noch läuft', async () => {
    const { verzoegert, freigeben } = angehalten()
    renderPage({
      // Der Lauf mit dem Vorhaben steht **oben** und klappt seit #914 von selbst auf; der zweite
      // wird im Test aufgeklappt. Die Rollen sind damit gegenüber der früheren Fassung getauscht,
      // die Inszenierung ist dieselbe: Ein Lauf hängt im Vorhaben-Abruf, der andere veröffentlicht
      // währenddessen den geteilten Katalog.
      listen: zweiLaeufe(
        [{ id: 11, cardNumber: 701, title: 'Paket B', state: 'GREEN' }],
        [{ id: 12, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
      ),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', parentId: 5 }),
        701: karte({ id: 2, number: 701, title: 'Paket B' }),
      },
      kartenNachId: { 5: vorhaben({ id: 5, number: 9, title: 'Leitstand ausbauen' }) },
      kartenNachIdVerzoegert: verzoegert,
    })
    await screen.findByTestId(`lauf-${startedAt(30)}`)
    await waitFor(() => expect(vorhabenAufrufe()).toHaveLength(1))

    aufklappen(0)

    // Der zweite Lauf veröffentlicht den geteilten Katalog — Karte 700 ist damit aufgelöst, ihr
    // Vorhaben aber noch nicht. Dann steht dort keine Zeile, nicht etwa „ohne" oder „nicht
    // auflösbar".
    await within(lauf(0)).findByText('Vorhaben: ohne')
    expect(within(lauf(30)).getByRole('button', { name: '#700 Paket A' })).toBeInTheDocument()
    expect(within(lauf(30)).queryByText(/^Vorhaben:/)).toBeNull()

    freigeben()
    expect(
      await within(lauf(30)).findByRole('button', { name: /^Vorhaben #9 / }),
    ).toBeInTheDocument()
  })

  it('ruft ein Vorhaben auch bei zwei gleichzeitig aufgeklappten Läufen nur einmal ab', async () => {
    const { verzoegert, freigeben } = angehalten()
    renderPage({
      listen: zweiLaeufe(
        [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
        [{ id: 12, cardNumber: 701, title: 'Paket B', state: 'GREEN' }],
      ),
      karten: {
        700: karte({ id: 1, number: 700, title: 'Paket A', parentId: 42 }),
        701: karte({ id: 2, number: 701, title: 'Paket B', parentId: 42 }),
      },
      kartenNachId: { 42: vorhaben({ id: 42, number: 9, title: 'Leitstand ausbauen' }) },
      kartenNachIdVerzoegert: verzoegert,
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    aufklappen(0)
    await waitFor(() => expect(vorhabenAufrufe()).toHaveLength(1))
    aufklappen(30)
    freigeben()

    expect(await within(lauf(0)).findByRole('button', { name: /^Vorhaben #9 / })).toBeInTheDocument()
    expect(await within(lauf(30)).findByRole('button', { name: /^Vorhaben #9 / })).toBeInTheDocument()
    expect(vorhabenAufrufe()).toHaveLength(1)
  })
})

describe('NightRunPage — Häufigkeit einer Fehlerklasse', () => {
  /**
   * Drei aufbewahrte Läufe, nur der neueste trägt Befunde. `M` ist die Länge **dieser** Liste (3),
   * nicht die Zahl der Läufe mit einer bestimmten Fehlerklasse.
   */
  const DREI_LAEUFE: NightRunView[] = [
    aufbewahrt({
      id: 3,
      startedAt: startedAt(30),
      items: [
        { id: 11, cardNumber: 700, title: 'Paket A', state: 'YELLOW', errorClass: 'CHECKS_RED' },
        { id: 12, cardNumber: 701, title: 'Paket B', state: 'RED', errorClass: 'HARD_ABORT' },
        { id: 13, cardNumber: 702, title: 'Paket C', state: 'GREEN' },
        { id: 14, cardNumber: 703, title: 'Paket D', state: 'GREY', errorClass: 'DEPENDENCY_UNMET' },
      ],
    }),
    aufbewahrt({ id: 2, startedAt: startedAt(10) }),
    aufbewahrt({ id: 1, startedAt: startedAt(0) }),
  ]

  /** Die Häufigkeitszeile eines Arbeitspakets im Lauf von Minute 30; `null`, wenn keine erscheint. */
  const haeufigkeit = (cardNumber: number) =>
    within(lauf(30)).queryByTestId(`haeufigkeit-${cardNumber}`)

  it('zeigt zu einem gelben und einem roten Befund die Häufigkeit seiner Fehlerklasse', async () => {
    renderPage({ listen: [DREI_LAEUFE], zaehler: [{ CHECKS_RED: 3, HARD_ABORT: 2 }] })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('haeufigkeit-700')).toHaveTextContent(
      'Prüfungen rot: 3 von 3 aufbewahrten Läufen',
    )
    expect(haeufigkeit(701)).toHaveTextContent('Harter Abbruch: 2 von 3 aufbewahrten Läufen')
  })

  it('nennt ein erstes Vorkommen „zum ersten Mal", nicht „0" und nicht „1 von M"', async () => {
    renderPage({ listen: [DREI_LAEUFE], zaehler: [{ CHECKS_RED: 1 }] })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    const zeile = await within(lauf(30)).findByTestId('haeufigkeit-700')
    expect(zeile).toHaveTextContent('Prüfungen rot: zum ersten Mal')
    expect(zeile).not.toHaveTextContent('0')
    expect(zeile).not.toHaveTextContent('1 von')
  })

  it('zeigt zu einem grünen und einem grauen Arbeitspaket keine Häufigkeit', async () => {
    renderPage({
      listen: [DREI_LAEUFE],
      zaehler: [{ CHECKS_RED: 3, HARD_ABORT: 2, DEPENDENCY_UNMET: 2 }],
    })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    await within(lauf(30)).findByTestId('haeufigkeit-700')
    expect(haeufigkeit(702)).toBeNull()
    // Grau trägt eine Fehlerklasse (offene Abhängigkeit) — trotzdem ist es kein Befund.
    expect(haeufigkeit(703)).toBeNull()
  })

  it('zeigt die Zahl des Endpunkts, nicht die aus den geladenen Läufen gerechnete', async () => {
    // Genau **ein** geladener Lauf trägt CHECKS_RED; der Endpunkt meldet 3. Erschiene die 1, wäre
    // im Browser gerechnet worden.
    renderPage({ listen: [DREI_LAEUFE], zaehler: [{ CHECKS_RED: 3 }] })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('haeufigkeit-700')).toHaveTextContent(
      'Prüfungen rot: 3 von 3 aufbewahrten Läufen',
    )
    expect(zaehlerAufrufe()).toHaveLength(1)
  })

  it('lädt die Häufigkeit nach erfolgreichem Senden neu', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(GELB) },
      listen: [[], wieAufbewahrt(GELB)],
      zaehler: [{}, { CHECKS_RED: 1 }],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(GELB)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    expect(await within(lauf(0)).findByTestId('haeufigkeit-700')).toHaveTextContent(
      'Prüfungen rot: zum ersten Mal',
    )
    expect(zaehlerAufrufe()).toHaveLength(2)
  })

  it('hält Läufe und Befunde sichtbar, wenn der Abruf der Häufigkeit scheitert', async () => {
    renderPage({ listen: [DREI_LAEUFE], zaehlerFehler: 'Nur der Owner darf die Auswertung sehen.' })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('haeufigkeit-700')).toHaveTextContent(
      'Prüfungen rot: Häufigkeit nicht abrufbar',
    )
    // Unterscheidbar von einem ersten Vorkommen — und die Seite bleibt vollständig.
    expect(within(lauf(30)).queryByText(/zum ersten Mal/)).not.toBeInTheDocument()
    expect(within(lauf(30)).getByTestId('zustand-700')).toBeInTheDocument()
    expect(within(lauf(30)).getByTestId('zustand-701')).toBeInTheDocument()
  })

  it('zeigt zu einer noch nicht gespeicherten Auswertung keine Häufigkeit', async () => {
    // Das Senden scheitert: Der Lauf steht auf der Seite, zählt aber nicht zu den aufbewahrten.
    renderPage({ submit: { fehler: 'Auszug zu lang' }, zaehler: [{ CHECKS_RED: 4 }] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(GELB)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    expect(await within(lauf(0)).findByTestId('zustand-700')).toBeInTheDocument()
    expect(within(lauf(0)).queryByTestId('haeufigkeit-700')).toBeNull()
  })

  it('zeigt keine Häufigkeit, wenn der Endpunkt die Fehlerklasse nicht kennt', async () => {
    renderPage({ listen: [DREI_LAEUFE], zaehler: [{ HARD_ABORT: 2 }] })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('haeufigkeit-701')).toBeInTheDocument()
    expect(haeufigkeit(700)).toBeNull()
  })

  it('zeigt keine Häufigkeit zu einem Befund ohne Fehlerklasse', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 3,
            startedAt: startedAt(30),
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'RED' }],
          }),
        ],
      ],
      zaehler: [{ CHECKS_RED: 3 }],
    })
    await screen.findByTestId(`lauf-${startedAt(30)}`)

    aufklappen(30)

    expect(await within(lauf(30)).findByTestId('zustand-700')).toBeInTheDocument()
    expect(haeufigkeit(700)).toBeNull()
  })
})

describe('NightRunPage — Übernahmetext für die Entwicklungsumgebung', () => {
  /** Ein Auszug mit Markdown-Zeichen — er ist Fremdtext und wird nie gedeutet. */
  const AUSZUG_GELB =
    '  Issue #700: gelaufen: *fett* `npm test` -> rot (Frontend) | ausgelassen: keine'

  const GELB: NightRunHandoffItem = {
    cardNumber: 700,
    title: 'Paket A',
    state: 'YELLOW',
    errorClass: 'CHECKS_RED',
    excerpt: AUSZUG_GELB,
  }

  const ROT: NightRunHandoffItem = {
    cardNumber: 701,
    title: 'Paket B',
    state: 'RED',
    errorClass: 'HARD_ABORT',
    excerpt: '  HARTER STOPP: erfolgreiche Runde zu Issue #701 hinterlaesst einen dirty Tree',
  }

  const GRUEN: NightRunHandoffItem = {
    cardNumber: 702,
    title: 'Paket C',
    state: 'GREEN',
    errorClass: undefined,
    excerpt: '  Erfolg nach 7 min, Commit c3d4e5f, Issue #702 in In review.',
  }

  const GRAU: NightRunHandoffItem = {
    cardNumber: 703,
    title: 'Paket D',
    state: 'GREY',
    errorClass: 'DEPENDENCY_UNMET',
    excerpt: '  #703 Paket D -> uebersprungen (Abhaengigkeit #999 liegt nicht in Done)',
  }

  const BEFUNDE: NightRunView[] = [
    aufbewahrt({
      id: 1,
      startedAt: startedAt(0),
      items: [GELB, ROT, GRUEN, GRAU].map((item, position) => ({ id: position + 1, ...item })),
    }),
  ]

  /** Das schreibgeschützte Feld eines Arbeitspakets; `null`, wenn keines gerendert wird. */
  const feld = (cardNumber: number) =>
    within(lauf(0)).queryByLabelText(`Übernahmetext zu Karte #${cardNumber}`) as
      | HTMLTextAreaElement
      | null

  const kopierKnopf = (cardNumber: number) =>
    within(lauf(0)).queryByRole('button', {
      name: `Übernahmetext zu Karte #${cardNumber} kopieren`,
    })

  let writeText: ReturnType<typeof vi.fn>

  beforeEach(() => {
    writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })
  })

  /** Rendert die Seite mit den vier Befunden und klappt den Lauf auf. */
  async function befundeZeigen() {
    renderPage({ listen: [BEFUNDE] })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('zustand-700')
  }

  it('zeigt zu einem gelben und einem roten Arbeitspaket den vollständigen Text, bevor kopiert wird', async () => {
    await befundeZeigen()

    // `getByDisplayValue` mit dem **ganzen** String: Ein gekürzter Wert fände nichts. Der
    // Normalisierer bleibt aus, sonst faltete Testing Library die Zeilenumbrüche des Werts zu
    // Leerzeichen zusammen und der Vergleich prüfte weniger, als er behauptet.
    const woertlich = getDefaultNormalizer({ trim: false, collapseWhitespace: false })
    expect(
      within(lauf(0)).getByDisplayValue(buildHandoffText(GELB) as string, { normalizer: woertlich }),
    ).toBeInTheDocument()
    expect(
      within(lauf(0)).getByDisplayValue(buildHandoffText(ROT) as string, { normalizer: woertlich }),
    ).toBeInTheDocument()
    expect(feld(700)?.value).toContain(AUSZUG_GELB)
    expect(writeText).not.toHaveBeenCalled()
  })

  it('zeigt zu einem grünen und einem grauen Arbeitspaket weder Feld noch Knopf', async () => {
    await befundeZeigen()

    expect(feld(702)).toBeNull()
    expect(kopierKnopf(702)).toBeNull()
    expect(feld(703)).toBeNull()
    expect(kopierKnopf(703)).toBeNull()
  })

  it('legt exakt den String des sichtbaren Feldes in die Zwischenablage', async () => {
    await befundeZeigen()
    const sichtbar = feld(700)?.value

    fireEvent.click(kopierKnopf(700) as HTMLElement)

    await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1))
    expect(writeText).toHaveBeenCalledWith(sichtbar)
  })

  it('gibt den Auszug wörtlich wieder, statt seine Markdown-Zeichen zu deuten', async () => {
    await befundeZeigen()

    // Der Wert einer `textarea` trägt kein Markup — geprüft wird beides: der wörtliche Inhalt und
    // dass daneben nichts vom Markdown-Renderer Erzeugtes steht.
    expect(feld(700)?.value).toContain('*fett*')
    expect(feld(700)?.value).toContain('`npm test`')
    expect(within(lauf(0)).queryByText('fett', { selector: 'em' })).toBeNull()
    expect(within(lauf(0)).queryByText('npm test', { selector: 'code' })).toBeNull()
  })

  it('lässt den Text sichtbar, wenn die Zwischenablage nicht verfügbar ist', async () => {
    writeText.mockRejectedValue(new Error('Clipboard nicht verfügbar'))
    await befundeZeigen()
    const sichtbar = feld(700)?.value

    fireEvent.click(kopierKnopf(700) as HTMLElement)

    await waitFor(() => expect(writeText).toHaveBeenCalledTimes(1))
    // Der Text bleibt stehen und ist von Hand markierbar; eine Fehlermeldung ist nicht nötig.
    expect(feld(700)?.value).toBe(sichtbar)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('zeigt zu einem Befund ohne Auszug einen Text ohne `undefined`', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [{ id: 1, cardNumber: 700, title: 'Paket A', state: 'RED', errorClass: 'HARD_ABORT' }],
          }),
        ],
      ],
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('zustand-700')

    expect(feld(700)?.value).not.toContain('undefined')
    expect(feld(700)?.value).toContain('Harter Abbruch')
  })
})

describe('NightRunPage — null aus der API (#734)', () => {
  /**
   * So schickt der Server einen Lauf ohne Befunde: Fehlende Werte stehen als `null` im JSON, nicht
   * als fehlendes Feld — weder `@JsonInclude(NON_NULL)` noch `default-property-inclusion` sind
   * gesetzt. Die Anzeigeform kennt für „fehlt" nur `undefined`; wer das nicht übersetzt, rechnet
   * mit `null` weiter (`null / 1000` ist `0`) und schreibt es in Texte.
   */
  const MIT_NULL: NightRunView[] = [
    aufbewahrt({
      id: 1,
      startedAt: startedAt(0),
      unparsedSample: null,
      items: [
        {
          id: 11,
          cardNumber: 700,
          title: 'Paket A',
          state: 'RED',
          errorClass: null,
          durationMs: null,
          commitHash: null,
          excerpt: null,
        },
      ],
    }),
  ]

  /** Das schreibgeschützte Feld des Übernahmetexts; `null`, wenn keines gerendert wird. */
  const feld = (cardNumber: number) =>
    within(lauf(0)).queryByLabelText(`Übernahmetext zu Karte #${cardNumber}`) as
      | HTMLTextAreaElement
      | null

  async function nullLaufZeigen() {
    renderPage({
      listen: [MIT_NULL],
      zaehler: [{ HARD_ABORT: 2 }],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('zustand-700')
  }

  it('zeigt den Lauf an, statt an einem null-Feld abzustürzen', async () => {
    await nullLaufZeigen()

    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
    expect(lauf(0).textContent).not.toContain('null')
    expect(lauf(0).textContent).not.toContain('undefined')
  })

  it('lässt Dauer und Auszug weg, statt „0 s" und „Auszug: null" zu behaupten', async () => {
    await nullLaufZeigen()

    expect(lauf(0).textContent).not.toContain('0 s')
    expect(lauf(0).textContent).not.toContain('Auszug:')
    expect(within(lauf(0)).queryByTestId('haeufigkeit-700')).toBeNull()
  })

  it('lässt die Fehlerklasse aus dem Übernahmetext weg, statt „undefined" hineinzuschreiben', async () => {
    await nullLaufZeigen()

    expect(feld(700)?.value).toBe('Nachtlauf-Befund zu Karte #700 Paket A\nZustand: gescheitert')
  })
})

describe('NightRunPage — Aufschlüsselung der gesichteten Karten (#873)', () => {
  /** Die Zahl der gesichteten Karten, wie sie über der Aufschlüsselung steht. */
  const gesichtet = (panelEl: HTMLElement) =>
    within(panelEl).getByTestId('aufschluesselung-gesichtet')

  const bearbeitet = (panelEl: HTMLElement) =>
    within(panelEl).getByTestId('aufschluesselung-bearbeitet')

  /** Die Zeilen der Überspringgründe, in der Reihenfolge ihrer Häufigkeit. */
  const grundzeilen = (panelEl: HTMLElement) =>
    within(panelEl)
      .getAllByTestId(/^aufschluesselung-grund-\d+$/)
      .map((zeile) => zeile.textContent)

  /**
   * Der frisch eingelesene Prüf-Lauf vom 11. September: 35 gesichtete Karten, eine bearbeitete
   * (#782, „geprüft mit Befund"), 34 übersprungene aus drei Gründen.
   */
  async function frischGeparsterPrueflauf() {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_PRUEFLAUF_STAND, 'night-run-2026-09-11-103116.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_PRUEFLAUF_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('aufschluesselung')
    return panelEl
  }

  /**
   * Derselbe Lauf **ohne** den Ergebnisstand dieser Sitzung — so, wie ihn die Seite nach einem
   * Neuladen vom Server bekommt. Das Ausgangswort fehlt dann; die Aufschlüsselung entsteht allein
   * aus Zustand, Fehlerklasse und Auszug (Punkt 5).
   */
  async function aufbewahrterPrueflauf(ansicht = wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)) {
    renderPage({ listen: [ansicht] })

    const panelEl = await screen.findByTestId(`lauf-${ansicht[0].startedAt}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('aufschluesselung')
    return panelEl
  }

  it('schlüsselt den frisch geparsten Prüf-Lauf nach seinen Ausgängen auf (Punkt 8)', async () => {
    const panelEl = await frischGeparsterPrueflauf()

    expect(gesichtet(panelEl)).toHaveTextContent('35 Karten gesichtet')
    expect(bearbeitet(panelEl)).toHaveTextContent('1 bearbeitet')
    expect(grundzeilen(panelEl)).toEqual([
      '17 übersprungen: kein Plan-Dokument ([Plan])',
      "15 übersprungen: kein Label 'review:offen'",
      '2 übersprungen: Idee ([Idee])',
    ])
    // Jede Karte zählt genau einmal: 1 + 17 + 15 + 2 = 35.
    expect(within(panelEl).queryByTestId('aufschluesselung-liegengeblieben')).not.toBeInTheDocument()
    expect(within(panelEl).queryByTestId('aufschluesselung-unbekannt')).not.toBeInTheDocument()
  })

  it('kommt am aufbewahrten Lauf ohne das Ausgangswort auf dieselben Zahlen (Punkt 9)', async () => {
    const panelEl = await aufbewahrterPrueflauf()

    expect(gesichtet(panelEl)).toHaveTextContent('35 Karten gesichtet')
    expect(bearbeitet(panelEl)).toHaveTextContent('1 bearbeitet')
    expect(grundzeilen(panelEl)).toEqual([
      '17 übersprungen: kein Plan-Dokument ([Plan])',
      "15 übersprungen: kein Label 'review:offen'",
      '2 übersprungen: Idee ([Idee])',
    ])
    expect(within(panelEl).queryByTestId('aufschluesselung-unbekannt')).not.toBeInTheDocument()
  })

  it('zählt einen nicht sicher zuzuordnenden Vorgang als „ohne bekannten Ausgang" (Punkt 10)', async () => {
    // Konstruiert: Keines der fünf Protokolle liefert einen aufbewahrten Lauf mit diesen Kanten.
    // Der Auszug ist das einzige Unterscheidungsmerkmal, das die Server-Sicht noch trägt — fehlt
    // er oder steht eine Fehlerklasse daneben, wird nicht geraten.
    const panelEl = await aufbewahrterPrueflauf([
      aufbewahrt({
        id: 1,
        startedAt: startedAt(0),
        mode: 'REVIEW',
        processedCount: 0,
        skippedCount: 4,
        items: [
          {
            id: 11,
            cardNumber: 700,
            title: 'Paket A',
            state: 'GREY',
            excerpt: "kein Label 'review:offen'",
          },
          {
            id: 12,
            cardNumber: 701,
            title: 'Paket B',
            state: 'GREY',
            excerpt: 'Über die Obergrenze (--max) hinaus — bleibt liegen',
          },
          // Ohne Auszug bleibt nur die graue Farbe — sie allein sagt nicht, warum.
          { id: 13, cardNumber: 702, title: 'Paket C', state: 'GREY' },
          // Eine Fehlerklasse trägt kein übersprungener und kein liegengebliebener Vorgang.
          {
            id: 14,
            cardNumber: 703,
            title: 'Paket D',
            state: 'GREY',
            errorClass: 'DEPENDENCY_UNMET',
            excerpt: 'Abhaengigkeit #999 liegt nicht in Done.',
          },
        ],
      }),
    ])

    expect(gesichtet(panelEl)).toHaveTextContent('4 Karten gesichtet')
    expect(bearbeitet(panelEl)).toHaveTextContent('0 bearbeitet')
    // Der eine sicher zuzuordnende Grund steht für sich; die beiden unklaren werden ihm nicht
    // zugeschlagen.
    expect(grundzeilen(panelEl)).toEqual(["1 übersprungen: kein Label 'review:offen'"])
    expect(within(panelEl).getByTestId('aufschluesselung-liegengeblieben')).toHaveTextContent(
      '1 liegengeblieben',
    )
    expect(within(panelEl).getByTestId('aufschluesselung-unbekannt')).toHaveTextContent(
      '2 ohne bekannten Ausgang',
    )
  })

  it('führt den Erzeugungs-Lauf mit übersprungenen, liegengebliebenen und bearbeiteten (Punkt 11)', async () => {
    renderPage({ listen: [[]] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_ERZEUGUNG_STAND, 'night-run-2026-09-09-125621.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_ERZEUGUNG_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('aufschluesselung')

    // 32 + 3 + 3 = 38 — die Summe der Teilmengen ist die Zahl der gesichteten Karten.
    expect(gesichtet(panelEl)).toHaveTextContent('38 Karten gesichtet')
    expect(bearbeitet(panelEl)).toHaveTextContent('3 bearbeitet')
    expect(grundzeilen(panelEl)).toEqual(["32 übersprungen: kein Label 'kit:nightplan'"])
    expect(within(panelEl).getByTestId('aufschluesselung-liegengeblieben')).toHaveTextContent(
      '3 liegengeblieben',
    )
    // Dieselbe Zahl wie in der unveränderten Kopfzeile des Laufs (AK 2).
    expect(within(panelEl).getByText('3 bearbeitet, 35 übergangen')).toBeInTheDocument()
  })

  it('zeigt die Kartennummern einer Grund-Zeile erst beim Aufklappen (Punkt 12)', async () => {
    const panelEl = await frischGeparsterPrueflauf()

    // Zugeklappt steht in der Aufschlüsselung nur die Zahl — die Zeilenliste darunter führt die
    // Karte weiterhin einzeln, und genau sie soll der Betrachter nicht mehr durchsehen müssen.
    const block = within(panelEl).getByTestId('aufschluesselung')
    expect(within(block).queryByText(/#683/)).not.toBeInTheDocument()

    fireEvent.click(within(panelEl).getByRole('button', { name: '2 übersprungen: Idee ([Idee])' }))

    const zeile = within(panelEl).getByTestId('aufschluesselung-grund-2')
    expect(within(zeile).getByText(/^#683/)).toBeInTheDocument()
    expect(within(zeile).getByText(/^#670/)).toBeInTheDocument()
  })

  it('lässt an einem Lauf ohne übersprungene Karte die Grund-Zeilen weg (Punkt 13)', async () => {
    // Konstruiert: Jeder der fünf Ergebnisstände hat übersprungene Karten.
    const OHNE_UEBERSPRUNGENE = stand({
      art: 'review',
      stufe: 'issue',
      einheiten: [einheit({ ausgang: 'mitBefund' })],
    })
    renderPage({
      submit: { ergebnis: alleNeu(OHNE_UEBERSPRUNGENE) },
      listen: [[], wieAufbewahrt(OHNE_UEBERSPRUNGENE)],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(OHNE_UEBERSPRUNGENE)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('aufschluesselung')

    expect(gesichtet(lauf(0))).toHaveTextContent('1 Karten gesichtet')
    expect(bearbeitet(lauf(0))).toHaveTextContent('1 bearbeitet')
    expect(within(lauf(0)).queryAllByTestId(/^aufschluesselung-grund-\d+$/)).toHaveLength(0)
  })

  it('benennt einen übersprungenen Vorgang ohne Grundtext, statt eine leere Zeile zu zeigen', async () => {
    // Konstruiert: `night.mjs` schreibt zu jedem Überspringen einen Grund; fehlte er, stünde hier
    // sonst „1 übersprungen: " ohne Aussage. Der Ausgang ist bekannt, nur sein Grund nicht — das
    // ist etwas anderes als „ohne bekannten Ausgang".
    const OHNE_GRUND = stand({
      art: 'erzeugung',
      stufe: 'plan',
      einheiten: [
        einheit({ ausgang: 'uebersprungen', dauerMs: undefined }),
        // `offen` ist grau, aber weder übersprungen noch liegengeblieben.
        einheit({ id: '701', titel: 'Paket B', ausgang: 'offen', dauerMs: undefined }),
      ],
    })
    renderPage({ listen: [[]] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(OHNE_GRUND)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByTestId('aufschluesselung')

    expect(grundzeilen(lauf(0))).toEqual(['1 übersprungen: ohne genannten Grund'])
    expect(within(lauf(0)).getByTestId('aufschluesselung-unbekannt')).toHaveTextContent(
      '1 ohne bekannten Ausgang',
    )
  })

  it('zeigt an einem Umsetzungs-Lauf das Laufband und keine Aufschlüsselung (Punkt 14)', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_STAND, 'night-run-2026-09-07-085229.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTER_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('laufband')

    expect(within(panelEl).queryByTestId('aufschluesselung')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Kennzahlenzeile je Lauf-Art (#874)', () => {
  /**
   * Der Hinweis, den `night.mjs` genau dann an den Lauf-Kopf schreibt, wenn weder `--verbose`
   * noch `--kette` gesetzt war — wörtlich wie dort und wie in `nightRunErgebnisstand.test.ts`.
   */
  const KENNZAHLEN_HINWEIS =
    'Ohne --verbose fordert der Runner die Stream-Ausgabe der Session nicht an; die Session-Kennzahlen fehlen darum in allen Einheiten.'

  /**
   * Klappt einen frisch eingelesenen Lauf auf und gibt sein Panel zurück. Die Kennzahlenzeile
   * steht **nur** am Ergebnisstand dieser Sitzung — Kosten und Züge verlassen den Browser nie
   * (Plan #718, A1), der Server bewahrt sie nicht auf.
   */
  async function eingelesen(
    ergebnisstand: string,
    start: string,
    dateiname: string,
    antworten: Antworten,
  ) {
    renderPage(antworten)
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ergebnisstand, dateiname)

    const panelEl = await screen.findByTestId(`lauf-${start}`)
    panelAufklappen(panelEl)
    // Beide Formen: Ketten- und Umsetzungs-Lauf tragen seit #915 die Reihe des Entwurfs, die
    // beiden Altbestand-Arten weiter die bisherige Zeile.
    await within(panelEl).findByTestId(KENNZAHLEN_REIHE)
    return panelEl
  }

  /**
   * Die Kennzahlenzeile eines aufgeklappten Laufs. Umsetzungs-Läufe tragen seit #915 die Reihe des
   * Entwurfs, die beiden Altbestand-Arten weiter die bisherige Zeile — dieselben Werte in beiden.
   */
  const zeile = (panelEl: HTMLElement) => within(within(panelEl).getByTestId(KENNZAHLEN_REIHE))

  const echterUmsetzungslauf = () =>
    eingelesen(ECHTER_STAND, ECHTER_START, 'night-run-2026-09-07-085229.json', {
      submit: { ergebnis: alleNeu(ECHTER_STAND) },
      listen: [[], wieAufbewahrt(ECHTER_STAND)],
    })

  it('zählt den grünen und den gelben Vorgang als erledigt (Punkt 7)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // #767 und #770 sind erfolgreich beendet, tragen aber `pruefung.zustand: "ungeprueft"` und
    // werden dadurch **gelb**. Engt man „erledigt" auf den grünen Zustand ein, steht hier
    // „0 von 5", obwohl zwei Pakete fertig wurden — die Gegenprobe zu Plan #864, E6.
    expect(zeile(panelEl).getByText('2 von 5')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Vorgänge erledigt')).toBeInTheDocument()
  })

  it('rechnet die Kosten der Nacht aus den fünf Einzelwerten und kennzeichnet sie (Punkt 8)', async () => {
    const panelEl = await echterUmsetzungslauf()

    // 2,1366 + 5,1342 + 10,8605 + 1,6051 + 8,1324 = 27,8688 US-Dollar. Der Stand selbst führt
    // keine Summe — nur die Kette schreibt eine (Plan #864, E9).
    expect(zeile(panelEl).getByText('27,87 $')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Kosten der Nacht')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('gerechnet, nicht im Protokoll')).toBeInTheDocument()
    // 38 + 63 + 123 + 33 + 121 Züge, dazu die Summe der fünf Vorgangsdauern.
    expect(zeile(panelEl).getByText('378')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('51,5 min')).toBeInTheDocument()
  })

  it('nennt am Prüf-Lauf gesichtete zu bearbeiteten Karten und keine erledigten (Punkt 9)', async () => {
    const panelEl = await eingelesen(
      ECHTER_PRUEFLAUF_STAND,
      ECHTER_PRUEFLAUF_START,
      'night-run-2026-09-11-103116.json',
      {
        submit: { ergebnis: alleNeu(ECHTER_PRUEFLAUF_STAND) },
        listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
      },
    )

    expect(zeile(panelEl).getByText('1 von 35')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Karten bearbeitet')).toBeInTheDocument()
    // „Erledigt" ergibt für einen Prüf-Lauf keinen Sinn — er erledigt keine Vorgänge, er sichtet
    // Karten. Die Kennzahl erscheint nicht und bekommt auch keinen Platzhalter.
    expect(zeile(panelEl).queryByText(/erledigt/)).not.toBeInTheDocument()
  })

  it('nennt am Erzeugungs-Lauf die entstandenen Dokumente (Punkt 10)', async () => {
    const panelEl = await eingelesen(
      ECHTE_ERZEUGUNG_STAND,
      ECHTE_ERZEUGUNG_START,
      'night-run-2026-09-09-125621.json',
      { listen: [[]] },
    )

    // #479, #533 und #535 haben je ein Dokument hinterlassen; die 35 aussortierten Karten keines.
    expect(zeile(panelEl).getByText('3')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Dokumente entstanden')).toBeInTheDocument()
    expect(zeile(panelEl).queryByText(/erledigt/)).not.toBeInTheDocument()
    // #535 wurde in einer früheren Nacht fortgesetzt und meldet gar keine Kennzahlen — die Summe
    // der beiden übrigen stimmt, sie ist nur unvollständig (Punkt 12 an echten Daten).
    expect(zeile(panelEl).getByText('14,88 $')).toBeInTheDocument()
    expect(
      zeile(panelEl).getByText('gerechnet, unvollständig — ein Vorgang ohne Kostenmeldung'),
    ).toBeInTheDocument()
  })

  it('zeigt den Kennzahlen-Hinweis einmal am Lauf statt jeder Fehlanzeige (Punkt 11)', async () => {
    // Konstruiert: Keines der fünf Protokolle stammt aus einem Lauf ohne die ausführliche Ausgabe
    // — dabei ist genau das der Regelfall eines Umsetzungs-Laufs (Plan #864, E8).
    const MIT_HINWEIS = stand({
      kennzahlenHinweis: KENNZAHLEN_HINWEIS,
      einheiten: [
        einheit({ ausgang: 'erfolg', commit: 'a1b2c3d', pruefung: GEPRUEFT }),
        einheit({ id: '701', titel: 'Paket B', ausgang: 'fehlschlag', pruefung: NACHWEIS_ROT }),
      ],
    })
    const panelEl = await eingelesen(MIT_HINWEIS, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(MIT_HINWEIS) },
      listen: [[], wieAufbewahrt(MIT_HINWEIS)],
    })

    expect(screen.getAllByText(KENNZAHLEN_HINWEIS)).toHaveLength(1)
    expect(within(panelEl).queryByText('Kosten unbekannt')).not.toBeInTheDocument()
    expect(within(panelEl).queryByText('Züge unbekannt')).not.toBeInTheDocument()
    // Je Vorgang bleibt die gemessene Dauer stehen; die drei Fehlanzeigen für die nicht
    // angeforderten Kennzahlen entfallen.
    for (const nummer of [700, 701]) {
      const vorgang = within(panelEl).getByTestId(`kennzahlen-${nummer}`)
      expect(vorgang).toHaveTextContent('7 Min')
      expect(vorgang.textContent).not.toContain('nicht gemeldet')
    }
  })

  it('weist eine unvollständige Summe aus und kennt keine Null (Punkt 12)', async () => {
    // Konstruiert: Ein Umsetzungs-Lauf, in dem ein Vorgang Kennzahlen meldet und der andere nicht.
    const TEILWEISE = stand({
      einheiten: [
        einheit({ ausgang: 'erfolg', pruefung: GEPRUEFT, kennzahlen: { kostenUsd: 1.5, zuege: 7 } }),
        einheit({ id: '701', titel: 'Paket B', ausgang: 'fehlschlag', pruefung: NACHWEIS_ROT }),
      ],
    })
    const panelEl = await eingelesen(TEILWEISE, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(TEILWEISE) },
      listen: [[], wieAufbewahrt(TEILWEISE)],
    })

    expect(zeile(panelEl).getByText('1,50 $')).toBeInTheDocument()
    expect(
      zeile(panelEl).getByText('gerechnet, unvollständig — ein Vorgang ohne Kostenmeldung'),
    ).toBeInTheDocument()
  })

  it('sagt ohne jede Kostenangabe und ohne Hinweis „Kosten unbekannt" (Punkt 12)', async () => {
    const panelEl = await eingelesen(VIER_ZUSTAENDE, startedAt(0), 'night-run.json', {
      submit: { ergebnis: alleNeu(VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(VIER_ZUSTAENDE)],
    })

    // „0,00 $" behauptete eine Nacht ohne Kosten; gemeldet wurde nur nichts.
    expect(zeile(panelEl).getByText('Kosten unbekannt')).toBeInTheDocument()
    expect(zeile(panelEl).getByText('Züge unbekannt')).toBeInTheDocument()
    expect(zeile(panelEl).queryByText(/0,00 \$/)).not.toBeInTheDocument()
    expect(zeile(panelEl).queryByText(/gerechnet/)).not.toBeInTheDocument()
    // Ohne den Kopf-Hinweis bleiben die Fehlanzeigen je Vorgang stehen: Hier fehlen die
    // Kennzahlen wirklich, statt gar nicht erst angefordert worden zu sein.
    expect(within(panelEl).getByTestId('kennzahlen-700')).toHaveTextContent('Kosten nicht gemeldet')
  })

  it('zeigt an einem Ketten-Lauf keine Kennzahlenzeile, sondern die Übersicht', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(ECHTE_KETTE_STAND) },
      listen: [[], wieAufbewahrt(ECHTE_KETTE_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTE_KETTE_STAND, 'night-run-2026-09-14-131200.json')

    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)
    panelAufklappen(panelEl)
    await within(panelEl).findByTestId('ketten-uebersicht')

    // Die Kette führt ihre Kennzahlen bereits in der Übersicht (#866) — und ihre Kostensumme
    // steht im Stand, statt gerechnet zu werden.
    expect(within(panelEl).queryByTestId('lauf-kennzahlen')).not.toBeInTheDocument()
  })

  it('zeigt an einem aufbewahrten Lauf ohne Ergebnisstand keine Kennzahlenzeile', async () => {
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            items: [
              { id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN', durationMs: SIEBEN_MIN },
            ],
          }),
        ],
      ],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)
    await within(lauf(0)).findByText('Vorhaben: ohne')

    // Kosten und Züge bewahrt der Server nicht auf — eine Zeile aus lauter Fehlanzeigen wäre
    // dieselbe Wand, die der Kennzahlen-Hinweis vermeidet.
    expect(within(lauf(0)).queryByTestId('lauf-kennzahlen')).not.toBeInTheDocument()
  })
})

describe('NightRunPage — Rahmen des Entwurfs (#914)', () => {
  /** Zwei aufbewahrte Läufe, der spätere steht oben. */
  const zweiAufbewahrte = () => ({
    listen: [
      [
        aufbewahrt({
          id: 1,
          startedAt: startedAt(0),
          items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' as const }],
        }),
        aufbewahrt({
          id: 2,
          startedAt: startedAt(30),
          items: [{ id: 21, cardNumber: 701, title: 'Paket B', state: 'GREEN' as const }],
        }),
      ],
    ],
    karten: {
      700: karte({ id: 1, number: 700, title: 'Paket A' }),
      701: karte({ id: 2, number: 701, title: 'Paket B' }),
    },
  })

  it('zeigt den obersten Lauf offen und den zweiten zugeklappt', async () => {
    // AK 2: Kopf, Kennzahlenreihe und erster Vorgangsblock ohne Scrollen. Genau der oberste
    // Lauf, nicht alle (E7) — alle aufzuklappen löste die Anfragelawine aus, die A8 vermeidet.
    renderPage(zweiAufbewahrte())

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    expect(within(lauf(30)).getByRole('button', { expanded: true })).toBeInTheDocument()
    expect(within(lauf(0)).getByRole('button', { expanded: false })).toBeInTheDocument()
  })

  it('lädt die Herkunftskette des obersten Laufs genau einmal, auch bei erneutem Rendern', async () => {
    const { rerender } = renderPage(zweiAufbewahrte())

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    await waitFor(() => expect(byNumberAufrufe()).toHaveLength(1))

    rerender(
      <ThemeProvider theme={theme}>
        <SnackbarProvider>
          <MemoryRouter initialEntries={['/projects/5/nachtlauf']}>
            <Routes>
              <Route path="/projects/:projectId/nachtlauf" element={<NightRunPage />} />
            </Routes>
          </MemoryRouter>
        </SnackbarProvider>
      </ThemeProvider>,
    )

    // `geladeneLaeufe` ist die Stelle, die das sicherstellt — ein zweiter Abruf derselben Kette
    // wäre für den Nutzer unsichtbar und für den Server eine verdoppelte Last.
    await waitFor(() => expect(byNumberAufrufe()).toHaveLength(1))
  })

  it('legt das Theme des Entwurfs über den Inhaltsbereich', async () => {
    renderPage(zweiAufbewahrte())

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    const knopf = screen.getByRole('button', { name: 'Protokoll einlesen' })
    expect(getComputedStyle(knopf).fontFamily).toContain('IBM Plex Sans')
  })

  it('lässt den Kartendialog außerhalb des Theme-Teilbaums', async () => {
    renderPage(zweiAufbewahrte())

    await screen.findByTestId(`lauf-${startedAt(30)}`)
    aufklappen(30)
    fireEvent.click(await within(lauf(30)).findByRole('button', { name: /#701/ }))

    // Nicht-Ziel 2: Der Dialog gehört zur übrigen Anwendung und trägt deren Designsprache.
    const detail = await screen.findByTestId('karten-detail')
    expect(detail.dataset.schrift).toContain('Carlito')
    expect(detail.dataset.schrift).not.toContain('IBM Plex Sans')
  })

  it('hält Brotkrumenpfad, Protokoll-Knopf und die Meldung zu einem nicht deutbaren Stand', async () => {
    // AK 10: Wo der Entwurf kein Element vorsieht, bleibt es in seiner Funktion erhalten.
    renderPage({ listen: [[]] })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    expect(screen.getByRole('link', { name: 'Projekte' })).toBeInTheDocument()
    expect(screen.getByText('Nachtlauf')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Protokoll einlesen' })).toBeInTheDocument()
    expect(screen.getByLabelText('Protokolldatei auswählen')).toBeInTheDocument()

    protokollWaehlen('{kein json')

    expect(await screen.findByText(/^Nicht auswertbar —/)).toBeInTheDocument()
  })
})

describe('NightRunPage — Kopf und Kennzahlenreihe der Nacht (#915)', () => {
  /** Liest einen Ergebnisstand ein und gibt das Panel seines Laufs zurück. */
  async function nachEinlesen(standText: string, start: string, dateiname: string) {
    renderPage({
      submit: { ergebnis: alleNeu(standText) },
      listen: [[], wieAufbewahrt(standText)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(standText, dateiname)
    return await screen.findByTestId(`lauf-${start}`)
  }

  it('gibt dem Ketten-Lauf den Kopf des Entwurfs mit seiner Vorzeile', async () => {
    const panelEl = await nachEinlesen(
      ECHTE_KETTE_STAND,
      ECHTE_KETTE_START,
      'night-run-2026-09-14-131200.json',
    )

    expect(within(panelEl).getByTestId('nachtlauf-vorzeile')).toHaveTextContent('Nachtlauf · Kette')
    expect(within(panelEl).getByTestId('nachtlauf-kennzahlen')).toBeInTheDocument()
  })

  it('gibt dem Umsetzungs-Lauf den Kopf des Entwurfs mit seiner Vorzeile', async () => {
    const panelEl = await nachEinlesen(
      ECHTER_STAND,
      ECHTER_START,
      'night-run-2026-09-07-085229.json',
    )

    expect(within(panelEl).getByTestId('nachtlauf-vorzeile')).toHaveTextContent(
      'Nachtlauf · Umsetzung',
    )
    expect(within(panelEl).getByTestId('nachtlauf-kennzahlen')).toBeInTheDocument()
  })

  it('lässt den Prüf-Lauf bei seiner heutigen Darstellung (E5, Altbestand)', async () => {
    const panelEl = await nachEinlesen(
      ECHTER_PRUEFLAUF_STAND,
      ECHTER_PRUEFLAUF_START,
      'night-run-2026-09-11-103116.json',
    )

    expect(within(panelEl).queryByTestId('nachtlauf-vorzeile')).not.toBeInTheDocument()
    expect(within(panelEl).getByTestId('aufschluesselung')).toBeInTheDocument()
    expect(within(panelEl).getByTestId('lauf-kennzahlen')).toBeInTheDocument()
  })

  it('lässt den Erzeugungs-Lauf bei seiner heutigen Darstellung (E5, Altbestand)', async () => {
    // Nachtplan-Läufe bleiben browser-only (Plan #803, Entscheidung 8) — sie gehen nicht an den
    // Server und kommen deshalb auch nicht aus der Liste zurück.
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')
    protokollWaehlen(ECHTE_ERZEUGUNG_STAND, 'night-run-2026-09-09-125621.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_ERZEUGUNG_START}`)
    panelAufklappen(panelEl)

    expect(within(panelEl).queryByTestId('nachtlauf-vorzeile')).not.toBeInTheDocument()
    expect(await within(panelEl).findByTestId('aufschluesselung')).toBeInTheDocument()
    expect(within(panelEl).getByTestId('lauf-kennzahlen')).toBeInTheDocument()
  })

  it.each([
    ['Ketten-Lauf', ECHTE_KETTE_STAND, ECHTE_KETTE_START, 'night-run-2026-09-14-131200.json'],
    ['Umsetzungs-Lauf', ECHTER_STAND, ECHTER_START, 'night-run-2026-09-07-085229.json'],
  ])(
    'nennt die Herkunft des Stands in der Metazeile des %s (AK 9, Fall 3)',
    async (_art, standText, start, dateiname) => {
      const panelEl = await nachEinlesen(standText, start, dateiname)
      expect(within(panelEl).getByTestId('nachtlauf-meta')).toHaveTextContent('Ergebnisstand')
    },
  )

  it('vermerkt am Kostenwert der Kette die Sitzungen ohne Kostenmeldung (AK 9, Fall 1)', async () => {
    const panelEl = await nachEinlesen(
      ECHTE_KETTE_STAND,
      ECHTE_KETTE_START,
      'night-run-2026-09-14-131200.json',
    )

    const kosten = within(panelEl).getByTestId('nachtlauf-kennzahl-Kosten der Nacht')
    expect(kosten).toHaveTextContent(/ohne Kostenmeldung/)
  })

  it('gibt einem aufbewahrten Ketten-Lauf ohne Stand denselben Kopf, aber keine Kennzahlen (E6)', async () => {
    renderPage({
      listen: [wieAufbewahrt(ECHTE_KETTE_STAND)],
    })
    const panelEl = await screen.findByTestId(`lauf-${ECHTE_KETTE_START}`)

    // Derselbe Kopf — die Gestaltung hängt an der Lauf-Art, nicht am Vorliegen eines Stands.
    expect(within(panelEl).getByTestId('nachtlauf-vorzeile')).toHaveTextContent('Nachtlauf · Kette')
    // Kosten und Züge bewahrt der Server nicht auf; eine Reihe aus lauter Fehlanzeigen wäre
    // dieselbe Wand, die der Bestand schon vermeidet.
    expect(within(panelEl).queryByTestId('nachtlauf-kennzahlen')).not.toBeInTheDocument()
    expect(within(panelEl).getByTestId('nachtlauf-meta')).toHaveTextContent('Herkunft unbekannt')
  })
})

describe('NightRunPage — Altbestand-Arten behalten ihre Kopfzeile (#915)', () => {
  it('zeigt ungedeutete Zeilen eines Prüf-Laufs weiter als eigenen Chip', async () => {
    // Die Chip-Zeile bleibt für `REVIEW` und `NIGHTPLAN` in Kraft (E5). Ohne diesen Nachweis wäre
    // der Zweig nach dem Umbau ungeprüft, obwohl er erreichbar ist.
    renderPage({
      listen: [
        [
          aufbewahrt({
            id: 1,
            startedAt: startedAt(0),
            mode: 'REVIEW',
            unparsedCount: 1,
            unparsedSample: UNGEDEUTET,
            items: [{ id: 11, cardNumber: 700, title: 'Paket A', state: 'GREEN' }],
          }),
        ],
      ],
    })
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    expect(within(lauf(0)).getByText('Ungedeutete Zeilen: 1')).toBeInTheDocument()
    expect(within(lauf(0)).queryByTestId('nachtlauf-vorzeile')).not.toBeInTheDocument()
  })

  it('zeigt den Kennzahlen-Hinweis eines Prüf-Laufs in dessen bisheriger Zeile', async () => {
    // Der echte Prüf-Lauf, ergänzt um den Hinweis: Seine Ausgänge sind prüfungseigen, ein
    // selbstgebauter Stand mit `ausgang: 'erfolg'` wäre gar nicht deutbar.
    const PRUEFUNG_MIT_HINWEIS = JSON.stringify({
      ...echterPrueflauf,
      kennzahlenHinweis: 'Kennzahlen nicht angefordert',
    })
    renderPage({
      submit: { ergebnis: alleNeu(PRUEFUNG_MIT_HINWEIS) },
      listen: [[], wieAufbewahrt(PRUEFUNG_MIT_HINWEIS)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PRUEFUNG_MIT_HINWEIS, 'night-run-2026-09-11-103116.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTER_PRUEFLAUF_START}`)
    panelAufklappen(panelEl)

    expect(await within(panelEl).findByTestId('lauf-kennzahlen-hinweis')).toHaveTextContent(
      'Kennzahlen nicht angefordert',
    )
  })

  it('kennzeichnet einen schon bekannten Prüf-Lauf weiter mit seinem eigenen Chip', async () => {
    // Die Gegenprobe zu „neu angelegt": Auch der zweite Zustand des Einlieferungs-Chips gehört zu
    // den beiden Altbestand-Arten und bleibt dort in seiner bisherigen Form.
    renderPage({
      submit: { ergebnis: [{ startedAt: ECHTER_PRUEFLAUF_START, created: false }] },
      listen: [[], wieAufbewahrt(ECHTER_PRUEFLAUF_STAND)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(ECHTER_PRUEFLAUF_STAND, 'night-run-2026-09-11-103116.json')
    const panelEl = await screen.findByTestId(`lauf-${ECHTER_PRUEFLAUF_START}`)

    expect(await within(panelEl).findByText('lag schon vor')).toBeInTheDocument()
  })
})
