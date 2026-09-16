import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type {
  NightRunUsageApi,
  VerbrauchAngaben,
  VerbrauchKennzahlen,
  VerbrauchZeitraum,
  VerbrauchZeitraumArt,
} from '../../api/nightRunUsage'
import { KEIN_LAUF_TEXT } from '../../lib/verbrauchZeitraum'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufVerbrauchZeitraum } from './NachtlaufVerbrauchZeitraum'

/** Die Zeitraum-Sicht der Verbrauchs-Auswertung (Issue #942, #926 AK 5–9). */

const nichts: VerbrauchAngaben = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

const kosten = (costUsd: number | null): VerbrauchAngaben => ({ ...nichts, costUsd })

const TAGE: Record<VerbrauchZeitraumArt, [string, string]> = {
  DAY: ['2026-09-15', '2026-09-15'],
  WEEK: ['2026-09-07', '2026-09-13'],
  MONTH: ['2026-08-01', '2026-08-31'],
}

const kennzahlen = (
  type: VerbrauchZeitraumArt,
  werte: Partial<VerbrauchKennzahlen> = {},
): VerbrauchKennzahlen => ({
  type,
  firstDay: TAGE[type][0],
  lastDay: TAGE[type][1],
  from: '2026-09-07T10:00:00Z',
  to: '2026-09-14T10:00:00Z',
  coverage: 'COMPLETE',
  noRuns: false,
  runCount: 4,
  durationMs: 1000,
  cardCount: 3,
  usage: { total: kosten(6.5), cardShare: kosten(5), remainder: kosten(1.5) },
  ...werte,
})

const zeitraum = (
  type: VerbrauchZeitraumArt,
  current: Partial<VerbrauchKennzahlen> = {},
): VerbrauchZeitraum => ({
  current: kennzahlen(type, current),
  previous: kennzahlen(type, {
    firstDay: '2026-08-31',
    lastDay: '2026-09-06',
    usage: { total: kosten(4), cardShare: kosten(3), remainder: kosten(1) },
  }),
  nights: [
    { night: '2026-09-08', runCount: 2, cardCount: 1, usage: { total: kosten(3), cardShare: nichts, remainder: nichts }, aborted: false },
    { night: '2026-09-09', runCount: 1, cardCount: 1, usage: { total: nichts, cardShare: nichts, remainder: nichts }, aborted: true },
  ],
  epics: [
    { epicId: 1, shortcode: 'PLANEN', title: 'Planen', cardCount: 2, usage: kosten(4) },
  ],
  withoutEpic: { epicId: null, shortcode: null, title: null, cardCount: 1, usage: kosten(1) },
  epicsOverlap: false,
})

const apiMit = (antwort: (type: VerbrauchZeitraumArt) => Promise<VerbrauchZeitraum>) => ({
  // Über die Signatur der API getippt: Die Tests lesen den Rückschritt aus den aufgezeichneten
  // Aufrufen, und die kennt der Mock nur mit allen drei Parametern.
  period: vi.fn<NightRunUsageApi['period']>((_, type) => antwort(type)),
})

const zeige = (api: ReturnType<typeof apiMit>, onNacht = vi.fn()) => {
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufVerbrauchZeitraum projectId={5} api={api} onNachtWaehlen={onNacht} />
    </ThemeProvider>,
  )
  return onNacht
}

const lesbar = (element: HTMLElement) => element.textContent?.replaceAll(' ', ' ') ?? ''

