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
import type { CardByNumber } from '../api/cards'
import type {
  NightRunErrorClassCounts,
  NightRunItemView,
  NightRunResult,
  NightRunView,
} from '../api/nightRuns'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { buildHandoffText, type NightRunHandoffItem } from '../lib/nightRunHandoff'
import { parseNightRunLog } from '../lib/nightRunLog'
import { theme } from '../theme'
import { NightRunPage } from './NightRunPage'

/**
 * Die Seite wird gegen einen **`fetch`-Stub** getestet, nicht gegen gemockte API-Module. Nur so
 * ist das Kriterium aus Issue #725 überhaupt prüfbar, dass beim Hineingeben eines Protokolls
 * **kein** Request seinen Inhalt trägt: Ein gemocktes `nightRunsApi` erzeugte gar keine Requests,
 * und der Test wäre grün, ohne etwas zu belegen.
 *
 * Fixtures sind anonymisiert (wie in `lib/nightRunLog.test.ts`, Issue #720): Titel nach dem Schema
 * `Paket N`, keine Pfade, keine Sitzungs-IDs.
 */

// Das Karten-Detail ist separat getestet (CardDetailModal.test.tsx) — hier ein Stub, der die
// geöffnete Karte sichtbar macht und den Schließen-Pfad auslöst.
vi.mock('../components/CardDetailModal', () => ({
  CardDetailModal: ({ card, onClose }: { card: { number: number | null }; onClose: () => void }) => (
    <div data-testid="karten-detail">
      Karte {card.number}
      <button type="button" onClick={onClose}>
        detail-schliessen
      </button>
    </div>
  ),
}))

/** Runner-Zeile mit Zeitstempel-Präfix, wie `log()` in `night.mjs` sie schreibt. */
const z = (minute: number, text: string) =>
  `[2026-09-01T22:${String(minute).padStart(2, '0')}:00.000Z] ${text}`

const START = (minute: number, modus = 'Implementierung') =>
  z(minute, `Nacht-Runner startet (Modus ${modus}, max 5 Sessions, Modell claude-opus-5, Label none)`)

const ENDE = (minute: number) =>
  z(minute, 'Nacht-Runner beendet: 1 erfolgreich, 0 zurueckgestellt, 1 Session(s) gestartet.')

/** Startzeitpunkt eines mit {@link START} eröffneten Laufs — der Schlüssel der Auswertung. */
const startedAt = (minute: number) => `2026-09-01T22:${String(minute).padStart(2, '0')}:00.000Z`

/**
 * Markierungstext in einer Sitzungsstrom-Zeile (ohne Zeitstempel-Präfix). Er darf in keinem
 * Request auftauchen — weder in einer URL noch in einem Body.
 */
const GEHEIM = 'GEHEIM-NICHT-SENDEN'

const PROTOKOLL_EIN_LAUF = [
  START(0),
  z(1, 'Session 1/5: Issue #700 — Paket A'),
  `{"type":"assistant","text":"${GEHEIM}"}`,
  z(8, '  Erfolg nach 7 min, Commit a1b2c3d, Issue #700 in In review.'),
  ENDE(10),
].join('\n')

/** Zwei echte Läufe, dazwischen ein Probelauf — er erzeugt keine Auswertung. */
const PROTOKOLL_ZWEI_LAEUFE_UND_PROBELAUF = [
  START(0),
  z(1, 'Session 1/5: Issue #700 — Paket A'),
  z(8, '  Erfolg nach 7 min, Commit a1b2c3d, Issue #700 in In review.'),
  ENDE(10),
  START(20),
  z(21, 'Dry-Run beendet: 2 Session(s) wuerden starten.'),
  START(30),
  z(31, 'Session 1/5: Issue #701 — Paket B'),
  z(38, '  Erfolg nach 7 min, Commit b2c3d4e, Issue #701 in In review.'),
  ENDE(40),
].join('\n')

