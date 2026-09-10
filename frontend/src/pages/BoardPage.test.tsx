import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { boardsApi } from '../api/boards'
import { cardsApi } from '../api/cards'
import { epicsApi } from '../api/epics'
import { labelsApi } from '../api/labels'
import { membersApi } from '../api/members'
import { configApi } from '../api/config'
import { projectsApi } from '../api/projects'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { BoardPage } from './BoardPage'

let memberships: { projectId: number; role: string }[] = []
let platformRole = 'USER'
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, email: 'a@b.c', displayName: 'A', platformRole, memberships } }),
}))
vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn(), rename: vi.fn(), list: vi.fn() } }))
vi.mock('../api/cards', () => ({
  cardsApi: {
    list: vi.fn(),
    // Das CardDetailModal lädt die volle Beschreibung beim Öffnen nach (Issue #769).
    get: vi.fn().mockResolvedValue({ description: null }),
    getActivity: vi.fn().mockResolvedValue([]),
    update: vi.fn(),
    setAssignees: vi.fn(),
    setLabels: vi.fn(),
    restore: vi.fn(),
    archive: vi.fn(),
    move: vi.fn(),
    create: vi.fn(),
    remove: vi.fn(),
    bulkArchive: vi.fn(),
    bulkTransfer: vi.fn(),
    bulkDelete: vi.fn(),
    // Der Detail-Dialog eines Vorhabens lädt seinen Herkunftsbaum nach (Issue #686).
    epicTree: vi.fn().mockResolvedValue([]),
    listTrash: vi.fn().mockResolvedValue([]),
    restoreDeleted: vi.fn(),
    purge: vi.fn(),
  },
}))
vi.mock('../api/epics', () => ({ epicsApi: { list: vi.fn(), assign: vi.fn(), create: vi.fn() } }))
vi.mock('../api/labels', () => ({ labelsApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() } }))
vi.mock('../api/members', () => ({ membersApi: { list: vi.fn() } }))
vi.mock('../api/config', () => ({ configApi: { get: vi.fn() } }))
vi.mock('../api/comments', () => ({
  commentsApi: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('../api/attachments', () => ({
  attachmentsApi: { list: vi.fn().mockResolvedValue([]), upload: vi.fn(), remove: vi.fn(), fetchBlob: vi.fn() },
}))
vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
// Editiermodus gemockt: Bestandstests laufen mit editMode=true (Bleistifte sichtbar); der
// Editiermodus-aus-Test schaltet editMode.value=false.
const editMode = vi.hoisted(() => ({ value: true }))
vi.mock('../lib/EditModeContext', () => ({
  useEditMode: () => ({ editMode: editMode.value, setEditMode: vi.fn(), toggleEditMode: vi.fn() }),
}))
beforeEach(() => {
  editMode.value = true
  platformRole = 'USER'
})

const mockedBoards = boardsApi as unknown as {
  get: ReturnType<typeof vi.fn>
  rename: ReturnType<typeof vi.fn>
  list: ReturnType<typeof vi.fn>
}
const mockedCards = cardsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  update: ReturnType<typeof vi.fn>
}
const mockedEpics = epicsApi as unknown as { list: ReturnType<typeof vi.fn>; create: ReturnType<typeof vi.fn> }
const mockedLabels = labelsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
}
const mockedMembers = membersApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedConfig = configApi as unknown as { get: ReturnType<typeof vi.fn> }
const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }

function renderPage() {
  mockedBoards.get.mockResolvedValue({
    id: 1, projectId: 9, name: 'B', createdAt: '',
    columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
  })
  mockedCards.list.mockResolvedValue([])
  mockedEpics.list.mockResolvedValue([])
  mockedLabels.list.mockResolvedValue([])
  mockedMembers.list.mockResolvedValue([])
  mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
  mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'OWNER', createdAt: '' }])
  return render(
    <SnackbarProvider>
      <MemoryRouter initialEntries={['/boards/1']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>
    </SnackbarProvider>,
  )
}

/** Server-Ablehnung mit einer für den Nutzer formulierten Meldung in `detail` (RFC 9457). */
const serverfehler = (text: string) => new ApiError(409, 'Conflict', undefined, text)

/**
 * Prüft den Fehler-Toast: Der Text steht dort, und der Alert trägt die Severity `error`.
 *
 * `hidden: true`: Der offene Umbenennen-Dialog stellt alles außerhalb seines Portals auf
 * `aria-hidden` — der Toast trägt seine Rolle, wird von der Standardabfrage aber übergangen.
 */
async function erwarteFehlerToast(text: string) {
  expect(await screen.findByText(text)).toBeInTheDocument()
  expect(await screen.findByRole('alert', { hidden: true })).toHaveClass('MuiAlert-filledError')
}

