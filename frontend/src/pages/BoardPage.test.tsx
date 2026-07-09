import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi } from '../api/cards'
import { projectsApi } from '../api/projects'
import { BoardPage } from './BoardPage'

let memberships: { projectId: number; role: string }[] = []
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, email: 'a@b.c', displayName: 'A', platformRole: 'USER', memberships } }),
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/cards', () => ({ cardsApi: { list: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))

const mockedBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const mockedCards = cardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }

function renderPage() {
  mockedBoards.get.mockResolvedValue({
    id: 1, projectId: 9, name: 'B', createdAt: '',
    columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
  })
  mockedCards.list.mockResolvedValue([])
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

  it('leitet Editier-Rechte synchron aus den Memberships ab (kein projectsApi-Nachladen)', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderPage()
    expect(await screen.findByText('B')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Karte in Backlog anlegen')).toBeInTheDocument())
    expect(mockedProjects.list).not.toHaveBeenCalled()
  })

  it('blendet für VIEWER-Membership die Anlege-Aktion aus', async () => {
    memberships = [{ projectId: 9, role: 'VIEWER' }]
    renderPage()
    expect(await screen.findByText('B')).toBeInTheDocument()
    expect(screen.queryByLabelText('Karte in Backlog anlegen')).not.toBeInTheDocument()
  })
})
