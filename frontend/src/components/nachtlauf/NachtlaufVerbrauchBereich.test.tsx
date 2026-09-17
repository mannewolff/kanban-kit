import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type {
  VerbrauchAngaben,
  VerbrauchAufteilung,
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

const kosten = (costUsd: number | null): VerbrauchAngaben => ({ ...nichts, costUsd })

const aufteilung = (
  total: VerbrauchAngaben,
  cardShare: VerbrauchAngaben = nichts,
  remainder: VerbrauchAngaben = nichts,
): VerbrauchAufteilung => ({ total, cardShare, remainder })

const LEER = aufteilung(nichts)

/** Nachtlauf-Anteil, Sitzungs-Anteil und Gesamtsumme einer Antwort (Issue #1016). */
const verbrauch = (
  nachtlauf: VerbrauchAufteilung,
  sitzungen: VerbrauchAufteilung = LEER,
  gesamt: VerbrauchAufteilung = nachtlauf,
) => ({ usage: gesamt, usageByKind: { night: nachtlauf, interactive: sitzungen } })

const kennzahlen: VerbrauchKennzahlen = {
  type: 'DAY',
  firstDay: '2026-09-15',
  lastDay: '2026-09-15',
  from: '2026-09-15T10:00:00Z',
  to: '2026-09-16T10:00:00Z',
  coverage: 'COMPLETE',
  noRuns: false,
  runCount: 1,
  nightRunCount: 1,
  interactiveRunCount: 0,
  durationMs: 1,
  cardCount: 0,
  interactiveUsageSince: null,
  ...verbrauch(LEER),
}

const zeitraum: VerbrauchZeitraum = {
  current: kennzahlen,
  previous: { ...kennzahlen, firstDay: '2026-09-14', lastDay: '2026-09-14' },
  nights: [
    {
      night: '2026-09-10',
      runCount: 1,
      cardCount: 1,
      ...verbrauch(LEER),
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
  ...verbrauch(LEER),
  aborted: false,
  cards: [],
}

/**
 * Dieselbe Antwort mit einem Sitzungs-Anteil ungleich null (Issue #1016): Nachtläufe 6,50 $,
 * Sitzungen 10,00 $, Gesamtsumme 16,50 $. Die Nachtlauf-Seite darf davon nur die 6,50 $ zeigen.
 */
const mitSitzungen: VerbrauchZeitraum = {
  ...zeitraum,
  current: {
    ...kennzahlen,
    ...verbrauch(aufteilung(kosten(6.5), kosten(5), kosten(1.5)), aufteilung(kosten(10)), aufteilung(kosten(16.5))),
  },
  previous: { ...kennzahlen, firstDay: '2026-09-14', lastDay: '2026-09-14', ...verbrauch(aufteilung(kosten(6.5))) },
  nights: [
    {
      night: '2026-09-10',
      runCount: 1,
      cardCount: 1,
      ...verbrauch(aufteilung(kosten(2)), aufteilung(kosten(3)), aufteilung(kosten(5))),
      aborted: false,
    },
  ],
  epics: [{ epicId: 1, shortcode: 'PLANEN', title: 'Planen', cardCount: 2, usage: kosten(4) }],
}

/** Die Nacht dazu: Nachtläufe 4,00 $, Sitzungen 6,00 $, Gesamtsumme 10,00 $. */
const nachtMitSitzungen: VerbrauchNacht = {
  ...nacht,
  night: '2026-09-15',
  ...verbrauch(aufteilung(kosten(4)), aufteilung(kosten(6)), aufteilung(kosten(10))),
}

/** `Intl` setzt vor der Einheit ein geschütztes Leerzeichen; verglichen wird der Wortlaut. */
const lesbar = (element: HTMLElement) => element.textContent?.replaceAll(' ', ' ') ?? ''

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

  it('stellt die Zeitraum-Sicht in den Kupferwarte-Bereich und laesst die Nachtansicht in der Ausnahme', async () => {
    const api = {
      period: vi.fn().mockResolvedValue(zeitraum),
      night: vi.fn().mockResolvedValue(nacht),
    }

    zeige(api)

    const kupferwarte = screen.getByTestId('kupferwarte-bereich')
    expect(kupferwarte).toContainElement(screen.getByTestId('verbrauch-zeitraum'))
    expect(kupferwarte).not.toContainElement(await screen.findByTestId('verbrauch-nacht'))
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

  it('bleibt in seinen Zahlen auf dem Nachtlauf-Anteil, auch wenn Sitzungen mitgemeldet sind', async () => {
    const api = {
      period: vi.fn().mockResolvedValue(mitSitzungen),
      night: vi.fn().mockResolvedValue(nachtMitSitzungen),
    }

    zeige(api)

    const aktuell = await screen.findByTestId('verbrauch-zeitraum-aktuell')
    expect(lesbar(within(aktuell).getByTestId('verbrauch-kachel-Gesamtsumme'))).toContain('6,50')
    expect(lesbar(aktuell)).not.toContain('16,50')
    const nachtzeile = screen.getByRole('button', { name: /Nacht vom 10\.09\.2026/ })
    expect(lesbar(nachtzeile)).toContain('2,00 $')
    expect(lesbar(nachtzeile)).not.toContain('5,00 $')
  })

  it('zeigt in der Nachtansicht den Nachtlauf-Anteil der Nacht', async () => {
    const api = {
      period: vi.fn().mockResolvedValue(mitSitzungen),
      night: vi.fn().mockResolvedValue(nachtMitSitzungen),
    }

    zeige(api)

    const ansicht = await screen.findByTestId('verbrauch-nacht')
    expect(lesbar(within(ansicht).getByTestId('nachtlauf-kennzahl-Gesamtsumme'))).toContain('4,00 $')
    expect(lesbar(ansicht)).not.toContain('10,00 $')
  })

  /**
   * Die Vorhaben-Aufstellung trägt in der Antwort **keine** Aufteilung nach Gattung — der Server
   * summiert je Vorhaben über beide (`EpicResponse`). Sie bleibt deshalb unberührt davon, dass die
   * Kennzahlen daneben einen Sitzungs-Anteil führen; belegt wird genau das, nicht mehr.
   */
  it('laesst die Vorhaben-Aufstellung von einem Sitzungs-Anteil unberuehrt', async () => {
    const api = {
      period: vi.fn().mockResolvedValue(mitSitzungen),
      night: vi.fn().mockResolvedValue(nachtMitSitzungen),
    }

    zeige(api)

    const vorhaben = await screen.findByTestId('verbrauch-vorhaben')
    expect(lesbar(vorhaben)).toContain('PLANEN · Planen')
    expect(lesbar(vorhaben)).toContain('4,00 $')
    expect(lesbar(vorhaben)).not.toContain('16,50')
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
