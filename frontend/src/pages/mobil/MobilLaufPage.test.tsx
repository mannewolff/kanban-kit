import { screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import { mobilApi, type MobileMeldung, type MobilerLaufDetail } from '../../api/mobil'
import { formatierer } from '../../i18n/format'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { MobilLaufPage } from './MobilLaufPage'

vi.mock('../../api/mobil', () => ({ mobilApi: { laeufe: vi.fn(), lauf: vi.fn() } }))

const lauf = vi.mocked(mobilApi.lauf)

const detail = (teil: Partial<MobilerLaufDetail['lauf']> = {}, meldung: MobileMeldung | null = null): MobilerLaufDetail => ({
  lauf: {
    laufId: 4711,
    projectId: 7,
    projectName: 'Leitstand',
    boardId: 40,
    boardName: 'Produkt',
    zustand: 'GESCHEITERT',
    startedAt: '2026-10-28T22:00:00Z',
    durationMs: 5_400_000,
    ...teil,
  },
  meldung,
})

const meldung = (teil: Partial<MobileMeldung>): MobileMeldung => ({
  art: 'PAKET',
  text: null,
  cardNumber: null,
  stage: null,
  seit: null,
  ...teil,
})

function seite(pfad = '/mobil/laeufe/4711', sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <MemoryRouter initialEntries={[pfad]}>
      <Routes>
        <Route path="/mobil/laeufe/:laufId" element={<MobilLaufPage />} />
      </Routes>
    </MemoryRouter>,
    sprache,
  )
}

/** Der Wert unter einer Beschriftung der Beschreibungsliste; der Wert trägt sie als Namen. */
const wert = (beschriftung: string) => screen.getByRole('definition', { name: beschriftung }).textContent

