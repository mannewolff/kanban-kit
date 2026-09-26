import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { ApiError } from '../api/client'
import { labelsApi } from '../api/labels'
import { projectsApi } from '../api/projects'
import { epicsApi, type Epic } from '../api/epics'
import { membersApi } from '../api/members'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { BoardListPage } from './BoardListPage'
import { ARCHIVED_STATUS_COLOR } from '../lib/statusColors'
import { ThemeProvider } from '@mui/material/styles'
import { cssRegel, cssRegelMit } from '../test/cssRegel'
import { theme } from '../theme'

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
    // Das CardDetailModal lädt die volle Beschreibung beim Öffnen nach (Issue #769).
    get: vi.fn().mockResolvedValue({ description: null }),
    move: vi.fn(),
    restore: vi.fn(),
    moveToIdeaStorage: vi.fn(),
    create: vi.fn(),
    getActivity: vi.fn().mockResolvedValue([]),
    // Der Detail-Dialog eines Vorhabens lädt seinen Herkunftsbaum nach (Issue #644).
    epicTree: vi.fn().mockResolvedValue([]),
    update: vi.fn(),
    setAssignees: vi.fn(),
    setLabels: vi.fn(),
  },
}))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn() } }))
vi.mock('../api/labels', () => ({ labelsApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('../api/members', () => ({ membersApi: { list: vi.fn().mockResolvedValue([]) } }))
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
  boardId: 1, positionInColumn: 0, movedToDoneAt: null as string | null,
  dependencies: [] as number[], type: 'CARD' as const, parentId: null as number | null, shortcode: null as string | null, assignees: [] as number[], dueDate: null as string | null, labels: [] as number[],
  derivedFrom: null as number | null,
  // Die Listen-Antwort liefert die Beschreibung nur noch als Auszug (Issue #771); `description`
  // ist dort immer `null`. Die Fixturen tragen den Vorschautext deshalb in `excerpt`.
  excerpt: null as string | null,
}
const active: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Aufgabe', description: '# Titel\nText **fett**', excerpt: '# Titel\nText **fett**', archived: false }
const archived: Card = { ...base, id: 101, columnId: 20, number: 2, title: 'AlteKarte', description: 'x', archived: true }
// `memberNumbers: [1]` fasst die Zugehörigkeit der Karte #1: Die Zuordnung rechnet über die
// Vorhaben-Liste, nicht über `parentId` (Issue #689) — das Muster „parentId gesetzt,
// memberNumbers leer" gibt es real nicht.
const epic: Epic = { id: 7, number: 1, title: 'Mein Epic', description: null, shortcode: 'EP1', done: 0, total: 1, memberNumbers: [1], rootNumbers: [], requirementCardNumber: null }

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
    <SnackbarProvider>
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>
    </SnackbarProvider>,
  )
}

/** Server-Ablehnung mit einer für den Nutzer formulierten Meldung in `detail` (RFC 9457). */
const serverfehler = (text: string) => new ApiError(409, 'Conflict', undefined, text)

/** Prüft den Fehler-Toast: Der Text steht dort, und der Alert trägt die Severity `error`. */
async function erwarteFehlerToast(text: string) {
  expect(await screen.findByText(text)).toBeInTheDocument()
  expect(await screen.findByRole('alert', { hidden: true })).toHaveClass('MuiAlert-filledError')
}

/**
 * Eine Liste mit genau einer Karte (`active`, Nummer 1) und dem Vorhaben `epic`, das sie über
 * `memberNumbers` führt — ohne `parentId` an der Karte (Issue #689).
 */