describe('BoardPage canEdit aus Membership', () => {
  beforeEach(() => vi.clearAllMocks())

  it('leitet Editier-Rechte synchron aus den Memberships ab', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderPage()
    expect(await screen.findByText('B')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Neu anlegen' })).toBeInTheDocument()
    // Hinweis: projectsApi.list() läuft seit #160 für den Projektnamen (useProjectName); die
    // Rolle selbst kommt weiterhin synchron aus den Memberships (Anlege-Aktion sofort sichtbar).
  })

  it('blendet für VIEWER-Membership die Anlege-Aktion aus', async () => {
    memberships = [{ projectId: 9, role: 'VIEWER' }]
    renderPage()
    expect(await screen.findByText('B')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Neu anlegen' })).not.toBeInTheDocument()
  })

  it('benennt das Board über das Edit-Icon um (mit Bearbeiten-Recht)', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.rename.mockResolvedValue({
      id: 1, projectId: 9, name: 'Neu', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    renderPage()

    fireEvent.click(await screen.findByLabelText('Board umbenennen'))
    fireEvent.change(screen.getByLabelText('Neuer Board-Name'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(mockedBoards.rename).toHaveBeenCalledWith(1, 'Neu'))
    expect(await screen.findByText('Neu')).toBeInTheDocument()
  })

  it('zeigt die Server-Meldung, wenn das Umbenennen scheitert', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.rename.mockRejectedValue(serverfehler('Ein Board mit diesem Namen existiert bereits.'))
    renderPage()

    fireEvent.click(await screen.findByLabelText('Board umbenennen'))
    fireEvent.change(screen.getByLabelText('Neuer Board-Name'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    // Ein Namenskonflikt blieb bisher stumm (Issue #812).
    await erwarteFehlerToast('Ein Board mit diesem Namen existiert bereits.')
    // Der Dialog schließt nur bei Erfolg — sonst wäre die Eingabe verloren.
    expect(screen.getByLabelText('Neuer Board-Name')).toBeInTheDocument()
  })

  it('fällt beim Umbenennen ohne Server-Meldung auf den eigenen Text zurück', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.rename.mockRejectedValue(new TypeError('Failed to fetch'))
    renderPage()

    fireEvent.click(await screen.findByLabelText('Board umbenennen'))
    fireEvent.change(screen.getByLabelText('Neuer Board-Name'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await erwarteFehlerToast('Umbenennen fehlgeschlagen.')
  })

  it('blendet das Board-Umbenennen für VIEWER aus', async () => {
    memberships = [{ projectId: 9, role: 'VIEWER' }]
    renderPage()
    expect(await screen.findByText('B')).toBeInTheDocument()
    expect(screen.queryByLabelText('Board umbenennen')).not.toBeInTheDocument()
  })

  it('blendet bei ausgeschaltetem Editiermodus Umbenennen und Labels aus, behält aber den Papierkorb', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    editMode.value = false
    renderPage()
    expect(await screen.findByText('B')).toBeInTheDocument()
    // Struktur-/Verwaltungs-Bleistifte verschwinden ...
    expect(screen.queryByLabelText('Board umbenennen')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Labels' })).not.toBeInTheDocument()
    // ... Papierkorb-Zugang und Karten-Alltag bleiben.
    expect(screen.getByRole('button', { name: 'Papierkorb' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Neu anlegen' })).toBeInTheDocument()
  })

  it('zeigt bei ungültiger Board-ID einen Fehler und ruft keine API auf', async () => {
    memberships = []
    render(
      <MemoryRouter initialEntries={['/boards/abc']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Ungültige Board-ID.')).toBeInTheDocument()
    expect(mockedBoards.get).not.toHaveBeenCalled()
    expect(mockedCards.list).not.toHaveBeenCalled()
    expect(mockedEpics.list).not.toHaveBeenCalled()
  })
})

describe('BoardPage 404-Handling und Refetch', () => {
  beforeEach(() => vi.clearAllMocks())

  function renderWith(entry: string, targets: React.ReactNode) {
    mockedCards.list.mockResolvedValue([])
    mockedEpics.list.mockResolvedValue([])
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    mockedProjects.list.mockResolvedValue([])
    return render(
      <SnackbarProvider>
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path="/boards/:boardId" element={<BoardPage />} />
            {targets}
          </Routes>
        </MemoryRouter>
      </SnackbarProvider>,
    )
  }

  it('leitet bei 404 auf die Projektübersicht um und zeigt einen Hinweis', async () => {
    memberships = []
    mockedBoards.get.mockRejectedValue(new ApiError(404, 'weg'))
    renderWith('/boards/1', <Route path="/" element={<div>Projektübersicht</div>} />)

    expect(await screen.findByText('Projektübersicht')).toBeInTheDocument()
    expect(
      await screen.findByText('Dieses Board wurde archiviert oder gelöscht.'),
    ).toBeInTheDocument()
  })

  it('leitet bei erneutem Fokus auf die Board-Liste um, wenn das Board verschwunden ist', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.get.mockResolvedValueOnce({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    renderWith('/boards/1', <Route path="/projects/:projectId" element={<div>Board-Liste</div>} />)

    expect(await screen.findByText('B')).toBeInTheDocument()

    // Board ist in einer anderen Session verschwunden: nächster Load liefert 404.
    mockedBoards.get.mockRejectedValue(new ApiError(404, 'weg'))
    act(() => window.dispatchEvent(new Event('focus')))

    expect(await screen.findByText('Board-Liste')).toBeInTheDocument()
  })

  it('ignoriert einen zweiten Board-verschwunden-Trigger (goneRef bereits gesetzt)', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.get.mockResolvedValueOnce({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    renderWith('/boards/1', <Route path="/projects/:projectId" element={<div>Board-Liste</div>} />)
    expect(await screen.findByText('B')).toBeInTheDocument()

    // Zwei Fokus-Events feuern gleichzeitig: beide Loads laufen los, bevor der erste 404 navigiert.
    // Der zweite Trigger trifft goneRef.current === true und kehrt früh zurück.
    mockedBoards.get.mockRejectedValue(new ApiError(404, 'weg'))
    act(() => {
      window.dispatchEvent(new Event('focus'))
      window.dispatchEvent(new Event('focus'))
    })

    expect(await screen.findByText('Board-Liste')).toBeInTheDocument()
  })

  it('zeigt „Board nicht gefunden.“ bei einem anderen Ladefehler als 404', async () => {
    memberships = []
    mockedBoards.get.mockRejectedValue(new Error('Netzwerkfehler'))
    renderWith('/boards/1', <Route path="/" element={<div>Projektübersicht</div>} />)

    expect(await screen.findByText('Board nicht gefunden.')).toBeInTheDocument()
  })
})

describe('BoardPage weitere Orchestrierung', () => {
  beforeEach(() => vi.clearAllMocks())

  it('lädt die Rolle nach, wenn sie nicht in den Memberships steht', async () => {
    memberships = []
    mockedBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mockedCards.list.mockResolvedValue([])
    mockedEpics.list.mockResolvedValue([])
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    // VIEWER darf keine Karten anlegen — Rolle kommt hier ausschließlich über den Fallback-Fetch.
    mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'VIEWER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('B')).toBeInTheDocument()
    await waitFor(() => expect(mockedProjects.list).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: 'Neu anlegen' })).not.toBeInTheDocument()
  })

  it('nutzt VIEWER als Fallback, wenn das Projekt nicht in der Liste steht', async () => {
    memberships = []
    mockedBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mockedCards.list.mockResolvedValue([])
    mockedEpics.list.mockResolvedValue([])
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    // Projekt 9 (Board-Projekt) fehlt in der Liste → find() undefined → Fallback 'VIEWER'.
    mockedProjects.list.mockResolvedValue([{ id: 999, name: 'Anderes', role: 'OWNER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('B')).toBeInTheDocument()
    await waitFor(() => expect(mockedProjects.list).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: 'Neu anlegen' })).not.toBeInTheDocument()
  })

  it('legt ein Epic über BoardView an und lädt die Epics neu', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderPage()
    const epicsApiMock = epicsApi as unknown as { create: ReturnType<typeof vi.fn> }
    epicsApiMock.create.mockResolvedValue({
      id: 20, number: 1, title: 'Auth', description: '', shortcode: 'AUT', done: 0, total: 0,
    })
    expect(await screen.findByRole('button', { name: 'Neu anlegen' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Neu anlegen' }))
    fireEvent.change(screen.getByLabelText('Typ'), { target: { value: 'EPIC' } })
    fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: 'AUT' } })
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Auth' } })
    mockedEpics.list.mockClear()
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(epicsApiMock.create).toHaveBeenCalled())
    await waitFor(() => expect(mockedEpics.list).toHaveBeenCalled())
    // reloadEpics stößt ein nachgelagertes setEpics an (list().then(setEpics)); die Microtask
    // deterministisch settlen lassen, sonst kann v8 die reloadEpics-Branch unter CI-Last
    // gelegentlich verfehlen (Coverage-Flake) und React eine act-Warnung emittieren.
    await act(async () => { await Promise.resolve() })
  })

  it('legt ein Label über den Label-Manager an und lädt Labels und Karten neu', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderPage()
    mockedLabels.create.mockResolvedValue({ id: 5, boardId: 1, name: 'Bug', color: '#1976d2', countOnEpicTile: false })
    expect(await screen.findByRole('button', { name: 'Neu anlegen' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))
    fireEvent.change(await screen.findByLabelText('Neues Label'), { target: { value: 'Bug' } })
    mockedLabels.list.mockClear()
    mockedCards.list.mockClear()
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(mockedLabels.create).toHaveBeenCalledWith(1, 'Bug', expect.any(String)))
    await waitFor(() => expect(mockedLabels.list).toHaveBeenCalled())
    expect(mockedCards.list).toHaveBeenCalled()
    // reloadLabels + reloadCards stoßen nachgelagerte setState-Microtasks an; deterministisch
    // settlen lassen (siehe Kommentar beim Epic-Test).
    await act(async () => { await Promise.resolve() })
  })

  it('öffnet eine Karte per Klick über die echte BoardView und lädt nach dem Speichern Karten und Epics neu', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    const openCard = {
      id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null,
      positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
      type: 'CARD' as const, parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
    }
    mockedBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mockedCards.list.mockResolvedValue([openCard])
    mockedCards.update.mockResolvedValue(openCard)
    mockedEpics.list.mockResolvedValue([])
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'OWNER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByTestId('card-100'))
    fireEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }))
    mockedCards.list.mockClear()
    mockedEpics.list.mockClear()
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(mockedCards.update).toHaveBeenCalled())
    await waitFor(() => expect(mockedCards.list).toHaveBeenCalled())
    expect(mockedEpics.list).toHaveBeenCalled()
    // onChanged ruft reloadCards + reloadEpics; deren nachgelagerte setState-Microtasks
    // deterministisch settlen lassen (siehe Kommentar beim Epic-Test).
    await act(async () => { await Promise.resolve() })
  })

  it('öffnet über das Kürzel auf der Karte das Vorhaben im Detail-Dialog, nicht die Karte', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    // Karte ohne parentId, die allein über die Herkunft zum Vorhaben gehört (memberNumbers).
    const memberCard = {
      id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null,
      positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
      type: 'CARD' as const, parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
      derivedFrom: null,
    }
    mockedBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mockedCards.list.mockResolvedValue([memberCard])
    mockedEpics.list.mockResolvedValue([{
      id: 20, number: 2, title: 'Auth-Vorhaben', description: null, shortcode: 'AUT',
      done: 0, total: 1, memberNumbers: [1], rootNumbers: [], requirementCardNumber: null,
    }])
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'OWNER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Vorhaben AUT öffnen' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Auth-Vorhaben')).toBeInTheDocument()
    // Der Klick auf das Kürzel öffnet ausschließlich das Vorhaben, nicht zusätzlich die Karte.
    expect(within(dialog).queryByText('Aufgabe')).not.toBeInTheDocument()
    // Der Dialog lädt Kommentare/Anhänge nach; die Microtasks deterministisch settlen lassen.
    await act(async () => { await Promise.resolve() })
  })

  it('öffnet und schließt den Papierkorb-Dialog', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderPage()
    expect(await screen.findByRole('button', { name: 'Papierkorb' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Papierkorb' }))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('öffnet und schließt den Label-Manager', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderPage()
    expect(await screen.findByRole('button', { name: 'Labels' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))
    expect(await screen.findByLabelText('Neues Label')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('schließt das Karten-Detail wieder', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    const openCard = {
      id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null,
      positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
      type: 'CARD' as const, parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
    }
    mockedBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mockedCards.list.mockResolvedValue([openCard])
    mockedEpics.list.mockResolvedValue([])
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'OWNER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByTestId('card-100'))
    fireEvent.click(await screen.findByRole('button', { name: 'Schließen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('bricht das Umbenennen über Abbrechen ab', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderPage()
    expect(await screen.findByLabelText('Board umbenennen')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Board umbenennen'))
    fireEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(mockedBoards.rename).not.toHaveBeenCalled()
  })

  it('schließt den Umbenennen-Dialog per Escape', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderPage()
    expect(await screen.findByLabelText('Board umbenennen')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Board umbenennen'))
    fireEvent.keyDown(await screen.findByRole('dialog'), { key: 'Escape', code: 'Escape' })

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(mockedBoards.rename).not.toHaveBeenCalled()
  })

  it('öffnet eine Karte im Edit-Modus über das ⋮-Menü der BoardView', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    const openCard = {
      id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null,
      positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
      type: 'CARD' as const, parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
    }
    mockedBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mockedCards.list.mockResolvedValue([openCard])
    mockedEpics.list.mockResolvedValue([])
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'OWNER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Bearbeiten' }))

    expect(await screen.findByLabelText('Titel')).toBeInTheDocument()
  })

  it('behandelt einen fehlenden Board-Parameter als ungültig (boardId undefined)', () => {
    render(
      <MemoryRouter initialEntries={['/x']}>
        <Routes>
          <Route path="/x" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByText('Ungültige Board-ID.')).toBeInTheDocument()
  })
})

// Die drei Berechtigungspfade, die BoardPage selbst aus dem rohen effectiveRole bzw. dem
// Plattform-Admin-Status bildet — sie liegen außerhalb von canEdit/canModerate und wären bei der
// Zentralisierung der Rollenauflösung (Issue #581) sonst ungedeckt gebrochen.
describe('BoardPage Berechtigungen aus Rolle und Plattform-Admin', () => {
  beforeEach(() => vi.clearAllMocks())

  const trashedCard = {
    id: 200, boardId: 1, columnId: 10, number: 2, title: 'Weg', description: null,
    positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
    type: 'CARD' as const, parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
  }
  const openCard = { ...trashedCard, id: 100, number: 1, title: 'Aufgabe' }
  const mockedTrash = cardsApi as unknown as { listTrash: ReturnType<typeof vi.fn> }

  function renderBoardWithCard(projects: { id: number; name: string; role: string; createdAt: string }[]) {
    mockedBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mockedBoards.list.mockResolvedValue([])
    mockedCards.list.mockResolvedValue([openCard])
    mockedTrash.listTrash.mockResolvedValue([trashedCard])
    mockedEpics.list.mockResolvedValue([])
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    mockedProjects.list.mockResolvedValue(projects)
    return render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )
  }

  const ownProject = { id: 9, name: 'P', role: 'OWNER', createdAt: '' }
  const foreignProject = { id: 77, name: 'Fremd', role: 'VIEWER', createdAt: '' }

  it('bietet OWNER das Verschieben auf ein anderes Board an (canTransfer)', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderBoardWithCard([ownProject])

    fireEvent.click(await screen.findByLabelText('Menü Aufgabe'))
    expect(
      screen.getByRole('menuitem', { name: 'Auf anderes Board verschieben…' }),
    ).toBeInTheDocument()
  })

  it('verbirgt das Verschieben für MEMBER (canTransfer)', async () => {
    memberships = [{ projectId: 9, role: 'MEMBER' }]
    renderBoardWithCard([ownProject])

    fireEvent.click(await screen.findByLabelText('Menü Aufgabe'))
    expect(
      screen.queryByRole('menuitem', { name: 'Auf anderes Board verschieben…' }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Bearbeiten' })).toBeInTheDocument()
  })

  it('erlaubt OWNER das endgültige Löschen im Papierkorb (canPurge)', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderBoardWithCard([ownProject])

    fireEvent.click(await screen.findByRole('button', { name: 'Papierkorb' }))
    expect(await screen.findByLabelText('Weg endgültig löschen')).toBeInTheDocument()
  })

  it('verwehrt MEMBER das endgültige Löschen im Papierkorb (canPurge)', async () => {
    memberships = [{ projectId: 9, role: 'MEMBER' }]
    renderBoardWithCard([ownProject])

    fireEvent.click(await screen.findByRole('button', { name: 'Papierkorb' }))
    expect(await screen.findByLabelText('Weg wiederherstellen')).toBeInTheDocument()
    expect(screen.queryByLabelText('Weg endgültig löschen')).not.toBeInTheDocument()
  })

  it('reicht den Plattform-Admin an BoardView weiter: alle Projekte als Verschiebeziel (platformAdmin)', async () => {
    // Rolle kommt hier über den Fallback-Fetch (keine Mitgliedschaft) — der Admin darf trotzdem
    // verschieben, und der Zieldialog zeigt ihm auch Projekte ohne OWNER-Rolle.
    memberships = []
    platformRole = 'ADMIN'
    renderBoardWithCard([{ ...ownProject, role: 'VIEWER' }, foreignProject])

    fireEvent.click(await screen.findByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Auf anderes Board verschieben…' }))

    const target = await screen.findByLabelText('Zielprojekt')
    await waitFor(() => expect(target).toHaveTextContent('Fremd'))
    expect(target).toHaveTextContent('P')
  })

  it('zeigt einem OWNER ohne Plattform-Admin nur eigene OWNER-Projekte als Ziel (platformAdmin)', async () => {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    renderBoardWithCard([ownProject, foreignProject])

    fireEvent.click(await screen.findByLabelText('Menü Aufgabe'))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Auf anderes Board verschieben…' }))

    const target = await screen.findByLabelText('Zielprojekt')
    await waitFor(() => expect(target).toHaveTextContent('P'))
    expect(target).not.toHaveTextContent('Fremd')
  })
})

// Statuswechsel über den interaktiven Chip des Detail-Modals (Issue #752): BoardPage ist der
// einzige Integrationspunkt — sie berechnet die Zielposition, schreibt optimistisch und rollt
// jeden Fehlschlag zurück.
describe('BoardPage Statuswechsel aus dem Detail-Modal', () => {
  beforeEach(() => vi.clearAllMocks())

  const spalten = [
    { id: 10, name: 'Backlog', position: 0, wipLimit: null },
    { id: 11, name: 'Ready', position: 1, wipLimit: null },
    { id: 12, name: 'In Progress', position: 2, wipLimit: null },
  ]
  const offeneKarte = {
    id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null,
    positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
    type: 'CARD' as const, parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
  }
  // Zwei Karten liegen bereits in „In Progress" — die verschobene Karte gehört also an Position 2.
  const belegt1 = { ...offeneKarte, id: 101, number: 2, title: 'Läuft', columnId: 12, positionInColumn: 0 }
  const belegt2 = { ...offeneKarte, id: 102, number: 3, title: 'Läuft auch', columnId: 12, positionInColumn: 1 }

  const mockedMove = cardsApi as unknown as { move: ReturnType<typeof vi.fn> }

  function renderStatus(karten: (typeof offeneKarte)[]) {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.get.mockResolvedValue({ id: 1, projectId: 9, name: 'B', createdAt: '', columns: spalten })
    mockedCards.list.mockResolvedValue(karten)
    mockedEpics.list.mockResolvedValue([])
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'OWNER', createdAt: '' }])
    return render(
      <SnackbarProvider>
        <MemoryRouter initialEntries={['/boards/1']}>
          <Routes>
            <Route path="/boards/:boardId" element={<BoardPage />} />
          </Routes>
        </MemoryRouter>
      </SnackbarProvider>,
    )
  }

  /** Öffnet die Karte im Detail-Modal und wählt im Status-Chip die Zielspalte. */
  async function waehleZustand(ziel: string) {
    fireEvent.click(await screen.findByTestId('card-100'))
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: 'Zustand' }))
    fireEvent.click(await screen.findByRole('option', { name: ziel }))
  }

  it('verschiebt ans Ende der Zielspalte und aktualisiert Board und Modal optimistisch', async () => {
    mockedMove.move.mockResolvedValue(belegt1)
    renderStatus([offeneKarte, belegt1, belegt2])

    await waehleZustand('In Progress')

    await waitFor(() => expect(mockedMove.move).toHaveBeenCalledWith(100, 12, 2))
    // Modal (selectedCard) und Board (cards) ziehen ohne Serverantwort nach.
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Zustand' })).toHaveTextContent('In Progress'),
    )
    expect(within(screen.getByTestId('column-12')).getByTestId('card-100')).toBeInTheDocument()
  })

  it('verschiebt nicht, wenn die Zielspalte inzwischen die aktuelle Spalte ist', async () => {
    // Race: Die Rückfrage vor „Ready" steht offen, während ein Refetch die Karte bereits in Ready
    // zeigt (jemand anders hat sie dorthin gezogen). Der Guard verhindert den Zug ins Leere.
    renderStatus([offeneKarte])

    await waehleZustand('Ready')
    expect(await screen.findByText('Nach Ready verschieben?')).toBeInTheDocument()

    mockedCards.list.mockResolvedValue([{ ...offeneKarte, columnId: 11 }])
    act(() => window.dispatchEvent(new Event('focus')))
    await waitFor(() =>
      expect(within(screen.getByTestId('column-11')).getByTestId('card-100')).toBeInTheDocument(),
    )
    // Erneuter Klick auf die Karte zieht den geöffneten Kartenstand nach (Modal bleibt montiert).
    // Die offene Rückfrage macht den Rest der Seite aria-hidden — das Auswahlfeld ist deshalb hier
    // nur noch über sein Label erreichbar, nicht mehr über die Rolle.
    fireEvent.click(screen.getByTestId('card-100'))
    await waitFor(() => expect(screen.getByLabelText('Zustand')).toHaveTextContent('Ready'))

    fireEvent.click(screen.getByRole('button', { name: 'Nach Ready verschieben' }))

    await waitFor(() => expect(screen.queryByText('Nach Ready verschieben?')).toBeNull())
    expect(mockedMove.move).not.toHaveBeenCalled()
  })

  it('rollt bei einem Konflikt zurück, meldet ihn und übernimmt den frischen Serverstand', async () => {
    mockedMove.move.mockRejectedValue(new ApiError(409, 'Karte wurde zwischenzeitlich verschoben.'))
    renderStatus([offeneKarte, belegt1, belegt2])

    fireEvent.click(await screen.findByTestId('card-100'))
    mockedCards.list.mockClear()
    mockedCards.list.mockResolvedValue([{ ...offeneKarte, columnId: 11 }, belegt1, belegt2])
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: 'Zustand' }))
    fireEvent.click(await screen.findByRole('option', { name: 'In Progress' }))

    expect(await screen.findByText('Karte wurde zwischenzeitlich verschoben.')).toBeInTheDocument()
    // Genau ein Nachladen — der Serverstand kommt aus dieser einen frischen Liste.
    await waitFor(() => expect(mockedCards.list).toHaveBeenCalledTimes(1))
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Zustand' })).toHaveTextContent('Ready'),
    )
    expect(within(screen.getByTestId('column-11')).getByTestId('card-100')).toBeInTheDocument()
  })

  it('schließt das Detail, wenn die Karte nach dem Konflikt nicht mehr in der frischen Liste steht', async () => {
    mockedMove.move.mockRejectedValue(new ApiError(409, 'Karte ist weg.'))
    renderStatus([offeneKarte, belegt1, belegt2])

    fireEvent.click(await screen.findByTestId('card-100'))
    mockedCards.list.mockResolvedValue([belegt1, belegt2])
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: 'Zustand' }))
    fireEvent.click(await screen.findByRole('option', { name: 'In Progress' }))

    expect(await screen.findByText('Karte ist weg.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('meldet ein gescheitertes Nachladen nach dem Konflikt und lässt den zurückgerollten Stand stehen', async () => {
    mockedMove.move.mockRejectedValue(new ApiError(409, 'Konflikt.'))
    renderStatus([offeneKarte, belegt1, belegt2])

    fireEvent.click(await screen.findByTestId('card-100'))
    mockedCards.list.mockRejectedValue(new Error('offline'))
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: 'Zustand' }))
    fireEvent.click(await screen.findByRole('option', { name: 'In Progress' }))

    expect(await screen.findByText('Neu laden fehlgeschlagen.')).toBeInTheDocument()
    // Zurückgerollter Stand: Karte wieder in Backlog, Modal offen.
    expect(screen.getByRole('combobox', { name: 'Zustand' })).toHaveTextContent('Backlog')
    expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
  })

  it('rollt bei einem Serverfehler zurück und lädt die Karten nicht erneut', async () => {
    mockedMove.move.mockRejectedValue(new ApiError(500, 'kaputt'))
    renderStatus([offeneKarte, belegt1, belegt2])

    fireEvent.click(await screen.findByTestId('card-100'))
    mockedCards.list.mockClear()
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: 'Zustand' }))
    fireEvent.click(await screen.findByRole('option', { name: 'In Progress' }))

    expect(await screen.findByText('Statuswechsel fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Zustand' })).toHaveTextContent('Backlog')
    expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument()
    expect(mockedCards.list).not.toHaveBeenCalled()
  })

  it('rollt auch bei einem Wurf ohne ApiError zurück', async () => {
    mockedMove.move.mockRejectedValue(new Error('Netzwerk weg'))
    renderStatus([offeneKarte, belegt1, belegt2])

    fireEvent.click(await screen.findByTestId('card-100'))
    mockedCards.list.mockClear()
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: 'Zustand' }))
    fireEvent.click(await screen.findByRole('option', { name: 'In Progress' }))

    expect(await screen.findByText('Statuswechsel fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Zustand' })).toHaveTextContent('Backlog')
    expect(mockedCards.list).not.toHaveBeenCalled()
  })
})

