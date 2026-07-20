import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi, type Board } from '../api/boards'
import { projectsApi } from '../api/projects'
import { ProjectBoardsPage } from './ProjectBoardsPage'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

function makeBoards(names: string[], projectId: number): Board[] {
  return names.map((name, i) => ({ id: 100 + i, name, projectId, createdAt: '', columns: [] }))
}

vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/boards', () => ({
  boardsApi: {
    list: vi.fn(),
    listArchived: vi.fn(),
    create: vi.fn(),
    remove: vi.fn(),
    restore: vi.fn(),
    purge: vi.fn(),
  },
}))

const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedBoards = boardsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  listArchived: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
  restore: ReturnType<typeof vi.fn>
  purge: ReturnType<typeof vi.fn>
}

function renderAt(role: string, boards: Array<{ id: number; name: string }>) {
  mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role, createdAt: '' }])
  mockedBoards.list.mockResolvedValue(
    boards.map((b) => ({ ...b, projectId: 5, createdAt: '', columns: [] })),
  )
  mockedBoards.listArchived.mockResolvedValue([])
  return render(
    <MemoryRouter initialEntries={['/projects/5']}>
      <Routes>
        <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

function renderPage(role: string) {
  mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role, createdAt: '2026-01-01T00:00:00Z' }])
  mockedBoards.list.mockResolvedValue([])
  mockedBoards.listArchived.mockResolvedValue([])
  return render(
    <MemoryRouter initialEntries={['/projects/5']}>
      <Routes>
        <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProjectBoardsPage RBAC', () => {
  beforeEach(() => vi.clearAllMocks())

  it('zeigt Angemeldeten mit OWNER-Rolle den Board-Anlegen-Bereich', async () => {
    renderPage('OWNER')
    expect(await screen.findByText('Team')).toBeInTheDocument()
    expect(await screen.findByLabelText(/Neues Board/)).toBeInTheDocument()
  })

  it('blendet für VIEWER die Bearbeiten-Aktionen aus', async () => {
    renderPage('VIEWER')
    expect(await screen.findByText('Team')).toBeInTheDocument()
    expect(screen.queryByLabelText(/Neues Board/)).not.toBeInTheDocument()
  })

  it('archiviert ein Board erst nach Bestätigung (OWNER)', async () => {
    // Zwei Boards, damit kein Auto-Routing greift.
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    mockedBoards.remove.mockResolvedValue(undefined)

    fireEvent.click(await screen.findByLabelText('Board Board A archivieren'))
    expect(await screen.findByText('Board archivieren?')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    await waitFor(() => expect(mockedBoards.remove).toHaveBeenCalledWith(9))
  })

  it('blendet den Board-Archivieren-Button für VIEWER aus', async () => {
    renderAt('VIEWER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    expect(await screen.findByText('Board A')).toBeInTheDocument()
    expect(screen.queryByLabelText('Board Board A archivieren')).not.toBeInTheDocument()
  })

  it('zeigt archivierte Boards, stellt sie wieder her und löscht sie endgültig nur bei Namensgleichheit', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([
      { id: 9, name: 'Aktiv', projectId: 5, createdAt: '', columns: [] },
      { id: 10, name: 'Aktiv2', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.listArchived.mockResolvedValue([
      { id: 20, name: 'Altes Board', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.restore.mockResolvedValue(undefined)
    mockedBoards.purge.mockResolvedValue(undefined)
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )

    // Archiv-Sektion zeigt das archivierte Board.
    expect(await screen.findByText('Altes Board')).toBeInTheDocument()

    // Endgültig löschen: Button erst bei exakt eingetipptem Namen aktiv.
    fireEvent.click(screen.getByLabelText('Board Altes Board endgültig löschen'))
    const purgeButton = screen.getByRole('button', { name: 'Endgültig löschen' })
    expect(purgeButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Board-Name zur Bestätigung'), {
      target: { value: 'falsch' },
    })
    expect(purgeButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Board-Name zur Bestätigung'), {
      target: { value: 'Altes Board' },
    })
    expect(purgeButton).toBeEnabled()
    fireEvent.click(purgeButton)
    await waitFor(() => expect(mockedBoards.purge).toHaveBeenCalledWith(20))
  })

  it('stellt ein archiviertes Board wieder her', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([
      { id: 9, name: 'Aktiv', projectId: 5, createdAt: '', columns: [] },
      { id: 10, name: 'Aktiv2', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.listArchived.mockResolvedValue([
      { id: 20, name: 'Altes Board', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.restore.mockResolvedValue(undefined)
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Wiederherstellen' }))
    await waitFor(() => expect(mockedBoards.restore).toHaveBeenCalledWith(20))
  })

  it('routet beim Erst-Aufruf mit genau einem Board direkt aufs Board', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([{ id: 9, name: 'Solo', projectId: 5, createdAt: '', columns: [] }])
    mockedBoards.listArchived.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
          <Route path="/boards/:boardId" element={<div>Board-Ansicht</div>} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Board-Ansicht')).toBeInTheDocument()
  })

  it('verwirft eine spät auflösende Antwort der alten Projekt-ID nach einem ID-Wechsel', async () => {
    // Zwei Boards je Projekt, damit kein Auto-Routing bei genau einem Board greift.
    const dOld = deferred<Board[]>()
    const dNew = deferred<Board[]>()
    mockedBoards.list.mockReturnValueOnce(dOld.promise).mockReturnValueOnce(dNew.promise)
    mockedBoards.listArchived.mockResolvedValue([])
    mockedProjects.list.mockResolvedValue([
      { id: 5, name: 'Team5', role: 'OWNER', createdAt: '' },
      { id: 6, name: 'Team6', role: 'OWNER', createdAt: '' },
    ])

    function Nav() {
      const navigate = useNavigate()
      return <button onClick={() => navigate('/projects/6')}>wechseln</button>
    }
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Nav />
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )

    // Wechsel auf Projekt 6, bevor die Antwort für Projekt 5 da ist.
    fireEvent.click(screen.getByText('wechseln'))
    dNew.resolve(makeBoards(['NeuesBoard1', 'NeuesBoard2'], 6))
    expect(await screen.findByText('NeuesBoard1')).toBeInTheDocument()

    // Die verspätete Antwort der alten ID darf den State nicht mehr überschreiben.
    dOld.resolve(makeBoards(['AltesBoard1', 'AltesBoard2'], 5))
    expect(await screen.findByText('NeuesBoard1')).toBeInTheDocument()
    expect(screen.queryByText('AltesBoard1')).not.toBeInTheDocument()
  })

  it('zeigt bei ungültiger Projekt-ID einen Fehler und ruft keine API auf', async () => {
    render(
      <MemoryRouter initialEntries={['/projects/abc']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Ungültige Projekt-ID.')).toBeInTheDocument()
    expect(mockedBoards.list).not.toHaveBeenCalled()
    expect(mockedProjects.list).not.toHaveBeenCalled()
  })

  it('legt ein neues Board an', async () => {
    mockedBoards.create.mockResolvedValue({ id: 30, name: 'Neu', projectId: 5, createdAt: '', columns: [] })
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')

    fireEvent.change(screen.getByLabelText(/Neues Board/), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(mockedBoards.create).toHaveBeenCalledWith(5, 'Neu'))
  })

  it('legt kein Board ohne Namen an', async () => {
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')

    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(mockedBoards.create).not.toHaveBeenCalled()
  })

  it('lädt Boards, Archiv und Rolle beim Fensterfokus neu', async () => {
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')
    mockedBoards.list.mockClear()
    mockedBoards.listArchived.mockClear()
    mockedProjects.list.mockClear()

    fireEvent(window, new Event('focus'))

    await waitFor(() => expect(mockedBoards.list).toHaveBeenCalled())
    expect(mockedBoards.listArchived).toHaveBeenCalled()
    expect(mockedProjects.list).toHaveBeenCalled()
  })

  it('navigiert per Klick auf ein Board', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([
      { id: 9, name: 'Board A', projectId: 5, createdAt: '', columns: [] },
      { id: 10, name: 'Board B', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.listArchived.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
          <Route path="/boards/:boardId" element={<div>Board-Ansicht</div>} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByText('Board A'))
    expect(await screen.findByText('Board-Ansicht')).toBeInTheDocument()
  })

  it('schließt den Archivieren-Dialog per Escape und über Abbrechen', async () => {
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')

    fireEvent.click(screen.getByLabelText('Board Board A archivieren'))
    fireEvent.keyDown(await screen.findByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('Board Board A archivieren'))
    fireEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(mockedBoards.remove).not.toHaveBeenCalled()
  })
})
