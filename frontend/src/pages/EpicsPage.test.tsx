import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi } from '../api/cards'
import { epicsApi } from '../api/epics'
import { EpicsPage } from './EpicsPage'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, memberships: [{ projectId: 9, role: 'OWNER' }] } }),
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/cards', () => ({ cardsApi: { list: vi.fn() } }))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn(), assign: vi.fn(), create: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))

const mBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mEpics = epicsApi as unknown as { list: ReturnType<typeof vi.fn>; create: ReturnType<typeof vi.fn> }

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/boards/1/epics']}>
      <Routes>
        <Route path="/boards/:boardId/epics" element={<EpicsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('EpicsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mBoards.get.mockResolvedValue({ id: 1, projectId: 9, name: 'B', createdAt: '', columns: [] })
    mCards.list.mockResolvedValue([])
    mEpics.create.mockResolvedValue({})
  })

  it('listet Epics mit Kürzel und Fortschritt', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2 },
    ])
    renderPage()

    expect(await screen.findByText('Auth')).toBeInTheDocument()
    expect(screen.getByText('AUT')).toBeInTheDocument()
    expect(screen.getByText('1/2 Stories fertig')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Fortschritt Auth')).toBeInTheDocument())
  })

  it('legt über „Neues Epic" ein Epic an', async () => {
    mEpics.list.mockResolvedValue([])
    renderPage()
    await screen.findByText('Epics')

    fireEvent.click(screen.getByRole('button', { name: 'Neues Epic' }))
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Auth-Epic' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(mEpics.create).toHaveBeenCalledWith(1, 'Auth-Epic', expect.any(String), null))
  })
})
