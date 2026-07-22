import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { labelsApi } from '../api/labels'
import { projectsApi } from '../api/projects'
import { epicsApi, type Epic } from '../api/epics'
import { BoardListPage } from './BoardListPage'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, memberships: [{ projectId: 9, role: 'OWNER' }] } }),
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn() } }))
vi.mock('../api/cards', () => ({
  cardsApi: {
    list: vi.fn(),
    move: vi.fn(),
    restore: vi.fn(),
    moveToIdeaStorage: vi.fn(),
    create: vi.fn(),
    getActivity: vi.fn().mockResolvedValue([]),
    update: vi.fn(),
    setAssignees: vi.fn(),
    setLabels: vi.fn(),
  },
}))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn() } }))
vi.mock('../api/labels', () => ({ labelsApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
vi.mock('../api/comments', () => ({ commentsApi: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), update: vi.fn(), remove: vi.fn() } }))
vi.mock('../api/attachments', () => ({ attachmentsApi: { list: vi.fn().mockResolvedValue([]), upload: vi.fn(), remove: vi.fn(), fetchBlob: vi.fn() } }))
// BoardListPage selbst kennt keinen Editiermodus; das eingebettete CardDetailModal aber schon —
// für dessen Bearbeiten-Button laufen die Tests mit editMode=true.
vi.mock('../lib/EditModeContext', () => ({
  useEditMode: () => ({ editMode: true, setEditMode: vi.fn(), toggleEditMode: vi.fn() }),
}))

const mBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  restore: ReturnType<typeof vi.fn>
  move: ReturnType<typeof vi.fn>
  update: ReturnType<typeof vi.fn>
  moveToIdeaStorage: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
}
const mEpics = epicsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mLabels = labelsApi as unknown as { list: ReturnType<typeof vi.fn> }

const base = {
  boardId: 1, positionInColumn: 0, ideaStored: false, movedToDoneAt: null as string | null,
  dependencies: [] as number[], type: 'CARD' as const, parentId: null as number | null, shortcode: null as string | null, assignees: [] as number[], dueDate: null as string | null, labels: [] as number[],
}
const active: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Aufgabe', description: '# Titel\nText **fett**', archived: false }
const archived: Card = { ...base, id: 101, columnId: 20, number: 2, title: 'AlteKarte', description: 'x', archived: true }
const idea: Card = { ...base, id: 102, columnId: 10, number: 3, title: 'MeineIdee', description: 'Idee-Text', archived: false, ideaStored: true }
const epic: Epic = { id: 7, number: 1, title: 'Mein Epic', description: null, shortcode: 'EP1', done: 0, total: 1 }

