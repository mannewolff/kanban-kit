import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { cssRegel } from '../test/cssRegel'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi, type Board } from '../api/boards'
import { ApiError } from '../api/client'
import { projectsApi } from '../api/projects'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { ProjectBoardsPage } from './ProjectBoardsPage'
import { renderMitSprache } from '../i18n/renderMitSprache'

/** Server-Ablehnung mit einer für den Nutzer formulierten Meldung in `detail` (RFC 9457). */
const serverfehler = (text: string) => new ApiError(409, 'Conflict', undefined, text)

/**
 * Prüft den Fehler-Toast: Der Text steht dort, und der Alert trägt die Severity `error`.
 *
 * `hidden: true`: Ein offener MUI-Dialog stellt alles außerhalb seines Portals auf `aria-hidden` —
 * der Toast trägt seine Rolle, wird von der Standardabfrage aber übergangen.
 */
async function erwarteFehlerToast(text: string) {
  expect(await screen.findByText(text)).toBeInTheDocument()
  expect(await screen.findByRole('alert', { hidden: true })).toHaveClass('MuiAlert-filledError')
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

function makeBoards(names: string[], projectId: number): Board[] {
  return names.map((name, i) => ({ id: 100 + i, name, projectId, createdAt: '', columns: [] }))
}

vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn() } }))
// Vorschau-Zustand der „Ansicht als XY“ (Plan #1914, E15); außer im eigenen Block ohne Vorschau.
const auth = vi.hoisted(() => ({ vorschau: null as null | Record<string, string> }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ vorschau: auth.vorschau }) }))
const vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }
beforeEach(() => {
  auth.vorschau = null
})
vi.mock('../api/boards', () => ({
  boardsApi: {
    list: vi.fn(),
    listArchived: vi.fn(),
    create: vi.fn(),
    remove: vi.fn(),
    restore: vi.fn(),
    purge: vi.fn(),
  },
}))

const mockedProjects = projectsApi as unknown as { list: ReturnType<typeof vi.fn> }
const mockedBoards = boardsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  listArchived: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
  restore: ReturnType<typeof vi.fn>
  purge: ReturnType<typeof vi.fn>
}

function renderAt(role: string, boards: Array<{ id: number; name: string }>) {
  mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role, createdAt: '' }])
  mockedBoards.list.mockResolvedValue(
    boards.map((b) => ({ ...b, projectId: 5, createdAt: '', columns: [] })),
  )
  mockedBoards.listArchived.mockResolvedValue([])
  return render(
    <SnackbarProvider>
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>
    </SnackbarProvider>,
  )
}

function renderPage(role: string) {
  mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role, createdAt: '2026-01-01T00:00:00Z' }])
  mockedBoards.list.mockResolvedValue([])
  mockedBoards.listArchived.mockResolvedValue([])
  return render(
    <SnackbarProvider>
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>
    </SnackbarProvider>,
  )
}

/** Zwei aktive Boards (kein Auto-Routing) plus ein archiviertes — Grundlage der Archiv-Aktionen. */
function renderMitArchiv() {
  mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
  mockedBoards.list.mockResolvedValue([
    { id: 9, name: 'Aktiv', projectId: 5, createdAt: '', columns: [] },
    { id: 10, name: 'Aktiv2', projectId: 5, createdAt: '', columns: [] },
  ])
  mockedBoards.listArchived.mockResolvedValue([
    { id: 20, name: 'Altes Board', projectId: 5, createdAt: '', columns: [] },
  ])
  return render(
    <SnackbarProvider>
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>
    </SnackbarProvider>,
  )
}

describe('ProjectBoardsPage Zahlen (#960)', () => {
  it('setzt die Spaltenzahl der Boards in Tabellenziffern — sie stehen untereinander', async () => {
    renderAt('OWNER', [{ id: 1, name: 'Eins' }, { id: 2, name: 'Zwei' }])

    const zahlen = await screen.findAllByText('0 Spalten')
    expect(zahlen).toHaveLength(2)
    expect(cssRegel(zahlen[0])).toContain('font-variant-numeric: tabular-nums')
  })
})

