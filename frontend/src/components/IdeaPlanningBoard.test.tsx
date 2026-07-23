import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { ideasApi, type Idea } from '../api/ideas'
import { IdeaPlanningBoard } from './IdeaPlanningBoard'

vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn() } }))
vi.mock('../api/cards', () => ({ cardsApi: { list: vi.fn(), move: vi.fn() } }))
vi.mock('../api/ideas', () => ({
  ideasApi: { list: vi.fn(), planOntoBoard: vi.fn(), moveBackToPool: vi.fn() },
}))

const mBoards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mCards = cardsApi as unknown as { list: ReturnType<typeof vi.fn>; move: ReturnType<typeof vi.fn> }
const mIdeas = ideasApi as unknown as {
  list: ReturnType<typeof vi.fn>
  planOntoBoard: ReturnType<typeof vi.fn>
  moveBackToPool: ReturnType<typeof vi.fn>
}

const col = (id: number, name: string, position: number) => ({ id, name, position, wipLimit: null })

const BOARDS = [
  { id: 10, name: 'Board X', projectId: 5, createdAt: '', columns: [col(100, 'Backlog', 0), col(101, 'Ready', 1)] },
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
    ...partial,
  }
}

// board 10, erste Spalte 100: nur die aktiven Nicht-Idee-Karten der ersten Spalte gehören ins
// Backlog. backlogCard2 kommt im Input VOR backlogCard, muss aber nach positionInColumn dahinter
// einsortiert werden (deckt den Sort-Comparator ab).
const backlogCard = card({ id: 1, columnId: 100, number: 7, title: 'Backlog A', positionInColumn: 0 })
const backlogCard2 = card({ id: 5, columnId: 100, number: 6, title: 'Backlog Z', positionInColumn: 1 })
const readyCard = card({ id: 2, columnId: 101, number: 8, title: 'Ready B', positionInColumn: 0 })
const archivedCard = card({ id: 3, columnId: 100, number: 9, title: 'Archiv C', positionInColumn: 2, archived: true })
const ideaCard = card({ id: 4, columnId: 100, number: 10, title: 'Idee D', positionInColumn: 3, ideaStored: true })

const poolIdea = idea({ id: 20, title: 'Pool 1' })
const legacyIdea = idea({ id: 21, title: 'Legacy', boardId: 10 })

function setup({
  boards = BOARDS,
  cards = [backlogCard2, backlogCard, readyCard, archivedCard, ideaCard],
  ideas = [poolIdea, legacyIdea],
} = {}) {
  mBoards.list.mockResolvedValue(boards)
  mCards.list.mockResolvedValue(cards)
  mCards.move.mockResolvedValue({})
  mIdeas.list.mockResolvedValue(ideas)
  mIdeas.planOntoBoard.mockResolvedValue(idea({ id: 20, title: 'x' }))
  mIdeas.moveBackToPool.mockResolvedValue(idea({ id: 1, title: 'x' }))
}

function renderBoard(canEdit = true) {
  return render(<IdeaPlanningBoard projectId={5} canEdit={canEdit} />)
}

const dt = () => ({ dataTransfer: { setData: vi.fn() } })

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

function fakeStorage(): Storage {
  const map = new Map<string, string>()
  return {
    getItem: (k) => map.get(k) ?? null,
    setItem: (k, v) => void map.set(k, String(v)),
    removeItem: (k) => void map.delete(k),
    clear: () => map.clear(),
    key: (i) => [...map.keys()][i] ?? null,
    get length() {
      return map.size
    },
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('localStorage', fakeStorage())
})

afterEach(() => vi.unstubAllGlobals())