const PROTOKOLL_NUR_PROBELAEUFE = [
  START(0),
  z(1, 'Dry-Run beendet: 2 Session(s) wuerden starten.'),
  START(10),
  z(11, 'Dry-Run beendet: 1 Session(s) wuerden starten.'),
].join('\n')

/** Erfolgsmeldung, aber roter Prüfblock — der Fall, für den es die Auswertung gibt. */
const PROTOKOLL_GELB = [
  START(0),
  z(1, 'Session 1/5: Issue #700 — Paket A'),
  z(8, '  Erfolg nach 7 min, Commit a1b2c3d, Issue #700 in In review.'),
  z(9, 'Pruefungen der Sessions:'),
  z(9, '  Issue #700: gelaufen: npm test -> rot (Frontend) | ausgelassen: keine'),
  ENDE(10),
].join('\n')

/** Alle vier Zustände in einem Lauf. */
const PROTOKOLL_VIER_ZUSTAENDE = [
  START(0),
  z(1, 'Session 1/5: Issue #700 — Paket A'),
  z(8, '  Erfolg nach 7 min, Commit a1b2c3d, Issue #700 in In review.'),
  z(9, 'Session 2/5: Issue #701 — Paket B'),
  z(16, '  Erfolg nach 7 min, Commit b2c3d4e, Issue #701 in In review.'),
  z(17, 'Session 3/5: Issue #702 — Paket C'),
  z(24, '  Fehlschlag nach 7 min: Issue #702 nicht in In review, Tree sauber — Issue ins Backlog, weiter.'),
  z(25, '  #703 Paket D -> uebersprungen (Abhaengigkeit #999 liegt nicht in Done)'),
  z(26, 'Pruefungen der Sessions:'),
  z(26, '  Issue #701: gelaufen: npm test -> rot (Frontend) | ausgelassen: keine'),
  ENDE(30),
].join('\n')

const PROTOKOLL_PRUEF_LAUF = [
  START(0, 'Review'),
  z(1, 'Review-Session 1/5: Issue #700 — Paket A'),
  z(8, '  Erfolg nach 7 min: Issue #700 geprueft mit Befund.'),
  z(10, 'Nacht-Review beendet (Stufe issue): 1 geprueft.'),
].join('\n')

/** Eine Runner-Zeile, die kein Muster deutet — mit Markdown-Zeichen im Text. */
const UNGEDEUTET = 'Voellig unbekannte Runner-Zeile mit *Sternchen* und `Backticks`'

const PROTOKOLL_UNGEDEUTET = [
  START(0),
  z(1, 'Session 1/5: Issue #700 — Paket A'),
  z(2, UNGEDEUTET),
  z(8, '  Erfolg nach 7 min, Commit a1b2c3d, Issue #700 in In review.'),
  ENDE(10),
].join('\n')

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
 * Die Läufe eines Protokolls so, wie der Server sie nach dem Einliefern zurückgibt. Die Seite lädt
 * nach erfolgreichem Senden die Liste neu — ohne diese Nachbildung zeigte jeder Sendetest danach
 * eine leere Seite. Der Helfer bildet nur die Feldabbildung des Servers nach; **welchen** Zustand
 * ein Arbeitspaket trägt, prüfen die Tests weiterhin am sichtbaren Text.
 */
