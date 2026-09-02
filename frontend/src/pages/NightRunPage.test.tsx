import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CardByNumber } from '../api/cards'
import type { NightRunResult, NightRunView } from '../api/nightRuns'
import { SnackbarProvider } from '../components/SnackbarProvider'
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
    ...(run.unparsedSample.length === 0 ? {} : { unparsedSample: run.unparsedSample.join('\n') }),
    createdAt: '2026-09-02T06:00:00.000Z',
    items: run.items.map((item, position) => ({
      id: position + 1,
      cardNumber: item.cardNumber,
      title: item.title,
      state: item.state,
      ...(item.errorClass === undefined ? {} : { errorClass: item.errorClass }),
      ...(item.durationMs === undefined ? {} : { durationMs: item.durationMs }),
      excerpt: item.excerpt,
    })),
  }))
}

/** Die Einlieferungs-Antwort zu einem Protokoll, in dem jeder Lauf neu ist. */
const alleNeu = (protokoll: string): NightRunResult[] =>
  parseNightRunLog(protokoll).runs.map((run) => ({ startedAt: run.startedAt, created: true }))

function aufbewahrt(partial: Partial<NightRunView> & { id: number; startedAt: string }): NightRunView {
  return {
    mode: 'IMPLEMENTATION',
    durationMs: 10 * 60_000,
    processedCount: 1,
    skippedCount: 0,
    unparsedCount: 0,
    createdAt: '2026-09-02T06:00:00.000Z',
    items: [],
    ...partial,
  }
}

interface Antworten {
  /** Je `GET /night-runs` eine Antwort; die letzte gilt für alle weiteren Aufrufe. */
  listen?: NightRunView[][]
  /** Statt einer Liste eine Fehlerantwort — der Ladepfad beim Öffnen der Seite. */
  listenFehler?: string
  /** Ergebnis des `POST`; `fehler` erzeugt stattdessen eine 400-Antwort. */
  submit?: { ergebnis?: NightRunResult[]; fehler?: string }
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
    // Palette, nicht aus `success`/`warning`/`error` (A15).
    expect(within(lauf(0)).getByTestId('zustand-700')).toHaveStyle({
      color: theme.palette.nightRun.yellow,
    })
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
  const lauf700 = (items: NightRunView['items']) => [
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