describe('IdeaPlanningBoard', () => {
  it('zeigt einen Hinweis, wenn das Projekt kein Board hat', async () => {
    setup({ boards: [] })
    renderBoard()
    expect(await screen.findByText(/kein Board/i)).toBeInTheDocument()
    expect(mCards.list).not.toHaveBeenCalled()
  })

  it('lädt das Backlog der ersten Spalte und den board-losen Pool', async () => {
    setup()
    renderBoard()

    // Backlog: nur die aktiven Nicht-Idee-Karten der ersten Spalte, nach Position sortiert.
    expect(await screen.findByText('Backlog A')).toBeInTheDocument()
    const backlogItems = screen.getAllByText(/^Backlog [AZ]$/).map((el) => el.textContent)
    expect(backlogItems).toEqual(['Backlog A', 'Backlog Z'])
    expect(screen.queryByText('Ready B')).not.toBeInTheDocument()
    expect(screen.queryByText('Archiv C')).not.toBeInTheDocument()
    expect(screen.queryByText('Idee D')).not.toBeInTheDocument()
    // Pool: nur board-lose Ideen (Legacy board-gebunden bleibt draußen).
    expect(screen.getByText('Pool 1')).toBeInTheDocument()
    expect(screen.queryByText('Legacy')).not.toBeInTheDocument()
  })

  it('plant eine Pool-Idee per Button ein', async () => {
    setup()
    renderBoard()
    await screen.findByText('Pool 1')

    fireEvent.click(screen.getByRole('button', { name: 'Idee Pool 1 einplanen' }))

    await waitFor(() => expect(mIdeas.planOntoBoard).toHaveBeenCalledWith(20, 10))
  })

  it('holt eine Backlog-Karte per Button in den Pool', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.click(screen.getByRole('button', { name: 'Karte Backlog A in den Pool' }))

    await waitFor(() => expect(mIdeas.moveBackToPool).toHaveBeenCalledWith(1))
  })

  it('plant per Drag von Pool auf das Backlog ein', async () => {
    setup()
    renderBoard()
    await screen.findByText('Pool 1')

    fireEvent.dragStart(screen.getByTestId('pool-item-20'), dt())
    fireEvent.dragOver(screen.getByTestId('backlog-zone'), dt())
    fireEvent.drop(screen.getByTestId('backlog-zone'), dt())

    await waitFor(() => expect(mIdeas.planOntoBoard).toHaveBeenCalledWith(20, 10))
  })

  it('holt per Drag vom Backlog in den Pool', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.dragStart(screen.getByTestId('backlog-item-1'), dt())
    fireEvent.dragOver(screen.getByTestId('pool-zone'), dt())
    fireEvent.drop(screen.getByTestId('pool-zone'), dt())

    await waitFor(() => expect(mIdeas.moveBackToPool).toHaveBeenCalledWith(1))
  })

  it('ignoriert einen Drop in dieselbe Zone', async () => {
    setup()
    renderBoard()
    await screen.findByText('Pool 1')

    fireEvent.dragStart(screen.getByTestId('pool-item-20'), dt())
    fireEvent.drop(screen.getByTestId('pool-zone'), dt())

    expect(mIdeas.planOntoBoard).not.toHaveBeenCalled()
    expect(mIdeas.moveBackToPool).not.toHaveBeenCalled()
  })

  it('ignoriert einen Drop ohne vorheriges Dragstart', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.drop(screen.getByTestId('backlog-zone'), dt())

    expect(mIdeas.planOntoBoard).not.toHaveBeenCalled()
  })

  it('zeigt Leer-Hinweise für Backlog und Pool', async () => {
    setup({ cards: [], ideas: [] })
    renderBoard()

    expect(await screen.findByText(/Kein Backlog/i)).toBeInTheDocument()
    expect(screen.getByText('Keine Ideen im Pool.')).toBeInTheDocument()
  })

  it('zeigt an jeder ziehbaren Zeile einen Ziehgriff', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    // 2 Backlog-Karten + 1 Pool-Idee = 3 ziehbare Zeilen, jede mit Ziehgriff.
    expect(screen.getAllByLabelText('Ziehen')).toHaveLength(3)
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

  it('wechselt das Board und lädt dessen Backlog + merkt die Wahl', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')
    mCards.list.mockClear()

    fireEvent.change(screen.getByLabelText('Board wählen'), { target: { value: '11' } })

    await waitFor(() => expect(mCards.list).toHaveBeenCalledWith(11))
    expect(localStorage.getItem('manban.ideaBoard.5')).toBe('11')
  })

  it('wählt das zuletzt gemerkte Board vor', async () => {
    localStorage.setItem('manban.ideaBoard.5', '11')
    setup()
    renderBoard()

    await waitFor(() => expect(mCards.list).toHaveBeenCalledWith(11))
    expect((screen.getByLabelText('Board wählen') as HTMLSelectElement).value).toBe('11')
  })

  it('fällt auf das erste Board zurück, wenn das gemerkte Board nicht mehr existiert', async () => {
    localStorage.setItem('manban.ideaBoard.5', '999')
    setup()
    renderBoard()

    await waitFor(() => expect(mCards.list).toHaveBeenCalledWith(10))
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

  it('bleibt bei einem lokalStorage-Fehler funktionsfähig', async () => {
    const boom = () => {
      throw new Error('storage disabled')
    }
    vi.stubGlobal('localStorage', {
      getItem: boom,
      setItem: boom,
      removeItem: boom,
      clear: boom,
      key: boom,
      get length() {
        return 0
      },
    } as unknown as Storage)
    setup()
    renderBoard()

    // Ohne lesbares localStorage: Fallback auf das erste Board, kein Crash.
    await waitFor(() => expect(mCards.list).toHaveBeenCalledWith(10))
    // Board-Wechsel schreibt ins (werfende) localStorage, ohne zu brechen.
    fireEvent.change(screen.getByLabelText('Board wählen'), { target: { value: '11' } })
    await waitFor(() => expect(mCards.list).toHaveBeenCalledWith(11))
  })

  it('ignoriert eine spät auflösende Board-Antwort nach Unmount', async () => {
    const d = deferred<Board[]>()
    mBoards.list.mockReturnValue(d.promise)
    mCards.list.mockResolvedValue([])
    mIdeas.list.mockResolvedValue([])

    const { unmount } = render(<IdeaPlanningBoard projectId={5} canEdit />)
    unmount()
    d.resolve(BOARDS)
    await d.promise

    // active=false nach Unmount -> die späte Antwort setzt keinen State und lädt kein Backlog.
    expect(mCards.list).not.toHaveBeenCalled()
  })

  it('sortiert eine Backlog-Karte per Drag auf eine andere Zeile um', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    // „Backlog A" (id 1) auf „Backlog Z" (id 5, Spalte 100, Position 1) ziehen.
    fireEvent.dragStart(screen.getByTestId('backlog-item-1'), dt())
    fireEvent.dragOver(screen.getByTestId('backlog-item-5'), dt())
    fireEvent.drop(screen.getByTestId('backlog-item-5'), dt())

    await waitFor(() => expect(mCards.move).toHaveBeenCalledWith(1, 100, 1))
  })

  it('sortiert nicht um, wenn eine Karte auf sich selbst fällt', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.dragStart(screen.getByTestId('backlog-item-1'), dt())
    fireEvent.drop(screen.getByTestId('backlog-item-1'), dt())

    expect(mCards.move).not.toHaveBeenCalled()
  })

  it('sortiert nicht um bei einem Drop ohne Drag', async () => {
    setup()
    renderBoard()
    await screen.findByText('Backlog A')

    fireEvent.drop(screen.getByTestId('backlog-item-1'), dt())

    expect(mCards.move).not.toHaveBeenCalled()
  })

  it('plant (statt sortieren), wenn eine Pool-Idee auf eine Backlog-Zeile fällt', async () => {
    setup()
    renderBoard()
    await screen.findByText('Pool 1')

    // Pool-Quelle auf eine Backlog-Zeile: die Zeile reicht durch, die Zone plant ein.
    fireEvent.dragStart(screen.getByTestId('pool-item-20'), dt())
    fireEvent.drop(screen.getByTestId('backlog-item-1'), dt())

    await waitFor(() => expect(mIdeas.planOntoBoard).toHaveBeenCalledWith(20, 10))
    expect(mCards.move).not.toHaveBeenCalled()
  })
})
