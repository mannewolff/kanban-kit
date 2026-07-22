import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi, type Board } from '../api/boards'
import { ideasApi, type Idea } from '../api/ideas'
import { projectsApi } from '../api/projects'
import { IdeasPage } from './IdeasPage'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

vi.mock('../api/ideas', () => ({
  ideasApi: { list: vi.fn(), create: vi.fn(), planOntoBoard: vi.fn(), moveBackToPool: vi.fn() },
}))
vi.mock('../api/boards', () => ({ boardsApi: { list: vi.fn() } }))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))

// NewCardModal ist separat getestet — hier durch einen schlanken Stub ersetzt, der onSubmit mit
// einer festen Idee auslöst, damit der handleCreate-Pfad der Seite geprüft werden kann.
vi.mock('../components/NewCardModal', () => ({
  NewCardModal: ({
    open,
    onSubmit,
    onClose,
  }: {
    open: boolean
    onSubmit: (input: unknown) => void
    onClose: () => void
  }) =>
    open ? (
      <div>
        <button
          type="button"
          onClick={() =>
            onSubmit({
              type: 'CARD',
              title: 'Neue Idee',
              description: 'Body',
              parentId: null,
              shortcode: null,
              dependencies: [],
              dueDate: null,
              assigneeIds: [],
              labelIds: [],
            })
          }
        >
          modal-anlegen
        </button>
        <button type="button" onClick={onClose}>
          modal-schliessen
        </button>
      </div>
    ) : null,
}))

const mockedIdeas = ideasApi as unknown as {
  list: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  planOntoBoard: ReturnType<typeof vi.fn>
  moveBackToPool: ReturnType<typeof vi.fn>
}
const mockedBoards = boardsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }

const BOARDS = [
  { id: 10, name: 'Board X', projectId: 5, createdAt: '', columns: [] },
  { id: 11, name: 'Board Y', projectId: 5, createdAt: '', columns: [] },
]

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

const poolA = idea({ id: 1, title: 'Pool A', targetBoardId: 10 })
const poolB = idea({ id: 2, title: 'Pool B' })
const legacy = idea({ id: 3, title: 'Legacy', boardId: 10 })
const poolC = idea({ id: 4, title: 'Pool C', targetBoardId: 999 })
const orphan = idea({ id: 5, title: 'Orphan', boardId: 99 })

type RenderOptions = {
  role?: string
  ideas?: Idea[]
  boards?: typeof BOARDS
  path?: string
  projects?: Array<{ id: number; name: string; role: string; createdAt: string }>
}

