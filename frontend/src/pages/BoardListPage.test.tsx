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

function fakeStorage(): Storage {
  const map = new Map<string, string>()
  return {
    getItem: (k) => map.get(k) ?? null,
    setItem: (k, v) => void map.set(k, String(v)),
    removeItem: (k) => void map.delete(k),
    clear: () => map.clear(),
    key: (i) => [...map.keys()][i] ?? null,
    get length() { return map.size },
  }
}

describe('BoardListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('localStorage', fakeStorage())
  })

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

  it('sortiert Spalten per Header-Drag um und merkt die Reihenfolge', async () => {
    renderPage()
    await screen.findByText('Aufgabe')

    // Vorher: erste Spalte ist 'Nr'.
    const before = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(before[0]).toBe('Spalte Nr')

    fireEvent.dragStart(screen.getByLabelText('Spalte Beschreibung'))
    fireEvent.dragOver(screen.getByLabelText('Spalte Nr'))
    fireEvent.drop(screen.getByLabelText('Spalte Nr'))

    // Nachher: 'Beschreibung' steht vorne und die Reihenfolge liegt in localStorage.
    const after = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(after[0]).toBe('Spalte Beschreibung')
    expect(JSON.parse(localStorage.getItem('manban.listColumns.1')!)).toEqual(
      ['excerpt', 'number', 'status', 'epic', 'title'],
    )
  })

  it('zeigt bei ungültiger Board-ID einen Fehler und ruft keine API auf', async () => {
    render(
      <MemoryRouter initialEntries={['/boards/abc/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Ungültige Board-ID.')).toBeInTheDocument()
    expect(mBoards.get).not.toHaveBeenCalled()
    expect(mCards.list).not.toHaveBeenCalled()
    expect(mEpics.list).not.toHaveBeenCalled()
  })

  it('verbreitert die Beschreibungs-Spalte per Resize-Drag und merkt die Breite', async () => {
    const rectSpy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 1000, height: 0, top: 0, left: 0, right: 1000, bottom: 0, x: 0, y: 0, toJSON: () => ({}),
    } as DOMRect)
    try {
      renderPage()
      await screen.findByText('Aufgabe')

      const handle = screen.getByLabelText('Beschreibung-Spalte breiter ziehen')
      fireEvent.mouseDown(handle, { clientX: 500 })
      fireEvent.mouseMove(document, { clientX: 400 }) // 100px nach links → +10 %
      fireEvent.mouseUp(document)

      // Default 30 % + 10 % = 40 %, in localStorage persistiert.
      expect(localStorage.getItem('manban.listExcerptWidth.1')).toBe('40')
    } finally {
      rectSpy.mockRestore()
    }
  })
})
