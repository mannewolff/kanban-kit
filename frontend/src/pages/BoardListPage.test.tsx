import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { epicsApi } from '../api/epics'
import { BoardListPage } from './BoardListPage'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, memberships: [{ projectId: 9, role: 'OWNER' }] } }),
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/cards', () => ({ cardsApi: { list: vi.fn(), move: vi.fn() } }))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/comments', () => ({ commentsApi: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), update: vi.fn(), remove: vi.fn() } }))
vi.mock('../api/attachments', () => ({ attachmentsApi: { list: vi.fn().mockResolvedValue([]), upload: vi.fn(), remove: vi.fn(), fetchBlob: vi.fn() } }))

const mBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mEpics = epicsApi as unknown as { list: ReturnType<typeof vi.fn> }

const base = {
  boardId: 1, positionInColumn: 0, movedToDoneAt: null as string | null,
  dependencies: [] as number[], type: 'CARD' as const, parentId: null as number | null, shortcode: null as string | null,
}
const active: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Aufgabe', description: '# Titel\nText **fett**', archived: false }
const archived: Card = { ...base, id: 101, columnId: 20, number: 2, title: 'AlteKarte', description: 'x', archived: true }

function renderPage() {
  mBoards.get.mockResolvedValue({
    id: 1, projectId: 9, name: 'B', createdAt: '',
    columns: [
      { id: 10, name: 'Backlog', position: 0, wipLimit: null },
      { id: 20, name: 'Done', position: 1, wipLimit: null },
    ],
  })
  mCards.list.mockResolvedValue([active, archived])
  mEpics.list.mockResolvedValue([])
  return render(
    <MemoryRouter initialEntries={['/boards/1/list']}>
      <Routes>
        <Route path="/boards/:boardId/list" element={<BoardListPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('BoardListPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('zeigt aktive Karten mit Status-Chip und Body-Vorschau, archivierte erst nach Filter', async () => {
    renderPage()
    expect(await screen.findByText('Aufgabe')).toBeInTheDocument()
    expect(screen.getByText('Titel Text fett')).toBeInTheDocument()
    // Archivierte Karte ist per Default ausgeblendet.
    expect(screen.queryByText('AlteKarte')).not.toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Filter Archiv'))
    expect(await screen.findByText('AlteKarte')).toBeInTheDocument()
  })

  it('öffnet das Detail-Modal beim Klick auf eine Zeile', async () => {
    renderPage()
    fireEvent.click(await screen.findByText('Aufgabe'))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Schließen' })).toBeInTheDocument())
  })
})
