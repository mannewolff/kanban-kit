import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { DisruptionView } from '../api/plattformLeitstand'
import { plattformLeitstandApi } from '../api/plattformLeitstand'
import PlattformLeitstandPage from './PlattformLeitstandPage'

vi.mock('../api/plattformLeitstand', async () => {
  const echt = await vi.importActual<typeof import('../api/plattformLeitstand')>(
    '../api/plattformLeitstand',
  )
  return { ...echt, plattformLeitstandApi: { liste: vi.fn(), quittieren: vi.fn() } }
})

const api = vi.mocked(plattformLeitstandApi)

/**
 * Der Bereich „Störungen" des Plattform-Leitstands (Issue #1083, fachliche Quelle #1064).
 *
 * Geprüft wird die **Aussage** der Zeile, nicht ihre Gestalt: Projekt, Zeitpunkt, anklickbare
 * Kennung, Grund und die Taste. Wie die Seite aussieht, nimmt ein Mensch gegen
 * `docs/entwurf-leitstand.html` ab — der Entwurf führt für diese Ansicht kein eigenes Mockup.
 */
describe('PlattformLeitstandPage (#1083)', () => {
  const stoerung = (extra: Partial<DisruptionView> = {}): DisruptionView => ({
    nightRunId: 5,
    projectId: 9,
    projectName: 'Mein Projekt',
    startedAt: '2026-09-19T21:10:00Z',
    outcome: {
      verdict: 'FAILED',
      decisiveItem: { cardNumber: 721, state: 'RED', errorClass: 'CHECKS_RED' },
      noWorkReason: null,
    },
    ...extra,
  })

  const zeigeSeite = () => render(<PlattformLeitstandPage />, { wrapper: MemoryRouter })

  beforeEach(() => {
    vi.clearAllMocks()
    api.quittieren.mockResolvedValue(undefined)
  })

  /** AK 13: die jüngste Störung zuoberst — die Reihenfolge der Antwort bleibt erhalten. */
  it('zeigt die Störungen in der Reihenfolge der Antwort, jüngste zuoberst', async () => {
    api.liste.mockResolvedValue([
      stoerung({ nightRunId: 7, startedAt: '2026-09-19T21:10:00Z', projectName: 'Jung' }),
      stoerung({ nightRunId: 6, startedAt: '2026-09-18T21:10:00Z', projectName: 'Mittel' }),
      stoerung({ nightRunId: 5, startedAt: '2026-09-17T21:10:00Z', projectName: 'Alt' }),
    ])

    zeigeSeite()

    const zeilen = await screen.findAllByTestId(/^stoerung-/)
    expect(zeilen.map((z) => z.getAttribute('data-testid'))).toEqual([
      'stoerung-7',
      'stoerung-6',
      'stoerung-5',
    ])
  })

  /** AK 6: Projekt, Datum und Uhrzeit, Kennung und Grund — vier Angaben in einer Zeile. */
  it('nennt in einer Zeile Projekt, Zeitpunkt, Kennung und Grund', async () => {
    api.liste.mockResolvedValue([stoerung()])

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    expect(within(zeile).getByText('Mein Projekt')).toBeInTheDocument()
    expect(within(zeile).getByText('19.09., 23:10')).toBeInTheDocument()
    expect(within(zeile).getByRole('link', { name: 'Lauf #5' })).toBeInTheDocument()
    expect(within(zeile).getByText(/^Karte #721:/)).toBeInTheDocument()
  })

  /**
   * AK 6: derselbe Text wie in der Nachtlauf-Auswertung, gebildet mit derselben Funktion — aber
   * ohne die Dauer, die dort je Paket dabeisteht. Die Störzeile führt den Zeitpunkt schon.
   */
  it('bildet den Grund als „Karte #n: Zustand" ohne Dauer', async () => {
    api.liste.mockResolvedValue([stoerung()])

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    const grund = within(zeile).getByText(/^Karte #721:/)
    expect(grund).toHaveTextContent('Karte #721: gescheitert')
    expect(grund.textContent).not.toMatch(/\d+\s*(ms|s|min)/)
  })

  /** Ein Lauf ohne Arbeit hat kein Paket — sein Grund ist der Text selbst (#1069). */
  it('zeigt beim Lauf ohne Arbeit den Grund wörtlich', async () => {
    api.liste.mockResolvedValue([
      stoerung({
        outcome: { verdict: 'FAILED', decisiveItem: null, noWorkReason: 'Ready war leer' },
      }),
    ])

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    expect(within(zeile).getByText('Ready war leer')).toBeInTheDocument()
  })

/**
   * Ein Befund ohne massgebliches Paket und ohne Grund kann der Server nicht liefern — eine
   * Stoerung ist entweder ein Paket oder ein Lauf ohne Arbeit. Der Zweig steht trotzdem, weil der
   * Typ beides als `null` zulaesst; er zeigt dann nichts statt „undefined".
   */
  it('zeigt bei einem Befund ohne Paket und ohne Grund keinen Text', async () => {
    api.liste.mockResolvedValue([
      stoerung({ outcome: { verdict: 'FAILED', decisiveItem: null, noWorkReason: null } }),
    ])

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    expect(within(zeile).queryByText(/^Karte #/)).not.toBeInTheDocument()
  })

  /**
   * Der Typ laesst eine Fehlerklasse `null` zu — dann nennt der Grundtext nur den Zustand. Der Fall
   * ist selten (die Datenbank verlangt zu jedem nicht-gruenen Zustand eine Klasse), aber der Zweig
   * steht, und ein Test sagt, was er dann zeigt.
   */
  it('nennt ohne Fehlerklasse nur den Zustand des Pakets', async () => {
    api.liste.mockResolvedValue([
      stoerung({
        outcome: {
          verdict: 'FAILED',
          decisiveItem: { cardNumber: 721, state: 'RED', errorClass: null },
          noWorkReason: null,
        },
      }),
    ])

    zeigeSeite()

    const zeile = await screen.findByTestId('stoerung-5')
    expect(within(zeile).getByText(/^Karte #721:/)).toBeInTheDocument()
  })

  /** AK 7: Der Klick führt zur Auswertung genau dieses Laufs im betroffenen Projekt. */
  it('verweist mit der Kennung auf den Lauf im Projekt', async () => {
    api.liste.mockResolvedValue([stoerung()])

    zeigeSeite()

    const verweis = await screen.findByRole('link', { name: 'Lauf #5' })
    expect(verweis).toHaveAttribute('href', '/projects/9/nachtlauf?lauf=5')
  })

  /** AK 8: kein Rückfragen-Dialog, kein Rückgängig — die Zeile ist sofort weg. */
  it('räumt die Zeile ohne Rückfrage weg und ruft den Endpunkt', async () => {
    api.liste.mockResolvedValue([stoerung(), stoerung({ nightRunId: 6, projectName: 'Anderes' })])

    zeigeSeite()
    const taste = await screen.findByRole('button', {
      name: 'Störung von Mein Projekt, Lauf #5 löschen',
    })
    await userEvent.click(taste)

    expect(api.quittieren).toHaveBeenCalledWith(5)
    await waitFor(() => expect(screen.queryByTestId('stoerung-5')).not.toBeInTheDocument())
    expect(screen.getByTestId('stoerung-6')).toBeInTheDocument()
    expect(api.liste).toHaveBeenCalledTimes(1)
  })

  /** In einer Liste gleichlautender Tasten braucht jede ihre eigene Ansage. */
  it('gibt jeder Taste ein unterscheidbares aria-label', async () => {
    api.liste.mockResolvedValue([stoerung(), stoerung({ nightRunId: 6, projectName: 'Anderes' })])

    zeigeSeite()

    await screen.findByTestId('stoerung-5')
    const namen = screen.getAllByRole('button').map((b) => b.getAttribute('aria-label'))
    expect(new Set(namen).size).toBe(namen.length)
  })

  /** AK 14: Eine leere Fläche wäre von einer kaputten Anzeige nicht zu unterscheiden. */
  it('sagt ausdrücklich, wenn keine Störung offen ist', async () => {
    api.liste.mockResolvedValue([])

    zeigeSeite()

    expect(await screen.findByTestId('keine-stoerungen')).toHaveTextContent('Keine offene Störung.')
  })

  /** AK 3: dasselbe wie auf jeder anderen Admin-Seite — ein Fehlertext statt Inhalt. */
  it('zeigt bei 403 einen Fehlertext statt Inhalt', async () => {
    api.liste.mockRejectedValue(new ApiError(403, 'Forbidden'))

    zeigeSeite()

    expect(await screen.findByText('Kein Admin-Zugriff.')).toBeInTheDocument()
    expect(screen.queryByTestId('keine-stoerungen')).not.toBeInTheDocument()
  })

  it('unterscheidet den Ladefehler vom fehlenden Recht', async () => {
    api.liste.mockRejectedValue(new ApiError(500, 'Boom'))

    zeigeSeite()

    expect(await screen.findByText('Laden fehlgeschlagen.')).toBeInTheDocument()
  })
})