function wieAufbewahrt(protokoll: string): NightRunView[] {
  return parseNightRunLog(protokoll).runs.map((run, index) => ({
    id: index + 1,
    startedAt: run.startedAt,
    mode: run.mode,
    durationMs: run.durationMs,
    processedCount: run.processedCount,
    skippedCount: run.skippedCount,
    unparsedCount: run.unparsedCount,
    unparsedSample: run.unparsedSample.length === 0 ? null : run.unparsedSample.join('\n'),
    createdAt: '2026-09-02T06:00:00.000Z',
    items: run.items.map((item, position) => wieAufbewahrtesItem({ id: position + 1, ...item })),
  }))
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

/** Die Einlieferungs-Antwort zu einem Protokoll, in dem jeder Lauf neu ist. */
const alleNeu = (protokoll: string): NightRunResult[] =>
  parseNightRunLog(protokoll).runs.map((run) => ({ startedAt: run.startedAt, created: true }))

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
  /** Ergebnis des `POST`; `fehler` erzeugt stattdessen eine 400-Antwort. */
  submit?: { ergebnis?: NightRunResult[]; fehler?: string }
  /** Je `GET /night-runs/error-class-counts` eine Antwort; die letzte gilt für alle weiteren Aufrufe. */
  zaehler?: NightRunErrorClassCounts[]
  /** Statt der Häufigkeiten eine Fehlerantwort — der Fehlerpfad aus Issue #726. */
  zaehlerFehler?: string
  /** Karten je projektweiter Nummer; ein fehlender Eintrag antwortet mit 404. */
  karten?: Record<number, CardByNumber>
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
        return Promise.resolve(
          antworten.submit?.fehler === undefined
            ? antwortOk(antworten.submit?.ergebnis ?? [])
            : antwortFehler(antworten.submit.fehler),
        )
      }
      const nummer = /^\/api\/projects\/5\/cards\/by-number\/(\d+)$/.exec(url)
      if (nummer) {
        const gefunden = antworten.karten?.[Number(nummer[1])]
        return Promise.resolve(
          gefunden === undefined ? antwortFehler('Karte nicht gefunden', 404) : antwortOk(gefunden),
        )
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

/** Wählt eine Protokolldatei im versteckten Datei-Input aus. */
function protokollWaehlen(inhalt: string, name = 'nacht.log') {
  const input = screen.getByLabelText('Protokolldatei auswählen')
  const datei = new File([inhalt], name, { type: 'text/plain' })
  Object.defineProperty(input, 'files', { value: [datei], configurable: true })
  fireEvent.change(input)
}

/** Das Panel eines Laufs, identifiziert über seinen Startzeitpunkt. */
const lauf = (minute: number) => screen.getByTestId(`lauf-${startedAt(minute)}`)

/** Klappt den Lauf auf; erst dabei wird die Herkunftskette aufgelöst (Plan #718, A8). */
function aufklappen(minute: number) {
  fireEvent.click(within(lauf(minute)).getByRole('button', { expanded: false }))
}

const byNumberAufrufe = () => anfragen.filter((a) => a.url.includes('/cards/by-number/'))

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
    const reihenfolge = screen.getAllByTestId(/^lauf-/).map((el) => el.dataset.testid)
    expect(reihenfolge).toEqual([`lauf-${startedAt(30)}`, `lauf-${startedAt(0)}`])
  })

  it('löst die Herkunftskette beim Öffnen der Seite nicht auf (A8)', async () => {
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
    })

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(byNumberAufrufe()).toHaveLength(0)
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