function renderEpicPage() {
  mBoards.get.mockResolvedValue({
    id: 1, projectId: 9, name: 'B', createdAt: '',
    columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
  })
  mCards.list.mockResolvedValue([active])
  mEpics.list.mockResolvedValue([epic])
  return render(
    <MemoryRouter initialEntries={['/boards/1/list']}>
      <Routes>
        <Route path="/boards/:boardId/list" element={<BoardListPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

/**
 * Der Hinweis, der zum Umordnen von Karten per Drag anleitet. Er spricht bewusst nicht vom
 * „Sortieren" — das heißt seit Issue #700 nur noch das Sortieren nach Spalteninhalt.
 */
const UMORDNEN_HINWEIS =
  'Kartenreihenfolge ändern: dazu genau einen Status-Filter wählen und den Archiv-Filter abwählen.'

/** Board mit genau einer Spalte: der Zustand, in dem Umordnen per Drag möglich ist. */
function einspaltigesBoard(boardId: number, projectId = 9) {
  return {
    id: boardId, projectId, name: 'B', createdAt: '',
    columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
  }
}

// Board-Reihenfolge (positionInColumn): Beta, Zebra, Anton. Weder nach Titel noch nach Nr fällt sie
// mit einer der beiden Sortierrichtungen zusammen — nur so belegt ein Test, welcher Zustand gilt.
const beta: Card = { ...base, id: 100, columnId: 10, number: 3, title: 'Beta', description: '', archived: false, positionInColumn: 0 }
const zebra: Card = { ...base, id: 101, columnId: 10, number: 1, title: 'Zebra', description: '', archived: false, positionInColumn: 1 }
const anton: Card = { ...base, id: 102, columnId: 10, number: 2, title: 'Anton', description: '', archived: false, positionInColumn: 2 }

function renderSortPage(cards: Card[] = [beta, zebra, anton], projectId = 9) {
  mBoards.get.mockImplementation((bid: number) => Promise.resolve(einspaltigesBoard(bid, projectId)))
  mCards.list.mockResolvedValue(cards)
  mEpics.list.mockResolvedValue([])
  mCards.move.mockResolvedValue({})
  return render(
    <MemoryRouter initialEntries={['/boards/1/list']}>
      <BoardWechsler />
      <Routes>
        <Route path="/boards/:boardId/list" element={<BoardListPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

/** Wechselt den Routen-Parameter, ohne die Seite neu zu montieren. */
function BoardWechsler() {
  const navigate = useNavigate()
  return <button onClick={() => navigate('/boards/2/list')}>Board wechseln</button>
}

/** Die Titel der Zeilen in ihrer angezeigten Reihenfolge. */
function zeilenTitel(): string[] {
  return screen
    .getAllByLabelText(/^Detail öffnen: /)
    .map((el) => el.getAttribute('aria-label')!.replace('Detail öffnen: ', ''))
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
    // Die Bestandstests prüfen die ungruppierte Liste; die Gruppierung nach Vorhaben hat eigene Tests.
    localStorage.setItem('manban.listGruppierung', 'keine')
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
  })

  it('zeigt den Breadcrumb-Pfad ab Projekte', async () => {
    renderPage()
    expect(await screen.findByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/projects')
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

  it('trägt den Status als Plakette mit LED in der Zeile, archivierte in ihrer eigenen Farbe (#980)', async () => {
    renderPage()
    fireEvent.click(await screen.findByLabelText('Filter Archiv'))

    const aufgabe = within(await screen.findByLabelText('Detail öffnen: Aufgabe')).getByTestId('zustand-100')
    expect(aufgabe).toHaveTextContent('Backlog')
    expect(cssRegel(within(aufgabe).getByTestId('zustand-led-100'))).toContain('background-color: var(--mb-palette-status-backlog-dot')
    const alt = within(screen.getByLabelText('Detail öffnen: AlteKarte')).getByTestId('zustand-101')
    expect(alt).toHaveTextContent('Archiv')
    expect(cssRegel(within(alt).getByTestId('zustand-led-101'))).toContain('background-color: var(--mb-palette-status-archived-dot')
    expect(cssRegel(screen.getByLabelText('Detail öffnen: Aufgabe'))).not.toContain('border-left:')
    expect(ARCHIVED_STATUS_COLOR.dot).toBe('var(--mb-palette-status-archived-dot)')
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

  it('zeigt die Server-Meldung, wenn das Wiederherstellen scheitert, und lädt den Serverstand nach', async () => {
    mCards.restore.mockRejectedValue(serverfehler('Die Karte wurde inzwischen gelöscht.'))
    renderPage()
    fireEvent.click(await screen.findByLabelText('Filter Archiv'))
    await screen.findByText('AlteKarte')
    mCards.list.mockClear()

    fireEvent.click(screen.getByLabelText('Karte AlteKarte wiederherstellen'))

    await erwarteFehlerToast('Die Karte wurde inzwischen gelöscht.')
    // Nach einem Konflikt zeigt die Liste den Serverstand — der Nachladeschritt läuft im `finally`.
    await waitFor(() => expect(mCards.list).toHaveBeenCalled())
  })

  it('fällt beim Wiederherstellen ohne Server-Meldung auf den eigenen Text zurück', async () => {
    mCards.restore.mockRejectedValue(new TypeError('Failed to fetch'))
    renderPage()
    fireEvent.click(await screen.findByLabelText('Filter Archiv'))
    await screen.findByText('AlteKarte')

    fireEvent.click(screen.getByLabelText('Karte AlteKarte wiederherstellen'))

    await erwarteFehlerToast('Karte wiederherstellen fehlgeschlagen.')
  })

  it('zeigt die Server-Meldung, wenn ein Zeilen-Drop scheitert, und lädt den Serverstand nach', async () => {
    const first: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Erste', description: '', archived: false }
    const second: Card = { ...base, id: 103, columnId: 10, number: 4, title: 'Zweite', description: '', archived: false, positionInColumn: 1 }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([first, second])
    mEpics.list.mockResolvedValue([])
    mCards.move.mockRejectedValue(serverfehler('Die Karte liegt inzwischen woanders.'))
    render(
      <SnackbarProvider>
        <MemoryRouter initialEntries={['/boards/1/list']}>
          <Routes>
            <Route path="/boards/:boardId/list" element={<BoardListPage />} />
          </Routes>
        </MemoryRouter>
      </SnackbarProvider>,
    )
    await screen.findByText('Erste')
    mCards.list.mockClear()

    const dataTransfer = { setData: vi.fn() }
    fireEvent.dragStart(screen.getByText('Erste'), { dataTransfer })
    fireEvent.dragOver(screen.getByText('Zweite'), { dataTransfer })
    fireEvent.drop(screen.getByText('Zweite'), { dataTransfer })

    await erwarteFehlerToast('Die Karte liegt inzwischen woanders.')
    await waitFor(() => expect(mCards.list).toHaveBeenCalled())
  })

  it('öffnet das Detail-Modal beim Klick auf eine Zeile', async () => {
    renderPage()
    fireEvent.click(await screen.findByText('Aufgabe'))
    expect(await screen.findByRole('button', { name: 'Schließen' })).toBeInTheDocument()
  })

  it('zeigt jede aktive Karte der Spalte — es gibt keine Ideen-Zone mehr (Issue #1202)', async () => {
    renderPage([active])
    await screen.findByText('Aufgabe')

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
    expect(JSON.parse(localStorage.getItem('manban.listColumns')!)).toEqual(
      ['excerpt', 'number', 'status', 'epic', 'title'],
    )
  })

  it('merkt die Spaltenreihenfolge über einen Neu-Mount hinweg', async () => {
    const view = renderPage()
    await screen.findByText('Aufgabe')
    fireEvent.dragStart(screen.getByLabelText('Spalte Beschreibung'))
    fireEvent.dragOver(screen.getByLabelText('Spalte Nr'))
    fireEvent.drop(screen.getByLabelText('Spalte Nr'))
    view.unmount()

    renderPage()
    await screen.findByText('Aufgabe')
    const order = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(order[0]).toBe('Spalte Beschreibung')
  })

  it('gilt mit Reihenfolge und Breite auch auf einem anderen Board', async () => {
    localStorage.setItem('manban.listColumns', JSON.stringify(['excerpt', 'title', 'number', 'status', 'epic']))
    localStorage.setItem('manban.listExcerptWidth', '45')
    mBoards.get.mockResolvedValue({
      id: 2, projectId: 9, name: 'Zweites', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([active])
    mEpics.list.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/boards/2/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText('Aufgabe')

    const order = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(order[0]).toBe('Spalte Beschreibung')
  })

  it('entfernt board-gebundene Alt-Schlüssel für Reihenfolge und Breite beim Laden', async () => {
    localStorage.setItem('manban.listColumns.1', JSON.stringify(['excerpt']))
    localStorage.setItem('manban.listExcerptWidth.7', '42')
    localStorage.setItem('manban.listFilters.1', JSON.stringify([10]))
    renderPage()
    await screen.findByText('Aufgabe')

    expect(localStorage.getItem('manban.listColumns.1')).toBeNull()
    expect(localStorage.getItem('manban.listExcerptWidth.7')).toBeNull()
    // Der Spalten-Filter bleibt bewusst board-gebunden und wird nicht angetastet.
    expect(localStorage.getItem('manban.listFilters.1')).not.toBeNull()
  })

  it('bricht nicht ab, wenn beim Aufräumen der Alt-Schlüssel localStorage wirft', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => null,
      setItem: () => undefined,
      removeItem: () => { throw new Error('nope') },
      clear: () => undefined,
      key: () => 'manban.listColumns.1',
      get length() { return 1 },
    })
    renderPage()
    expect(await screen.findByText('Aufgabe')).toBeInTheDocument()
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
      expect(localStorage.getItem('manban.listExcerptWidth')).toBe('40')
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
    mLabels.list.mockResolvedValue([{ id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false }])

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
    localStorage.setItem('manban.listColumns', JSON.stringify(['excerpt', 'foo', 'title']))
    renderPage()
    await screen.findByText('Aufgabe')

    const order = screen.getAllByLabelText(/^Spalte /).map((el) => el.getAttribute('aria-label'))
    expect(order).toEqual([
      'Spalte Beschreibung',
      'Spalte Titel',
      'Spalte Nr',
      'Spalte Status',
      'Spalte Vorhaben',
    ])
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

  it('zeigt in der Mehrspalt-Ansicht keine Ziehgriffe, aber einen Umordnen-Hinweis', async () => {
    // Default-Board: zwei Spalten (Backlog + Done), beide gefiltert-sichtbar -> nicht umsortierbar.
    renderPage()
    await screen.findByText('Aufgabe')

    expect(screen.queryByLabelText('Reihenfolge ändern')).not.toBeInTheDocument()
    expect(screen.getByText(UMORDNEN_HINWEIS)).toBeInTheDocument()
    expect(screen.getByLabelText('Detail öffnen: Aufgabe')).not.toHaveAttribute('draggable', 'true')
  })

  it('sortiert nicht per Drag, solange mehrere Spalten sichtbar sind', async () => {
    const first: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Erste', description: '', archived: false }
    const second: Card = { ...base, id: 103, columnId: 10, number: 4, title: 'Zweite', description: '', archived: false, positionInColumn: 1 }
    mCards.move.mockResolvedValue({})
    renderPage([first, second]) // Default-Board hat zwei Spalten -> Mehrspalt-Ansicht.
    await screen.findByText('Erste')

    const dataTransfer = { setData: vi.fn() }
    fireEvent.dragStart(screen.getByText('Erste'), { dataTransfer })
    fireEvent.dragOver(screen.getByText('Zweite'), { dataTransfer })
    fireEvent.drop(screen.getByText('Zweite'), { dataTransfer })

    expect(mCards.move).not.toHaveBeenCalled()
  })

  it('aktiviert das Umsortieren, sobald nur noch eine Spalte gefiltert ist', async () => {
    const first: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Erste', description: '', archived: false }
    const second: Card = { ...base, id: 103, columnId: 10, number: 4, title: 'Zweite', description: '', archived: false, positionInColumn: 1 }
    mCards.move.mockResolvedValue({})
    renderPage([first, second])
    await screen.findByText('Erste')
    // Mehrspalt: kein Griff, Hinweis da.
    expect(screen.queryByLabelText('Reihenfolge ändern')).not.toBeInTheDocument()
    expect(screen.getByText(UMORDNEN_HINWEIS)).toBeInTheDocument()

    // „Done" abwählen -> nur noch Backlog aktiv -> umsortierbar: Ziehgriffe erscheinen, Hinweis weg.
    fireEvent.click(screen.getByLabelText('Filter Done'))

    await waitFor(() => expect(screen.getAllByLabelText('Reihenfolge ändern').length).toBeGreaterThan(0))
    expect(screen.queryByText(UMORDNEN_HINWEIS)).not.toBeInTheDocument()
  })

  it('sortiert nicht, wenn eine Karte auf sich selbst fällt (Einzelspalt)', async () => {
    const only: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Erste', description: '', archived: false }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([only])
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
    fireEvent.drop(screen.getByText('Erste'), { dataTransfer })

    expect(mCards.move).not.toHaveBeenCalled()
  })

  it('sortiert nicht bei einem Drop ohne laufenden Drag (Einzelspalt)', async () => {
    const only: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Erste', description: '', archived: false }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([only])
    mCards.move.mockResolvedValue({})
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText('Erste')

    fireEvent.drop(screen.getByText('Erste'), { dataTransfer: { setData: vi.fn() } })

    expect(mCards.move).not.toHaveBeenCalled()
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

  it('zeigt das Kürzel auch an einer Karte, die allein über ihre Herkunft zum Vorhaben gehört', async () => {
    // Keine `parentId` — die Zugehörigkeit steht nur in `memberNumbers` des Vorhabens, wo der
    // Server den ausdrücklich zugeordneten und den geerbten Weg zusammenführt.
    renderEpicPage()

    await screen.findByText('Aufgabe')
    expect(screen.getByText('EP1')).toBeInTheDocument()
  })

  it('öffnet über das Kürzel den Detail-Dialog des Vorhabens, nicht zusätzlich die Karte', async () => {
    renderEpicPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Vorhaben EP1 öffnen' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Mein Epic')).toBeInTheDocument()
    expect(within(dialog).queryByText('Aufgabe')).not.toBeInTheDocument()
  })

  it('öffnet über Enter auf dem Kürzel den Dialog des Vorhabens, nicht zusätzlich die Karte', async () => {
    const user = userEvent.setup()
    renderEpicPage()

    // Die Listenzeile öffnet auf Enter selbst die Karte; der Badge muss den Tastendruck deshalb
    // vor ihr abfangen, sonst gingen Vorhaben und Karte zugleich auf.
    ;(await screen.findByRole('button', { name: 'Vorhaben EP1 öffnen' })).focus()
    await user.keyboard('{Enter}')

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Mein Epic')).toBeInTheDocument()
    expect(within(dialog).queryByText('Aufgabe')).not.toBeInTheDocument()
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
    localStorage.setItem('manban.listExcerptWidth', '45')
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
      expect(localStorage.getItem('manban.listExcerptWidth')).toBe('30')
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
    mLabels.list.mockResolvedValue([{ id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false }])
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

  it('rendert den aktiven Label-Filter auch bei einer Farbe, die keine CSS-Farbe ist', async () => {
    // Der aktive Filter-Chip rechnet seine Textfarbe aus der Labelfarbe. Die ist serverseitig nur
    // laengenbegrenzt, und `getContrastText` wirft auf allem, was keine CSS-Farbe ist — ohne
    // ErrorBoundary nimmt ein Klick dann die ganze Seite mit.
    const labelled: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'MitLabel', description: '', archived: false, labels: [5] }
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([labelled])
    mEpics.list.mockResolvedValue([])
    mLabels.list.mockResolvedValue([{ id: 5, boardId: 1, name: 'Bug', color: 'primary.main' }])
    render(
      <MemoryRouter initialEntries={['/boards/1/list']}>
        <Routes>
          <Route path="/boards/:boardId/list" element={<BoardListPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const chip = await screen.findByLabelText('Label-Filter Bug')
    expect(() => fireEvent.click(chip)).not.toThrow()
    expect(screen.getByText('MitLabel')).toBeInTheDocument()
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

  describe('Sortierung nach Spalteninhalt', () => {
    it('sortiert per Klick auf den Spaltenkopf auf, dann ab, dann zurück in die Board-Reihenfolge', async () => {
      renderSortPage()
      await screen.findByText('Beta')
      expect(zeilenTitel()).toEqual(['Beta', 'Zebra', 'Anton'])

      fireEvent.click(screen.getByLabelText('Spalte Titel'))
      expect(zeilenTitel()).toEqual(['Anton', 'Beta', 'Zebra'])

      fireEvent.click(screen.getByLabelText('Spalte Titel, aufsteigend sortiert'))
      expect(zeilenTitel()).toEqual(['Zebra', 'Beta', 'Anton'])

      fireEvent.click(screen.getByLabelText('Spalte Titel, absteigend sortiert'))
      expect(zeilenTitel()).toEqual(['Beta', 'Zebra', 'Anton'])
      expect(screen.getByLabelText('Spalte Titel')).toBeInTheDocument()
    })

    it('nennt im Hinweis die tatsächlich sortierte Spalte, nicht einen festen Text', async () => {
      renderSortPage()
      await screen.findByText('Beta')

      fireEvent.click(screen.getByLabelText('Spalte Nr'))

      expect(zeilenTitel()).toEqual(['Zebra', 'Anton', 'Beta'])
      expect(screen.getByText(/^Sortiert nach Nr\./)).toBeInTheDocument()
      expect(screen.queryByText(/Sortiert nach Titel/)).not.toBeInTheDocument()
    })

    it('nimmt bei aktiver Sortierung die Ziehgriffe weg und verschiebt keine Karte per Drop', async () => {
      renderSortPage()
      await screen.findByText('Beta')
      // Einspaltiges Board: vor der Sortierung ist das Umordnen per Drag möglich.
      expect(screen.getAllByLabelText('Reihenfolge ändern')).toHaveLength(3)

      fireEvent.click(screen.getByLabelText('Spalte Titel'))

      expect(screen.queryByLabelText('Reihenfolge ändern')).not.toBeInTheDocument()
      expect(screen.getByLabelText('Detail öffnen: Anton')).not.toHaveAttribute('draggable', 'true')
      const dataTransfer = { setData: vi.fn() }
      fireEvent.dragStart(screen.getByLabelText('Detail öffnen: Anton'), { dataTransfer })
      fireEvent.dragOver(screen.getByLabelText('Detail öffnen: Zebra'), { dataTransfer })
      fireEvent.drop(screen.getByLabelText('Detail öffnen: Zebra'), { dataTransfer })

      expect(mCards.move).not.toHaveBeenCalled()
    })

    it('weist bei aktiver Sortierung auf das Aufheben hin, um wieder umordnen zu können', async () => {
      renderSortPage()
      await screen.findByText('Beta')

      fireEvent.click(screen.getByLabelText('Spalte Titel'))

      expect(
        screen.getByText('Sortiert nach Titel. Zum Ändern der Kartenreihenfolge die Sortierung aufheben.'),
      ).toBeInTheDocument()
      expect(screen.queryByText(UMORDNEN_HINWEIS)).not.toBeInTheDocument()
    })

    it('sortiert per Enter und per Leertaste auf dem Spaltenkopf, andere Tasten nicht', async () => {
      renderSortPage()
      await screen.findByText('Beta')

      fireEvent.keyDown(screen.getByLabelText('Spalte Titel'), { key: 'Enter' })
      expect(zeilenTitel()).toEqual(['Anton', 'Beta', 'Zebra'])

      fireEvent.keyDown(screen.getByLabelText('Spalte Titel, aufsteigend sortiert'), { key: ' ' })
      expect(zeilenTitel()).toEqual(['Zebra', 'Beta', 'Anton'])

      // Eine beliebige andere Taste lässt den Zustand, wo er ist.
      fireEvent.keyDown(screen.getByLabelText('Spalte Titel, absteigend sortiert'), { key: 'a' })
      expect(zeilenTitel()).toEqual(['Zebra', 'Beta', 'Anton'])
    })

    it('löst mit einem Drag der Kopfzelle keine Sortierung aus', async () => {
      renderSortPage()
      await screen.findByText('Beta')

      // Spalten-Umordnen per Drag: der abschließende Click darf nicht als Sortierklick zählen.
      fireEvent.dragStart(screen.getByLabelText('Spalte Titel'))
      fireEvent.dragOver(screen.getByLabelText('Spalte Nr'))
      fireEvent.drop(screen.getByLabelText('Spalte Nr'))
      fireEvent.dragEnd(screen.getByLabelText('Spalte Nr'))
      fireEvent.click(screen.getByLabelText('Spalte Nr'))

      expect(zeilenTitel()).toEqual(['Beta', 'Zebra', 'Anton'])
      expect(screen.queryByText(/^Sortiert nach/)).not.toBeInTheDocument()
    })

    it('löst mit einem Klick auf den Resize-Griff keine Sortierung aus', async () => {
      renderSortPage()
      await screen.findByText('Beta')

      fireEvent.click(screen.getByLabelText('Beschreibung-Spalte breiter ziehen'))

      expect(screen.getByLabelText('Spalte Beschreibung')).toBeInTheDocument()
      expect(screen.queryByText(/^Sortiert nach/)).not.toBeInTheDocument()
    })

    it('verweist bei genau einem Status-Filter plus Archiv trotzdem auf das Umordnen', async () => {
      // `sortable` ist auch dann false, wenn genau eine Spalte gefiltert ist und das Archiv dazukommt.
      renderPage()
      await screen.findByText('Aufgabe')

      fireEvent.click(screen.getByLabelText('Filter Done'))
      fireEvent.click(screen.getByLabelText('Filter Archiv'))

      expect(await screen.findByText('AlteKarte')).toBeInTheDocument()
      expect(screen.getByText(UMORDNEN_HINWEIS)).toBeInTheDocument()
      expect(screen.queryByLabelText('Reihenfolge ändern')).not.toBeInTheDocument()
    })

    it('setzt die Sortierung beim Board-Wechsel ohne Remount zurück', async () => {
      renderSortPage()
      await screen.findByText('Beta')
      fireEvent.click(screen.getByLabelText('Spalte Titel'))
      expect(zeilenTitel()).toEqual(['Anton', 'Beta', 'Zebra'])

      fireEvent.click(screen.getByText('Board wechseln'))

      await waitFor(() => expect(zeilenTitel()).toEqual(['Beta', 'Zebra', 'Anton']))
      expect(screen.getByLabelText('Spalte Titel')).toBeInTheDocument()
      expect(screen.queryByText(/^Sortiert nach/)).not.toBeInTheDocument()
    })

    it('lässt einen Nur-Leser sortieren, ohne ihm ein Umordnen anzubieten', async () => {
      mProjects.list.mockResolvedValue([{ id: 42, name: 'Fremd', role: 'VIEWER', createdAt: '' }])
      renderSortPage([beta, zebra, anton], 42)
      await screen.findByText('Beta')
      await waitFor(() => expect(mProjects.list).toHaveBeenCalled())
      expect(screen.queryByLabelText('Reihenfolge ändern')).not.toBeInTheDocument()
      expect(screen.queryByText(UMORDNEN_HINWEIS)).not.toBeInTheDocument()

      fireEvent.click(screen.getByLabelText('Spalte Titel'))

      expect(zeilenTitel()).toEqual(['Anton', 'Beta', 'Zebra'])
      expect(screen.getByText('Sortiert nach Titel.')).toBeInTheDocument()
    })
  })
})

describe('BoardListPage Dichte, Ziffern und Ziehen (AK 7, AK 8, AK 10, AK 12, #957)', () => {
  const erste: Card = { ...base, id: 100, columnId: 10, number: 1, title: 'Erste', description: '', archived: false }
  const zweite: Card = {
    ...base, id: 103, columnId: 10, number: 4, positionInColumn: 1, archived: false, description: '',
    title: 'Ein langer Titel, der in einer schmalen Titelspalte über mehr als eine Zeile laufen darf',
    excerpt: 'Auszug',
  }

  /** Einspaltiges Board: Dort ist das Umordnen per Ziehen zugelassen. Im echten Theme gerendert. */
  const renderEinspaltig = async () => {
    localStorage.setItem('manban.listGruppierung', 'keine')
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mCards.list.mockResolvedValue([erste, zweite])
    mEpics.list.mockResolvedValue([])
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
    render(
      <ThemeProvider theme={theme}>
        <SnackbarProvider>
          <MemoryRouter initialEntries={['/boards/1/list']}>
            <Routes>
              <Route path="/boards/:boardId/list" element={<BoardListPage />} />
            </Routes>
          </MemoryRouter>
        </SnackbarProvider>
      </ThemeProvider>,
    )
    await screen.findByText('Erste')
  }

  it('setzt die Zeilen in der dichteren Stufe: knappe Polsterung, knapper Zeilenabstand', async () => {
    // Ausgangswert vor #957 auf 1440 x 900: 13 vollständig sichtbare Zeilen bei 8 px Polsterung
    // oben und unten und 6 px Abstand. Die neue Stufe hält 2 px Polsterung und 2 px Abstand.
    await renderEinspaltig()

    // Unter dem Variablen-Theme schreibt MUI Abstände als Vielfaches von `--mb-spacing` (8 px).
    expect(theme.spacing(0.25)).toBe('calc(0.25 * var(--mb-spacing, 8px))')
    const zeile = screen.getByLabelText('Detail öffnen: Erste')
    expect(cssRegel(zeile)).toContain('padding-top: calc(0.25 * var(--mb-spacing))')
    expect(cssRegel(zeile)).toContain('padding-bottom: calc(0.25 * var(--mb-spacing))')
    expect(cssRegel(screen.getByTestId('listen-zeilen'))).toContain('gap: calc(0.25 * var(--mb-spacing))')
  })

  it('schneidet den Titel nicht ab, lässt den Auszug aber einzeilig', async () => {
    await renderEinspaltig()

    const titel = screen.getByText(zweite.title)
    expect(cssRegel(titel)).not.toContain('white-space: nowrap')
    expect(cssRegel(titel)).not.toContain('text-overflow')
    // Der Auszug bleibt die „einzeilige Vorschau" aus `lib/listExcerpt.ts` (Plan #932 E16).
    expect(cssRegel(screen.getByText('Auszug'))).toContain('white-space: nowrap')
  })

  it('malt die Zeilenfläche aus einem Token statt mit festem Weiß', async () => {
    await renderEinspaltig()

    const zeile = screen.getByLabelText('Detail öffnen: Erste')
    expect(cssRegel(zeile)).toContain('background-color: var(--mb-palette-background-paper)')
    expect(cssRegel(zeile)).not.toContain('common-white')
  })

  it('setzt die Nummernspalte rechtsbündig in Tabellenziffern, in der Kopfzeile wie in den Zeilen', async () => {
    await renderEinspaltig()

    const nummer = within(screen.getByLabelText('Detail öffnen: Erste')).getByText('#1')
    const zelle = cssRegel(nummer)
    expect(zelle).toContain('font-variant-numeric: tabular-nums')
    expect(zelle).toContain('text-align: right')
    expect(cssRegel(screen.getByLabelText('Spalte Nr'))).toContain('justify-content: flex-end')
  })

  it('kennzeichnet die bewegte Zeile und zeigt die Stelle, an der sie landen würde', async () => {
    await renderEinspaltig()
    const dataTransfer = { setData: vi.fn() }

    fireEvent.dragStart(screen.getByLabelText('Detail öffnen: Erste'), { dataTransfer })
    await waitFor(() => expect(screen.getByLabelText('Detail öffnen: Erste')).toHaveAttribute('data-zieh-zustand', 'bewegt'))
    fireEvent.dragOver(screen.getByLabelText(`Detail öffnen: ${zweite.title}`), { dataTransfer })

    const quelle = screen.getByLabelText('Detail öffnen: Erste')
    const ziel = screen.getByLabelText(`Detail öffnen: ${zweite.title}`)
    // Dieselben Bausteine wie auf dem Board (`boardSurfaceSx.ts`): Platzhalter und Ablagefläche.
    expect(cssRegelMit(quelle, '>*')).toContain('visibility: hidden')
    expect(cssRegel(quelle)).toContain('border: 1px dashed')
    expect(ziel).toHaveAttribute('data-ablage', 'aktiv')
    expect(cssRegel(ziel)).toContain('outline: 2px dashed')

    fireEvent.dragEnd(quelle)

    expect(screen.getByLabelText('Detail öffnen: Erste')).not.toHaveAttribute('data-zieh-zustand')
    expect(screen.getByLabelText(`Detail öffnen: ${zweite.title}`)).not.toHaveAttribute('data-ablage')
  })

  it('setzt keinen Ziehzustand, wenn das Ziehen endet, bevor er greift', async () => {
    await renderEinspaltig()

    fireEvent.dragStart(screen.getByLabelText('Detail öffnen: Erste'), { dataTransfer: { setData: vi.fn() } })
    fireEvent.dragEnd(screen.getByLabelText('Detail öffnen: Erste'))
    await new Promise((fertig) => setTimeout(fertig, 5))

    expect(screen.getByLabelText('Detail öffnen: Erste')).not.toHaveAttribute('data-zieh-zustand')
  })
})

describe('BoardListPage Gruppierung und Filter (#980)', () => {
  const mMembers = membersApi as unknown as { list: ReturnType<typeof vi.fn> }
  const zweitesEpic: Epic = { id: 8, number: 2, title: 'Zweites Vorhaben', description: null, shortcode: 'ZV', done: 1, total: 4, memberNumbers: [5], rootNumbers: [], requirementCardNumber: null }
  const imEpic: Card = { ...active, dueDate: new Date(Date.now() - 86_400_000).toISOString(), assignees: [3] }
  const imZweiten: Card = { ...base, id: 104, columnId: 10, number: 5, positionInColumn: 1, title: 'Im zweiten', description: '', archived: false }
  const ohne: Card = { ...base, id: 105, columnId: 10, number: 6, positionInColumn: 2, title: 'Ohne Zuordnung', description: '', archived: false, assignees: [4] }
  const fertig: Card = { ...base, id: 106, columnId: 20, number: 7, title: 'Fertig und alt', description: '', archived: false, dueDate: new Date(Date.now() - 86_400_000).toISOString() }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('localStorage', fakeStorage())
    mProjects.list.mockResolvedValue([{ id: 9, name: 'Projekt', role: 'OWNER', createdAt: '' }])
    mMembers.list.mockResolvedValue([
      { userId: 3, displayName: 'Anna', email: 'a@x', role: 'MEMBER' },
      { userId: 4, displayName: 'Ben', email: 'b@x', role: 'MEMBER' },
    ])
  })

  const renderGruppiert = async (cards: Card[] = [imEpic, imZweiten, ohne, fertig]) => {
    renderPage(cards)
    mEpics.list.mockResolvedValue([epic, zweitesEpic])
    await screen.findByText('Ohne Zuordnung')
  }

  it('gruppiert die Liste standardmäßig nach Vorhaben, Karten ohne Vorhaben am Ende', async () => {
    renderPage([imEpic, imZweiten, ohne])
    mEpics.list.mockResolvedValue([epic, zweitesEpic])
    // renderPage setzt die Vorhaben leer; die Gruppen entstehen, sobald sie geladen sind.
    await screen.findByText('Ohne Zuordnung')

    const koepfe = await screen.findAllByRole('heading', { level: 3 })
    expect(koepfe.map((k) => k.getAttribute('aria-label'))).toContain('Ohne Vorhaben')
    expect(screen.getByRole('button', { name: 'Vorhaben' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Kartenreihenfolge ändern: dazu die Gruppierung aufheben.')).toBeInTheDocument()
  })

  it('zeigt je Vorhaben Name und Fortschritt und hebt die Gruppierung auf Wunsch auf', async () => {
    mEpics.list.mockResolvedValue([epic, zweitesEpic])
    mBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [
        { id: 10, name: 'Backlog', position: 0, wipLimit: null },
        { id: 20, name: 'Done', position: 1, wipLimit: null },
      ],
    })
    mCards.list.mockResolvedValue([imEpic, imZweiten, ohne])
    render(
      <SnackbarProvider>
        <MemoryRouter initialEntries={['/boards/1/list']}>
          <Routes>
            <Route path="/boards/:boardId/list" element={<BoardListPage />} />
          </Routes>
        </MemoryRouter>
      </SnackbarProvider>,
    )

    const zweite = await screen.findByTestId('gruppe-8')
    expect(zweite).toHaveTextContent('Zweites Vorhaben1 von 4 fertig')
    expect(cssRegel(screen.getByTestId('gruppe-fortschritt-8'))).toContain('width: 25%')
    expect(screen.getByTestId('gruppe-7')).toHaveTextContent('Mein Epic0 von 1 fertig')
    expect(screen.getByTestId('gruppe-ohne')).toHaveTextContent('Ohne Vorhaben1 Karte')

    fireEvent.click(screen.getByRole('button', { name: 'keine' }))

    expect(screen.queryByTestId('gruppe-8')).not.toBeInTheDocument()
    expect(localStorage.getItem('manban.listGruppierung')).toBe('keine')
    fireEvent.click(screen.getByRole('button', { name: 'Vorhaben' }))
    expect(await screen.findByTestId('gruppe-8')).toBeInTheDocument()
  })

  it('nennt mehrere Karten ohne Vorhaben im Plural und lässt eine Gruppe ohne Karten weg', async () => {
    mEpics.list.mockResolvedValue([epic, zweitesEpic])
    mBoards.get.mockResolvedValue({ id: 1, projectId: 9, name: 'B', createdAt: '', columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }] })
    mCards.list.mockResolvedValue([ohne, { ...ohne, id: 107, number: 8, title: 'Noch eine' }])
    render(
      <SnackbarProvider>
        <MemoryRouter initialEntries={['/boards/1/list']}>
          <Routes>
            <Route path="/boards/:boardId/list" element={<BoardListPage />} />
          </Routes>
        </MemoryRouter>
      </SnackbarProvider>,
    )

    expect(await screen.findByTestId('gruppe-ohne')).toHaveTextContent('2 Karten')
    expect(screen.queryByTestId('gruppe-7')).not.toBeInTheDocument()
  })

  it('filtert auf überfällige Karten und zählt fertige nicht mit', async () => {
    localStorage.setItem('manban.listGruppierung', 'keine')
    await renderGruppiert()
    fireEvent.click(screen.getByLabelText('Filter Done'))

    const filter = screen.getByLabelText('Filter Überfällig')
    expect(filter).toHaveTextContent('Überfällig1')
    fireEvent.click(filter)

    expect(filter).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Aufgabe')).toBeInTheDocument()
    expect(screen.queryByText('Ohne Zuordnung')).not.toBeInTheDocument()
    expect(screen.queryByText('Fertig und alt')).not.toBeInTheDocument()
    expect(within(screen.getByLabelText('Detail öffnen: Aufgabe')).getByLabelText('Fällig Aufgabe')).toHaveAttribute('data-ueberfaellig', 'ja')
  })

  it('filtert nach Zuständigen und zurück auf alle', async () => {
    localStorage.setItem('manban.listGruppierung', 'keine')
    await renderGruppiert()

    const auswahl = await screen.findByRole('combobox', { name: 'Zuständig' })
    fireEvent.change(auswahl, { target: { value: '4' } })
    expect(screen.getByText('Ohne Zuordnung')).toBeInTheDocument()
    expect(screen.queryByText('Aufgabe')).not.toBeInTheDocument()

    fireEvent.change(auswahl, { target: { value: '' } })
    expect(screen.getByText('Aufgabe')).toBeInTheDocument()
  })

  // Wie in der Werkzeugleiste des Boards steht die Benennung im Wert, nicht über dem Feld
  // (Entwurf `.waehler`, Z. 833–842; Issue #986).
  it('benennt den Zuständig-Filter im Wert statt über einer Beschriftung', async () => {
    localStorage.setItem('manban.listGruppierung', 'keine')
    await renderGruppiert()

    const auswahl = await screen.findByRole('combobox', { name: 'Zuständig' })
    expect(screen.queryByText('Zuständig')).not.toBeInTheDocument()
    expect(within(auswahl).getByRole('option', { name: 'Zuständig: alle' })).toBeInTheDocument()
    expect(within(auswahl).getByRole('option', { name: 'Zuständig: Anna' })).toBeInTheDocument()
  })

  it('lässt den Zuständigen-Filter weg, wenn die Mitglieder nicht geladen werden können', async () => {
    mMembers.list.mockRejectedValue(new Error('403'))
    localStorage.setItem('manban.listGruppierung', 'keine')
    await renderGruppiert()

    await waitFor(() => expect(mMembers.list).toHaveBeenCalledWith(9))
    expect(screen.queryByRole('combobox', { name: 'Zuständig' })).not.toBeInTheDocument()
  })

  it('liest die Gruppierung auch dann, wenn localStorage nicht verfügbar ist', async () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('gesperrt')
      },
      setItem: () => {
        throw new Error('gesperrt')
      },
      removeItem: () => undefined,
      key: () => null,
      length: 0,
      clear: () => undefined,
    })
    await renderGruppiert()

    expect(screen.getByRole('button', { name: 'Vorhaben' })).toHaveAttribute('aria-pressed', 'true')
    fireEvent.click(screen.getByRole('button', { name: 'keine' }))
    expect(screen.getByRole('button', { name: 'keine' })).toHaveAttribute('aria-pressed', 'true')
  })
})