describe('ProjectBoardsPage RBAC', () => {
  beforeEach(() => vi.clearAllMocks())

  it('zeigt Angemeldeten mit OWNER-Rolle den Board-Anlegen-Bereich', async () => {
    renderPage('OWNER')
    expect(await screen.findByText('Team')).toBeInTheDocument()
    expect(await screen.findByLabelText(/Neues Board/)).toBeInTheDocument()
  })

  it('blendet für VIEWER die Bearbeiten-Aktionen aus', async () => {
    renderPage('VIEWER')
    expect(await screen.findByText('Team')).toBeInTheDocument()
    expect(screen.queryByLabelText(/Neues Board/)).not.toBeInTheDocument()
  })

  it('archiviert ein Board erst nach Bestätigung (OWNER)', async () => {
    // Zwei Boards, damit kein Auto-Routing greift.
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    mockedBoards.remove.mockResolvedValue(undefined)

    fireEvent.click(await screen.findByLabelText('Board Board A archivieren'))
    expect(await screen.findByText('Board archivieren?')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    await waitFor(() => expect(mockedBoards.remove).toHaveBeenCalledWith(9))
  })

  it('blendet den Board-Archivieren-Button für VIEWER aus', async () => {
    renderAt('VIEWER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    expect(await screen.findByText('Board A')).toBeInTheDocument()
    expect(screen.queryByLabelText('Board Board A archivieren')).not.toBeInTheDocument()
  })

  it('zeigt archivierte Boards, stellt sie wieder her und löscht sie endgültig nur bei Namensgleichheit', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([
      { id: 9, name: 'Aktiv', projectId: 5, createdAt: '', columns: [] },
      { id: 10, name: 'Aktiv2', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.listArchived.mockResolvedValue([
      { id: 20, name: 'Altes Board', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.restore.mockResolvedValue(undefined)
    mockedBoards.purge.mockResolvedValue(undefined)
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )

    // Archiv-Sektion zeigt das archivierte Board.
    expect(await screen.findByText('Altes Board')).toBeInTheDocument()

    // Endgültig löschen: Button erst bei exakt eingetipptem Namen aktiv.
    fireEvent.click(screen.getByLabelText('Board Altes Board endgültig löschen'))
    const purgeButton = screen.getByRole('button', { name: 'Endgültig löschen' })
    expect(purgeButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Board-Name zur Bestätigung'), {
      target: { value: 'falsch' },
    })
    expect(purgeButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Board-Name zur Bestätigung'), {
      target: { value: 'Altes Board' },
    })
    expect(purgeButton).toBeEnabled()
    fireEvent.click(purgeButton)
    await waitFor(() => expect(mockedBoards.purge).toHaveBeenCalledWith(20))
  })

  it('stellt ein archiviertes Board wieder her', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([
      { id: 9, name: 'Aktiv', projectId: 5, createdAt: '', columns: [] },
      { id: 10, name: 'Aktiv2', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.listArchived.mockResolvedValue([
      { id: 20, name: 'Altes Board', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.restore.mockResolvedValue(undefined)
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Wiederherstellen' }))
    await waitFor(() => expect(mockedBoards.restore).toHaveBeenCalledWith(20))
  })

  it('routet beim Erst-Aufruf mit genau einem Board direkt aufs Board', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([{ id: 9, name: 'Solo', projectId: 5, createdAt: '', columns: [] }])
    mockedBoards.listArchived.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
          <Route path="/boards/:boardId/leitstand" element={<div>Board-Ansicht</div>} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Board-Ansicht')).toBeInTheDocument()
  })

  it('routet mit autoRoute-State und genau einem Board durch, auch ohne default-Key', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([{ id: 9, name: 'Solo', projectId: 5, createdAt: '', columns: [] }])
    mockedBoards.listArchived.mockResolvedValue([])

    function Start() {
      const navigate = useNavigate()
      return <button onClick={() => navigate('/projects/5', { state: { autoRoute: true } })}>los</button>
    }
    render(
      <MemoryRouter initialEntries={['/start']}>
        <Routes>
          <Route path="/start" element={<Start />} />
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
          <Route path="/boards/:boardId/leitstand" element={<div>Board-Ansicht</div>} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByText('los'))
    expect(await screen.findByText('Board-Ansicht')).toBeInTheDocument()
  })

  it('verwirft eine spät auflösende Antwort der alten Projekt-ID nach einem ID-Wechsel', async () => {
    // Zwei Boards je Projekt, damit kein Auto-Routing bei genau einem Board greift.
    const dOld = deferred<Board[]>()
    const dNew = deferred<Board[]>()
    mockedBoards.list.mockReturnValueOnce(dOld.promise).mockReturnValueOnce(dNew.promise)
    mockedBoards.listArchived.mockResolvedValue([])
    mockedProjects.list.mockResolvedValue([
      { id: 5, name: 'Team5', role: 'OWNER', createdAt: '' },
      { id: 6, name: 'Team6', role: 'OWNER', createdAt: '' },
    ])

    function Nav() {
      const navigate = useNavigate()
      return <button onClick={() => navigate('/projects/6')}>wechseln</button>
    }
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Nav />
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )

    // Wechsel auf Projekt 6, bevor die Antwort für Projekt 5 da ist.
    fireEvent.click(screen.getByText('wechseln'))
    dNew.resolve(makeBoards(['NeuesBoard1', 'NeuesBoard2'], 6))
    expect(await screen.findByText('NeuesBoard1')).toBeInTheDocument()

    // Die verspätete Antwort der alten ID darf den State nicht mehr überschreiben.
    dOld.resolve(makeBoards(['AltesBoard1', 'AltesBoard2'], 5))
    expect(await screen.findByText('NeuesBoard1')).toBeInTheDocument()
    expect(screen.queryByText('AltesBoard1')).not.toBeInTheDocument()
  })

  it('zeigt bei ungültiger Projekt-ID einen Fehler und ruft keine API auf', async () => {
    render(
      <MemoryRouter initialEntries={['/projects/abc']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Ungültige Projekt-ID.')).toBeInTheDocument()
    expect(mockedBoards.list).not.toHaveBeenCalled()
    expect(mockedProjects.list).not.toHaveBeenCalled()
  })

  it('lädt beim Fensterfokus nichts nach, wenn die Projekt-ID ungültig ist', async () => {
    render(
      <MemoryRouter initialEntries={['/projects/abc']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText('Ungültige Projekt-ID.')

    fireEvent(window, new Event('focus'))

    expect(mockedBoards.list).not.toHaveBeenCalled()
  })

  it('legt ein neues Board an', async () => {
    mockedBoards.create.mockResolvedValue({ id: 30, name: 'Neu', projectId: 5, createdAt: '', columns: [] })
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')

    fireEvent.change(screen.getByLabelText(/Neues Board/), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(mockedBoards.create).toHaveBeenCalledWith(5, 'Neu'))
  })

  it('legt kein Board ohne Namen an', async () => {
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')

    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(mockedBoards.create).not.toHaveBeenCalled()
  })

  it('lädt Boards, Archiv und Rolle beim Fensterfokus neu', async () => {
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')
    mockedBoards.list.mockClear()
    mockedBoards.listArchived.mockClear()
    mockedProjects.list.mockClear()

    fireEvent(window, new Event('focus'))

    await waitFor(() => expect(mockedBoards.list).toHaveBeenCalled())
    expect(mockedBoards.listArchived).toHaveBeenCalled()
    expect(mockedProjects.list).toHaveBeenCalled()
  })

  it('navigiert per Klick auf ein Board', async () => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role: 'OWNER', createdAt: '' }])
    mockedBoards.list.mockResolvedValue([
      { id: 9, name: 'Board A', projectId: 5, createdAt: '', columns: [] },
      { id: 10, name: 'Board B', projectId: 5, createdAt: '', columns: [] },
    ])
    mockedBoards.listArchived.mockResolvedValue([])
    render(
      <MemoryRouter initialEntries={['/projects/5']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
          <Route path="/boards/:boardId/leitstand" element={<div>Board-Ansicht</div>} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByText('Board A'))
    expect(await screen.findByText('Board-Ansicht')).toBeInTheDocument()
  })

  it('schließt den Archivieren-Dialog per Escape und über Abbrechen', async () => {
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')

    fireEvent.click(screen.getByLabelText('Board Board A archivieren'))
    fireEvent.keyDown(await screen.findByRole('dialog'), { key: 'Escape', code: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    fireEvent.click(screen.getByLabelText('Board Board A archivieren'))
    fireEvent.click(await screen.findByRole('button', { name: 'Abbrechen' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(mockedBoards.remove).not.toHaveBeenCalled()
  })

  it('zeigt die Server-Meldung, wenn das Anlegen eines Boards scheitert', async () => {
    mockedBoards.create.mockRejectedValue(serverfehler('Ein Board mit diesem Namen existiert bereits.'))
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')

    fireEvent.change(screen.getByLabelText(/Neues Board/), { target: { value: 'Board A' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await erwarteFehlerToast('Ein Board mit diesem Namen existiert bereits.')
  })

  it('fällt beim Anlegen ohne Server-Meldung auf den eigenen Text zurück', async () => {
    mockedBoards.create.mockRejectedValue(new TypeError('Failed to fetch'))
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')

    fireEvent.change(screen.getByLabelText(/Neues Board/), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await erwarteFehlerToast('Board anlegen fehlgeschlagen.')
  })

  /**
   * Review-Fund (Code-Review Schritt 7, #807-#812-Batch): Mutation und Nachladen waren im selben
   * try/catch. Scheitert nur das Nachladen, war das angelegte Board trotzdem da — die Meldung darf
   * das nicht als Fehlschlag der Aktion selbst ausgeben.
   */
  it('meldet ein gescheitertes Nachladen getrennt vom Anlegen, wenn das Board angelegt wurde', async () => {
    mockedBoards.create.mockResolvedValue({ id: 99, name: 'Neu', projectId: 5, createdAt: '', columns: [] })
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])
    await screen.findByText('Board A')

    // Nur der naechste list()-Aufruf (das Nachladen nach dem Anlegen) scheitert.
    mockedBoards.list.mockRejectedValueOnce(new Error('boom'))

    fireEvent.change(screen.getByLabelText(/Neues Board/), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await erwarteFehlerToast('Board angelegt, Liste konnte nicht aktualisiert werden.')
    expect(mockedBoards.create).toHaveBeenCalledWith(5, 'Neu')
  })

  it('zeigt die Server-Meldung, wenn das Archivieren scheitert, und lässt den Dialog offen', async () => {
    mockedBoards.remove.mockRejectedValue(serverfehler('Board ist gesperrt.'))
    renderAt('OWNER', [{ id: 9, name: 'Board A' }, { id: 10, name: 'Board B' }])

    fireEvent.click(await screen.findByLabelText('Board Board A archivieren'))
    fireEvent.click(await screen.findByRole('button', { name: 'Archivieren' }))

    await erwarteFehlerToast('Board ist gesperrt.')
    // Der Bestätigungsdialog schließt nur bei Erfolg — sonst sähe der Fehlschlag wie ein Erfolg aus.
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('zeigt die Server-Meldung, wenn das Wiederherstellen scheitert', async () => {
    mockedBoards.restore.mockRejectedValue(serverfehler('Board lässt sich nicht wiederherstellen.'))
    renderMitArchiv()

    fireEvent.click(await screen.findByRole('button', { name: 'Wiederherstellen' }))

    await erwarteFehlerToast('Board lässt sich nicht wiederherstellen.')
  })

  it('zeigt die Server-Meldung, wenn das endgültige Löschen scheitert, und lässt den Dialog offen', async () => {
    mockedBoards.purge.mockRejectedValue(serverfehler('Board hat noch Anhänge.'))
    renderMitArchiv()

    fireEvent.click(await screen.findByLabelText('Board Altes Board endgültig löschen'))
    fireEvent.change(screen.getByLabelText('Board-Name zur Bestätigung'), {
      target: { value: 'Altes Board' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Endgültig löschen' }))

    await erwarteFehlerToast('Board hat noch Anhänge.')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('behandelt einen fehlenden Projekt-Parameter als ungültig (projectId undefined)', () => {
    render(
      <MemoryRouter initialEntries={['/x']}>
        <Routes>
          <Route path="/x" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByText('Ungültige Projekt-ID.')).toBeInTheDocument()
  })

  describe('Board-Name als Link (Issue #1307)', () => {
    function Ziel() {
      return <div>Ziel {useLocation().pathname}</div>
    }
    function renderMitZiel(role: string) {
      mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role, createdAt: '' }])
      // Zwei Boards: Bei genau einem routet die Seite von selbst durch.
      mockedBoards.list.mockResolvedValue([
        { id: 9, name: 'Board A', projectId: 5, createdAt: '', columns: [] },
        { id: 10, name: 'Board B', projectId: 5, createdAt: '', columns: [] },
      ])
      mockedBoards.listArchived.mockResolvedValue([])
      return render(
        <SnackbarProvider>
          <MemoryRouter initialEntries={['/projects/5']}>
            <Routes>
              <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
              <Route path="/boards/:boardId/leitstand" element={<Ziel />} />
            </Routes>
          </MemoryRouter>
        </SnackbarProvider>,
      )
    }

    it('erreicht den Board-Namen per Tab als Link und navigiert mit Enter', async () => {
      const user = userEvent.setup()
      renderMitZiel('OWNER')
      const link = await screen.findByRole('link', { name: 'Board A' })

      screen.getByRole('button', { name: 'Anlegen' }).focus()
      await user.tab()
      expect(link).toHaveFocus()
      await user.keyboard('{Enter}')

      expect(await screen.findByText('Ziel /boards/9/leitstand')).toBeInTheDocument()
    })

    it('trägt das Ziel des Leitstand-Boards als href', async () => {
      renderMitZiel('OWNER')

      expect(await screen.findByRole('link', { name: 'Board A' })).toHaveAttribute('href', '/boards/9/leitstand')
    })

    it('ist auch für einen Nur-Leser erreichbar und wirksam', async () => {
      const user = userEvent.setup()
      renderMitZiel('VIEWER')
      const link = await screen.findByRole('link', { name: 'Board A' })
      await waitFor(() => expect(mockedProjects.list).toHaveBeenCalled())

      screen.getByRole('link', { name: 'Projekte' }).focus()
      await user.tab()
      expect(link).toHaveFocus()
      await user.keyboard('{Enter}')

      expect(await screen.findByText('Ziel /boards/9/leitstand')).toBeInTheDocument()
    })

    it('navigiert weiterhin per Klick auf die Fläche neben dem Namen', async () => {
      renderMitZiel('OWNER')
      await screen.findByRole('link', { name: 'Board A' })

      // Die Kachelfläche (Spaltenzahl der ersten Kachel), nicht der Name: Der Name ist seit
      // Issue #1307 ein eigener Link.
      fireEvent.click(screen.getAllByText('0 Spalten')[0])

      expect(await screen.findByText('Ziel /boards/9/leitstand')).toBeInTheDocument()
    })

    it('steht vor dem Archivieren, und Enter darauf öffnet nur dessen Dialog', async () => {
      const user = userEvent.setup()
      renderMitZiel('OWNER')
      ;(await screen.findByRole('link', { name: 'Board A' })).focus()

      await user.tab()
      expect(screen.getByLabelText('Board Board A archivieren')).toHaveFocus()
      await user.keyboard('{Enter}')

      expect(await screen.findByText('Board archivieren?')).toBeInTheDocument()
      expect(screen.queryByText(/^Ziel /)).not.toBeInTheDocument()
    })
  })
})

describe('ProjectBoardsPage auf Englisch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const renderEn = (role: string, boards: Board[], archiv: Board[] = []) => {
    mockedProjects.list.mockResolvedValue([{ id: 5, name: 'Team', role, createdAt: '' }])
    mockedBoards.list.mockResolvedValue(boards)
    mockedBoards.listArchived.mockResolvedValue(archiv)
    return renderMitSprache(
      <SnackbarProvider>
        <MemoryRouter initialEntries={[{ pathname: '/projects/5', key: 'push' }]}>
          <Routes>
            <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
          </Routes>
        </MemoryRouter>
      </SnackbarProvider>,
      'en',
    )
  }

  it('zeigt Leerzustand, Anlegeformular und Mitglieder-Link auf Englisch', async () => {
    renderEn('OWNER', [])
    expect(await screen.findByText('No boards yet.')).toBeInTheDocument()
    expect(screen.getByLabelText('New board')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Members' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Projects' })).toBeInTheDocument()
  })

  it('zeigt Spaltenzahl, Archiv und beide Dialoge auf Englisch', async () => {
    const [aktiv, alt] = makeBoards(['Eins', 'Alt'], 5)
    mockedBoards.remove.mockRejectedValue(new ApiError(500, 'Boom'))
    renderEn('OWNER', [{ ...aktiv, columns: [{ id: 1, name: 'A', position: 0, wipLimit: null }] }], [alt])
    expect(await screen.findByText('1 column')).toBeInTheDocument()
    expect(screen.queryByText('2 columns')).not.toBeInTheDocument()
    expect(await screen.findByText('Archive')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Restore' })).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Archive board Eins'))
    expect(await screen.findByText('Archive board?')).toBeInTheDocument()
    expect(
      screen.getByText(
        'The board “Eins” will be archived. It disappears from the list but keeps all of its epics and tickets and can be restored from the archive.',
      ),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Archive' }))
    await erwarteFehlerToast('Archiving failed.')
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    fireEvent.click(screen.getByLabelText('Delete board Alt permanently'))
    expect(await screen.findByText('Delete board permanently?')).toBeInTheDocument()
    expect(screen.getByLabelText('Board name to confirm')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete permanently' })).toBeDisabled()
  })

  it('meldet Fehler beim Anlegen, Wiederherstellen und endgültigen Löschen auf Englisch', async () => {
    const [alt] = makeBoards(['Alt'], 5)
    mockedBoards.create.mockRejectedValue(new ApiError(500, 'Boom'))
    mockedBoards.restore.mockRejectedValue(new ApiError(500, 'Boom'))
    mockedBoards.purge.mockRejectedValue(new ApiError(500, 'Boom'))
    renderEn('OWNER', [], [alt])
    await screen.findByText('Alt')

    fireEvent.change(screen.getByLabelText('New board'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create' }))
    expect(await screen.findByText('Creating the board failed.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Restore' }))
    expect(await screen.findByText('Restoring failed.')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Delete board Alt permanently'))
    fireEvent.change(await screen.findByLabelText('Board name to confirm'), { target: { value: 'Alt' } })
    fireEvent.click(screen.getByRole('button', { name: 'Delete permanently' }))
    expect(await screen.findByText('Permanent deletion failed.')).toBeInTheDocument()
  })

  it('zählt mehrere Spalten auf Englisch', async () => {
    const [zwei] = makeBoards(['Zwei'], 5)
    const spalte = { id: 1, name: 'A', position: 0, wipLimit: null }
    renderEn('VIEWER', [{ ...zwei, columns: [spalte, { ...spalte, id: 2, position: 1 }] }])
    expect(await screen.findByText('2 columns')).toBeInTheDocument()
  })

  it('meldet eine ungültige Projekt-ID auf Englisch', () => {
    renderMitSprache(
      <MemoryRouter initialEntries={['/projects/abc']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectBoardsPage />} />
        </Routes>
      </MemoryRouter>,
      'en',
    )
    expect(screen.getByText('Invalid project ID.')).toBeInTheDocument()
  })
})

describe('ProjectBoardsPage — Ansicht als XY (Issue #1927)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('zeigt Anlegen, Archivieren, Wiederherstellen und Endgültig löschen in der Vorschau gesperrt mit dem Hinweis „Vorschau“', async () => {
    auth.vorschau = vorschau
    renderMitArchiv()

    expect(await screen.findByText('Altes Board')).toBeInTheDocument()
    expect(screen.queryByLabelText('Board Aktiv archivieren')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Board Altes Board endgültig löschen')).not.toBeInTheDocument()
    const knoepfe = screen.getAllByRole('button', { name: 'Vorschau' })
    expect(knoepfe.map((k) => k.textContent)).toEqual(['Anlegen', '', '', 'Wiederherstellen', ''])
    knoepfe.forEach((k) => {
      expect(k).toBeDisabled()
      fireEvent.click(k)
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mockedBoards.create).not.toHaveBeenCalled()
    expect(mockedBoards.remove).not.toHaveBeenCalled()
    expect(mockedBoards.restore).not.toHaveBeenCalled()
    expect(mockedBoards.purge).not.toHaveBeenCalled()
  })

  it('lässt dieselben Knöpfe außerhalb der Vorschau bedienbar', async () => {
    renderMitArchiv()

    expect(await screen.findByText('Altes Board')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeEnabled()
    expect(screen.getByLabelText('Board Aktiv archivieren')).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Wiederherstellen' })).toBeEnabled()
    expect(screen.getByLabelText('Board Altes Board endgültig löschen')).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Vorschau' })).not.toBeInTheDocument()
  })
})
