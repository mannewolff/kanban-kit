import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { attachmentsApi } from '../api/attachments'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { commentsApi } from '../api/comments'
import { ideasApi, type Idea } from '../api/ideas'
import { membersApi } from '../api/members'
import { IdeaPlanningBoard } from './IdeaPlanningBoard'

// Toast-Weg: useSnackbar liefert im Test einen Spy (statt des No-op-Defaults ohne Provider).
const { mNotify } = vi.hoisted(() => ({ mNotify: vi.fn() }))
vi.mock('./SnackbarProvider', () => ({ useSnackbar: () => mNotify }))

vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn() } }))
vi.mock('../api/cards', () => ({
  cardsApi: {
    list: vi.fn(),
    move: vi.fn(),
    transfer: vi.fn(),
    update: vi.fn(),
    setAssignees: vi.fn(),
    setLabels: vi.fn(),
    getActivity: vi.fn(),
    restore: vi.fn(),
    moveToIdeaStorage: vi.fn(),
  },
}))
vi.mock('../api/ideas', () => ({
  ideasApi: { list: vi.fn(), planOntoBoard: vi.fn(), moveBackToPool: vi.fn() },
}))
vi.mock('../api/members', () => ({ membersApi: { list: vi.fn() } }))
// Das im Modal geöffnete CardDetailModal lädt Kommentare/Anhänge/Aktivität und liest den User —
// hier nur so weit gemockt, dass das Öffnen einer Pool-Idee ohne echtes Backend rendert.
vi.mock('../api/comments', () => ({
  commentsApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('../api/attachments', () => ({
  attachmentsApi: { list: vi.fn(), upload: vi.fn(), remove: vi.fn(), fetchBlob: vi.fn() },
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 7, email: 'a@b.c', displayName: 'A', platformRole: 'USER', memberships: [] } }),
}))

const mBoards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  move: ReturnType<typeof vi.fn>
  transfer: ReturnType<typeof vi.fn>
  update: ReturnType<typeof vi.fn>
  getActivity: ReturnType<typeof vi.fn>
}
const mIdeas = ideasApi as unknown as {
  list: ReturnType<typeof vi.fn>
  planOntoBoard: ReturnType<typeof vi.fn>
  moveBackToPool: ReturnType<typeof vi.fn>
}
const mMembers = membersApi as unknown as { list: ReturnType<typeof vi.fn> }
const mComments = commentsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mAttachments = attachmentsApi as unknown as { list: ReturnType<typeof vi.fn> }

const col = (id: number, name: string, position: number) => ({ id, name, position, wipLimit: null })

const BOARDS = [
  { id: 10, name: 'Board X', projectId: 5, createdAt: '', columns: [col(101, 'Ready', 1), col(100, 'Backlog', 0)] },
  { id: 11, name: 'Board Y', projectId: 5, createdAt: '', columns: [col(110, 'Backlog', 0)] },
]

const cardBase = {
  boardId: 10,
  description: null as string | null,
  archived: false,
  ideaStored: false,
  movedToDoneAt: null as string | null,
  dependencies: [] as number[],
  type: 'CARD' as const,
  parentId: null as number | null,
  shortcode: null as string | null,
  assignees: [] as number[],
  dueDate: null as string | null,
  labels: [] as number[],
}
function card(partial: Partial<Card> & { id: number; columnId: number; number: number; title: string; positionInColumn: number }): Card {
  return { ...cardBase, ...partial }
}

function idea(partial: Partial<Idea> & { id: number; title: string }): Idea {
  return {
    boardId: null,
    columnId: null,
    number: null,
    description: null,
    ideaStored: true,
    targetBoardId: null,
    type: 'CARD',
    positionInColumn: 0,
    archived: false,
    movedToDoneAt: null,
    dependencies: [],
    parentId: null,
    shortcode: null,
    assignees: [],
    dueDate: null,
    labels: [],
    ...partial,
  }
}

