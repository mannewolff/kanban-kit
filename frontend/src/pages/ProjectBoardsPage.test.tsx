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
vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn(), create: vi.fn(), remove: vi.fn() } }))

const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedBoards = boardsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
}

function renderAt(role: string, boards: Array<{ id: number; name: string }>) {
  mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role, createdAt: '' }])
  mockedBoards.list.mockResolvedValue(
    boards.map((b) => ({ ...b, projectId: 5, createdAt: '', columns: [] })),
  )
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
    await waitFor(() => expect(screen.getByLabelText(/Neues Board/)).toBeInTheDocument())
  })

  it('blendet für VIEWER die Bearbeiten-Aktionen aus', async () => {
    renderPage('VIEWER')
    expect(await screen.findByText('Team')).toBeInTheDocument()
    expect(screen.queryByLabelText(/Neues Board/)).not.toBeInTheDocument()
  })

  it('löscht ein Board erst nach Bestätigung (OWNER)', async () => {
    // Zwei Boards, damit kein Auto-Routing greift.
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    mockedBoards.remove.mockResolvedValue(undefined)

    fireEvent.click(await screen.findByLabelText('Board Board A löschen'))
    expect(await screen.findByText('Board löschen?')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    await waitFor(() => expect(mockedBoards.remove).toHaveBeenCalledWith(9))
  })

  it('blendet den Board-Löschen-Button für VIEWER aus', async () => {
    renderAt('VIEWER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    expect(await screen.findByText('Board A')).toBeInTheDocument()
    expect(screen.queryByLabelText('Board Board A löschen')).not.toBeInTheDocument()
  })

  it('routet beim Erst-Aufruf mit genau einem Board direkt aufs Board', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([{ id: 9, name: 'Solo', projectId: 5, createdAt: '', columns: [] }])
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
    await waitFor(() => expect(screen.getByText('NeuesBoard1')).toBeInTheDocument())
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
})
