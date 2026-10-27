import { screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { cardsApi } from '../api/cards'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { KartenLinkRedirect } from './KartenLinkPage'

vi.mock('../api/cards', () => ({ cardsApi: { byNumber: vi.fn() } }))
const byNumber = (cardsApi as unknown as { byNumber: ReturnType<typeof vi.fn> }).byNumber

function Ziel({ name }: Readonly<{ name: string }>) {
  const { pathname, search } = useLocation()
  return <div>{`${name}: ${pathname}${search}`}</div>
}

function renderLink(entry: string, sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <SnackbarProvider>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/projects/:projectId/karten/:nummer" element={<KartenLinkRedirect />} />
          <Route path="/boards/:boardId" element={<Ziel name="Board" />} />
          <Route path="/projects/:projectId" element={<Ziel name="Projekt" />} />
        </Routes>
      </MemoryRouter>
    </SnackbarProvider>,
    sprache,
  )
}

describe('KartenLinkRedirect (Issue #1915)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('leitet eine bekannte Nummer auf das Board mit ?karte= weiter', async () => {
    byNumber.mockResolvedValue({ id: 100, number: 7, boardId: 3, columnId: 10 })
    renderLink('/projects/9/karten/7')

    expect(await screen.findByText('Board: /boards/3?karte=7')).toBeInTheDocument()
    expect(byNumber).toHaveBeenCalledWith(9, 7)
  })

  it.each([404, 403])('geht bei %i mit derselben Meldung auf die Projektseite', async (status) => {
    byNumber.mockRejectedValue(new ApiError(status, 'nein'))
    renderLink('/projects/9/karten/7')

    expect(await screen.findByText('Projekt: /projects/9')).toBeInTheDocument()
    expect(await screen.findByText('Karte #7 ist nicht erreichbar.')).toBeInTheDocument()
  })

  it.each(['/projects/9/karten/abc', '/projects/9/karten/0', '/projects/9/karten/7x', '/projects/x/karten/7'])(
    'zeigt bei %s die Meldung für einen ungültigen Link und fragt nicht an',
    (entry) => {
      renderLink(entry)

      expect(screen.getByRole('alert')).toHaveTextContent('Ungültiger Kartenlink.')
      expect(byNumber).not.toHaveBeenCalled()
    },
  )

  it('leitet nach dem Verlassen der Seite nicht mehr weiter', async () => {
    let aufloesen: (wert: unknown) => void = () => {}
    byNumber.mockReturnValue(new Promise((r) => { aufloesen = r }))
    const { unmount } = renderLink('/projects/9/karten/7')
    expect(screen.getByRole('progressbar')).toBeInTheDocument()
    unmount()
    aufloesen({ id: 100, number: 7, boardId: 3, columnId: 10 })
    await Promise.resolve()
    expect(screen.queryByText(/Board:/)).not.toBeInTheDocument()
  })

  it('meldet nach dem Verlassen der Seite keinen Fehlschlag mehr', async () => {
    let ablehnen: (grund: unknown) => void = () => {}
    byNumber.mockReturnValue(new Promise((_, r) => { ablehnen = r }))
    const { unmount } = renderLink('/projects/9/karten/7')
    unmount()
    ablehnen(new ApiError(404, 'weg'))
    await Promise.resolve()
    expect(screen.queryByText('Karte #7 ist nicht erreichbar.')).not.toBeInTheDocument()
  })

  it('meldet auf Englisch dasselbe, ohne den Grund zu nennen', async () => {
    byNumber.mockRejectedValue(new ApiError(403, 'nein'))
    renderLink('/projects/9/karten/7', 'en')

    expect(await screen.findByText('Card #7 is not reachable.')).toBeInTheDocument()
  })

  it('zeigt den ungültigen Link auf Englisch', () => {
    renderLink('/projects/9/karten/abc', 'en')

    expect(screen.getByRole('alert')).toHaveTextContent('Invalid card link.')
  })
})