// Board 10, erste Spalte 100: nur die aktiven Nicht-Idee-Karten der ersten Spalte gehören ins
// Backlog. backlogCard2 kommt im Input VOR backlogCard, muss aber nach positionInColumn dahinter
// einsortiert werden (deckt den Sort-Comparator ab). Die erste Spalte ist 100 (position 0),
// obwohl sie im Board-Array hinter 101 steht — deckt den Spalten-Sort nach position ab.
const backlogCard = card({ id: 1, columnId: 100, number: 7, title: 'Backlog A', positionInColumn: 0 })
const backlogCard2 = card({ id: 5, columnId: 100, number: 6, title: 'Backlog Z', positionInColumn: 1 })
const readyCard = card({ id: 2, columnId: 101, number: 8, title: 'Ready B', positionInColumn: 0 })
const archivedCard = card({ id: 3, columnId: 100, number: 9, title: 'Archiv C', positionInColumn: 2, archived: true })
const ideaCard = card({ id: 4, columnId: 100, number: 10, title: 'Idee D', positionInColumn: 3, ideaStored: true })
// Board 11, erste (einzige) Spalte 110: eigene Karte, damit sichtbar wird, dass je Board geladen wird.
const boardYCard = card({ id: 8, columnId: 110, number: 20, title: 'Board Y Karte', positionInColumn: 0, boardId: 11 })

const poolIdea = idea({ id: 20, title: 'Pool 1' })
const legacyIdea = idea({ id: 21, title: 'Legacy', boardId: 10 })

const CARDS_BY_BOARD: Record<number, Card[]> = {
  10: [backlogCard2, backlogCard, readyCard, archivedCard, ideaCard],
  11: [boardYCard],
}

function setup({
  boards = BOARDS,
  cardsByBoard = CARDS_BY_BOARD,
  ideas = [poolIdea, legacyIdea],
}: { boards?: Board[]; cardsByBoard?: Record<number, Card[]>; ideas?: Idea[] } = {}) {
  mBoards.list.mockResolvedValue(boards)
  mCards.list.mockImplementation((boardId: number) => Promise.resolve(cardsByBoard[boardId] ?? []))
  mCards.move.mockResolvedValue({})
  mCards.transfer.mockResolvedValue(card({ id: 1, columnId: 110, number: 7, title: 'Backlog A', positionInColumn: 0 }))
  mCards.update.mockResolvedValue(idea({ id: 20, title: 'x' }))
  mCards.getActivity.mockResolvedValue([])
  mIdeas.list.mockResolvedValue(ideas)
  mIdeas.planOntoBoard.mockResolvedValue(idea({ id: 20, title: 'x' }))
  mIdeas.moveBackToPool.mockResolvedValue(idea({ id: 1, title: 'x' }))
  mMembers.list.mockResolvedValue([])
  mComments.list.mockResolvedValue([])
  mAttachments.list.mockResolvedValue([])
}

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname}</div>
}

function renderBoard(canEdit = true) {
  return render(
    <MemoryRouter initialEntries={['/start']}>
      <LocationProbe />
      <IdeaPlanningBoard projectId={5} canEdit={canEdit} />
    </MemoryRouter>,
  )
}

const dt = () => ({ dataTransfer: { setData: vi.fn() } })

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(() => vi.unstubAllGlobals())

