import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { boardsApi } from '../api/boards'
import { cardsApi } from '../api/cards'
import { epicsApi } from '../api/epics'
import { projectsApi } from '../api/projects'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { BoardPage } from './BoardPage'

let memberships: { projectId: number; role: string }[] = []
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, email: 'a@b.c', displayName: 'A', platformRole: 'USER', memberships } }),
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn(), rename: vi.fn() } }))
vi.mock('../api/cards', () => ({ cardsApi: { list: vi.fn() } }))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn(), assign: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))

const mockedBoards = boardsApi as unknown as {
  get: ReturnType<typeof vi.fn>
  rename: ReturnType<typeof vi.fn>
}
const mockedCards = cardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedEpics = epicsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }

function renderPage() {
  mockedBoards.get.mockResolvedValue({
    id: 1, projectId: 9, name: 'B', createdAt: '',
    columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
  })
  mockedCards.list.mockResolvedValue([])
  mockedEpics.list.mockResolvedValue([])
  mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'OWNER', createdAt: '' }])
  return render(
    <MemoryRouter initialEntries={['/boards/1']}>
      <Routes>
        <Route path="/boards/:boardId" element={<BoardPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('BoardPage canEdit aus Membership', () => {
  beforeEach(() => vi.clearAllMocks())

  it('leitet Editier-Rechte synchron aus den Memberships ab', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderPage()
    expect(await screen.findByText('B')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Karte in Backlog anlegen')).toBeInTheDocument())
    // Hinweis: projectsApi.list() läuft seit #160 für den Projektnamen (useProjectName); die
    // Rolle selbst kommt weiterhin synchron aus den Memberships (Anlege-Aktion sofort sichtbar).
  })

  it('blendet für VIEWER-Membership die Anlege-Aktion aus', async () => {
    memberships = [{ projectId: 9, role: 'VIEWER' }]
    renderPage()
    expect(await screen.findByText('B')).toBeInTheDocument()
    expect(screen.queryByLabelText('Karte in Backlog anlegen')).not.toBeInTheDocument()
  })

  it('benennt das Board über das Edit-Icon um (mit Bearbeiten-Recht)', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.rename.mockResolvedValue({
      id: 1, projectId: 9, name: 'Neu', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    renderPage()

    fireEvent.click(await screen.findByLabelText('Board umbenennen'))
    fireEvent.change(screen.getByLabelText('Neuer Board-Name'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(mockedBoards.rename).toHaveBeenCalledWith(1, 'Neu'))
    expect(await screen.findByText('Neu')).toBeInTheDocument()
  })

  it('blendet das Board-Umbenennen für VIEWER aus', async () => {
    memberships = [{ projectId: 9, role: 'VIEWER' }]
    renderPage()
    expect(await screen.findByText('B')).toBeInTheDocument()
    expect(screen.queryByLabelText('Board umbenennen')).not.toBeInTheDocument()
  })

  it('zeigt bei ungültiger Board-ID einen Fehler und ruft keine API auf', async () => {
    memberships = []
    render(
      <MemoryRouter initialEntries={['/boards/abc']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Ungültige Board-ID.')).toBeInTheDocument()
    expect(mockedBoards.get).not.toHaveBeenCalled()
    expect(mockedCards.list).not.toHaveBeenCalled()
    expect(mockedEpics.list).not.toHaveBeenCalled()
  })
})

describe('BoardPage 404-Handling und Refetch', () => {
  beforeEach(() => vi.clearAllMocks())

  function renderWith(entry: string, targets: React.ReactNode) {
    mockedCards.list.mockResolvedValue([])
    mockedEpics.list.mockResolvedValue([])
    mockedProjects.list.mockResolvedValue([])
    return render(
      <SnackbarProvider>
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path="/boards/:boardId" element={<BoardPage />} />
            {targets}
          </Routes>
        </MemoryRouter>
      </SnackbarProvider>,
    )
  }

  it('leitet bei 404 auf die Projektübersicht um und zeigt einen Hinweis', async () => {
    memberships = []
    mockedBoards.get.mockRejectedValue(new ApiError(404, 'weg'))
    renderWith('/boards/1', <Route path="/" element={<div>Projektübersicht</div>} />)

    expect(await screen.findByText('Projektübersicht')).toBeInTheDocument()
    expect(
      await screen.findByText('Dieses Board wurde archiviert oder gelöscht.'),
    ).toBeInTheDocument()
  })

  it('leitet bei erneutem Fokus auf die Board-Liste um, wenn das Board verschwunden ist', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.get.mockResolvedValueOnce({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    renderWith('/boards/1', <Route path="/projects/:projectId" element={<div>Board-Liste</div>} />)

    expect(await screen.findByText('B')).toBeInTheDocument()

    // Board ist in einer anderen Session verschwunden: nächster Load liefert 404.
    mockedBoards.get.mockRejectedValue(new ApiError(404, 'weg'))
    act(() => window.dispatchEvent(new Event('focus')))

    expect(await screen.findByText('Board-Liste')).toBeInTheDocument()
  })
})
