import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { projectsApi } from '../api/projects'
import { ProjectBoardsPage } from './ProjectBoardsPage'

vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn(), create: vi.fn() } }))

const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedBoards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }

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
})