describe('MobilLaufPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('Szenario: Ein Lauf scheitert unterwegs', async () => {
    // Ein Tipp auf die Nachricht öffnet den Lauf: Er zeigt Zustand, letzte Meldung und Dauer.
    lauf.mockResolvedValue(
      detail({}, meldung({ art: 'PAKET', text: 'Pruefung rot: mvn verify', cardNumber: 1975 })),
    )
    seite()
    expect(await screen.findByRole('heading', { name: 'Leitstand · Produkt' })).toBeInTheDocument()
    expect(lauf).toHaveBeenCalledWith(4711)
    expect(wert('Zustand')).toBe('gescheitert')
    expect(wert('Letzte Meldung')).toBe('Paket #1975: Pruefung rot: mvn verify')
    expect(wert('Dauer')).toBe(formatierer('de').dauer(5400))
  })

  it('zeigt auch einen inzwischen beendeten Lauf und nennt „alle Boards“ ohne Board', async () => {
    lauf.mockResolvedValue(detail({ zustand: 'BEENDET', boardId: null, boardName: null }))
    seite()
    expect(await screen.findByRole('heading', { name: 'Leitstand · alle Boards' })).toBeInTheDocument()
    expect(wert('Zustand')).toBe('beendet')
    expect(wert('Letzte Meldung')).toBe('Keine Meldung.')
  })

  it('zeigt den Abbruchgrund wörtlich', async () => {
    lauf.mockResolvedValue(detail({}, meldung({ art: 'ABBRUCHGRUND', text: 'Harter Stopp (dirty-tree)' })))
    seite()
    await screen.findByRole('heading', { name: 'Leitstand · Produkt' })
    expect(wert('Letzte Meldung')).toBe('Harter Stopp (dirty-tree)')
  })

  it('zeigt den Auszug ohne Karte wörtlich', async () => {
    lauf.mockResolvedValue(detail({}, meldung({ art: 'PAKET', text: 'Auszug' })))
    seite()
    await screen.findByRole('heading', { name: 'Leitstand · Produkt' })
    expect(wert('Letzte Meldung')).toBe('Auszug')
  })

  it('nennt beim verstummten Lauf sein letztes Lebenszeichen', async () => {
    lauf.mockResolvedValue(detail({}, meldung({ art: 'STILLE', seit: '2026-10-28T23:30:00Z' })))
    seite()
    await screen.findByRole('heading', { name: 'Leitstand · Produkt' })
    const seit = formatierer('de').datumUhrzeit('2026-10-28T23:30:00Z')
    expect(wert('Letzte Meldung')).toBe(`Meldet sich nicht mehr seit ${seit}.`)
  })

  it('nennt die zuletzt begonnene Stufe, mit und ohne Karte', async () => {
    const seit = formatierer('de').datumUhrzeit('2026-10-28T23:00:00Z')
    lauf.mockResolvedValue(
      detail({ zustand: 'LAEUFT' }, meldung({ art: 'STUFE', stage: 'umsetzung', cardNumber: 1975, seit: '2026-10-28T23:00:00Z' })),
    )
    const { unmount } = seite()
    await screen.findByRole('heading', { name: 'Leitstand · Produkt' })
    expect(wert('Zustand')).toBe('läuft')
    expect(wert('Letzte Meldung')).toBe(`Stufe „umsetzung“ an Karte #1975 begann ${seit}.`)
    unmount()

    lauf.mockResolvedValue(detail({}, meldung({ art: 'STUFE', stage: 'plan', seit: '2026-10-28T23:00:00Z' })))
    seite()
    await screen.findByRole('heading', { name: 'Leitstand · Produkt' })
    expect(wert('Letzte Meldung')).toBe(`Stufe „plan“ begann ${seit}.`)
  })

  it('zeigt bei 404 „Lauf nicht gefunden“', async () => {
    lauf.mockRejectedValue(new ApiError(404, 'Not Found'))
    seite()
    expect(await screen.findByText('Lauf nicht gefunden.')).toBeInTheDocument()
    expect(screen.queryByText('Der Lauf konnte nicht geladen werden.')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Alle Läufe' })).toHaveAttribute('href', '/mobil/laeufe')
  })

  it('meldet einen anderen Fehler als Ladefehler', async () => {
    lauf.mockRejectedValue(new ApiError(500, 'kaputt'))
    seite()
    expect(await screen.findByText('Der Lauf konnte nicht geladen werden.')).toBeInTheDocument()
    expect(screen.queryByText('Lauf nicht gefunden.')).not.toBeInTheDocument()
  })

  it('fragt bei einer Kennung, die keine Zahl ist, gar nicht erst und zeigt „Lauf nicht gefunden“', async () => {
    seite('/mobil/laeufe/abc')
    expect(await screen.findByText('Lauf nicht gefunden.')).toBeInTheDocument()
    expect(lauf).not.toHaveBeenCalled()
  })

  it('bietet keinen Knopf zum Schließen oder Quittieren', async () => {
    lauf.mockResolvedValue(detail({}, meldung({ art: 'ABBRUCHGRUND', text: 'weg' })))
    seite()
    await screen.findByRole('heading', { name: 'Leitstand · Produkt' })
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getAllByRole('link').map((l) => l.textContent)).toEqual(['Alle Läufe'])
  })

  it.each([
    [meldung({ art: 'ABBRUCHGRUND' }), 'Keine Meldung.'],
    [meldung({ art: 'PAKET' }), 'Keine Meldung.'],
    [meldung({ art: 'PAKET', cardNumber: 3 }), 'Paket #3: Keine Meldung.'],
    [meldung({ art: 'STILLE' }), 'Meldet sich nicht mehr seit .'],
    [meldung({ art: 'STUFE' }), 'Stufe „“ begann .'],
    [meldung({ art: 'STUFE', cardNumber: 3 }), 'Stufe „“ an Karte #3 begann .'],
  ])('fällt bei fehlenden Angaben nicht um: %o', async (m, erwartet) => {
    lauf.mockResolvedValue(detail({}, m))
    seite()
    await screen.findByRole('heading', { name: 'Leitstand · Produkt' })
    expect(wert('Letzte Meldung')).toBe(erwartet)
  })

  it.each([
    [meldung({ art: 'STILLE', seit: '2026-10-28T23:30:00Z' }), (seit: string) => `Silent since ${seit}.`],
    [meldung({ art: 'STUFE', stage: 'plan', seit: '2026-10-28T23:30:00Z' }), (seit: string) => `Stage “plan” began ${seit}.`],
    [
      meldung({ art: 'STUFE', stage: 'plan', cardNumber: 3, seit: '2026-10-28T23:30:00Z' }),
      (seit: string) => `Stage “plan” on card #3 began ${seit}.`,
    ],
  ])('bildet die Meldung auch auf Englisch: %o', async (m, erwartet) => {
    lauf.mockResolvedValue(detail({}, m))
    seite('/mobil/laeufe/4711', 'en')
    await screen.findByRole('heading', { name: 'Leitstand · Produkt' })
    expect(wert('Last message')).toBe(erwartet(formatierer('en').datumUhrzeit('2026-10-28T23:30:00Z')))
  })

  it('schreibt in der Sprache der Installation', async () => {
    lauf.mockResolvedValue(detail({ zustand: 'HAENGT' }, meldung({ art: 'PAKET', text: 'wartet', cardNumber: 3 })))
    seite('/mobil/laeufe/4711', 'en')
    await screen.findByRole('heading', { name: 'Leitstand · Produkt' })
    expect(wert('State')).toBe('stalled')
    expect(wert('Last message')).toBe('Package #3: wartet')
  })
})