describe('NightRunPage — Protokoll hineingeben', () => {
  it('erzeugt aus mehreren Läufen mehrere Auswertungen und überspringt Probeläufe', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(PROTOKOLL_ZWEI_LAEUFE_UND_PROBELAUF) },
      listen: [[], wieAufbewahrt(PROTOKOLL_ZWEI_LAEUFE_UND_PROBELAUF)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_ZWEI_LAEUFE_UND_PROBELAUF)

    await waitFor(() => expect(screen.getAllByTestId(/^lauf-/)).toHaveLength(2))
    // Der Probelauf zwischen den beiden echten Läufen erzeugt keine Auswertung.
    expect(screen.queryByTestId(`lauf-${startedAt(20)}`)).not.toBeInTheDocument()
  })

  it('sendet die Auswertung und stellt den neuen Lauf nach oben', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(PROTOKOLL_EIN_LAUF) },
      listen: [
        [aufbewahrt({ id: 9, startedAt: '2026-08-30T22:00:00.000Z' })],
        [aufbewahrt({ id: 9, startedAt: '2026-08-30T22:00:00.000Z' }), ...wieAufbewahrt(PROTOKOLL_EIN_LAUF)],
      ],
    })
    await screen.findByTestId('lauf-2026-08-30T22:00:00.000Z')

    protokollWaehlen(PROTOKOLL_EIN_LAUF)

    await waitFor(() => expect(screen.getAllByTestId(/^lauf-/)).toHaveLength(2))
    expect(screen.getAllByTestId(/^lauf-/)[0].dataset.testid).toBe(`lauf-${startedAt(0)}`)
    expect(within(lauf(0)).getByText('neu angelegt')).toBeInTheDocument()
  })

  it('schickt die Auswertung hinaus, nie das Protokoll selbst', async () => {
    renderPage({ submit: { ergebnis: [{ startedAt: startedAt(0), created: true }] } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_EIN_LAUF)

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
      listen: [[], wieAufbewahrt(PROTOKOLL_EIN_LAUF)],
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_EIN_LAUF)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(within(lauf(0)).getByText('lag schon vor')).toBeInTheDocument()
    aufklappen(0)
    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
  })

  it('meldet ein Protokoll aus lauter Probeläufen, statt eine Auswertung zu erzeugen', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_NUR_PROBELAEUFE)

    expect(
      await screen.findByText('Das Protokoll enthält nur Probeläufe (2) — keine Auswertung.'),
    ).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-/)).toHaveLength(0)
    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('meldet ein Protokoll ohne jeden Lauf', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen('irgendein Text ohne eine einzige Startzeile')

    expect(await screen.findByText('Kein Nachtlauf-Protokoll erkannt')).toBeInTheDocument()
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

    protokollWaehlen(PROTOKOLL_EIN_LAUF)

    expect(await screen.findByText('Die Datei konnte nicht gelesen werden.')).toBeInTheDocument()
    expect(screen.queryAllByTestId(/^lauf-/)).toHaveLength(0)
  })

  it('öffnet nichts, wenn die Auswahl abgebrochen wurde', async () => {
    renderPage()
    await screen.findByText('Noch keine Auswertung vorhanden.')
    const input = screen.getByLabelText('Protokolldatei auswählen')
    Object.defineProperty(input, 'files', { value: null, configurable: true })

    fireEvent.change(input)

    expect(anfragen.some((a) => a.method === 'POST')).toBe(false)
  })

  it('setzt den Datei-Input zurück, damit dasselbe Protokoll erneut gewählt werden kann', async () => {
    renderPage({ submit: { ergebnis: [{ startedAt: startedAt(0), created: true }] } })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_EIN_LAUF)

    expect((screen.getByLabelText('Protokolldatei auswählen') as HTMLInputElement).value).toBe('')
  })

  it('lässt die Auswertung sichtbar, wenn das Senden scheitert, und nennt den Grund', async () => {
    renderPage({
      submit: { fehler: 'Auszug zu lang' },
      karten: { 700: karte({ id: 1, number: 700, title: 'Paket A' }) },
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_EIN_LAUF)

    expect(await screen.findByText('Auszug zu lang')).toBeInTheDocument()
    expect(lauf(0)).toBeInTheDocument()
    aufklappen(0)
    expect(await within(lauf(0)).findByRole('button', { name: /#700 Paket A/ })).toBeInTheDocument()
  })
})

describe('NightRunPage — Zustände, Kennzahlen und Auszüge', () => {
  it('macht jeden der vier Zustände am Text erkennbar, nicht nur an der Farbe', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(PROTOKOLL_VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(PROTOKOLL_VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_VIER_ZUSTAENDE)
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
      submit: { ergebnis: alleNeu(PROTOKOLL_GELB) },
      listen: [[], wieAufbewahrt(PROTOKOLL_GELB)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_GELB)
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
      submit: { ergebnis: alleNeu(PROTOKOLL_VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(PROTOKOLL_VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_VIER_ZUSTAENDE)
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
      submit: { ergebnis: alleNeu(PROTOKOLL_VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(PROTOKOLL_VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    expect(
      await within(lauf(0)).findByText(/Grund:.*#703 Paket D -> uebersprungen \(Abhaengigkeit #999/),
    ).toBeInTheDocument()
  })

  it('kennzeichnet einen Prüf-Lauf als solchen', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(PROTOKOLL_PRUEF_LAUF) },
      listen: [[], wieAufbewahrt(PROTOKOLL_PRUEF_LAUF)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_PRUEF_LAUF)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(within(lauf(0)).getByText('Prüf-Lauf')).toBeInTheDocument()
    expect(within(lauf(0)).queryByText('Umsetzungs-Lauf')).not.toBeInTheDocument()
  })

  it('kennzeichnet einen Umsetzungs-Lauf als solchen', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(PROTOKOLL_EIN_LAUF) },
      listen: [[], wieAufbewahrt(PROTOKOLL_EIN_LAUF)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_EIN_LAUF)

    await screen.findByTestId(`lauf-${startedAt(0)}`)
    expect(within(lauf(0)).getByText('Umsetzungs-Lauf')).toBeInTheDocument()
  })

  it('zeigt Dauer und Stückzahlen je Lauf sowie die Dauer je Arbeitspaket, aber keine Kosten', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(PROTOKOLL_VIER_ZUSTAENDE) },
      listen: [[], wieAufbewahrt(PROTOKOLL_VIER_ZUSTAENDE)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_VIER_ZUSTAENDE)
    await screen.findByTestId(`lauf-${startedAt(0)}`)
    aufklappen(0)

    const panel = within(lauf(0))
    expect(panel.getByText('30 Min')).toBeInTheDocument()
    expect(panel.getByText('3 bearbeitet, 1 übergangen')).toBeInTheDocument()
    expect((await panel.findAllByText('7 Min')).length).toBeGreaterThan(0)
    // Kosten sind ein Nicht-Ziel aus #715 — sie stehen im Protokoll, aber nicht in der Auswertung.
    expect(screen.queryByText(/Kosten|USD|\$/)).not.toBeInTheDocument()
  })

  it('weist ungedeutete Zeilen mit Anzahl und Auszug aus, wörtlich statt gerendert', async () => {
    renderPage({
      submit: { ergebnis: alleNeu(PROTOKOLL_UNGEDEUTET) },
      listen: [[], wieAufbewahrt(PROTOKOLL_UNGEDEUTET)],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_UNGEDEUTET)
    await screen.findByTestId(`lauf-${startedAt(0)}`)

    expect(within(lauf(0)).getByText('Ungedeutete Zeilen: 1')).toBeInTheDocument()
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

  it('löst die Kette erst beim Aufklappen auf und lädt jede Nummer nur einmal', async () => {
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
    expect(byNumberAufrufe()).toHaveLength(0)

    aufklappen(0)

    // Beide Arbeitspakete nennen dieselbe Karte, also erscheint die Kette zweimal.
    expect(await within(lauf(0)).findAllByText(/Plan: #718 \[Plan\] Nachtlauf/)).toHaveLength(2)
    expect(within(lauf(0)).getAllByText(/Fachliche Anforderung: #715 \[Fachlich\] Nachtlauf/)).toHaveLength(2)
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
    expect(within(lauf(0)).getByText(/Fachliche Anforderung: #715/)).toBeInTheDocument()
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

    expect(await within(lauf(0)).findByText(/#718 \[Plan\] Nachtlauf — abgerissen/)).toBeInTheDocument()
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
    fireEvent.click(within(lauf(0)).getByRole('button', { expanded: false }))

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

    expect(await within(lauf(0)).findByText(/Plan: #718 \[Plan\] Nachtlauf/)).toBeInTheDocument()
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
      submit: { ergebnis: alleNeu(PROTOKOLL_GELB) },
      listen: [[], wieAufbewahrt(PROTOKOLL_GELB)],
      zaehler: [{}, { CHECKS_RED: 1 }],
    })
    await screen.findByText('Noch keine Auswertung vorhanden.')

    protokollWaehlen(PROTOKOLL_GELB)
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

    protokollWaehlen(PROTOKOLL_GELB)
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
