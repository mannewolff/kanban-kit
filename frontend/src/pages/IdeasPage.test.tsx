import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))

// Das Planungs-Board ist separat getestet (IdeaPlanningBoard.test.tsx) — hier durch einen Stub
// ersetzt, der die durchgereichten Props sichtbar macht, damit die Seite isoliert geprüft wird.
vi.mock('../components/IdeaPlanningBoard', () => ({
  IdeaPlanningBoard: ({
    projectId,
    canEdit,
    filter,
    refreshKey,
  }: {
    projectId: number
    canEdit: boolean
    filter?: string
    refreshKey?: number
  }) => (
    <div data-testid="planning-board">
      board {projectId} canEdit {String(canEdit)} filter [{filter}] refresh {String(refreshKey)}
    </div>
  ),
}))

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
const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }

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

type RenderOptions = {
  role?: string
  path?: string
  projects?: Array<{ id: number; name: string; role: string; createdAt: string }>
}

function renderPage({
  role = 'OWNER',
  path = '/projects/5/ideas',
  projects,
}: RenderOptions = {}) {
  mockedProjects.list.mockResolvedValue(projects ?? [{ id: 5, name: 'Team', role, createdAt: '' }])
  mockedIdeas.create.mockResolvedValue(idea({ id: 99, title: 'x' }))
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/projects/:projectId/ideas" element={<IdeasPage />} />
        <Route path="/boards/:boardId/list" element={<div>board-liste</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

function fakeStorage(seed: Record<string, string> = {}): Storage {
  const map = new Map<string, string>(Object.entries(seed))
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

class MockEventSource {
  static instances: MockEventSource[] = []
  url: string
  closed = false
  private readonly listeners = new Map<string, Set<(e: Event) => void>>()
  constructor(url: string) {
    this.url = url
    MockEventSource.instances.push(this)
  }
  addEventListener(type: string, cb: (e: Event) => void): void {
    const set = this.listeners.get(type) ?? new Set()
    set.add(cb)
    this.listeners.set(type, set)
  }
  removeEventListener(type: string, cb: (e: Event) => void): void {
    this.listeners.get(type)?.delete(cb)
  }
  close(): void {
    this.closed = true
  }
  emit(type: string): void {
    this.listeners.get(type)?.forEach((cb) => cb(new Event(type)))
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  MockEventSource.instances = []
  vi.stubGlobal('localStorage', fakeStorage())
  vi.stubGlobal('EventSource', MockEventSource)
})

afterEach(() => vi.unstubAllGlobals())

describe('IdeasPage', () => {
  it('meldet eine ungültige Projekt-ID und lädt nichts', async () => {
    renderPage({ path: '/projects/abc/ideas' })
    expect(await screen.findByText('Ungültige Projekt-ID.')).toBeInTheDocument()
    expect(mockedProjects.list).not.toHaveBeenCalled()
    expect(screen.queryByTestId('planning-board')).not.toBeInTheDocument()
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

  it('rendert direkt die Planen-Ansicht ohne Liste/Planen-Umschalter', async () => {
    renderPage()
    const board = await screen.findByTestId('planning-board')

    expect(board).toHaveTextContent('board 5')
    expect(board).toHaveTextContent('canEdit true')
    expect(screen.queryByRole('button', { name: 'Liste' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Planen' })).not.toBeInTheDocument()
  })

  it('zeigt „Idee anlegen" unabhängig von einer Ansicht und legt eine board-lose Pool-Idee an', async () => {
    renderPage()
    await screen.findByTestId('planning-board')

    fireEvent.click(screen.getByRole('button', { name: 'Idee anlegen' }))
    fireEvent.click(screen.getByText('modal-anlegen'))

    await waitFor(() =>
      expect(mockedIdeas.create).toHaveBeenCalledWith(5, { title: 'Neue Idee', description: 'Body' }),
    )
    // Nach dem Anlegen bekommt die Planen-Ansicht einen neuen Reload-Impuls (refreshKey erhöht).
    await waitFor(() => expect(screen.getByTestId('planning-board')).toHaveTextContent('refresh 1'))
  })

  it('schließt den Anlege-Dialog wieder', async () => {
    renderPage()
    await screen.findByTestId('planning-board')

    fireEvent.click(screen.getByRole('button', { name: 'Idee anlegen' }))
    fireEvent.click(screen.getByText('modal-schliessen'))

    expect(screen.queryByText('modal-anlegen')).not.toBeInTheDocument()
  })

  it('reicht die Textsuche an die Planen-Ansicht durch (leeres Feld = alles)', async () => {
    renderPage()
    const board = await screen.findByTestId('planning-board')
    expect(board).toHaveTextContent('filter []')

    fireEvent.change(screen.getByLabelText('Ideen durchsuchen'), { target: { value: 'legacy' } })

    expect(screen.getByTestId('planning-board')).toHaveTextContent('filter [legacy]')
  })

  it('entfernt beim Laden den veralteten Ansichts-localStorage-Schlüssel', async () => {
    vi.stubGlobal('localStorage', fakeStorage({ 'manban.ideasView': 'liste' }))
    renderPage()
    await screen.findByTestId('planning-board')

    // Nutzer mit Altwert „liste" landen in der Planen-Ansicht, keine leere Seite; der Schlüssel ist weg.
    expect(localStorage.getItem('manban.ideasView')).toBeNull()
  })

  it('rendert trotz localStorage-Fehler beim Aufräumen ohne Crash', async () => {
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
    renderPage()

    expect(await screen.findByTestId('planning-board')).toBeInTheDocument()
  })

  it('blendet für Betrachter (VIEWER) „Idee anlegen" aus, reicht aber canEdit=false durch', async () => {
    renderPage({ role: 'VIEWER' })
    const board = await screen.findByTestId('planning-board')

    expect(screen.queryByRole('button', { name: 'Idee anlegen' })).not.toBeInTheDocument()
    expect(board).toHaveTextContent('canEdit false')
  })

  it('nutzt den Projekt-Fallback im Breadcrumb, wenn das Projekt nicht gefunden wird', async () => {
    renderPage({ projects: [{ id: 999, name: 'Fremd', role: 'OWNER', createdAt: '' }] })
    await screen.findByTestId('planning-board')

    expect(screen.getByText('Projekt')).toBeInTheDocument()
  })

  it('verwirft eine spät auflösende Projekt-Antwort der alten ID nach einem ID-Wechsel', async () => {
    const dProjects = deferred<Array<{ id: number; name: string; role: string; createdAt: string }>>()
    // Erster Aufruf (Projekt 5) bleibt hängen; der Folgeaufruf (Projekt 6) löst sofort auf.
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

    // Auf Projekt 6 wechseln, bevor die Antwort für Projekt 5 da ist.
    fireEvent.click(screen.getByText('wechseln'))
    expect(await screen.findByText('Team6')).toBeInTheDocument()

    // Verspätete Antwort der alten ID darf den Namen nicht mehr überschreiben.
    dProjects.resolve([{ id: 5, name: 'Team5', role: 'OWNER', createdAt: '' }])
    expect(await screen.findByText('Team6')).toBeInTheDocument()
    expect(screen.queryByText('Team5')).not.toBeInTheDocument()
  })

  it('abonniert den Ideen-Stream und erhöht bei einem Live-Event den Reload-Impuls', async () => {
    renderPage()
    await screen.findByTestId('planning-board')
    expect(MockEventSource.instances).toHaveLength(1)
    expect(MockEventSource.instances[0].url).toBe('/api/projects/5/ideas/events')

    MockEventSource.instances[0].emit('project-ideas-changed')

    await waitFor(() => expect(screen.getByTestId('planning-board')).toHaveTextContent('refresh 1'))
  })

  it('abonniert keinen Stream bei ungültiger Projekt-ID', async () => {
    renderPage({ path: '/projects/abc/ideas' })
    await screen.findByText('Ungültige Projekt-ID.')

    expect(MockEventSource.instances).toHaveLength(0)
  })
})
