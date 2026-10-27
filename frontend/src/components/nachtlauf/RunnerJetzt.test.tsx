import { ThemeProvider } from '@mui/material/styles'
import { act, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { RunnerJetzt as RunnerJetztDaten, RunnerLauf, RunnerLetzterLauf, RunnerStufe } from '../../api/nightRuns'
import { de } from '../../i18n/de'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { tagZeit } from '../../lib/leitstand'
import { theme } from '../../theme'
import { RunnerJetzt } from './RunnerJetzt'

// Vorschau-Zustand der „Ansicht als XY“ (Plan #1914, E16); außer im eigenen Block ohne Vorschau.
const auth = vi.hoisted(() => ({ vorschau: null as null | Record<string, string> }))
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ vorschau: auth.vorschau }) }))
const VORSCHAU = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }
const HINWEIS = 'Grace sieht diesen Bereich, die Vorschau zeigt ihn nicht.'
const verborgen = () => new ApiError(403, 'Forbidden', undefined, undefined, 'vorschau-verborgen')

/**
 * Der Bereich „Runner jetzt“ auf der Runner-Seite (Issue #1729, Plan #1724 E5, E6, E14, E15): je
 * Lauf Art und Zustand, je Stufe Karte, Stufe, Beginn, Frist, Teilschritt und ob sie die Umsetzung
 * hält; sonst „kein Runner aktiv“ mit dem letzten Lauf oder „noch kein Lauf gestartet“.
 */

const t = de.RUNNER_JETZT

const stufe = (felder: Partial<RunnerStufe> = {}): RunnerStufe => ({
  cardNumber: 1665,
  stage: 'VORBEREITUNG',
  startedAt: '2026-10-13T22:41:07Z',
  deadline: '2026-10-14T00:41:07Z',
  step: 'WAITING_FOR_IMPLEMENTATION',
  holdsImplementation: true,
  ...felder,
})

const lauf = (felder: Partial<RunnerLauf> = {}): RunnerLauf => ({
  runId: 312,
  mode: 'CHAIN',
  startedAt: '2026-10-13T22:39:12Z',
  tokenName: 'runner',
  lastReportAt: '2026-10-13T23:05:00Z',
  state: 'LAEUFT',
  stages: [stufe()],
  lastStage: null,
  ...felder,
})

const LETZTER: RunnerLetzterLauf = {
  runId: 311,
  mode: 'IMPLEMENTATION',
  startedAt: '2026-10-13T22:40:12Z',
  endedAt: '2026-10-13T22:40:14Z',
  outcome: 'NO_WORK',
  noWorkReason: 'Umsetzung belegt durch Kette #1665, Stufe VORBEREITUNG',
}

function zeige(jetzt: (projectId: number) => Promise<RunnerJetztDaten>, taktMs = 30_000) {
  const api = { jetzt: vi.fn(jetzt) }
  const view = render(
    <ThemeProvider theme={theme}>
      <RunnerJetzt projectId={5} taktMs={taktMs} api={api} />
    </ThemeProvider>,
  )
  return { ...view, api }
}

const laufZeile = (runId: number) => screen.findByTestId(`runner-jetzt-lauf-${runId}`)

afterEach(() => {
  vi.useRealTimers()
})

