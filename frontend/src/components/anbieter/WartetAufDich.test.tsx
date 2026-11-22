import { act, fireEvent, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { anbieterApi, type AnbieterLage, type WartendeKarte } from '../../api/anbieter'
import { auslieferungApi, type AuslieferungZuletzt } from '../../api/auslieferung'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { LAGE_TAKT_MS, WartetAufDich } from './WartetAufDich'

vi.mock('../../api/anbieter', () => ({ anbieterApi: { wartetAufDich: vi.fn(), lage: vi.fn() } }))
vi.mock('../../api/auslieferung', () => ({ auslieferungApi: { zuletzt: vi.fn() } }))

const wartet = anbieterApi.wartetAufDich as unknown as ReturnType<typeof vi.fn>
const lage = anbieterApi.lage as unknown as ReturnType<typeof vi.fn>
const zuletzt = auslieferungApi.zuletzt as unknown as ReturnType<typeof vi.fn>

const ERREICHBAR: AnbieterLage = { nichtErreichbar: false, boards: [] }
const NICHT_ERREICHBAR: AnbieterLage = {
  nichtErreichbar: true,
  boards: [{ boardId: 3, nichtErreichbar: true, zuletztGelesen: '2026-11-22T10:00:00Z' }],
}
const OHNE_AUSLIEFERUNG: AuslieferungZuletzt = { vorschau: null, veroeffentlichung: null }

const KARTEN: WartendeKarte[] = [
  { cardId: 812, boardId: 3, number: 41, title: 'Export als CSV', grund: 'RUECKFRAGE' },
  { cardId: 813, boardId: 3, number: 42, title: 'Neue Startseite', grund: 'FREIGABE' },
]

function Ziel() {
  const ort = useLocation()
  return <div>Ziel {ort.pathname + ort.search}</div>
}

function zeigen(sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <MemoryRouter initialEntries={['/projects/5']}>
      <Routes>
        <Route path="/projects/:projectId" element={<WartetAufDich projectId={5} />} />
        <Route path="/boards/:boardId" element={<Ziel />} />
      </Routes>
    </MemoryRouter>,
    sprache,
  )
}