describe('NachtlaufVerbrauchZeitraum', () => {
  it('zeigt beim Oeffnen den zuletzt abgeschlossenen Zeitraum der gewaehlten Art', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))

    zeige(api)

    expect(
      await screen.findByRole('heading', { name: 'Woche vom 07.09.2026 bis 13.09.2026' }),
    ).toBeInTheDocument()
    expect(api.period).toHaveBeenCalledWith(5, 'WEEK', 0)
    expect(screen.getByRole('button', { name: 'Woche' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('ruft Tag, Woche und Monat nacheinander ab, ohne den Bereich zu verlassen', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))
    zeige(api)
    await screen.findByRole('heading', { name: 'Woche vom 07.09.2026 bis 13.09.2026' })

    fireEvent.click(screen.getByRole('button', { name: 'Tag' }))
    expect(
      await screen.findByRole('heading', { name: 'Nacht vom 15.09.2026 auf den 16.09.2026' }),
    ).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Monat' }))
    expect(await screen.findByRole('heading', { name: 'August 2026' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Woche' }))
    expect(
      await screen.findByRole('heading', { name: 'Woche vom 07.09.2026 bis 13.09.2026' }),
    ).toBeInTheDocument()
    expect(api.period.mock.calls.map((c) => [c[1], c[2]])).toEqual([
      ['WEEK', 0],
      ['DAY', 0],
      ['MONTH', 0],
      ['WEEK', 0],
    ])
  })

  it('bleibt bei einem Klick auf die schon gewaehlte Art stehen', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))
    zeige(api)
    await screen.findByRole('heading', { name: 'Woche vom 07.09.2026 bis 13.09.2026' })

    fireEvent.click(screen.getByRole('button', { name: 'Woche' }))

    expect(api.period).toHaveBeenCalledTimes(1)
  })

  it('schreitet zurueck und wieder vor; spaeter als der letzte abgeschlossene geht nicht', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))
    zeige(api)
    await screen.findByRole('heading', { name: 'Woche vom 07.09.2026 bis 13.09.2026' })
    expect(screen.getByRole('button', { name: 'Späterer Zeitraum' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: 'Früherer Zeitraum' }))
    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'WEEK', 1))
    expect(screen.getByRole('button', { name: 'Späterer Zeitraum' })).toBeEnabled()

    fireEvent.click(screen.getByRole('button', { name: 'Späterer Zeitraum' }))
    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'WEEK', 0))
  })

  it('beginnt beim Wechsel der Art wieder beim zuletzt abgeschlossenen Zeitraum', async () => {
    const api = apiMit((type) => Promise.resolve(zeitraum(type)))
    zeige(api)
    await screen.findByRole('heading', { name: 'Woche vom 07.09.2026 bis 13.09.2026' })
    fireEvent.click(screen.getByRole('button', { name: 'Früherer Zeitraum' }))
    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'WEEK', 1))

    fireEvent.click(screen.getByRole('button', { name: 'Monat' }))

    await waitFor(() => expect(api.period).toHaveBeenLastCalledWith(5, 'MONTH', 0))
  })

  it('stellt Zeitraum und Vorzeitraum nebeneinander und nennt die Richtung als Text', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    const aktuell = await screen.findByTestId('verbrauch-zeitraum-aktuell')
    const vorher = screen.getByTestId('verbrauch-zeitraum-vorher')
    expect(lesbar(within(aktuell).getByTestId('nachtlauf-kennzahl-Gesamtsumme'))).toContain('6,50 $')
    expect(vorher).toHaveTextContent('Vorwoche: Woche vom 31.08.2026 bis 06.09.2026')
    expect(lesbar(within(vorher).getByTestId('nachtlauf-kennzahl-Gesamtsumme'))).toContain('4,00 $')
    expect(lesbar(screen.getByTestId('verbrauch-zeitraum-vergleich'))).toBe(
      '2,50 $ teurer als im Vorzeitraum',
    )
  })

  it('zeigt bei einem Zeitraum ohne Laeufe den Satz aus AK 9 und keine Zahlen', async () => {
    zeige(
      apiMit((type) =>
        Promise.resolve(
          zeitraum(type, { noRuns: true, runCount: 0, cardCount: 0, usage: { total: nichts, cardShare: nichts, remainder: nichts } }),
        ),
      ),
    )

    expect(await screen.findByText(KEIN_LAUF_TEXT)).toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-zeitraum-aktuell')).not.toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-vorhaben')).not.toBeInTheDocument()
  })

  it('zeigt bei Teilabdeckung die Zahlen und den Hinweis daneben', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type, { coverage: 'PARTIAL' }))))

    expect(await screen.findByTestId('verbrauch-zeitraum-hinweis')).toHaveTextContent('nur teilweise abgedeckt')
    expect(screen.getByTestId('verbrauch-zeitraum-aktuell')).toBeInTheDocument()
  })

  it('zeigt ganz vor dem aeltesten aufbewahrten Lauf den eigenen Satz, nicht den aus AK 9', async () => {
    zeige(
      apiMit((type) =>
        Promise.resolve(
          zeitraum(type, { coverage: 'BEFORE_RETENTION', noRuns: true, runCount: 0, usage: { total: nichts, cardShare: nichts, remainder: nichts } }),
        ),
      ),
    )

    const hinweis = await screen.findByTestId('verbrauch-zeitraum-hinweis')
    expect(hinweis).toHaveTextContent('vor dem ältesten aufbewahrten Lauf')
    expect(screen.queryByText(KEIN_LAUF_TEXT)).not.toBeInTheDocument()
    expect(screen.queryByTestId('verbrauch-zeitraum-aktuell')).not.toBeInTheDocument()
  })

  it('zeigt nicht gemessene Angaben mit Hinweis als „nicht gemessen"', async () => {
    zeige(
      apiMit((type) =>
        Promise.resolve(zeitraum(type, { usage: { total: nichts, cardShare: nichts, remainder: nichts } })),
      ),
    )

    expect(await screen.findByTestId('verbrauch-zeitraum-hinweis')).toHaveTextContent('nicht gemessen')
    expect(
      within(screen.getByTestId('verbrauch-zeitraum-aktuell')).getByTestId('nachtlauf-kennzahl-Gesamtsumme'),
    ).toHaveTextContent('nicht gemessen')
    expect(screen.getByTestId('verbrauch-zeitraum-vergleich')).toHaveTextContent('nicht vergleichbar')
  })

  it('macht die Naechte des Zeitraums erreichbar', async () => {
    const onNacht = zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    const knopf = await screen.findByRole('button', { name: /Nacht vom 09\.09\.2026/ })
    expect(knopf).toHaveTextContent('abgebrochen')
    fireEvent.click(knopf)

    expect(onNacht).toHaveBeenCalledWith('2026-09-09')
    expect(lesbar(screen.getByRole('button', { name: /Nacht vom 08\.09\.2026/ }))).toContain('3,00 $')
  })

  it('nennt den Hinweis des Vorzeitraums an seiner Spalte', async () => {
    const mitAltemVorzeitraum = (type: VerbrauchZeitraumArt): VerbrauchZeitraum => ({
      ...zeitraum(type),
      previous: kennzahlen(type, { coverage: 'BEFORE_RETENTION', noRuns: true, runCount: 0 }),
    })
    zeige(apiMit((type) => Promise.resolve(mitAltemVorzeitraum(type))))

    const vorher = await screen.findByTestId('verbrauch-zeitraum-vorher')
    expect(vorher).toHaveTextContent('vor dem ältesten aufbewahrten Lauf')
    expect(screen.getByTestId('verbrauch-zeitraum-aktuell')).not.toHaveTextContent('aufbewahrten')
  })

  it('zeigt keine Naechte-Liste, wenn der Zeitraum keine Nacht traegt', async () => {
    zeige(apiMit((type) => Promise.resolve({ ...zeitraum(type), nights: [] })))

    await screen.findByTestId('verbrauch-zeitraum-aktuell')
    expect(screen.queryByRole('heading', { name: 'Nächte' })).not.toBeInTheDocument()
  })

  it('zeigt die Vorhaben-Aufstellung des Zeitraums', async () => {
    zeige(apiMit((type) => Promise.resolve(zeitraum(type))))

    expect(await screen.findByTestId('verbrauch-vorhaben')).toHaveTextContent('PLANEN · Planen')
  })

  it('sagt es, wenn der Zeitraum nicht geladen werden kann', async () => {
    zeige(apiMit(() => Promise.reject(new Error('kaputt'))))

    expect(await screen.findByText('Der Zeitraum konnte nicht geladen werden.')).toBeInTheDocument()
  })

  it('verwirft eine Antwort, die nach dem Wechsel der Art eintrifft', async () => {
    let wocheLiefern: (z: VerbrauchZeitraum) => void = () => undefined
    let wocheScheitern: (e: Error) => void = () => undefined
    let aufrufe = 0
    const api = apiMit((type) => {
      aufrufe += 1
      if (aufrufe === 1) {
        return new Promise<VerbrauchZeitraum>((resolve) => {
          wocheLiefern = resolve
        })
      }
      if (aufrufe === 2) {
        return new Promise<VerbrauchZeitraum>((_, reject) => {
          wocheScheitern = reject
        })
      }
      return Promise.resolve(zeitraum(type))
    })
    zeige(api)

    fireEvent.click(screen.getByRole('button', { name: 'Tag' }))
    fireEvent.click(screen.getByRole('button', { name: 'Monat' }))
    expect(await screen.findByRole('heading', { name: 'August 2026' })).toBeInTheDocument()

    wocheLiefern(zeitraum('WEEK'))
    wocheScheitern(new Error('zu spaet'))
    await Promise.resolve()

    expect(screen.getByRole('heading', { name: 'August 2026' })).toBeInTheDocument()
    expect(screen.queryByText('Der Zeitraum konnte nicht geladen werden.')).not.toBeInTheDocument()
  })
})