describe('RunnerJetzt', () => {
  it('Szenario: Eine Kette belegt die Umsetzung', async () => {
    const { api } = zeige(() => Promise.resolve({ state: 'AKTIV', runs: [lauf()], lastRun: LETZTER }))

    const kette = await laufZeile(312)
    expect(api.jetzt).toHaveBeenCalledWith(5)
    expect(screen.getByRole('heading', { name: 'Runner jetzt' })).toBeInTheDocument()
    expect(within(kette).getByText('Kette')).toBeInTheDocument()
    expect(within(kette).getByText(t.LAEUFT)).toBeInTheDocument()
    const s = within(kette).getByTestId('runner-jetzt-stufe-312-0')
    expect(within(s).getByText('#1665 Vorbereitung')).toBeInTheDocument()
    expect(within(s).getByText(t.BEGINN(tagZeit('2026-10-13T22:41:07Z')))).toBeInTheDocument()
    expect(within(s).getByText(t.FRIST(tagZeit('2026-10-14T00:41:07Z')))).toBeInTheDocument()
    expect(within(s).getByText('wartet auf freie Umsetzung')).toBeInTheDocument()
    expect(within(s).getByText('hält die Umsetzung')).toBeInTheDocument()
    expect(
      screen.getByText(
        t.LETZTER_LAUF(
          'Umsetzungsnacht',
          tagZeit('2026-10-13T22:40:14Z'),
          'ohne Paket beendet – Umsetzung belegt durch Kette #1665, Stufe VORBEREITUNG',
        ),
      ),
    ).toBeInTheDocument()
    expect(screen.queryByText(t.KEIN_RUNNER)).not.toBeInTheDocument()
  })

  it('Szenario: Kein Runner aktiv', async () => {
    zeige(() =>
      Promise.resolve({
        state: 'KEIN_RUNNER',
        runs: [],
        lastRun: { ...LETZTER, outcome: 'SUCCEEDED', noWorkReason: null },
      }),
    )

    expect(await screen.findByText('kein Runner aktiv')).toBeInTheDocument()
    expect(
      screen.getByText(t.LETZTER_LAUF('Umsetzungsnacht', tagZeit('2026-10-13T22:40:14Z'), 'gelungen')),
    ).toBeInTheDocument()
  })

  it('Szenario: Noch nie ein Lauf', async () => {
    zeige(() => Promise.resolve({ state: 'NIE', runs: [], lastRun: null }))

    expect(await screen.findByText('kein Runner aktiv – noch kein Lauf gestartet')).toBeInTheDocument()
    expect(screen.queryByText(/^Letzter Lauf/)).not.toBeInTheDocument()
  })

  it('Szenario: Ein Mitglied ohne Owner-Rolle sieht nichts Neues', async () => {
    const { api, container } = zeige(() => Promise.reject(new ApiError(403, 'verboten')))

    await act(async () => {
      await Promise.resolve()
    })
    expect(api.jetzt).toHaveBeenCalledTimes(1)
    expect(container).toBeEmptyDOMElement()
  })

  it('zeigt einen anderen Ladefehler im Bereich', async () => {
    zeige(() => Promise.reject(new ApiError(500, 'kaputt', undefined, 'kaputt')))

    expect(await screen.findByText(t.LADEFEHLER('kaputt'))).toBeInTheDocument()
  })

  it('nennt einen verstummten Lauf mit Zeit und letzter Stufe', async () => {
    zeige(() =>
      Promise.resolve({
        state: 'AKTIV',
        runs: [lauf({ state: 'VERSTUMMT', lastStage: stufe({ stage: 'UMSETZUNG' }) })],
        lastRun: null,
      }),
    )

    const zeile = await laufZeile(312)
    expect(
      within(zeile).getByText(
        `Runner verstummt seit ${tagZeit('2026-10-13T23:05:00Z')} (letzte Stufe: Umsetzung), vermutlich abgebrochen; die Umsetzung gilt womöglich noch als belegt.`,
      ),
    ).toBeInTheDocument()
    expect(within(zeile).queryByText(t.LAEUFT)).not.toBeInTheDocument()
  })

  it('nennt in der Zeile eines laufenden und eines verstummten Laufs Beginn, Token und letzte Meldung', async () => {
    zeige(() =>
      Promise.resolve({
        state: 'AKTIV',
        runs: [
          lauf(),
          lauf({
            runId: 313,
            mode: 'IMPLEMENTATION',
            startedAt: '2026-09-17T22:00:00Z',
            tokenName: 'nacht-token',
            lastReportAt: '2026-09-17T23:10:00Z',
            state: 'VERSTUMMT',
            stages: [],
          }),
        ],
        lastRun: null,
      }),
    )

    const laeuft = await laufZeile(312)
    expect(within(laeuft).getByText(t.BEGINN(tagZeit('2026-10-13T22:39:12Z')))).toBeInTheDocument()
    expect(within(laeuft).getByText(t.TOKEN('runner'))).toBeInTheDocument()
    expect(within(laeuft).getByText(t.LETZTE_MELDUNG(tagZeit('2026-10-13T23:05:00Z')))).toBeInTheDocument()
    const stumm = await laufZeile(313)
    expect(within(stumm).getByText(t.BEGINN(tagZeit('2026-09-17T22:00:00Z')))).toBeInTheDocument()
    expect(within(stumm).getByText(t.TOKEN('nacht-token'))).toBeInTheDocument()
    expect(within(stumm).getByText(t.LETZTE_MELDUNG(tagZeit('2026-09-17T23:10:00Z')))).toBeInTheDocument()
  })

  it('nennt ein fehlendes Token „nicht gemeldet“', async () => {
    zeige(() => Promise.resolve({ state: 'AKTIV', runs: [lauf({ tokenName: null })], lastRun: null }))

    const zeile = await laufZeile(312)
    expect(within(zeile).getByText(t.TOKEN(t.NICHT_GEMELDET))).toBeInTheDocument()
  })

  it('nennt einen verstummten Lauf ohne Stufe mit „nicht gemeldet“', async () => {
    zeige(() =>
      Promise.resolve({ state: 'AKTIV', runs: [lauf({ state: 'VERSTUMMT', stages: [], lastStage: null })], lastRun: null }),
    )

    const zeile = await laufZeile(312)
    expect(within(zeile).getByText(t.VERSTUMMT(tagZeit('2026-10-13T23:05:00Z'), 'nicht gemeldet'))).toBeInTheDocument()
  })

  it('zeigt einen unbekannten Teilschritt und eine unbekannte Stufe wörtlich', async () => {
    zeige(() =>
      Promise.resolve({
        state: 'AKTIV',
        runs: [lauf({ stages: [stufe({ stage: 'GANZ_NEU', step: 'NEUER_SCHRITT', holdsImplementation: false })] })],
        lastRun: null,
      }),
    )

    const s = await screen.findByTestId('runner-jetzt-stufe-312-0')
    expect(within(s).getByText('#1665 GANZ_NEU')).toBeInTheDocument()
    expect(within(s).getByText('Teilschritt: NEUER_SCHRITT')).toBeInTheDocument()
    expect(within(s).queryByText(t.HAELT)).not.toBeInTheDocument()
  })

  it('zeigt „keine Frist vorgesehen“ ohne Frist', async () => {
    zeige(() =>
      Promise.resolve({
        state: 'AKTIV',
        runs: [lauf({ mode: 'IMPLEMENTATION', stages: [stufe({ deadline: null, step: 'SESSION' })] })],
        lastRun: null,
      }),
    )

    const zeile = await laufZeile(312)
    expect(within(zeile).getByText('Umsetzungsnacht')).toBeInTheDocument()
    expect(within(zeile).getByText('keine Frist vorgesehen')).toBeInTheDocument()
    expect(within(zeile).getByText('Sitzung arbeitet')).toBeInTheDocument()
  })

  it('zeigt das Ergebnis eines letzten Laufs mit Arbeit ohne Grund', async () => {
    zeige(() =>
      Promise.resolve({
        state: 'KEIN_RUNNER',
        runs: [],
        lastRun: { ...LETZTER, outcome: 'NO_WORK', noWorkReason: null },
      }),
    )

    expect(
      await screen.findByText(t.LETZTER_LAUF('Umsetzungsnacht', tagZeit('2026-10-13T22:40:14Z'), 'nichts zu tun')),
    ).toBeInTheDocument()
  })

  it('frischt im Takt auf, solange ein Lauf läuft, und nur bei sichtbarem Tab', async () => {
    vi.useFakeTimers()
    const { api } = zeige(() => Promise.resolve({ state: 'AKTIV', runs: [lauf()], lastRun: null }), 1_000)
    await act(async () => {
      await Promise.resolve()
    })
    expect(api.jetzt).toHaveBeenCalledTimes(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000)
    })
    expect(api.jetzt).toHaveBeenCalledTimes(2)

    const sichtbarkeit = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000)
    })
    expect(api.jetzt).toHaveBeenCalledTimes(2)
    sichtbarkeit.mockRestore()
  })

  it('frischt auch einen verstummten Lauf auf — er kann sich wieder melden', async () => {
    vi.useFakeTimers()
    const { api } = zeige(
      () => Promise.resolve({ state: 'AKTIV', runs: [lauf({ state: 'VERSTUMMT' })], lastRun: null }),
      1_000,
    )
    await act(async () => {
      await Promise.resolve()
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000)
    })
    expect(api.jetzt).toHaveBeenCalledTimes(2)
  })

  it('frischt ohne laufenden Lauf nicht auf', async () => {
    vi.useFakeTimers()
    const { api } = zeige(() => Promise.resolve({ state: 'KEIN_RUNNER', runs: [], lastRun: LETZTER }), 1_000)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000)
    })
    expect(api.jetzt).toHaveBeenCalledTimes(1)
  })

  it('verwirft eine Antwort, die nach dem Schließen kommt', async () => {
    let ablehnen: (grund: unknown) => void = () => {}
    let liefern: (lage: RunnerJetztDaten) => void = () => {}
    const fehler = zeige(() => new Promise<RunnerJetztDaten>((_, nein) => (ablehnen = nein)))
    const erfolg = zeige(() => new Promise<RunnerJetztDaten>((ja) => (liefern = ja)))
    fehler.unmount()
    erfolg.unmount()

    await act(async () => {
      ablehnen(new ApiError(500, 'kaputt', undefined, 'kaputt'))
      liefern({ state: 'NIE', runs: [], lastRun: null })
      await Promise.resolve()
    })
    expect(screen.queryByTestId('runner-jetzt')).not.toBeInTheDocument()
  })

  it('zeigt Läufe, Stufen und den letzten Lauf auf Englisch', async () => {
    const api = {
      jetzt: vi.fn(() =>
        Promise.resolve<RunnerJetztDaten>({
          state: 'AKTIV',
          runs: [
            lauf({ stages: [stufe(), stufe({ cardNumber: 1700, deadline: null, step: 'NEU' })] }),
            lauf({ runId: 313, state: 'VERSTUMMT', lastStage: stufe({ stage: 'UMSETZUNG' }) }),
          ],
          lastRun: LETZTER,
        }),
      ),
    }
    renderMitSprache(<RunnerJetzt projectId={5} taktMs={30_000} api={api} />, 'en')

    const kette = await screen.findByTestId('runner-jetzt-lauf-312')
    expect(within(kette).getByText('Chain')).toBeInTheDocument()
    expect(within(kette).getByRole('list', { name: 'Stages: Chain' })).toBeInTheDocument()
    expect(within(kette).getByText('#1665 Preparation')).toBeInTheDocument()
    expect(within(kette).getAllByText(/^Started /)).toHaveLength(3)
    expect(within(kette).getAllByText(/^Deadline /)).toHaveLength(1)
    expect(within(kette).getByText('no deadline set')).toBeInTheDocument()
    expect(within(kette).getByText('Step: NEU')).toBeInTheDocument()
    expect(within(kette).getByText('Token runner')).toBeInTheDocument()
    expect(within(kette).getByText(/^last report /)).toBeInTheDocument()
    const stumm = screen.getByTestId('runner-jetzt-lauf-313')
    expect(within(stumm).getByText(/^Runner silent since .* \(last stage: Implementation\)/)).toBeInTheDocument()
    expect(
      screen.getByText(/^Last run: Implementation night, ended .* – ended without a package – Umsetzung belegt/),
    ).toBeInTheDocument()
  })

  it('meldet einen Ladefehler auf Englisch', async () => {
    const api = { jetzt: vi.fn(() => Promise.reject(new ApiError(500, 'x', undefined, 'broken'))) }
    renderMitSprache(<RunnerJetzt projectId={5} taktMs={30_000} api={api} />, 'en')

    expect(await screen.findByText('The runner status could not be loaded: broken')).toBeInTheDocument()
  })

  it('spricht Englisch, wenn die Sprache Englisch ist', async () => {
    const api = { jetzt: vi.fn(() => Promise.resolve<RunnerJetztDaten>({ state: 'NIE', runs: [], lastRun: null })) }
    renderMitSprache(<RunnerJetzt projectId={5} taktMs={30_000} api={api} />, 'en')

    expect(await screen.findByText('no runner active – no run started yet')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Runner now' })).toBeInTheDocument()
  })

  it('reicht die geladenen Daten an onGeladen weiter und zeigt sie unverändert (Issue #1887)', async () => {
    const daten: RunnerJetztDaten = { state: 'AKTIV', runs: [lauf()], lastRun: null }
    const onGeladen = vi.fn()
    const api = { jetzt: vi.fn(() => Promise.resolve(daten)) }
    render(
      <ThemeProvider theme={theme}>
        <RunnerJetzt projectId={5} taktMs={30_000} api={api} onGeladen={onGeladen} />
      </ThemeProvider>,
    )

    expect(await laufZeile(312)).toBeInTheDocument()
    expect(onGeladen).toHaveBeenCalledWith(daten)
    expect(screen.getByText(t.LAEUFT)).toBeInTheDocument()
  })

  it('ruft onGeladen nach einem Ladefehler nicht', async () => {
    const onGeladen = vi.fn()
    const api = { jetzt: vi.fn(() => Promise.reject(new ApiError(500, 'x', undefined, 'kaputt'))) }
    render(<RunnerJetzt projectId={5} taktMs={30_000} api={api} onGeladen={onGeladen} />)

    expect(await screen.findByText(t.LADEFEHLER('kaputt'))).toBeInTheDocument()
    expect(onGeladen).not.toHaveBeenCalled()
  })
})

describe('RunnerJetzt — Ansicht als XY (Issue #1928, Plan #1914 E16)', () => {
  afterEach(() => {
    auth.vorschau = null
  })

  it('zeigt bei vorschau-verborgen den Bereich mit dem Hinweis statt der Lage', async () => {
    auth.vorschau = VORSCHAU
    zeige(() => Promise.reject(verborgen()))

    const bereich = await screen.findByTestId('runner-jetzt')
    expect(within(bereich).getByRole('heading', { name: 'Runner jetzt' })).toBeInTheDocument()
    expect(within(bereich).getByRole('note')).toHaveTextContent(HINWEIS)
  })

  it('anderer 403 behält das bisherige Verhalten: der Bereich fehlt', async () => {
    auth.vorschau = VORSCHAU
    const { container } = zeige(() => Promise.reject(new ApiError(403, 'verboten', undefined, undefined, 'vorschau-nur-lesend')))

    await act(async () => {
      await Promise.resolve()
    })
    expect(container).toBeEmptyDOMElement()
  })
})