beforeEach(() => {
  wartet.mockReset().mockResolvedValue([])
  lage.mockReset().mockResolvedValue(ERREICHBAR)
  zuletzt.mockReset().mockResolvedValue(OHNE_AUSLIEFERUNG)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('WartetAufDich', () => {
  it('Szenario: Das Rechenzentrum ist nicht erreichbar', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    // Angenommen das Rechenzentrum hat die Karten des Boards seit 10 Minuten nicht gelesen.
    lage.mockResolvedValueOnce(NICHT_ERREICHBAR).mockResolvedValue(ERREICHBAR)

    // Wenn der Kunde sein Projekt öffnet, dann steht oben „gerade nicht erreichbar“.
    zeigen()
    expect(await screen.findByText('Gerade nicht erreichbar')).toBeInTheDocument()
    expect(lage).toHaveBeenCalledWith(5)

    // Und nach dem nächsten Lesen durch das Rechenzentrum ist der Hinweis verschwunden.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(LAGE_TAKT_MS)
    })
    expect(lage).toHaveBeenCalledTimes(2)
    expect(screen.queryByText('Gerade nicht erreichbar')).not.toBeInTheDocument()
  })

  it('liest die Lage auch beim Zurückkehren in den Tab neu', async () => {
    lage.mockResolvedValueOnce(ERREICHBAR).mockResolvedValue(NICHT_ERREICHBAR)
    zeigen()
    await act(async () => {
      await Promise.resolve()
    })
    expect(screen.queryByText('Gerade nicht erreichbar')).not.toBeInTheDocument()

    act(() => {
      window.dispatchEvent(new Event('focus'))
    })

    expect(await screen.findByText('Gerade nicht erreichbar')).toBeInTheDocument()
  })

  it('beendet das wiederholte Lesen beim Verlassen der Seite', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { unmount } = zeigen()
    await act(async () => {
      await Promise.resolve()
    })
    unmount()

    await vi.advanceTimersByTimeAsync(LAGE_TAKT_MS * 2)
    expect(lage).toHaveBeenCalledTimes(1)
  })

  it('nennt die wartenden Karten mit ihrem Grund und öffnet eine im Kartendetail', async () => {
    wartet.mockResolvedValue(KARTEN)
    zeigen()

    expect(await screen.findByRole('heading', { name: 'Wartet auf dich' })).toBeInTheDocument()
    expect(wartet).toHaveBeenCalledWith(5)
    expect(screen.getByText('Rückfrage offen')).toBeInTheDocument()
    expect(screen.getByText('Freigabe ausstehend')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('link', { name: '#42 Neue Startseite' }))
    expect(await screen.findByText('Ziel /boards/3?karte=42')).toBeInTheDocument()
  })

  it('zeigt die jüngste Vorschau und Veröffentlichung als Link', async () => {
    zuletzt.mockResolvedValue({
      vorschau: { url: 'https://vorschau.example.org/42', revision: 'abc', finishedAt: '2026-11-22T10:00:00Z' },
      veroeffentlichung: { url: 'https://example.org', revision: 'def', finishedAt: '2026-11-21T10:00:00Z' },
    })
    zeigen()

    const vorschau = await screen.findByRole('link', { name: 'Vorschau ansehen' })
    expect(vorschau).toHaveAttribute('href', 'https://vorschau.example.org/42')
    expect(vorschau).toHaveAttribute('target', '_blank')
    expect(vorschau).toHaveAttribute('rel', 'noopener noreferrer')
    expect(zuletzt).toHaveBeenCalledWith(5)
    expect(screen.getByText(/Veröffentlicht unter/)).toBeInTheDocument()
    const veroeffentlicht = screen.getByRole('link', { name: 'https://example.org' })
    expect(veroeffentlicht).toHaveAttribute('href', 'https://example.org')
    expect(veroeffentlicht).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('übergeht einen gemeldeten Stand ohne Adresse', async () => {
    wartet.mockResolvedValue(KARTEN.slice(0, 1))
    zuletzt.mockResolvedValue({
      vorschau: { url: null, revision: 'abc', finishedAt: '2026-11-22T10:00:00Z' },
      veroeffentlichung: { url: null, revision: 'def', finishedAt: '2026-11-21T10:00:00Z' },
    })
    zeigen()

    await screen.findByText('Rückfrage offen')
    expect(screen.queryByText('Vorschau ansehen')).not.toBeInTheDocument()
    expect(screen.queryByText(/Veröffentlicht unter/)).not.toBeInTheDocument()
  })

  it('zeigt nichts, solange nichts wartet, nichts gemeldet und alles erreichbar ist', async () => {
    const { container } = zeigen()
    await act(async () => {
      await Promise.resolve()
    })

    expect(container).toBeEmptyDOMElement()
  })

  it('zeigt nichts von dem, was sich nicht laden lässt', async () => {
    wartet.mockRejectedValue(new Error('weg'))
    lage.mockRejectedValue(new Error('weg'))
    zuletzt.mockRejectedValue(new Error('weg'))
    const { container } = zeigen()
    await act(async () => {
      await Promise.resolve()
    })

    expect(container).toBeEmptyDOMElement()
  })

  it('spricht englisch, wenn die Oberfläche englisch ist', async () => {
    wartet.mockResolvedValue(KARTEN)
    lage.mockResolvedValue(NICHT_ERREICHBAR)
    zeigen('en')

    expect(await screen.findByRole('heading', { name: 'Waiting for you' })).toBeInTheDocument()
    expect(await screen.findByText('Currently unreachable')).toBeInTheDocument()
    expect(screen.getByText('Approval pending')).toBeInTheDocument()
  })
})