function renderPage({
  role = 'OWNER',
  ideas = [],
  boards = BOARDS,
  path = '/projects/5/ideas',
  projects,
}: RenderOptions = {}) {
  mockedIdeas.list.mockResolvedValue(ideas)
  mockedBoards.list.mockResolvedValue(boards)
  mockedProjects.list.mockResolvedValue(projects ?? [{ id: 5, name: 'Team', role, createdAt: '' }])
  mockedIdeas.create.mockResolvedValue(idea({ id: 99, title: 'x' }))
  mockedIdeas.planOntoBoard.mockResolvedValue(idea({ id: 1, title: 'x' }))
  mockedIdeas.moveBackToPool.mockResolvedValue(idea({ id: 1, title: 'x' }))
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/projects/:projectId/ideas" element={<IdeasPage />} />
        <Route path="/boards/:boardId/list" element={<div>board-liste</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => vi.clearAllMocks())

describe('IdeasPage', () => {
  it('meldet eine ungültige Projekt-ID', async () => {
    renderPage({ path: '/projects/abc/ideas' })
    expect(await screen.findByText('Ungültige Projekt-ID.')).toBeInTheDocument()
    expect(mockedIdeas.list).not.toHaveBeenCalled()
  })

  it('zeigt den Leerzustand, wenn der Pool leer ist', async () => {
    renderPage({ ideas: [] })
    expect(await screen.findByText('Noch keine Ideen im Pool.')).toBeInTheDocument()
  })

  it('listet Pool-Ideen und eingeplante/Legacy-Karten mit den passenden Aktionen', async () => {
    renderPage({ ideas: [poolA, legacy] })
    await screen.findByText('Pool A')

    // Pool-Idee: notiertes Zielboard + „Einplanen".
    expect(screen.getByText('Zielboard: Board X')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Einplanen' })).toBeInTheDocument()

    // Legacy-Karte: Board-Chip + „Zurück in Pool".
    expect(screen.getByText('Auf Board: Board X')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Zurück in Pool' })).toBeInTheDocument()
  })

  it('zeigt die Board-ID im Chip, wenn das Board nicht (mehr) in der Liste ist', async () => {
    renderPage({ ideas: [orphan] })
    expect(await screen.findByText('Auf Board: 99')).toBeInTheDocument()
  })

  it('filtert nach Text', async () => {
    renderPage({ ideas: [poolA, legacy] })
    await screen.findByText('Pool A')

    fireEvent.change(screen.getByLabelText('Ideen durchsuchen'), { target: { value: 'legacy' } })

    expect(screen.getByText('Legacy')).toBeInTheDocument()
    expect(screen.queryByText('Pool A')).not.toBeInTheDocument()
  })

  it('zeigt einen Filter-Leerzustand, wenn nichts passt', async () => {
    renderPage({ ideas: [poolA] })
    await screen.findByText('Pool A')

    fireEvent.change(screen.getByLabelText('Ideen durchsuchen'), { target: { value: 'zzz' } })

    expect(screen.getByText('Keine Ideen für diesen Filter.')).toBeInTheDocument()
  })

  it('filtert nach Zielboard: „ohne", ein konkretes Board und zurück zu „alle"', async () => {
    renderPage({ ideas: [poolA, poolB] })
    await screen.findByText('Pool A')
    const filter = screen.getByLabelText('Nach Zielboard filtern')

    // „ohne Zielboard" -> nur Pool B (targetBoardId null)
    fireEvent.change(filter, { target: { value: 'none' } })
    expect(screen.getByText('Pool B')).toBeInTheDocument()
    expect(screen.queryByText('Pool A')).not.toBeInTheDocument()

    // konkretes Board 10 -> nur Pool A
    fireEvent.change(filter, { target: { value: '10' } })
    expect(screen.getByText('Pool A')).toBeInTheDocument()
    expect(screen.queryByText('Pool B')).not.toBeInTheDocument()

    // zurück zu „alle" -> beide
    fireEvent.change(filter, { target: { value: 'all' } })
    expect(screen.getByText('Pool A')).toBeInTheDocument()
    expect(screen.getByText('Pool B')).toBeInTheDocument()
  })

  it('legt eine board-lose Idee an und lädt neu', async () => {
    renderPage({ ideas: [] })
    await screen.findByText('Noch keine Ideen im Pool.')

    fireEvent.click(screen.getByRole('button', { name: 'Idee anlegen' }))
    fireEvent.click(screen.getByText('modal-anlegen'))

    await waitFor(() =>
      expect(mockedIdeas.create).toHaveBeenCalledWith(5, { title: 'Neue Idee', description: 'Body' }),
    )
    // reload: ideasApi.list wurde erneut aufgerufen (initial + nach dem Anlegen).
    await waitFor(() => expect(mockedIdeas.list.mock.calls.length).toBeGreaterThanOrEqual(2))
  })

  it('schließt den Anlege-Dialog wieder', async () => {
    renderPage({ ideas: [] })
    await screen.findByText('Noch keine Ideen im Pool.')

    fireEvent.click(screen.getByRole('button', { name: 'Idee anlegen' }))
    fireEvent.click(screen.getByText('modal-schliessen'))

    expect(screen.queryByText('modal-anlegen')).not.toBeInTheDocument()
  })

  it('plant eine Pool-Idee mit vorgewähltem Zielboard ein und navigiert zum Board', async () => {
    renderPage({ ideas: [poolA] })
    await screen.findByText('Pool A')

    fireEvent.click(screen.getByRole('button', { name: 'Einplanen' }))
    const dialog = screen.getByRole('dialog')
    // Zielboard aus target_board_id vorgewählt.
    expect((within(dialog).getByLabelText('Zielboard wählen') as HTMLSelectElement).value).toBe('10')

    fireEvent.click(within(dialog).getByRole('button', { name: 'Einplanen' }))

    await waitFor(() => expect(mockedIdeas.planOntoBoard).toHaveBeenCalledWith(1, 10))
    expect(await screen.findByText('board-liste')).toBeInTheDocument()
  })

  it('wählt beim Einplanen ohne notiertes Zielboard das erste Board vor', async () => {
    renderPage({ ideas: [poolB] })
    await screen.findByText('Pool B')

    fireEvent.click(screen.getByRole('button', { name: 'Einplanen' }))

    expect((screen.getByLabelText('Zielboard wählen') as HTMLSelectElement).value).toBe('10')
  })

  it('wählt das erste Board vor, wenn das notierte Zielboard nicht mehr existiert', async () => {
    // Pool C trägt target_board_id 999, das nicht in der Board-Liste steht -> Fallback erstes Board.
    renderPage({ ideas: [poolC] })
    await screen.findByText('Pool C')

    fireEvent.click(screen.getByRole('button', { name: 'Einplanen' }))

    expect((screen.getByLabelText('Zielboard wählen') as HTMLSelectElement).value).toBe('10')
  })

  it('lässt beim Einplanen ein anderes Board wählen', async () => {
    renderPage({ ideas: [poolB] })
    await screen.findByText('Pool B')

    fireEvent.click(screen.getByRole('button', { name: 'Einplanen' }))
    const dialog = screen.getByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('Zielboard wählen'), { target: { value: '11' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Einplanen' }))

    await waitFor(() => expect(mockedIdeas.planOntoBoard).toHaveBeenCalledWith(2, 11))
  })

  it('bricht den Einplanen-Dialog über „Abbrechen" ab', async () => {
    renderPage({ ideas: [poolA] })
    await screen.findByText('Pool A')

    fireEvent.click(screen.getByRole('button', { name: 'Einplanen' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Abbrechen' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(mockedIdeas.planOntoBoard).not.toHaveBeenCalled()
  })

  it('schließt den Einplanen-Dialog per Escape', async () => {
    renderPage({ ideas: [poolA] })
    await screen.findByText('Pool A')

    fireEvent.click(screen.getByRole('button', { name: 'Einplanen' }))
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape', code: 'Escape' })

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('holt eine eingeplante/Legacy-Karte zurück in den Pool', async () => {
    renderPage({ ideas: [legacy] })
    await screen.findByText('Legacy')

    fireEvent.click(screen.getByRole('button', { name: 'Zurück in Pool' }))

    await waitFor(() => expect(mockedIdeas.moveBackToPool).toHaveBeenCalledWith(3))
  })

  it('deaktiviert „Einplanen", wenn das Projekt keine Boards hat', async () => {
    renderPage({ ideas: [poolA], boards: [] })
    await screen.findByText('Pool A')

    expect(screen.getByRole('button', { name: 'Einplanen' })).toBeDisabled()
  })

  it('blendet für Betrachter (VIEWER) alle Aktionen aus', async () => {
    renderPage({ ideas: [poolA, legacy], role: 'VIEWER' })
    await screen.findByText('Pool A')

    expect(screen.queryByRole('button', { name: 'Idee anlegen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Einplanen' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Zurück in Pool' })).not.toBeInTheDocument()
  })

  it('nutzt den Projekt-Fallback im Breadcrumb, wenn das Projekt nicht gefunden wird', async () => {
    renderPage({ ideas: [poolA], projects: [{ id: 999, name: 'Fremd', role: 'OWNER', createdAt: '' }] })
    await screen.findByText('Pool A')

    expect(screen.getByText('Projekt')).toBeInTheDocument()
  })

  it('behandelt einen fehlenden Projekt-Parameter als ungültig', () => {
    render(
      <MemoryRouter initialEntries={['/ideas']}>
        <Routes>
          <Route path="/ideas" element={<IdeasPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByText('Ungültige Projekt-ID.')).toBeInTheDocument()
  })

  it('verwirft spät auflösende Antworten der alten Projekt-ID nach einem ID-Wechsel', async () => {
    const dIdeas = deferred<Idea[]>()
    const dBoards = deferred<Board[]>()
    const dProjects = deferred<Array<{ id: number; name: string; role: string; createdAt: string }>>()
    // Erster Aufruf (Projekt 5) bleibt hängen; die Folgeaufrufe (Projekt 6) lösen sofort auf.
    mockedIdeas.list.mockReturnValueOnce(dIdeas.promise).mockResolvedValue([idea({ id: 6, title: 'Sechs' })])
    mockedBoards.list.mockReturnValueOnce(dBoards.promise).mockResolvedValue(BOARDS)
    mockedProjects.list
      .mockReturnValueOnce(dProjects.promise)
      .mockResolvedValue([{ id: 6, name: 'Team6', role: 'OWNER', createdAt: '' }])

    function Nav() {
      const navigate = useNavigate()
      return <button onClick={() => navigate('/projects/6/ideas')}>wechseln</button>
    }
    render(
      <MemoryRouter initialEntries={['/projects/5/ideas']}>
        <Nav />
        <Routes>
          <Route path="/projects/:projectId/ideas" element={<IdeasPage />} />
        </Routes>
      </MemoryRouter>,
    )

    // Auf Projekt 6 wechseln, bevor die Antworten für Projekt 5 da sind.
    fireEvent.click(screen.getByText('wechseln'))
    expect(await screen.findByText('Sechs')).toBeInTheDocument()

    // Verspätete Antworten der alten ID dürfen den State nicht mehr überschreiben.
    dIdeas.resolve([idea({ id: 5, title: 'Fünf' })])
    dBoards.resolve([])
    dProjects.resolve([{ id: 5, name: 'Team5', role: 'OWNER', createdAt: '' }])
    expect(await screen.findByText('Sechs')).toBeInTheDocument()
    expect(screen.queryByText('Fünf')).not.toBeInTheDocument()
  })

  it('lädt beim Fensterfokus neu', async () => {
    renderPage({ ideas: [poolA] })
    await screen.findByText('Pool A')
    mockedIdeas.list.mockClear()

    fireEvent(window, new Event('focus'))

    await waitFor(() => expect(mockedIdeas.list).toHaveBeenCalled())
  })

  it('lädt beim Fensterfokus nichts nach, wenn die Projekt-ID ungültig ist', async () => {
    renderPage({ path: '/projects/abc/ideas' })
    await screen.findByText('Ungültige Projekt-ID.')

    fireEvent(window, new Event('focus'))

    expect(mockedIdeas.list).not.toHaveBeenCalled()
  })
})