describe('IdeaPlanningBoard', () => {
  it('zeigt einen Hinweis, wenn das Projekt kein Board hat', async () => {
    setup({ boards: [] })
    renderBoard()
    expect(await screen.findByText(/kein Board/i)).toBeInTheDocument()
    expect(mCards.list).not.toHaveBeenCalled()
  })

  // #467: Der „Einplanen"-Knopf greift auf das erste Board zu. Ohne den Leer-Guard vor dem Pool
  // wäre dieser Zugriff bei leerer Board-Liste ein TypeError. Die beiden Fälle pinnen den Guard:
  // dauerhaft board-loses Projekt und das Zeitfenster, in dem der Pool schon geladen ist.
  it('rendert bei leerer Board-Liste keinen Pool und keinen Einplanen-Knopf', async () => {
    setup({ boards: [], ideas: [poolIdea] })
    renderBoard()
    await screen.findByText(/kein Board/i)

    expect(screen.queryByTestId('pool-item-20')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /einplanen/i })).not.toBeInTheDocument()
  })

  it('zeigt keinen Einplanen-Knopf, solange die Boards noch laden und der Pool schon da ist', async () => {
    const d = deferred<Board[]>()
    setup()
    mBoards.list.mockReturnValue(d.promise)
    renderBoard()

    // Der Pool ist aufgelöst, boards steht noch auf [] — in diesem Fenster darf es keinen
    // Bedienpfad geben, der auf das erste Board zugreift.
    await waitFor(() => expect(mIdeas.list).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /einplanen/i })).not.toBeInTheDocument()

    // Nach dem Laden der Boards erscheint der Knopf und plant auf das erste Board ein.
    d.resolve(BOARDS)
    fireEvent.click(await screen.findByRole('button', { name: 'Idee Pool 1 einplanen' }))
    await waitFor(() => expect(mIdeas.planOntoBoard).toHaveBeenCalledWith(20, 10))
  })

  it('rendert alle Boards untereinander mit ihrer ersten Spalte und den Pool darunter', async () => {
    setup()
    renderBoard()

    // Beide Boards sind sichtbar, in der Reihenfolge aus boardsApi.list.
    expect(await screen.findByText('Backlog A')).toBeInTheDocument()
    expect(screen.getByText('Board X')).toBeInTheDocument()
    expect(screen.getByText('Board Y')).toBeInTheDocument()

    // Board X: nur aktive Nicht-Idee-Karten der ersten Spalte (100), nach Position sortiert.
    const zoneX = screen.getByTestId('board-zone-10')
    const backlogItems = screen.getAllByText(/^Backlog [AZ]$/).map((el) => el.textContent)
    expect(backlogItems).toEqual(['Backlog A', 'Backlog Z'])
    expect(zoneX).toHaveTextContent('Backlog A')
    expect(screen.queryByText('Ready B')).not.toBeInTheDocument()
    expect(screen.queryByText('Archiv C')).not.toBeInTheDocument()
    expect(screen.queryByText('Idee D')).not.toBeInTheDocument()

    // Board Y bekommt seine eigenen Karten (je Board geladen).
    expect(screen.getByTestId('board-zone-11')).toHaveTextContent('Board Y Karte')
    expect(mCards.list).toHaveBeenCalledWith(10)
    expect(mCards.list).toHaveBeenCalledWith(11)

    // Pool: nur board-lose Ideen (Legacy board-gebunden bleibt draußen).
    expect(screen.getByText('Pool 1')).toBeInTheDocument()
    expect(screen.queryByText('Legacy')).not.toBeInTheDocument()
  })

  it('zeigt für ein Board ohne Spalten einen Leerzustand und lädt dafür keine Karten', async () => {
    const noCols = { id: 12, name: 'Board Leer', projectId: 5, createdAt: '', columns: [] }
    setup({ boards: [...BOARDS, noCols] })
    renderBoard()

    await screen.findByText('Board Leer')
    // Für das spaltenlose Board wird kein Karten-Load ausgelöst.
    expect(mCards.list).not.toHaveBeenCalledWith(12)
    expect(screen.getByTestId('board-zone-12')).toHaveTextContent(/Kein Backlog/i)
  })

  it('zeigt #N einer nummerierten Pool-Idee, aber kein nacktes # für Legacy-Ideen ohne Nummer', async () => {
    const numbered = idea({ id: 22, title: 'Pool Nummeriert', number: 42 })
    const legacyNoNumber = idea({ id: 23, title: 'Pool Legacy' }) // number: null
    setup({ ideas: [numbered, legacyNoNumber] })
    renderBoard()
    await screen.findByText('Pool Nummeriert')

    expect(screen.getByTestId('pool-item-22')).toHaveTextContent('#42')
    expect(screen.getByTestId('pool-item-23')).not.toHaveTextContent('#')
  })

  it('plant eine Pool-Idee per Button auf das erste Board ein', async () => {
    setup()
    renderBoard()
    await screen.findByText('Pool 1')

    fireEvent.click(screen.getByRole('button', { name: 'Idee Pool 1 einplanen' }))

    await waitFor(() => expect(mIdeas.planOntoBoard).toHaveBeenCalledWith(20, 10))
  })

  it('holt eine Board-Karte per Button in den Pool', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.click(screen.getByRole('button', { name: 'Karte Backlog A in den Pool' }))

    await waitFor(() => expect(mIdeas.moveBackToPool).toHaveBeenCalledWith(1))
  })

  it('plant per Drag von Pool auf ein beliebiges Board ein (nicht nur das erste)', async () => {
    setup()
    renderBoard()
    await screen.findByText('Pool 1')

    // Auf das ZWEITE Board (id 11) ziehen — nicht das erste.
    fireEvent.dragStart(screen.getByTestId('pool-item-20'), dt())
    fireEvent.dragOver(screen.getByTestId('board-zone-11'), dt())
    fireEvent.drop(screen.getByTestId('board-zone-11'), dt())

    await waitFor(() => expect(mIdeas.planOntoBoard).toHaveBeenCalledWith(20, 11))
  })

  it('holt per Drag von einem Board in den Pool', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.dragStart(screen.getByTestId('board-item-1'), dt())
    fireEvent.dragOver(screen.getByTestId('pool-zone'), dt())
    fireEvent.drop(screen.getByTestId('pool-zone'), dt())

    await waitFor(() => expect(mIdeas.moveBackToPool).toHaveBeenCalledWith(1))
  })

  it('verschiebt eine Board-Karte per Drag auf ein anderes Board in dessen erste Spalte und lädt beide Boards neu', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')
    mCards.list.mockClear()

    // „Backlog A" (id 1, Board 10) auf Board Y (id 11) ziehen — dessen erste Spalte ist 110.
    fireEvent.dragStart(screen.getByTestId('board-item-1'), dt())
    fireEvent.dragOver(screen.getByTestId('board-zone-11'), dt())
    fireEvent.drop(screen.getByTestId('board-zone-11'), dt())

    await waitFor(() => expect(mCards.transfer).toHaveBeenCalledWith(1, 11, 110))
    // Danach werden Quell- und Zielboard neu geladen.
    await waitFor(() => expect(mCards.list).toHaveBeenCalledWith(10))
    expect(mCards.list).toHaveBeenCalledWith(11)
    expect(mIdeas.planOntoBoard).not.toHaveBeenCalled()
    expect(mIdeas.moveBackToPool).not.toHaveBeenCalled()
  })

  it('verschiebt nicht, wenn eine Board-Karte auf ihr eigenes Board fällt (kein Netzaufruf)', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.dragStart(screen.getByTestId('board-item-1'), dt())
    fireEvent.drop(screen.getByTestId('board-zone-10'), dt())

    expect(mCards.transfer).not.toHaveBeenCalled()
  })

  it('verschiebt nicht auf ein Board ohne Spalte (kein gültiges Ziel)', async () => {
    const noCols = { id: 12, name: 'Board Leer', projectId: 5, createdAt: '', columns: [] }
    setup({ boards: [...BOARDS, noCols] })
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.dragStart(screen.getByTestId('board-item-1'), dt())
    fireEvent.drop(screen.getByTestId('board-zone-12'), dt())

    expect(mCards.transfer).not.toHaveBeenCalled()
  })

  it('zeigt bei fehlgeschlagenem Transfer eine Meldung und behält die Karte in der Ansicht', async () => {
    setup()
    mCards.transfer.mockRejectedValueOnce(new Error('boom'))
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.dragStart(screen.getByTestId('board-item-1'), dt())
    fireEvent.drop(screen.getByTestId('board-zone-11'), dt())

    await waitFor(() =>
      expect(mNotify).toHaveBeenCalledWith(expect.stringMatching(/fehlgeschlagen/i), 'error'),
    )
    // Ansicht bleibt konsistent: die Karte ist weiterhin sichtbar (kein optimistisches Entfernen).
    expect(screen.getByText('Backlog A')).toBeInTheDocument()
  })

  it('ignoriert einen Drop einer Pool-Idee zurück in den Pool', async () => {
    setup()
    renderBoard()
    await screen.findByText('Pool 1')

    fireEvent.dragStart(screen.getByTestId('pool-item-20'), dt())
    fireEvent.drop(screen.getByTestId('pool-zone'), dt())

    expect(mIdeas.planOntoBoard).not.toHaveBeenCalled()
    expect(mIdeas.moveBackToPool).not.toHaveBeenCalled()
  })

  it('ignoriert einen Board-Drop ohne vorheriges Dragstart', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.drop(screen.getByTestId('board-zone-10'), dt())

    expect(mIdeas.planOntoBoard).not.toHaveBeenCalled()
  })

  it('ignoriert einen Pool-Drop ohne vorheriges Dragstart', async () => {
    setup()
    renderBoard()
    await screen.findByText('Pool 1')

    fireEvent.drop(screen.getByTestId('pool-zone'), dt())

    expect(mIdeas.moveBackToPool).not.toHaveBeenCalled()
  })

  it('zeigt Leer-Hinweise für ein leeres Board-Backlog und einen leeren Pool', async () => {
    setup({ cardsByBoard: { 10: [], 11: [] }, ideas: [] })
    renderBoard()

    expect(await screen.findAllByText(/Kein Backlog/i)).toHaveLength(2)
    expect(screen.getByText('Keine Ideen im Pool.')).toBeInTheDocument()
  })

  it('springt per „Board öffnen" in die Listenansicht des jeweiligen Boards', async () => {
    setup()
    renderBoard()
    await screen.findByText('Board Y')

    // Der „Board öffnen"-Knopf des zweiten Boards führt zu dessen Liste.
    fireEvent.click(screen.getByRole('button', { name: 'Board Board Y öffnen' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/boards/11/list')
  })

  it('zeigt an jeder ziehbaren Zeile einen Ziehgriff', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    // 2 Karten (Board X) + 1 Karte (Board Y) + 1 Pool-Idee = 4 ziehbare Zeilen, jede mit Ziehgriff.
    expect(screen.getAllByLabelText('Ziehen')).toHaveLength(4)
  })

  it('blendet für Betrachter (canEdit=false) alle Aktionen aus', async () => {
    setup()
    renderBoard(false)
    await screen.findByText('Backlog A')

    expect(screen.queryByRole('button', { name: /einplanen/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /in den Pool/i })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Ziehen')).not.toBeInTheDocument()
    expect(screen.getByTestId('pool-item-20')).not.toHaveAttribute('draggable', 'true')
  })

  it('lädt beim Fensterfokus neu', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')
    mCards.list.mockClear()
    mIdeas.list.mockClear()

    fireEvent(window, new Event('focus'))

    await waitFor(() => expect(mCards.list).toHaveBeenCalled())
    expect(mIdeas.list).toHaveBeenCalled()
  })

  it('ignoriert eine spät auflösende Board-Antwort nach Unmount', async () => {
    const d = deferred<Board[]>()
    mBoards.list.mockReturnValue(d.promise)
    mCards.list.mockResolvedValue([])
    mIdeas.list.mockResolvedValue([])

    const { unmount } = render(
      <MemoryRouter>
        <IdeaPlanningBoard projectId={5} canEdit />
      </MemoryRouter>,
    )
    unmount()
    d.resolve(BOARDS)
    await d.promise

    // active=false nach Unmount -> die späte Antwort setzt keinen State und lädt kein Backlog.
    expect(mCards.list).not.toHaveBeenCalled()
  })

  it('sortiert eine Board-Karte per Drag auf eine andere Zeile desselben Boards um', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    // „Backlog A" (id 1) auf „Backlog Z" (id 5, Spalte 100, Position 1) ziehen.
    fireEvent.dragStart(screen.getByTestId('board-item-1'), dt())
    fireEvent.dragOver(screen.getByTestId('board-item-5'), dt())
    fireEvent.drop(screen.getByTestId('board-item-5'), dt())

    await waitFor(() => expect(mCards.move).toHaveBeenCalledWith(1, 100, 1))
  })

  it('sortiert nicht um, wenn eine Karte auf sich selbst fällt', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.dragStart(screen.getByTestId('board-item-1'), dt())
    fireEvent.drop(screen.getByTestId('board-item-1'), dt())

    expect(mCards.move).not.toHaveBeenCalled()
  })

  it('sortiert nicht um bei einem Drop ohne Drag', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.drop(screen.getByTestId('board-item-1'), dt())

    expect(mCards.move).not.toHaveBeenCalled()
  })

  it('sortiert nicht um, wenn eine Karte auf eine Zeile eines anderen Boards fällt', async () => {
    // Board→Board ist Issue #426; eine Zeilen-Umsortierung über Board-Grenzen gibt es nicht.
    setup()
    renderBoard()
    await screen.findByText('Board Y Karte')

    fireEvent.dragStart(screen.getByTestId('board-item-1'), dt())
    fireEvent.drop(screen.getByTestId('board-item-8'), dt())

    expect(mCards.move).not.toHaveBeenCalled()
  })

  it('plant (statt sortieren), wenn eine Pool-Idee auf eine Board-Zeile fällt', async () => {
    setup()
    renderBoard()
    await screen.findByText('Pool 1')

    // Pool-Quelle auf eine Board-Zeile: die Zeile reicht durch, die Zone plant ein.
    fireEvent.dragStart(screen.getByTestId('pool-item-20'), dt())
    fireEvent.drop(screen.getByTestId('board-item-1'), dt())

    await waitFor(() => expect(mIdeas.planOntoBoard).toHaveBeenCalledWith(20, 10))
    expect(mCards.move).not.toHaveBeenCalled()
  })

  describe('Auto-Scroll beim Ziehen', () => {
    it('scrollt nach oben, wenn der Zeiger den oberen Rand erreicht', async () => {
      const scrollBy = vi.fn()
      vi.stubGlobal('scrollBy', scrollBy)
      setup()
      renderBoard()
      await screen.findByText('Pool 1')

      fireEvent.dragStart(screen.getByTestId('pool-item-20'), dt())
      window.dispatchEvent(new MouseEvent('dragover', { clientY: 5 }))

      expect(scrollBy).toHaveBeenCalledTimes(1)
      const [, dy] = scrollBy.mock.calls[0]
      expect(dy).toBeLessThan(0)
    })

    it('scrollt nach unten, wenn der Zeiger den unteren Rand erreicht', async () => {
      const scrollBy = vi.fn()
      vi.stubGlobal('scrollBy', scrollBy)
      setup()
      renderBoard()
      await screen.findByText('Pool 1')

      fireEvent.dragStart(screen.getByTestId('pool-item-20'), dt())
      window.dispatchEvent(new MouseEvent('dragover', { clientY: window.innerHeight - 1 }))

      expect(scrollBy).toHaveBeenCalledTimes(1)
      const [, dy] = scrollBy.mock.calls[0]
      expect(dy).toBeGreaterThan(0)
    })

    it('scrollt nicht, solange der Zeiger in der Mitte bleibt', async () => {
      const scrollBy = vi.fn()
      vi.stubGlobal('scrollBy', scrollBy)
      setup()
      renderBoard()
      await screen.findByText('Pool 1')

      fireEvent.dragStart(screen.getByTestId('pool-item-20'), dt())
      window.dispatchEvent(new MouseEvent('dragover', { clientY: Math.round(window.innerHeight / 2) }))

      expect(scrollBy).not.toHaveBeenCalled()
    })

    it('scrollt nicht ohne laufenden Drag', async () => {
      const scrollBy = vi.fn()
      vi.stubGlobal('scrollBy', scrollBy)
      setup()
      renderBoard()
      await screen.findByText('Pool 1')

      window.dispatchEvent(new MouseEvent('dragover', { clientY: 5 }))

      expect(scrollBy).not.toHaveBeenCalled()
    })
  })

  describe('Pool-Idee im Detail-Modal öffnen (#404)', () => {
    const richIdea = idea({ id: 20, title: 'Pool 1', description: '# Story\n\nDetails der Idee' })

    it('öffnet eine Pool-Idee mit vollständigem Inhalt (Story + Kommentar-Bereich)', async () => {
      setup({ ideas: [richIdea] })
      renderBoard()

      fireEvent.click(await screen.findByRole('button', { name: 'Pool 1' }))

      expect(await screen.findByRole('heading', { name: 'Story' })).toBeInTheDocument()
      expect(screen.getByText('Details der Idee')).toBeInTheDocument()
      // Board-lose Idee, dennoch voll: der Kommentar-Bereich ist sichtbar.
      expect(screen.getByText('Kommentare')).toBeInTheDocument()
    })

    it('speichert eine Titel-/Beschreibungs-Änderung über cardsApi.update', async () => {
      setup({ ideas: [richIdea] })
      renderBoard()

      fireEvent.click(await screen.findByRole('button', { name: 'Pool 1' }))
      fireEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
      fireEvent.change(screen.getByLabelText('Markdown-Beschreibung'), { target: { value: 'Neuer Text' } })
      fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

      await waitFor(() =>
        expect(mCards.update).toHaveBeenCalledWith(20, 'Pool 1', 'Neuer Text', [], undefined, null, null),
      )
    })

    it('öffnet per Titel-Klick, ohne Drag/Einplanen auszulösen (kein Konflikt)', async () => {
      setup({ ideas: [richIdea] })
      renderBoard()

      fireEvent.click(await screen.findByRole('button', { name: 'Pool 1' }))
      await screen.findByRole('heading', { name: 'Story' })

      // Ein Klick auf den Titel öffnet nur — er plant nicht ein; die Zeile bleibt ziehbar.
      expect(mIdeas.planOntoBoard).not.toHaveBeenCalled()
      expect(screen.getByTestId('pool-item-20')).toHaveAttribute('draggable', 'true')
    })

    it('schließt das Modal über „Schließen"', async () => {
      setup({ ideas: [richIdea] })
      renderBoard()

      fireEvent.click(await screen.findByRole('button', { name: 'Pool 1' }))
      await screen.findByRole('heading', { name: 'Story' })

      fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))

      await waitFor(() => expect(screen.queryByRole('heading', { name: 'Story' })).not.toBeInTheDocument())
    })

    it('bleibt funktionsfähig, wenn das Laden der Mitglieder scheitert', async () => {
      setup({ ideas: [richIdea] })
      mMembers.list.mockRejectedValueOnce(new Error('boom'))
      renderBoard()

      // Trotz fehlgeschlagenem Members-Load öffnet die Idee (Fallback auf leere Mitgliederliste).
      fireEvent.click(await screen.findByRole('button', { name: 'Pool 1' }))
      expect(await screen.findByRole('heading', { name: 'Story' })).toBeInTheDocument()
    })
  })

  describe('Pool-Suche (Titel-Filter)', () => {
    const poolOne = idea({ id: 20, title: 'Pool 1' })
    const poolOther = idea({ id: 24, title: 'Andere Idee' })

    it('grenzt den Pool nach Titel ein und lässt die Backlog-Zone unberührt', async () => {
      setup({ ideas: [poolOne, poolOther] })
      render(
        <MemoryRouter>
          <IdeaPlanningBoard projectId={5} canEdit filter="pool" />
        </MemoryRouter>,
      )
      await screen.findByText('Backlog A')

      // Nur die passende Pool-Idee bleibt; die Backlog-Karte bleibt sichtbar (nicht gefiltert).
      expect(screen.getByText('Pool 1')).toBeInTheDocument()
      expect(screen.queryByText('Andere Idee')).not.toBeInTheDocument()
      expect(screen.getByText('Backlog A')).toBeInTheDocument()
    })

    it('zeigt einen Such-Leerzustand, wenn keine Pool-Idee passt, und behält das Backlog', async () => {
      setup({ ideas: [poolOne, poolOther] })
      render(
        <MemoryRouter>
          <IdeaPlanningBoard projectId={5} canEdit filter="zzz" />
        </MemoryRouter>,
      )
      await screen.findByText('Backlog A')

      expect(screen.getByText('Keine Idee passt zur Suche.')).toBeInTheDocument()
      expect(screen.queryByText('Pool 1')).not.toBeInTheDocument()
      expect(screen.getByText('Backlog A')).toBeInTheDocument()
    })
  })

  it('lädt Pool und Backlogs neu, wenn sich refreshKey ändert', async () => {
    setup()
    function Harness() {
      const [key, setKey] = useState(0)
      return (
        <MemoryRouter>
          <button onClick={() => setKey((n) => n + 1)}>bump</button>
          <IdeaPlanningBoard projectId={5} canEdit refreshKey={key} />
        </MemoryRouter>
      )
    }
    render(<Harness />)
    await screen.findByText('Backlog A')
    mCards.list.mockClear()
    mIdeas.list.mockClear()

    fireEvent.click(screen.getByText('bump'))

    await waitFor(() => expect(mIdeas.list).toHaveBeenCalled())
    expect(mCards.list).toHaveBeenCalledWith(10)
    expect(mCards.list).toHaveBeenCalledWith(11)
  })
})
