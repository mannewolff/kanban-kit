import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type {
  VerbrauchAngaben,
  VerbrauchKennzahlen,
  VerbrauchNacht,
  VerbrauchZeitraum,
} from '../../api/nightRunUsage'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufVerbrauchBereich } from './NachtlaufVerbrauchBereich'

/** Der Verbrauchs-Bereich der Nachtlauf-Seite (Issue #941). */

const nichts: VerbrauchAngaben = {
  costUsd: null,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
}

const kennzahlen: VerbrauchKennzahlen = {
  type: 'DAY',
  firstDay: '2026-09-15',
  lastDay: '2026-09-15',
  from: '2026-09-15T10:00:00Z',
  to: '2026-09-16T10:00:00Z',
  coverage: 'COMPLETE',
  noRuns: false,
  runCount: 1,
  durationMs: 1,
  cardCount: 0,
  usage: { total: nichts, cardShare: nichts, remainder: nichts },
}

const zeitraum: VerbrauchZeitraum = {
  current: kennzahlen,
  previous: { ...kennzahlen, firstDay: '2026-09-14', lastDay: '2026-09-14' },
  nights: [
    {
      night: '2026-09-10',
      runCount: 1,
      cardCount: 1,
      usage: { total: nichts, cardShare: nichts, remainder: nichts },
      aborted: false,
    },
  ],
  epics: [],
  withoutEpic: { epicId: null, shortcode: null, title: null, cardCount: 0, usage: nichts },
  epicsOverlap: false,
}

const nacht: VerbrauchNacht = {
  night: '2026-09-15',
  runCount: 2,
  durationMs: 60_000,
  cardCount: 1,
  usage: { total: nichts, cardShare: nichts, remainder: nichts },
  aborted: false,
  cards: [],
}

const zeige = (api: Parameters<typeof NachtlaufVerbrauchBereich>[0]['api']) =>
  render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufVerbrauchBereich projectId={5} api={api} />
    </ThemeProvider>,
  )

describe('NachtlaufVerbrauchBereich', () => {
  it('zeigt die zuletzt abgeschlossene Nacht — ihr Datum kommt vom Server', async () => {
    const api = {
      period: vi.fn().mockResolvedValue(zeitraum),
      night: vi.fn().mockResolvedValue(nacht),
    }

    zeige(api)

    expect(await screen.findByTestId('verbrauch-nacht')).toBeInTheDocument()
    expect(api.period).toHaveBeenCalledWith(5, 'DAY', 0)
    expect(api.night).toHaveBeenCalledWith(5, '2026-09-15')
    expect(screen.getByRole('heading', { level: 2, name: 'Verbrauch' })).toBeInTheDocument()
  })

  it('stellt die Nachtansicht auf die im Zeitraum gewaehlte Nacht um (AK 8)', async () => {
    const api = {
      period: vi.fn().mockResolvedValue(zeitraum),
      night: vi.fn((_: number, datum: string) => Promise.resolve({ ...nacht, night: datum })),
    }
    zeige(api)
    expect(await screen.findByTestId('verbrauch-nacht')).toHaveTextContent('Nacht vom 15.09.2026')

    fireEvent.click(await screen.findByRole('button', { name: /Nacht vom 10\.09\.2026/ }))

    await waitFor(() =>
      expect(screen.getByTestId('verbrauch-nacht')).toHaveTextContent('Nacht vom 10.09.2026 auf den 11.09.2026'),
    )
    expect(api.night).toHaveBeenLastCalledWith(5, '2026-09-10')
  })

  it('laesst eine vor der ersten Antwort gewaehlte Nacht gelten', async () => {
    let tagLiefern: (z: VerbrauchZeitraum) => void = () => undefined
    const api = {
      period: vi.fn((_: number, type: string) =>
        type === 'DAY'
          ? new Promise<VerbrauchZeitraum>((resolve) => {
              tagLiefern = resolve
            })
          : Promise.resolve(zeitraum),
      ),
      night: vi.fn((_: number, datum: string) => Promise.resolve({ ...nacht, night: datum })),
    }
    zeige(api)

    fireEvent.click(await screen.findByRole('button', { name: /Nacht vom 10\.09\.2026/ }))
    expect(await screen.findByTestId('verbrauch-nacht')).toHaveTextContent('Nacht vom 10.09.2026')
    tagLiefern(zeitraum)
    await Promise.resolve()

    expect(api.night).toHaveBeenCalledTimes(1)
    expect(api.night).toHaveBeenCalledWith(5, '2026-09-10')
  })

  it('sagt, dass geladen wird, solange die Antwort aussteht', () => {
    zeige({ period: vi.fn().mockReturnValue(new Promise(() => undefined)), night: vi.fn() })

    expect(screen.getByText('Der Verbrauch wird geladen …')).toBeInTheDocument()
  })

  it('sagt es, wenn der Zeitraum nicht geladen werden kann', async () => {
    const api = { period: vi.fn().mockRejectedValue(new Error('kaputt')), night: vi.fn() }

    zeige(api)

    expect(await screen.findByText('Der Verbrauch konnte nicht geladen werden.')).toBeInTheDocument()
    expect(api.night).not.toHaveBeenCalled()
  })

  it('sagt es, wenn die Nacht nicht geladen werden kann', async () => {
    zeige({
      period: vi.fn().mockResolvedValue(zeitraum),
      night: vi.fn().mockRejectedValue(new Error('kaputt')),
    })

    expect(await screen.findByText('Der Verbrauch konnte nicht geladen werden.')).toBeInTheDocument()
  })

  it('schreibt nichts mehr, wenn die Nacht erst nach dem Verlassen eintrifft', async () => {
    let liefern: (n: VerbrauchNacht) => void = () => undefined
    const api = {
      period: vi.fn().mockResolvedValue(zeitraum),
      night: vi.fn().mockReturnValue(
        new Promise<VerbrauchNacht>((resolve) => {
          liefern = resolve
        }),
      ),
    }
    const { unmount } = zeige(api)
    await waitFor(() => expect(api.night).toHaveBeenCalled())
    unmount()

    liefern(nacht)
    await Promise.resolve()

    expect(screen.queryByTestId('verbrauch-nacht')).not.toBeInTheDocument()
  })

  it('schreibt keinen Fehler mehr, wenn er erst nach dem Verlassen eintrifft', async () => {
    let scheitern: (grund: Error) => void = () => undefined
    const api = {
      period: vi.fn().mockReturnValue(
        new Promise<VerbrauchZeitraum>((_, reject) => {
          scheitern = reject
        }),
      ),
      night: vi.fn(),
    }
    const { unmount } = zeige(api)
    unmount()

    scheitern(new Error('kaputt'))
    await Promise.resolve()

    expect(screen.queryByText('Der Verbrauch konnte nicht geladen werden.')).not.toBeInTheDocument()
  })

  it('schreibt nach dem Verlassen nichts mehr', async () => {
    let liefern: (z: VerbrauchZeitraum) => void = () => undefined
    const api = {
      period: vi.fn().mockReturnValue(
        new Promise<VerbrauchZeitraum>((resolve) => {
          liefern = resolve
        }),
      ),
      night: vi.fn().mockResolvedValue(nacht),
    }
    const { unmount } = zeige(api)
    unmount()

    liefern(zeitraum)
    await waitFor(() => expect(api.period).toHaveBeenCalled())

    expect(api.night).not.toHaveBeenCalled()
  })
})