function renderPage(cards: Card[] = [active, archived]) {
  mBoards.get.mockResolvedValue({
    id: 1, projectId: 9, name: 'B', createdAt: '',
    columns: [
      { id: 10, name: 'Backlog', position: 0, wipLimit: null },
      { id: 20, name: 'Done', position: 1, wipLimit: null },
    ],
  })
  mCards.list.mockResolvedValue(cards)
  mEpics.list.mockResolvedValue([])
  mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
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
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
  })

  it('zeigt den Breadcrumb-Pfad ab Projekte', async () => {
    renderPage()
    expect(await screen.findByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/')
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

  it('stellt eine archivierte Karte über die Zeilen-Aktion wieder her', async () => {
    mCards.restore.mockResolvedValue({})
    renderPage()
    fireEvent.click(await screen.findByLabelText('Filter Archiv'))
    await screen.findByText('AlteKarte')

    // Nur die archivierte Karte trägt die Wiederherstellen-Aktion.
    expect(screen.queryByLabelText('Karte Aufgabe wiederherstellen')).not.toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Karte AlteKarte wiederherstellen'))

    await waitFor(() => expect(mCards.restore).toHaveBeenCalledWith(101))
  })

  it('öffnet das Detail-Modal beim Klick auf eine Zeile', async () => {
    renderPage()
    fireEvent.click(await screen.findByText('Aufgabe'))
    expect(await screen.findByRole('button', { name: 'Schließen' })).toBeInTheDocument()
  })

  it('blendet ideaStored-Karten komplett aus der Liste aus (Ideen sind jetzt projektweit)', async () => {
    renderPage([active, idea])
    await screen.findByText('Aufgabe')

    // Die Idee (ideaStored) taucht in der Board-Listenansicht nicht mehr auf — sie lebt seit dem
    // projektweiten Ideen-Pool auf der Projekt-Ideen-Seite. Es gibt keine Ideen-Zone mehr.
    expect(screen.queryByText('MeineIdee')).not.toBeInTheDocument()
    expect(screen.queryByTestId('idea-zone')).not.toBeInTheDocument()
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
    fireEvent.dragEnd(screen.getByLabelText('Spalte Nr'))

    // Nachher: 'Beschreibung' steht vorne und die Reihenfolge liegt in localStorage.
    const after = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(after[0]).toBe('Spalte Beschreibung')
    expect(JSON.parse(localStorage.getItem('manban.listColumns.1')!)).toEqual(
      ['excerpt', 'number', 'status', 'epic', 'title'],
    )
  })

  it('verwirft eine spät auflösende Karten-Antwort der alten Board-ID nach einem ID-Wechsel', async () => {
    const boardShape = (id: number) => ({
      id, projectId: 9, name: 'B', createdAt: '',
      columns: [
        { id: 10, name: 'Backlog', position: 0, wipLimit: null },
        { id: 20, name: 'Done', position: 1, wipLimit: null },
      ],
    })
    mBoards.get.mockImplementation((id: number) => Promise.resolve(boardShape(id)))
    mEpics.list.mockResolvedValue([])

    const oldCard: Card = { ...base, id: 200, columnId: 10, number: 5, title: 'AlteAufgabe', description: 'x', archived: false }
    const newCard: Card = { ...base, id: 300, columnId: 10, number: 6, title: 'NeueAufgabe', description: 'y', archived: false }
    const dOld = deferred<Card[]>()
    const dNew = deferred<Card[]>()
    mCards.list.mockReturnValueOnce(dOld.promise).mockReturnValueOnce(dNew.promise)

    function Nav() {
      const navigate = useNavigate()
      return <button onClick={() => navigate('/boards/2/list')}>wechseln</button>
    }
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Nav />
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    // Wechsel auf Board 2, bevor die Karten von Board 1 geladen sind.
    fireEvent.click(screen.getByText('wechseln'))
    dNew.resolve([newCard])
    expect(await screen.findByText('NeueAufgabe')).toBeInTheDocument()

    // Die verspätete Antwort für Board 1 darf die Karten nicht mehr überschreiben.
    dOld.resolve([oldCard])
    expect(await screen.findByText('NeueAufgabe')).toBeInTheDocument()
    expect(screen.queryByText('AlteAufgabe')).not.toBeInTheDocument()
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
      // Klick auf den Griff darf nicht zum Spalten-Header durchgereicht werden (stopPropagation).
      fireEvent.click(handle)
      fireEvent.mouseDown(handle, { clientX: 500 })
      fireEvent.mouseMove(document, { clientX: 400 }) // 100px nach links → +10 %
      fireEvent.mouseUp(document)

      // Default 30 % + 10 % = 40 %, in localStorage persistiert.
      expect(localStorage.getItem('manban.listExcerptWidth.1')).toBe('40')
    } finally {
      rectSpy.mockRestore()
    }
  })

  it('filtert die Liste nach Label', async () => {
    const labelled: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'MitLabel', description: '', archived: false, labels: [5] }
    const other: Card = { ...base, id: 102, columnId: 10, number: 3, title: 'OhneLabel', description: '', archived: false }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([labelled, other])
    mEpics.list.mockResolvedValue([])
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
    mLabels.list.mockResolvedValue([{ id: 5, boardId: 1, name: 'Bug', color: '#f00' }])

    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('MitLabel')).toBeInTheDocument()
    expect(screen.getByText('OhneLabel')).toBeInTheDocument()

    fireEvent.click(await screen.findByLabelText('Label-Filter Bug'))

    await waitFor(() => expect(screen.queryByText('OhneLabel')).not.toBeInTheDocument())
    expect(screen.getByText('MitLabel')).toBeInTheDocument()
  })

  it('blendet Karten einer Spalte über den Spalten-Filter aus und merkt die Auswahl', async () => {
    renderPage()
    await screen.findByText('Aufgabe')

    fireEvent.click(screen.getByLabelText('Filter Backlog'))

    await waitFor(() => expect(screen.queryByText('Aufgabe')).not.toBeInTheDocument())
    expect(JSON.parse(localStorage.getItem('manban.listFilters.1')!)).toEqual([20])
  })

  it('übernimmt eine gespeicherte Spaltenreihenfolge und ignoriert unbekannte Einträge', async () => {
    localStorage.setItem('manban.listColumns.1', JSON.stringify(['excerpt', 'foo', 'title']))
    renderPage()
    await screen.findByText('Aufgabe')

    const order = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(order).toEqual(['Spalte Beschreibung', 'Spalte Titel', 'Spalte Nr', 'Spalte Status', 'Spalte Epic'])
  })

  it('übernimmt einen gespeicherten Spalten-Filter aus localStorage', async () => {
    localStorage.setItem('manban.listFilters.1', JSON.stringify([20]))
    renderPage()

    // Nur Spalte 20 (Done) ist aktiv -> die aktive Karte (Spalte 10) ist ausgeblendet.
    await waitFor(() => expect(screen.queryByText('Aufgabe')).not.toBeInTheDocument())
  })

  it('fällt bei kaputtem Spalten-Filter in localStorage auf alle Spalten zurück', async () => {
    localStorage.setItem('manban.listFilters.1', 'kaputt{')
    renderPage()

    expect(await screen.findByText('Aufgabe')).toBeInTheDocument()
  })

  it('startet trotz kaputtem localStorage bei Breite und Spaltenreihenfolge mit den Vorgaben', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => { throw new Error('storage disabled') },
      setItem: () => { throw new Error('storage disabled') },
      removeItem: () => {},
      clear: () => {},
      key: () => null,
      length: 0,
    })
    renderPage()

    expect(await screen.findByText('Aufgabe')).toBeInTheDocument()
    const order = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(order[0]).toBe('Spalte Nr')
  })

  it('bricht das Resize-Speichern nicht ab, wenn localStorage beim Schreiben wirft', async () => {
    const rectSpy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 1000, height: 0, top: 0, left: 0, right: 1000, bottom: 0, x: 0, y: 0, toJSON: () => ({}),
    } as DOMRect)
    try {
      renderPage()
      await screen.findByText('Aufgabe')

      const handle = screen.getByLabelText('Beschreibung-Spalte breiter ziehen')
      fireEvent.dragStart(handle)
      vi.stubGlobal('localStorage', {
        getItem: () => null,
        setItem: () => { throw new Error('storage disabled') },
        removeItem: () => {},
        clear: () => {},
        key: () => null,
        length: 0,
      })
      fireEvent.mouseDown(handle, { clientX: 500 })
      fireEvent.mouseMove(document, { clientX: 400 })
      fireEvent.mouseUp(document)

      expect(screen.getByText('Aufgabe')).toBeInTheDocument()
    } finally {
      rectSpy.mockRestore()
    }
  })

  it('filtert und sortiert Spalten auch, wenn localStorage beim Schreiben wirft', async () => {
    renderPage()
    await screen.findByText('Aufgabe')

    vi.stubGlobal('localStorage', {
      getItem: () => null,
      setItem: () => { throw new Error('storage disabled') },
      removeItem: () => {},
      clear: () => {},
      key: () => null,
      length: 0,
    })

    fireEvent.click(screen.getByLabelText('Filter Backlog'))
    await waitFor(() => expect(screen.queryByText('Aufgabe')).not.toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('Filter Backlog'))
    await screen.findByText('Aufgabe')

    fireEvent.dragStart(screen.getByLabelText('Spalte Beschreibung'))
    fireEvent.drop(screen.getByLabelText('Spalte Nr'))

    const after = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(after[0]).toBe('Spalte Beschreibung')
  })

  it('lädt die Rolle nach, wenn sie nicht in den Memberships steht, und blendet dann Zeilen-Aktionen aus', async () => {
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 42, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([archived])
    mProjects.list.mockResolvedValue([{ id: 42, name: 'Fremd', role: 'VIEWER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByLabelText('Filter Archiv'))
    await screen.findByText('AlteKarte')
    await waitFor(() => expect(mProjects.list).toHaveBeenCalled())
    expect(screen.queryByLabelText('Karte AlteKarte wiederherstellen')).not.toBeInTheDocument()
  })

  it('verschiebt eine Karte per Zeilen-Drag innerhalb derselben Spalte', async () => {
    const first: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Erste', description: '', archived: false }
    const second: Card = { ...base, id: 103, columnId: 10, number: 4, title: 'Zweite', description: '', archived: false, positionInColumn: 1 }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([first, second])
    mCards.move.mockResolvedValue({})
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText('Erste')

    const dataTransfer = { setData: vi.fn() }
    fireEvent.dragStart(screen.getByText('Erste'), { dataTransfer })
    fireEvent.dragOver(screen.getByText('Zweite'), { dataTransfer })
    fireEvent.drop(screen.getByText('Zweite'), { dataTransfer })
    fireEvent.dragEnd(screen.getByText('Erste'))

    await waitFor(() => expect(mCards.move).toHaveBeenCalledWith(100, 10, 1))
  })

  it('öffnet das Detail per Enter-Taste auf einer Zeile', async () => {
    renderPage()
    const row = await screen.findByLabelText('Detail öffnen: Aufgabe')
    fireEvent.keyDown(row, { key: 'Enter' })
    expect(await screen.findByRole('button', { name: 'Schließen' })).toBeInTheDocument()
  })

  it('öffnet das Detail per Leertaste auf einer Zeile', async () => {
    renderPage()
    const row = await screen.findByLabelText('Detail öffnen: Aufgabe')
    fireEvent.keyDown(row, { key: ' ' })
    expect(await screen.findByRole('button', { name: 'Schließen' })).toBeInTheDocument()
  })

  it('schließt das Detail-Modal wieder und lädt nach dem Speichern Karten und Epics neu', async () => {
    const editCard: Card = {
      ...base, id: 100, columnId: 10, number: 1, title: 'Aufgabe', description: '', archived: false,
    }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([editCard])
    mCards.update.mockResolvedValue(editCard)
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByText('Aufgabe'))
    fireEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
    mCards.list.mockClear()
    mEpics.list.mockClear()
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(mCards.list).toHaveBeenCalled())
    expect(mEpics.list).toHaveBeenCalled()

    fireEvent.click(await screen.findByRole('button', { name: 'Schließen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('zeigt ein Fälligkeitsdatum-Badge in der Titel-Zelle', async () => {
    const withDue: Card = {
      ...base, id: 100, columnId: 10, number: 1, title: 'Aufgabe', description: '', archived: false,
      dueDate: new Date(Date.now() - 86_400_000).toISOString(),
    }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([withDue])
    mEpics.list.mockResolvedValue([])
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByLabelText('Fällig Aufgabe')).toBeInTheDocument()
  })

  it('zeigt kein überfälliges Fälligkeitsdatum in schlichter Farbe an', async () => {
    const withFutureDue: Card = {
      ...base, id: 100, columnId: 10, number: 1, title: 'Aufgabe', description: '', archived: false,
      dueDate: new Date(Date.now() + 7 * 86_400_000).toISOString(),
    }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([withFutureDue])
    mEpics.list.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByLabelText('Fällig Aufgabe')).toBeInTheDocument()
  })

  it('zeigt ein Epic-Badge in der Epic-Spalte, wenn die Karte einem Epic zugeordnet ist', async () => {
    const child: Card = {
      ...base, id: 100, columnId: 10, number: 1, title: 'Aufgabe', description: '', archived: false, parentId: 7,
    }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([child])
    mEpics.list.mockResolvedValue([epic])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    await screen.findByText('Aufgabe')
    expect(screen.getByText('EP1')).toBeInTheDocument()
  })

  it('rendert die Beschreibungs-Spalte auch bei fehlender Beschreibung ohne Fehler', async () => {
    const noDesc: Card = {
      ...base, id: 100, columnId: 10, number: 1, title: 'OhneText', description: null, archived: false,
    }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([noDesc])
    mEpics.list.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('OhneText')).toBeInTheDocument()
  })

  it('zeigt eine Karte an, deren Spalte nicht mehr existiert (leerer Status)', async () => {
    localStorage.setItem('manban.listFilters.1', JSON.stringify([999]))
    const orphan: Card = {
      ...base, id: 100, columnId: 999, number: 1, title: 'Verwaist', description: 'x', archived: false,
    }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([orphan])
    mEpics.list.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('Verwaist')).toBeInTheDocument()
  })

  it('sortiert mehrere archivierte Karten ohne existierende Spalte stabil nach Position', async () => {
    const a1: Card = { ...base, id: 100, columnId: 999, number: 1, title: 'ArchivEins', description: 'x', archived: true, positionInColumn: 0 }
    const a2: Card = { ...base, id: 101, columnId: 999, number: 2, title: 'ArchivZwei', description: 'y', archived: true, positionInColumn: 1 }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([a1, a2])
    mEpics.list.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByLabelText('Filter Archiv'))
    expect(await screen.findByText('ArchivEins')).toBeInTheDocument()
    expect(screen.getByText('ArchivZwei')).toBeInTheDocument()
  })

  it('nimmt eine gespeicherte Beschreibungs-Breite aus localStorage beim Mount an', async () => {
    localStorage.setItem('manban.listExcerptWidth.1', '45')
    renderPage()
    expect(await screen.findByText('Aufgabe')).toBeInTheDocument()
  })

  it('zeigt bei fehlendem Board-ID-Parameter einen Fehler', async () => {
    render(
      <MemoryRouter initialEntries={['/list']}>
        <Routes>
          <Route path="/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Ungültige Board-ID.')).toBeInTheDocument()
    expect(mBoards.get).not.toHaveBeenCalled()
  })

  it('räumt einen laufenden Resize-Drag beim Unmount ab', async () => {
    const rectSpy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 1000, height: 0, top: 0, left: 0, right: 1000, bottom: 0, x: 0, y: 0, toJSON: () => ({}),
    } as DOMRect)
    const removeSpy = vi.spyOn(document, 'removeEventListener')
    try {
      const { unmount } = renderPage()
      await screen.findByText('Aufgabe')

      // Drag starten (registriert Listener + Cleanup-Ref), dann ohne mouseUp unmounten.
      fireEvent.mouseDown(screen.getByLabelText('Beschreibung-Spalte breiter ziehen'), { clientX: 500 })
      removeSpy.mockClear()
      unmount()

      expect(removeSpy).toHaveBeenCalledWith('mousemove', expect.any(Function))
      expect(removeSpy).toHaveBeenCalledWith('mouseup', expect.any(Function))
    } finally {
      removeSpy.mockRestore()
      rectSpy.mockRestore()
    }
  })

  it('ignoriert Resize-Bewegungen, wenn die View-Breite nicht bestimmbar ist', async () => {
    const rectSpy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: undefined, height: 0, top: 0, left: 0, right: 0, bottom: 0, x: 0, y: 0, toJSON: () => ({}),
    } as unknown as DOMRect)
    try {
      renderPage()
      await screen.findByText('Aufgabe')

      const handle = screen.getByLabelText('Beschreibung-Spalte breiter ziehen')
      fireEvent.mouseDown(handle, { clientX: 500 })
      fireEvent.mouseMove(document, { clientX: 400 })
      fireEvent.mouseUp(document)

      // Ohne bestimmbare Breite bleibt die gespeicherte Breite auf dem Default (keine Verbreiterung).
      expect(localStorage.getItem('manban.listExcerptWidth.1')).toBe('30')
    } finally {
      rectSpy.mockRestore()
    }
  })

  it('behandelt einen Klick auf den Archiv-Filter, bevor das Board geladen ist', async () => {
    mBoards.get.mockReturnValue(new Promise(() => {}))
    mCards.list.mockResolvedValue([active])
    mEpics.list.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const archiv = await screen.findByLabelText('Filter Archiv')
    expect(archiv).toHaveAttribute('aria-pressed', 'false')
    fireEvent.click(archiv)
    expect(screen.getByLabelText('Filter Archiv')).toHaveAttribute('aria-pressed', 'true')
  })

  it('schaltet einen Label-Filter durch erneuten Klick wieder aus', async () => {
    const labelled: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'MitLabel', description: '', archived: false, labels: [5] }
    const other: Card = { ...base, id: 102, columnId: 10, number: 3, title: 'OhneLabel', description: '', archived: false }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([labelled, other])
    mEpics.list.mockResolvedValue([])
    mLabels.list.mockResolvedValue([{ id: 5, boardId: 1, name: 'Bug', color: '#f00' }])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByLabelText('Label-Filter Bug'))
    await waitFor(() => expect(screen.queryByText('OhneLabel')).not.toBeInTheDocument())
    // Zweiter Klick entfernt das Label wieder aus dem Filter.
    fireEvent.click(screen.getByLabelText('Label-Filter Bug'))
    expect(await screen.findByText('OhneLabel')).toBeInTheDocument()
  })

  it('ignoriert einen Spalten-Drop auf dieselbe Spalte (keine Umsortierung)', async () => {
    renderPage()
    await screen.findByText('Aufgabe')

    fireEvent.dragStart(screen.getByLabelText('Spalte Nr'))
    fireEvent.drop(screen.getByLabelText('Spalte Nr'))
    fireEvent.dragEnd(screen.getByLabelText('Spalte Nr'))

    const order = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(order[0]).toBe('Spalte Nr')
    expect(localStorage.getItem('manban.listColumns.1')).toBeNull()
  })

  it('ignoriert einen Zeilen-Drop ohne vorangehenden Drag', async () => {
    renderPage([active])
    await screen.findByText('Aufgabe')

    fireEvent.drop(screen.getByLabelText('Detail öffnen: Aufgabe'))

    expect(mCards.move).not.toHaveBeenCalled()
  })

  it('öffnet das Detail beim Klick und per Enter-/Leertaste auf einer Karten-Zeile', async () => {
    renderPage([active])
    const row = await screen.findByLabelText('Detail öffnen: Aufgabe')

    fireEvent.click(row)
    expect(await screen.findByRole('button', { name: 'Schließen' })).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Schließen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    fireEvent.keyDown(screen.getByLabelText('Detail öffnen: Aufgabe'), { key: 'Enter' })
    expect(await screen.findByRole('button', { name: 'Schließen' })).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Schließen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    fireEvent.keyDown(screen.getByLabelText('Detail öffnen: Aufgabe'), { key: ' ' })
    expect(await screen.findByRole('button', { name: 'Schließen' })).toBeInTheDocument()
  })
})