// Ausgeblendete Vorhaben (Plan #717, A3): Der Zustand liegt seit diesem Paket in BoardPage — das
// Board bekommt ihn als Prop. Deshalb belegen diese Tests ihn hier und nicht mehr an `BoardView`.
describe('BoardPage ausgeblendete Vorhaben', () => {
  beforeEach(() => vi.clearAllMocks())

  const hiddenKey = (boardId: number) => `manban.boardHiddenEpics.${boardId}`
  const filterKey = (boardId: number) => `manban.boardEpicFilter.${boardId}`

  const mkEpic = (id: number, titel: string, memberNumbers: number[]) => ({
    id, number: id, title: titel, description: null, shortcode: titel.slice(0, 3).toUpperCase(),
    done: 0, total: memberNumbers.length, memberNumbers, rootNumbers: memberNumbers,
    requirementCardNumber: null,
  })
  const mkKarte = (id: number, nummer: number, parentId: number | null) => ({
    id, boardId: 1, columnId: 10, number: nummer, title: `Karte ${nummer}`, description: null,
    positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null, dependencies: [],
    type: 'CARD' as const, parentId, shortcode: null, assignees: [], dueDate: null, labels: [],
  })

  /**
   * localStorage-Stub über eine echte Map — vorbelegbar und nach dem Test wieder auslesbar. Nötig
   * statt des nativen `localStorage`: Unter Node 26 ist es deaktiviert (siehe `src/test/setup.ts`),
   * ein Test gegen das globale Objekt wäre „grün lokal, rot in CI".
   */
  const stubStore = (entries: [string, string][]) => {
    const store = new Map<string, string>(entries)
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => { store.set(k, v) },
      removeItem: (k: string) => { store.delete(k) },
      clear: () => store.clear(), key: () => null, length: 0,
    })
    return store
  }

  // `unstubAllGlobals` nimmt auch den EventSource-Stub aus `src/test/setup.ts` mit — ohne ihn
  // scheiterte der jeweils nächste Test am SSE-Stream von `useBoardEvents`. Deshalb wird er nach
  // dem Aufräumen wieder gesetzt.
  const eventSourceStub = globalThis.EventSource
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.stubGlobal('EventSource', eventSourceStub)
  })

  function renderBoard(epics: ReturnType<typeof mkEpic>[], karten: ReturnType<typeof mkKarte>[]) {
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.get.mockResolvedValue({
      id: 1, projectId: 9, name: 'B', createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    })
    mockedCards.list.mockResolvedValue(karten)
    mockedEpics.list.mockResolvedValue(epics)
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'OWNER', createdAt: '' }])
    return render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )
  }

  it('liest die ausgeblendeten Vorhaben beim Mount aus localStorage', async () => {
    stubStore([[hiddenKey(1), JSON.stringify([9])]])
    renderBoard([mkEpic(9, 'Auth', [1])], [mkKarte(100, 1, 9), mkKarte(300, 4, null)])

    expect(await screen.findByTestId('card-300')).toBeInTheDocument()
    expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()
  })

  it('vergisst beim Einblenden beide gespeicherten Schlüssel', async () => {
    // Zweites, eingeblendetes Vorhaben nötig (Issue #785): Der Filter bietet seither nur noch
    // eingeblendete Vorhaben an — mit nur dem ausgeblendeten Auth existierte kein Dropdown mehr,
    // in das sich ein Wert wählen ließe.
    const store = stubStore([[hiddenKey(1), JSON.stringify([9])]])
    renderBoard(
      [mkEpic(9, 'Auth', [1]), mkEpic(8, 'Suche', [2])],
      [mkKarte(100, 1, 9), mkKarte(200, 2, 8), mkKarte(300, 4, null)],
    )

    expect(await screen.findByTestId('card-300')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Vorhaben-Filter'), { target: { value: '8' } })
    fireEvent.click(screen.getAllByRole('button', { name: /ausgeblendet/ })[0])

    expect(store.has(hiddenKey(1))).toBe(false)
    expect(store.has(filterKey(1))).toBe(false)
    // Und die Karte ist wieder da — das Löschen ist kein Ersatz für das Aufheben im Zustand.
    expect(screen.getByTestId('card-100')).toBeInTheDocument()
  })

  it('liest den Stand beim Board-Wechsel ohne Remount neu', async () => {
    // Die Route hält BoardPage bei einem reinen Parameterwechsel gemountet — der useState-
    // Initializer läuft dann nicht erneut. Ohne das Nachlesen filterte Board 2 mit dem Stand von
    // Board 1: seine eigene Ausblendung griffe nicht, die fremde weiter.
    stubStore([
      [hiddenKey(1), JSON.stringify([9])],
      [hiddenKey(2), JSON.stringify([8])],
    ])
    memberships = [{ projectId: 9, role: 'OWNER' }]
    mockedBoards.get.mockImplementation((id: number) => Promise.resolve({
      id, projectId: 9, name: `Board ${id}`, createdAt: '',
      columns: [{ id: 10, name: 'Backlog', position: 0, wipLimit: null }],
    }))
    mockedCards.list.mockImplementation((id: number) => Promise.resolve(
      id === 1 ? [mkKarte(100, 1, 9), mkKarte(300, 4, null)] : [mkKarte(200, 3, 8), mkKarte(400, 5, null)],
    ))
    mockedEpics.list.mockImplementation((id: number) => Promise.resolve(
      id === 1 ? [mkEpic(9, 'Auth', [1])] : [mkEpic(8, 'Suche', [3])],
    ))
    mockedLabels.list.mockResolvedValue([])
    mockedMembers.list.mockResolvedValue([])
    mockedConfig.get.mockResolvedValue({ doneRetentionDays: 30 })
    mockedProjects.list.mockResolvedValue([{ id: 9, name: 'P', role: 'OWNER', createdAt: '' }])
    render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <Link to="/boards/2">Zu Board 2</Link>
        <Routes>
          <Route path="/boards/:boardId" element={<BoardPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByTestId('card-300')).toBeInTheDocument()
    expect(screen.queryByTestId('card-100')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('link', { name: 'Zu Board 2' }))

    expect(await screen.findByTestId('card-400')).toBeInTheDocument()
    // Board 2 blendet sein eigenes Vorhaben aus …
    expect(screen.queryByTestId('card-200')).not.toBeInTheDocument()
    // … und nicht mehr das von Board 1: dessen Vorhaben gibt es hier gar nicht.
    expect(screen.getByText('Board 2')).toBeInTheDocument()
  })
})
