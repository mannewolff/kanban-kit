import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi } from '../api/cards'
import { epicsApi } from '../api/epics'
import { projectsApi } from '../api/projects'
import { EpicsPage } from './EpicsPage'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, memberships: [{ projectId: 9, role: 'OWNER' }] } }),
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/cards', () => ({
  cardsApi: {
    list: vi.fn(),
    getActivity: vi.fn().mockResolvedValue([]),
    update: vi.fn(),
    setAssignees: vi.fn(),
    setLabels: vi.fn(),
    restore: vi.fn(),
  },
}))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn(), assign: vi.fn(), create: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/comments', () => ({
  commentsApi: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('../api/attachments', () => ({
  attachmentsApi: { list: vi.fn().mockResolvedValue([]), upload: vi.fn(), remove: vi.fn(), fetchBlob: vi.fn() },
}))

const mBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mEpics = epicsApi as unknown as { list: ReturnType<typeof vi.fn>; create: ReturnType<typeof vi.fn> }
const mProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }

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
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
  })

  it('zeigt den Breadcrumb-Pfad ab Projekte', async () => {
    mEpics.list.mockResolvedValue([])
    renderPage()
    expect(await screen.findByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/')
  })

  it('listet Epics mit Kürzel und Fortschritt', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 1, total: 2 },
    ])
    renderPage()

    expect(await screen.findByText('Auth')).toBeInTheDocument()
    expect(screen.getByText('AUT')).toBeInTheDocument()
    expect(screen.getByText('1/2 Stories fertig')).toBeInTheDocument()
    expect(await screen.findByLabelText('Fortschritt Auth')).toBeInTheDocument()
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

  it('zeigt bei ungültiger Board-ID einen Fehler und ruft keine API auf', async () => {
    render(
      <MemoryRouter initialEntries={['/boards/abc/epics']}>
        <Routes>
          <Route path="/boards/:boardId/epics" element={<EpicsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Ungültige Board-ID.')).toBeInTheDocument()
    expect(mBoards.get).not.toHaveBeenCalled()
    expect(mEpics.list).not.toHaveBeenCalled()
    expect(mCards.list).not.toHaveBeenCalled()
  })

  it('öffnet ein Epic per Klick im Detail-Modal mit seinen Kind-Karten', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Auth', description: 'Text', shortcode: 'AUT', done: 1, total: 2 },
    ])
    mCards.list.mockResolvedValue([
      {
        id: 30, boardId: 1, columnId: 10, number: 3, title: 'Kind', description: null,
        positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
        type: 'CARD', parentId: 9, shortcode: null, assignees: [], dueDate: null, labels: [],
      },
    ])
    renderPage()

    fireEvent.click(await screen.findByText('Auth'))

    expect(await screen.findByText('Karten (1)')).toBeInTheDocument()
    expect(screen.getByText('#3 · Kind')).toBeInTheDocument()
  })

  it('zeigt 0 % Fortschritt für ein Epic ohne Stories und schließt das Detail-Modal', async () => {
    mEpics.list.mockResolvedValue([
      { id: 9, number: 2, title: 'Leer', description: 'X', shortcode: 'LEE', done: 0, total: 0 },
    ])
    renderPage()

    const progress = await screen.findByLabelText('Fortschritt Leer')
    expect(progress).toHaveAttribute('aria-valuenow', '0')

    fireEvent.click(screen.getByText('Leer'))
    expect(await screen.findByText('Karten (0)')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))
    await waitFor(() => expect(screen.queryByText('Karten (0)')).not.toBeInTheDocument())
  })

  it('lädt die Rolle nach, wenn sie nicht in den Memberships steht', async () => {
    mBoards.get.mockResolvedValue({ id: 1, projectId: 42, name: 'B', createdAt: '', columns: [] })
    mEpics.list.mockResolvedValue([])
    mProjects.list.mockResolvedValue([{ id: 42, name: 'Fremd', role: 'VIEWER', createdAt: '' }])
    renderPage()

    await screen.findByText('Epics')
    await waitFor(() => expect(mProjects.list).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: 'Neues Epic' })).not.toBeInTheDocument()
  })

  it('behandelt einen fehlenden Board-Parameter als ungültig (boardId undefined)', () => {
    render(
      <MemoryRouter initialEntries={['/epics']}>
        <Routes>
          <Route path="/epics" element={<EpicsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByText('Ungültige Board-ID.')).toBeInTheDocument()
  })
})
