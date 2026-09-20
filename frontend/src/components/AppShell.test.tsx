import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import pkg from '../../package.json'
import { APP_NAME } from '../appMeta'
import { boardsApi } from '../api/boards'
import { ApiError } from '../api/client'
import { projectsApi } from '../api/projects'
import { AppShell } from './AppShell'
import { SnackbarProvider } from './SnackbarProvider'
import { ThemeProvider } from '@mui/material/styles'
import { cssRegel, cssRegelMit } from '../test/cssRegel'
import { TEXT_SCHWACH, theme } from '../theme'

const logoutMock = vi.fn().mockResolvedValue(undefined)
const useAuthMock = vi.fn()
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => useAuthMock(),
}))

const loggedInUser = { userId: 1, email: 'a@b.c', displayName: 'Manne', platformRole: 'USER' as const, memberships: [] }

vi.mock('../api/boards', () => ({
  boardsApi: {
    get: vi.fn().mockResolvedValue({ id: 1, name: 'B', projectId: 5, columns: [] }),
    list: vi.fn().mockResolvedValue([]),
  },
}))
vi.mock('../api/projects', () => ({
  projectsApi: {
    list: vi.fn().mockResolvedValue([
      { id: 5, name: 'P1', role: 'OWNER', createdAt: '' },
      { id: 6, name: 'P2', role: 'MEMBER', createdAt: '' },
    ]),
  },
}))

// Editiermodus gemockt (Default aus): Bestandstests laufen ohne Banner; ein Test schaltet ihn an.
const editModeState = vi.hoisted(() => ({ editMode: false }))
vi.mock('../lib/EditModeContext', () => ({
  useEditMode: () => ({
    editMode: editModeState.editMode,
    setEditMode: vi.fn(),
    toggleEditMode: vi.fn(),
  }),
}))

const mockedBoards = boardsApi as unknown as {
  get: ReturnType<typeof vi.fn>
  list: ReturnType<typeof vi.fn>
}
const mockedProjects = projectsApi as unknown as {
  list: ReturnType<typeof vi.fn>
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

/** localStorage-Stub, dessen Zugriffe wie in einem privaten/gesperrten Kontext werfen. */
function throwingStorage(): Storage {
  const boom = () => {
    throw new Error('storage disabled')
  }
  return {
    getItem: boom,
    setItem: boom,
    removeItem: boom,
    clear: boom,
    key: boom,
    get length(): number {
      return 0
    },
  }
}

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname}</div>
}

function renderShell(entry = '/') {
  return render(
    <SnackbarProvider>
      <MemoryRouter initialEntries={[entry]}>
        <LocationProbe />
        <AppShell />
      </MemoryRouter>
    </SnackbarProvider>,
  )
}

/** Abmelden über das Menü am Nutzer-Mal (Entwurf `.nutzer`, #978). */
function abmelden() {
  fireEvent.click(screen.getByRole('button', { name: 'Konto von Manne' }))
  fireEvent.click(screen.getByRole('menuitem', { name: 'Abmelden' }))
}

/** Server-Ablehnung mit einer für den Nutzer formulierten Meldung in `detail` (RFC 9457). */
const serverfehler = (text: string) => new ApiError(409, 'Conflict', undefined, text)

/** Prüft den Fehler-Toast: Der Text steht dort, und der Alert trägt die Severity `error`. */
async function erwarteFehlerToast(text: string) {
  expect(await screen.findByText(text)).toBeInTheDocument()
  expect(await screen.findByRole('alert', { hidden: true })).toHaveClass('MuiAlert-filledError')
}

/** Wie {@link renderShell}, aber im echten Theme — nur so ist `text.primary` der eigene Wert. */
function renderShellThemed(entry = '/') {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={[entry]}>
        <AppShell />
      </MemoryRouter>
    </ThemeProvider>,
  )
}

type BoardId = 1 | 2 | 3

interface TestBoard {
  id: number
  name: string
  projectId: number
  columns: never[]
}

/** Drei Boards aus zwei Projekten — nur so ist der Projektname im Verlaufseintrag unterscheidbar. */
const BOARDS: Record<BoardId, TestBoard> = {
  1: { id: 1, name: 'B', projectId: 5, columns: [] },
  2: { id: 2, name: 'Zwei', projectId: 6, columns: [] },
  3: { id: 3, name: 'Drei', projectId: 5, columns: [] },
}

/** Sprungbrett außerhalb der Shell: echte Routenwechsel statt neu gerenderter Einstiegspunkte. */
function BoardNav() {
  const navigate = useNavigate()
  return (
    <>
      {([1, 2, 3] as BoardId[]).map((id) => (
        <button key={id} onClick={() => navigate(`/boards/${id}`)}>{`zu ${id}`}</button>
      ))}
      <button onClick={() => navigate('/')}>zur Übersicht</button>
    </>
  )
}

function renderBoardShell(entry = '/', extra: ReactNode = null) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <LocationProbe />
      <BoardNav />
      <AppShell />
      {extra}
    </MemoryRouter>,
  )
}

/** Board öffnen und warten, bis sein Kontext steht — erst dann darf ein Verlaufseintrag entstehen. */
async function visitBoard(id: BoardId): Promise<void> {
  fireEvent.click(screen.getByRole('button', { name: `zu ${id}` }))
  // Der Board-Name steht als letzter Teil des Pfads im Kopf, sobald der Kontext geladen ist.
  await within(screen.getByRole('navigation', { name: 'Pfad' })).findByRole('link', { name: BOARDS[id].name })
}

async function leaveBoard(): Promise<void> {
  fireEvent.click(screen.getByRole('button', { name: 'zur Übersicht' }))
  await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/'))
}

function openSwitcher(): void {
  fireEvent.click(screen.getByRole('button', { name: 'Board wechseln' }))
}

/** Das Overlay rendert den Verlauf als `listbox` — daran hängt seine Sichtbarkeit im Test. */
function switcherIsOpen(): boolean {
  return screen.queryByRole('listbox', { name: 'Zuletzt besuchte Boards' }) !== null
}

/** Beschriftungen der Verlaufseinträge in Reihenfolge — Board- und Projektname aneinander. */
function switcherEntries(): string[] {
  return screen.getAllByRole('option').map((option) => option.textContent ?? '')
}

/** Der Verlaufseintrag an dieser Position; angesprochen wird er über die Reihenfolge. */
function switcherEntry(position: number): HTMLElement {
  return screen.getAllByRole('option')[position]
}

describe('AppShell', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', fakeStorage())
    vi.clearAllMocks()
    editModeState.editMode = false
    logoutMock.mockResolvedValue(undefined)
    useAuthMock.mockReturnValue({ user: loggedInUser, logout: logoutMock })
    mockedBoards.get.mockResolvedValue({ id: 1, name: 'B', projectId: 5, columns: [] })
    mockedBoards.list.mockResolvedValue([])
    mockedProjects.list.mockResolvedValue([
      { id: 5, name: 'P1', role: 'OWNER', createdAt: '' },
      { id: 6, name: 'P2', role: 'MEMBER', createdAt: '' },
    ])
  })

  it('blendet im Ansichtsmodus keinen Editiermodus-Banner ein', () => {
    renderShell()
    expect(screen.queryByText('Achtung, Du befindest Dich im Editiermodus')).not.toBeInTheDocument()
  })

  it('zeigt bei aktivem Editiermodus den Hinweis-Banner über dem Header', () => {
    editModeState.editMode = true
    renderShell()
    expect(screen.getByText('Achtung, Du befindest Dich im Editiermodus')).toBeInTheDocument()
  })

  it('rendert Marke, Projekte-Navigation und den angemeldeten Nutzer', () => {
    renderShell()
    expect(screen.getByText(APP_NAME)).toBeInTheDocument()
    expect(screen.getByText('Projekte')).toBeInTheDocument()
    // Der Nutzer steht als rundes Mal mit seinem Kürzel im Kopf (Entwurf `.nutzer`).
    expect(screen.getByRole('button', { name: 'Konto von Manne' })).toHaveTextContent('M')
    // Kartensuche der Kopfzeile (#490); ihr Verhalten ist in CardNumberSearch.test.tsx geprüft.
    expect(screen.getByRole('search')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Kartennummer suchen' })).toBeInTheDocument()
  })

  it('zeigt die aktuelle App-Version im Header', () => {
    renderShell()
    expect(screen.getByText(`v${pkg.version}`)).toBeInTheDocument()
  })

  it('verlinkt „Dokumentation" im Administrations-Bereich auf /docs/ in neuem Tab', () => {
    renderShell()
    const doc = screen.getByRole('link', { name: 'Dokumentation' })
    expect(doc).toHaveAttribute('href', '/docs/')
    expect(doc).toHaveAttribute('target', '_blank')
    expect(doc).toHaveAttribute('rel', expect.stringContaining('noopener'))
  })

  it('zeigt „Dokumentation" auch eingeklappt als Anker mit /docs/', () => {
    renderShell()
    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    const doc = screen.getByRole('link', { name: 'Dokumentation' })
    expect(doc).toHaveAttribute('href', '/docs/')
    expect(doc).toHaveAttribute('target', '_blank')
  })

  it('klappt die Seitenleiste ein und merkt den Zustand in localStorage', () => {
    renderShell()
    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    expect(localStorage.getItem('sidebar-collapsed')).toBe('true')
    // Nach dem Einklappen bietet der Button das Ausklappen an.
    expect(screen.getByLabelText('Menü ausklappen')).toBeInTheDocument()
  })

  it('setzt die Kontextbereich-Maße als CSS-Variablen und aktualisiert sie beim Einklappen', () => {
    renderShell()
    const root = document.documentElement
    // Schiene 224 px und Kopf 59 px wie im Leitstand-Entwurf (#978).
    expect(root.style.getPropertyValue('--app-content-left')).toBe('224px')
    expect(root.style.getPropertyValue('--app-content-top')).toBe('59px')

    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    expect(root.style.getPropertyValue('--app-content-left')).toBe('64px')
  })

  it('überlebt den Wechsel von einer Nicht-Board- auf eine Board-Route (Rules of Hooks)', async () => {
    function Nav() {
      const navigate = useNavigate()
      return <button onClick={() => navigate('/boards/1')}>go</button>
    }
    render(
      <MemoryRouter initialEntries={['/']}>
        <Nav />
        <AppShell />
      </MemoryRouter>,
    )
    // Übergang /  ->  /boards/1: darf nicht crashen und zeigt die Board-Gruppe.
    fireEvent.click(screen.getByText('go'))
    expect(await screen.findByText('B')).toBeInTheDocument()
  })

  it('startet ausgeklappt, wenn localStorage beim Lesen wirft', () => {
    vi.stubGlobal('localStorage', throwingStorage())
    renderShell()
    expect(screen.getByLabelText('Menü einklappen')).toBeInTheDocument()
  })

  it('bricht beim Einklappen nicht ab, wenn localStorage beim Schreiben wirft', () => {
    vi.stubGlobal('localStorage', throwingStorage())
    renderShell()
    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    // Zustand toggelt trotzdem, nur das Persistieren schlägt (stumm) fehl.
    expect(screen.getByLabelText('Menü ausklappen')).toBeInTheDocument()
  })

  it('setzt Board und Boardanzahl zurück, wenn das Board nicht geladen werden kann', async () => {
    mockedBoards.get.mockRejectedValue(new Error('404'))
    renderShell('/boards/1')
    await waitFor(() => expect(mockedBoards.get).toHaveBeenCalled())
    expect(screen.queryByText('B')).not.toBeInTheDocument()
  })

  it('setzt nur die Boardanzahl zurück, wenn das Nachladen der Boardliste fehlschlägt', async () => {
    mockedBoards.list.mockRejectedValue(new Error('500'))
    renderShell('/boards/1')
    // Board selbst ist trotzdem geladen (Gruppe sichtbar).
    expect(await screen.findByText('B')).toBeInTheDocument()
  })

  it('lädt Projekt- und Board-Kontext beim Fensterfokus neu', async () => {
    renderShell('/boards/1')
    expect(await screen.findByText('B')).toBeInTheDocument()
    mockedProjects.list.mockClear()
    mockedBoards.get.mockClear()

    fireEvent(window, new Event('focus'))

    await waitFor(() => expect(mockedProjects.list).toHaveBeenCalledTimes(1))
    expect(mockedBoards.get).toHaveBeenCalledTimes(1)
  })

  it('setzt Board und Boardanzahl zurück, wenn das Board beim Fensterfokus-Nachladen nicht mehr geladen werden kann', async () => {
    renderShell('/boards/1')
    expect(await screen.findByText('B')).toBeInTheDocument()
    mockedBoards.get.mockRejectedValue(new Error('404'))

    fireEvent(window, new Event('focus'))

    await waitFor(() => expect(screen.queryByText('B')).not.toBeInTheDocument())
  })

  it('lädt nur die Projektliste beim Fensterfokus neu, wenn keine Board-Route aktiv ist', async () => {
    renderShell('/')
    await waitFor(() => expect(mockedProjects.list).toHaveBeenCalled())
    mockedProjects.list.mockClear()
    mockedBoards.get.mockClear()

    fireEvent(window, new Event('focus'))

    await waitFor(() => expect(mockedProjects.list).toHaveBeenCalledTimes(1))
    expect(mockedBoards.get).not.toHaveBeenCalled()
  })

  it('meldet ab und navigiert zur Login-Seite', async () => {
    renderShell()
    abmelden()
    expect(logoutMock).toHaveBeenCalled()
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'))
  })

  it('zeigt die Server-Meldung, wenn das Abmelden scheitert, und bleibt angemeldet auf der Seite', async () => {
    logoutMock.mockRejectedValue(serverfehler('Die Sitzung wurde bereits beendet.'))
    renderShell()

    abmelden()

    await erwarteFehlerToast('Die Sitzung wurde bereits beendet.')
    // Der Sprung auf /login gehört zum Erfolgsfall: Scheitert der Logout, bleibt der Nutzer
    // angemeldet auf der Seite, statt vor eine Login-Maske gestellt zu werden.
    expect(screen.getByTestId('location')).toHaveTextContent('/')
  })

  it('fällt beim Abmelden ohne Server-Meldung auf den eigenen Text zurück', async () => {
    logoutMock.mockRejectedValue(new TypeError('Failed to fetch'))
    renderShell()

    abmelden()

    await erwarteFehlerToast('Abmelden fehlgeschlagen.')
  })

  it('gliedert die Schiene in Blöcke mit Etikett-Titel, die immer offen stehen (#978)', async () => {
    renderShell('/boards/1')
    const projekt = await screen.findByRole('group', { name: 'Projekt P1' })

    expect(within(projekt).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'Leitstand',
      'Board',
      'Liste',
      'Vorhaben',
      'Ideen',
      'Nachtläufe',
    ])
    expect(screen.getByRole('group', { name: 'Verwaltung' })).toHaveTextContent('Rollen & Rechte')
  })

  it('markiert den Eintrag der aktuellen Ansicht und nur ihn als aktuelle Seite', async () => {
    renderShell('/boards/1/list')
    const projekt = await screen.findByRole('group', { name: 'Projekt P1' })

    expect(within(projekt).getByRole('link', { name: 'Liste' })).toHaveAttribute('aria-current', 'page')
    expect(within(projekt).getByRole('link', { name: 'Board' })).not.toHaveAttribute('aria-current')
  })

  it('zeigt im Kopf den Pfad aus Projekt und Board und verlinkt beide', async () => {
    renderShell('/boards/1')
    const pfad = await screen.findByRole('navigation', { name: 'Pfad' })

    expect(await within(pfad).findByRole('link', { name: 'B' })).toHaveAttribute('href', '/boards/1')
    expect(within(pfad).getByRole('link', { name: 'P1' })).toHaveAttribute('href', '/projects/5')
  })

  it('lässt einen Klick mit Zusatztaste beim Browser, statt selbst zu navigieren', async () => {
    renderShell('/boards/1')
    const liste = await screen.findByRole('link', { name: 'Liste' })

    fireEvent.click(liste, { metaKey: true })

    expect(screen.getByTestId('location')).toHaveTextContent('/boards/1')
  })

  it('navigiert bei eingeklappter Sidebar über einen einfachen Link', async () => {
    renderShell('/boards/1')
    expect(await screen.findByText('B')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    fireEvent.click(screen.getByRole('link', { name: 'Projekte' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'))
  })

  it('navigiert bei ausgeklappter Sidebar über einen einfachen Link', async () => {
    renderShell('/boards/1')
    expect(await screen.findByText('B')).toBeInTheDocument()
    fireEvent.click(screen.getByText('Projekte'))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'))
  })

  it('schließt das Menü am Nutzer-Mal mit Escape, ohne zu navigieren oder abzumelden', async () => {
    renderShell('/boards/1')
    fireEvent.click(screen.getByRole('button', { name: 'Konto von Manne' }))
    const menue = await screen.findByRole('menu', { name: 'Konto von Manne' })

    fireEvent.keyDown(menue, { key: 'Escape' })

    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Konto von Manne' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByTestId('location')).toHaveTextContent('/boards/1')
    expect(logoutMock).not.toHaveBeenCalled()
  })

  it('navigiert über das Menü am Nutzer-Mal zur Profilseite', async () => {
    renderShell()
    fireEvent.click(screen.getByRole('button', { name: 'Konto von Manne' }))
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Profil bearbeiten' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/profil'))
  })

  it('zeigt unten den abgesetzten Administration-Eintrag und navigiert dorthin', async () => {
    renderShell()
    fireEvent.click(screen.getByText('Administration'))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/administration'))
  })

  it('erreicht den Administration-Eintrag auch bei eingeklappter Sidebar', async () => {
    renderShell()
    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    fireEvent.click(screen.getByRole('link', { name: 'Administration' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/administration'))
  })

  it('zeigt ohne angemeldeten Nutzer weder Avatar noch Abmelden-Button', () => {
    useAuthMock.mockReturnValue({ user: null, logout: logoutMock })
    renderShell()
    expect(screen.getByText(APP_NAME)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Konto von/ })).not.toBeInTheDocument()
    // Die Kartensuche läuft über die eigenen Projekte — ohne Session gibt es nichts zu durchsuchen.
    expect(screen.queryByRole('search')).not.toBeInTheDocument()
  })


  it('ignoriert eine verspätet auflösende Board-Antwort nach dem Verlassen der Board-Route', async () => {
    let resolveGet: (b: { id: number; name: string; projectId: number; columns: never[] }) => void = () => {}
    mockedBoards.get.mockReturnValue(
      new Promise((resolve) => {
        resolveGet = resolve
      }),
    )

    function Nav() {
      const navigate = useNavigate()
      return <button onClick={() => navigate('/')}>weg</button>
    }
    render(
      <MemoryRouter initialEntries={['/boards/1']}>
        <LocationProbe />
        <Nav />
        <AppShell />
      </MemoryRouter>,
    )

    // Board-Route verlassen, bevor boardsApi.get aufgelöst hat — Effekt-Cleanup setzt cancelled.
    fireEvent.click(screen.getByText('weg'))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'))

    // Verspätete Antwort darf keinen State mehr setzen (kein act()-Warning, kein Crash).
    resolveGet({ id: 1, name: 'B', projectId: 5, columns: [] })
    await waitFor(() => expect(screen.queryByText('B')).not.toBeInTheDocument())
  })

  it('zeigt den projektweiten „Ideen"-Link auf einer Projekt-Route (ohne offenes Board)', async () => {
    renderShell('/projects/5')

    const ideen = await screen.findByText('Ideen')
    fireEvent.click(ideen)

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/projects/5/ideas'))
  })

  describe('Nachtläufe-Eintrag', () => {
    /**
     * Anker für „Kontext vollständig geladen": Der Verlaufseintrag — und damit der aktivierte
     * Wechsel-Knopf — entsteht erst, wenn Board *und* Projektliste da sind. Ohne ihn prüften die
     * negativen Fälle womöglich einen Zwischenstand, in dem die Rolle noch gar nicht vorlag.
     */
    async function waitForLoadedBoardContext(): Promise<void> {
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Board wechseln' })).toBeEnabled(),
      )
    }

    it('zeigt „Nachtläufe" auf einer Board-Route, wenn man Owner des Projekts ist', async () => {
      renderShell('/boards/1')

      fireEvent.click(await screen.findByText('Nachtläufe'))

      await waitFor(() =>
        expect(screen.getByTestId('location')).toHaveTextContent('/projects/5/nachtlauf'),
      )
    })

    it('blendet „Nachtläufe" für eine Rolle unterhalb OWNER aus', async () => {
      mockedProjects.list.mockResolvedValue([
        { id: 5, name: 'P1', role: 'MEMBER', createdAt: '' },
        { id: 6, name: 'P2', role: 'MEMBER', createdAt: '' },
      ])
      renderShell('/boards/1')
      await waitForLoadedBoardContext()

      expect(screen.queryByText('Nachtläufe')).not.toBeInTheDocument()
    })

    it('zeigt „Nachtläufe" auf einer Projekt-Route ohne offenes Board (routeProjectId)', async () => {
      renderShell('/projects/5')

      fireEvent.click(await screen.findByText('Nachtläufe'))

      await waitFor(() =>
        expect(screen.getByTestId('location')).toHaveTextContent('/projects/5/nachtlauf'),
      )
    })

    it('zeigt „Nachtläufe" dem Plattform-Admin nur am teilnehmenden Projekt', async () => {
      // Seit Issue #1079 lässt der Server einen Plattform-Admin ohne eigene OWNER-Rolle die
      // Nachtlauf-Auswertung nur noch am teilnehmenden Projekt lesen (fachliche Quelle #1064,
      // Frage 9). Bis dahin galt Plan-Entscheidung A6: `requireOwner` ließ ihn überall passieren.
      useAuthMock.mockReturnValue({
        user: { ...loggedInUser, platformRole: 'ADMIN' as const },
        logout: logoutMock,
      })
      mockedProjects.list.mockResolvedValue([
        { id: 5, name: 'P1', role: 'MEMBER', createdAt: '', dashboardParticipation: true },
        { id: 6, name: 'P2', role: 'MEMBER', createdAt: '' },
      ])
      renderShell('/boards/1')

      expect(await screen.findByText('Nachtläufe')).toBeInTheDocument()
    })

    it('blendet „Nachtläufe" dem Plattform-Admin am nicht teilnehmenden Projekt aus', async () => {
      useAuthMock.mockReturnValue({
        user: { ...loggedInUser, platformRole: 'ADMIN' as const },
        logout: logoutMock,
      })
      mockedProjects.list.mockResolvedValue([
        { id: 5, name: 'P1', role: 'MEMBER', createdAt: '' },
        { id: 6, name: 'P2', role: 'MEMBER', createdAt: '' },
      ])
      renderShell('/boards/1')

      expect(await screen.findByText('P1')).toBeInTheDocument()
      expect(screen.queryByText('Nachtläufe')).not.toBeInTheDocument()
    })
  })

  describe('Board-Wechsel', () => {
    beforeEach(() => {
      mockedBoards.get.mockImplementation((id: number) => Promise.resolve(BOARDS[id as BoardId]))
    })

    it('führt den Verlauf absteigend nach letzter Benutzung', async () => {
      renderBoardShell()
      await visitBoard(1)
      await visitBoard(2)
      await visitBoard(3)

      openSwitcher()

      expect(switcherEntries()).toEqual(['DreiP1', 'ZweiP2', 'BP1'])
    })

    it('hebt einen wiederholten Besuch nach vorne, ohne ein Duplikat anzulegen', async () => {
      renderBoardShell()
      await visitBoard(1)
      await visitBoard(2)
      await visitBoard(1)

      openSwitcher()

      expect(switcherEntries()).toEqual(['BP1', 'ZweiP2'])
    })

    it('schreibt während eines Wechsels erst nach kohärentem Kontext einen Eintrag', async () => {
      let resolveSecond: (board: TestBoard) => void = () => {}
      mockedBoards.get.mockImplementation((id: number) =>
        id === 2
          ? new Promise<TestBoard>((resolve) => {
              resolveSecond = resolve
            })
          : Promise.resolve(BOARDS[id as BoardId]),
      )
      renderBoardShell()
      await visitBoard(1)

      fireEvent.click(screen.getByRole('button', { name: 'zu 2' }))
      await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/boards/2'))

      // Die Route steht schon auf Board 2, geladen ist noch Board 1 — daraus entsteht nichts.
      openSwitcher()
      expect(switcherEntries()).toEqual(['BP1'])

      resolveSecond(BOARDS[2])

      await waitFor(() => expect(switcherEntries()).toEqual(['ZweiP2', 'BP1']))
    })

    it('trägt den Projektnamen in den Eintrag, obwohl der Board-Kontext ihn nicht liefert', async () => {
      renderBoardShell()
      await visitBoard(2)
      await leaveBoard()

      openSwitcher()

      expect(switcherEntries()).toEqual(['ZweiP2'])
    })

    it('schreibt keinen Eintrag, solange das Projekt des Boards nicht zugeordnet ist', async () => {
      mockedProjects.list.mockRejectedValue(new Error('500'))
      renderBoardShell()
      await visitBoard(1)
      await leaveBoard()

      expect(screen.getByRole('button', { name: 'Board wechseln' })).toBeDisabled()
    })

    it('stellt den Verlauf nach einem Remount aus dem Storage wieder her', async () => {
      const { unmount } = renderBoardShell()
      await visitBoard(1)
      unmount()

      renderBoardShell()
      openSwitcher()

      expect(switcherEntries()).toEqual(['BP1'])
    })

    it('öffnet das Overlay mit der Taste b', async () => {
      renderBoardShell()
      await visitBoard(1)
      await leaveBoard()

      fireEvent.keyDown(document, { key: 'b' })

      expect(switcherIsOpen()).toBe(true)
    })

    it('löst die Taste b in einem Eingabefeld nicht aus', async () => {
      renderBoardShell()
      await visitBoard(1)
      await leaveBoard()

      fireEvent.keyDown(screen.getByRole('textbox', { name: 'Kartennummer suchen' }), { key: 'b' })

      expect(switcherIsOpen()).toBe(false)
    })

    it('löst die Taste b bei einem fremden offenen Dialog nicht aus', async () => {
      renderBoardShell('/', <div role="dialog" aria-label="Fremder Dialog" />)
      await visitBoard(1)
      await leaveBoard()

      fireEvent.keyDown(document, { key: 'b' })

      expect(switcherIsOpen()).toBe(false)
    })

    it('löst die Taste b bei leerem Verlauf nicht aus', () => {
      renderBoardShell()

      fireEvent.keyDown(document, { key: 'b' })

      expect(switcherIsOpen()).toBe(false)
    })

    it('öffnet das Overlay über das Bedienelement der Kopfleiste', async () => {
      renderBoardShell()
      await visitBoard(1)
      await leaveBoard()

      openSwitcher()

      expect(switcherIsOpen()).toBe(true)
    })

    it('deaktiviert das Bedienelement bei leerem Verlauf', () => {
      renderBoardShell()

      expect(screen.getByRole('button', { name: 'Board wechseln' })).toBeDisabled()
    })

    it('reicht auf einer Board-Route die aktuelle Board-ID durch', async () => {
      renderBoardShell()
      await visitBoard(1)
      await visitBoard(2)

      openSwitcher()

      // Auf Board 2 vorausgewählt ist der zuletzt *andere* Eintrag.
      expect(screen.getByRole('option', { selected: true }).textContent).toBe('BP1')
    })

    it('reicht außerhalb einer Board-Route null durch — dann steht der erste Eintrag vorne', async () => {
      renderBoardShell()
      await visitBoard(1)
      await visitBoard(2)
      await leaveBoard()

      openSwitcher()

      expect(screen.getByRole('option', { selected: true }).textContent).toBe('ZweiP2')
    })

    it('navigiert bei der Auswahl eines Eintrags zur Board-Route', async () => {
      renderBoardShell()
      await visitBoard(1)
      await visitBoard(2)

      openSwitcher()
      fireEvent.click(screen.getByRole('option', { selected: true }))

      await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/boards/1'))
    })

    it('entfernt ein vom Overlay als 404 gemeldetes Ziel aus dem Verlauf', async () => {
      renderBoardShell()
      await visitBoard(1)
      await visitBoard(2)
      await visitBoard(3)
      mockedBoards.get.mockImplementation((id: number) =>
        id === 1 ? Promise.reject(new ApiError(404, 'weg')) : Promise.resolve(BOARDS[id as BoardId]),
      )

      openSwitcher()
      fireEvent.click(switcherEntry(2))

      await waitFor(() => expect(switcherEntries()).toEqual(['DreiP1', 'ZweiP2']))
    })
  })

  describe('Board-Kontext auf Projektseiten (#990)', () => {
    /** Boards je Projekt, wie `boardsApi.list` sie liefert — Projekt 5 hat zwei, Projekt 6 eines. */
    const PROJEKT_BOARDS: Record<number, TestBoard[]> = {
      5: [BOARDS[1], BOARDS[3]],
      6: [BOARDS[2]],
    }

    const ZIELE = [
      '/',
      '/boards/1',
      '/boards/3',
      '/projects/5/ideas',
      '/projects/5/nachtlauf',
      '/projects/6/ideas',
    ]

    function ZielNav() {
      const navigate = useNavigate()
      return (
        <>
          {ZIELE.map((ziel) => (
            <button key={ziel} onClick={() => navigate(ziel)}>{`nach ${ziel}`}</button>
          ))}
        </>
      )
    }

    function renderMitZielen(entry: string) {
      return render(
        <MemoryRouter initialEntries={[entry]}>
          <LocationProbe />
          <ZielNav />
          <AppShell />
        </MemoryRouter>,
      )
    }

    async function gehZu(ziel: string): Promise<void> {
      fireEvent.click(screen.getByRole('button', { name: `nach ${ziel}` }))
      await waitFor(() => expect(screen.getByTestId('location').textContent).toBe(ziel))
    }

    /** Verlauf des angemeldeten Nutzers vorbelegen — wie nach früheren Besuchen im selben Browser. */
    function verlaufSetzen(eintraege: unknown[]): void {
      localStorage.setItem(`manban.boardHistory.v1.${loggedInUser.userId}`, JSON.stringify(eintraege))
    }

    /** Die Ziele des Projekt-Blocks in ihrer Reihenfolge — Beschriftung und Ziel-Pfad. */
    function projektBlock(projektName: string): Array<[string, string | null]> {
      const block = screen.getByRole('group', { name: `Projekt ${projektName}` })
      return within(block)
        .getAllByRole('link')
        .map((link) => [link.textContent ?? '', link.getAttribute('href')])
    }

    beforeEach(() => {
      mockedBoards.get.mockImplementation((id: number) => Promise.resolve(BOARDS[id as BoardId]))
      mockedBoards.list.mockImplementation((projectId: number) =>
        Promise.resolve(PROJEKT_BOARDS[projectId] ?? []),
      )
    })

    it('behält beim Wechsel vom Board auf eine Projektseite desselben Projekts die Board-Einträge', async () => {
      renderMitZielen('/boards/1')
      await screen.findByRole('link', { name: 'Leitstand' })

      await gehZu('/projects/5/nachtlauf')

      expect(projektBlock('P1')).toEqual([
        ['Leitstand', '/boards/1/leitstand'],
        ['Board', '/boards/1'],
        ['Liste', '/boards/1/list'],
        ['Vorhaben', '/boards/1/vorhaben'],
        ['Ideen', '/projects/5/ideas'],
        ['Nachtläufe', '/projects/5/nachtlauf'],
      ])
      // Aktiv ist die Seite, auf der man steht — kein Board-Eintrag.
      expect(screen.getByRole('link', { name: 'Nachtläufe' })).toHaveAttribute('aria-current', 'page')
      expect(screen.getByRole('link', { name: 'Board' })).not.toHaveAttribute('aria-current')
      // Der Pfad im Kopf nennt dort nur das Projekt.
      const pfad = screen.getByRole('navigation', { name: 'Pfad' })
      expect(within(pfad).getAllByRole('link').map((link) => link.textContent)).toEqual(['P1'])
    })

    it('nimmt beim direkten Aufruf einer Projektseite das zuletzt besuchte Board dieses Projekts', async () => {
      verlaufSetzen([{ id: 3, name: 'Drei', projectId: 5, projectName: 'P1' }])
      renderMitZielen('/projects/5/ideas')

      expect(await screen.findByRole('link', { name: 'Board' })).toHaveAttribute('href', '/boards/3')
    })

    it('merkt sich das Projekt eines Besuchs und findet das Board nach dem Verlassen wieder', async () => {
      renderMitZielen('/boards/3')
      await screen.findByRole('link', { name: 'Leitstand' })
      await gehZu('/')

      await gehZu('/projects/5/ideas')

      // Ohne `projectId` im Verlaufseintrag fiele die Schiene auf das erste Board (1) zurück.
      expect(await screen.findByRole('link', { name: 'Board' })).toHaveAttribute('href', '/boards/3')
    })

    it('nimmt ohne Verlauf das erste Board des Projekts', async () => {
      renderMitZielen('/projects/5/ideas')

      expect(await screen.findByRole('link', { name: 'Board' })).toHaveAttribute('href', '/boards/1')
    })

    it('ignoriert einen alten Verlaufseintrag ohne projectId und nimmt das erste Board', async () => {
      verlaufSetzen([{ id: 3, name: 'Drei', projectName: 'P1' }])
      renderMitZielen('/projects/5/ideas')

      expect(await screen.findByRole('link', { name: 'Board' })).toHaveAttribute('href', '/boards/1')
    })

    it('zeigt bei einem Projekt ohne Board keine Board-Einträge', async () => {
      mockedBoards.list.mockResolvedValue([])
      renderMitZielen('/projects/6/ideas')

      await screen.findByRole('group', { name: 'Projekt P2' })
      await waitFor(() => expect(mockedBoards.list).toHaveBeenCalledWith(6))
      expect(projektBlock('P2')).toEqual([['Ideen', '/projects/6/ideas']])
    })

    it('zeigt auf der Projektseite eines anderen Projekts nie ein Board des vorigen', async () => {
      renderMitZielen('/boards/1')
      await screen.findByRole('link', { name: 'Leitstand' })

      await gehZu('/projects/6/ideas')

      // Schon vor der Antwort der Boardliste ist der fremde Kontext weg.
      expect(
        screen
          .getAllByRole('link')
          .map((link) => link.getAttribute('href'))
          .filter((href) => href?.startsWith('/boards/1')),
      ).toEqual([])
      await waitFor(() =>
        expect(screen.getByRole('link', { name: 'Board' })).toHaveAttribute('href', '/boards/2'),
      )
      expect(projektBlock('P2')).toEqual([
        ['Leitstand', '/boards/2/leitstand'],
        ['Board', '/boards/2'],
        ['Liste', '/boards/2/list'],
        ['Vorhaben', '/boards/2/vorhaben'],
        ['Ideen', '/projects/6/ideas'],
      ])
    })

    it('lässt die Schiene ohne Board, wenn die Boardliste des Projekts fehlschlägt', async () => {
      mockedBoards.list.mockRejectedValue(new Error('500'))
      renderMitZielen('/projects/5/ideas')

      await screen.findByRole('group', { name: 'Projekt P1' })
      await waitFor(() => expect(mockedBoards.list).toHaveBeenCalledWith(5))
      expect(projektBlock('P1')).toEqual([
        ['Ideen', '/projects/5/ideas'],
        ['Nachtläufe', '/projects/5/nachtlauf'],
      ])
    })

    it('verwirft eine verspätete Boardliste nach dem Verlassen der Projektseite', async () => {
      let liste: (boards: TestBoard[]) => void = () => {}
      mockedBoards.list.mockReturnValue(
        new Promise<TestBoard[]>((resolve) => {
          liste = resolve
        }),
      )
      renderMitZielen('/projects/5/ideas')
      await screen.findByRole('group', { name: 'Projekt P1' })

      await gehZu('/')
      liste(PROJEKT_BOARDS[5])

      await waitFor(() => expect(screen.queryByRole('link', { name: 'Board' })).not.toBeInTheDocument())
    })
  })
})

describe('AppShell Kopf (Entwurf `.kopf`, #978)', () => {
  beforeEach(() => {
    useAuthMock.mockReturnValue({ user: loggedInUser, logout: logoutMock })
    // Ein Verlaufseintrag aktiviert die Taste „Board wechseln"; deaktiviert trüge sie MUIs Disabled-Farbe.
    window.localStorage.setItem(
      `manban.boardHistory.v1.${loggedInUser.userId}`,
      JSON.stringify([{ id: 1, name: 'B', projectId: 5, projectName: 'P1' }]),
    )
  })

  afterEach(() => window.localStorage.clear())

  it.each([
    ['Board wechseln', 'Board wechseln'],
    ['Kartensuche', 'Karte suchen'],
  ])('färbt %s im Kopf mit text.primary', (_name, label) => {
    renderShellThemed()
    expect(screen.getByLabelText(label)).toHaveStyle({ color: 'var(--mb-palette-text-primary)' })
  })

  it('setzt das Nutzer-Mal weiß auf seinen dunklen Verlauf', () => {
    renderShellThemed()
    expect(screen.getByRole('button', { name: 'Konto von Manne' })).toHaveStyle({ color: '#FFFFFF' })
  })

  it('führt die Versionsangabe unter der Marke in schwacher Schrift', () => {
    renderShellThemed()
    expect(screen.getByText(`v${pkg.version}`)).toHaveStyle({ color: TEXT_SCHWACH })
  })

  it('klebt oben und trägt keine Ansichtswahl (Entscheidung Manne, #978)', () => {
    renderShellThemed()
    const kopf = screen.getByRole('banner')
    expect(cssRegel(kopf)).toContain('position: sticky')
    expect(within(kopf).queryByRole('tablist')).not.toBeInTheDocument()
  })
})

describe('AppShell Inhaltsbereich', () => {
  beforeEach(() => useAuthMock.mockReturnValue({ user: loggedInUser, logout: logoutMock }))

  // Der Grund der Anwendung liegt am `body` (theme.ts, `body::before`). Ein `main` mit eigenem
  // Hintergrund deckte ihn ab — genau das tat die Shell bis #713 mit `bgcolor: background.default`.
  it('laesst den Grund der Anwendung durchscheinen, statt ihn zu ueberdecken', () => {
    renderShellThemed()

    expect(screen.getByRole('main')).not.toHaveStyle({ backgroundColor: 'rgb(255, 255, 255)' })
  })
})

/**
 * Stellt eine Fensterbreite nach, soweit `useMediaQuery` sie sieht. jsdom kennt kein `matchMedia`
 * (und `src/test/setup.ts` stubbt es nicht); ohne Stub gilt jede Media-Query als nicht erfüllt, die
 * Bestandstests oben laufen damit im breiten Zweig.
 */
function mitFensterbreite(breite: number) {
  vi.stubGlobal('matchMedia', (query: string) => {
    const max = /max-width:\s*([\d.]+)px/.exec(query)
    const min = /min-width:\s*([\d.]+)px/.exec(query)
    const matches = (!max || breite <= Number(max[1])) && (!min || breite >= Number(min[1]))
    return {
      matches,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }
  })
}

describe('AppShell bis zur Mindestbreite (AK 19, Plan #932 E5, #955)', () => {
  beforeEach(() => {
    useAuthMock.mockReturnValue({ user: loggedInUser, logout: logoutMock })
    vi.stubGlobal('localStorage', fakeStorage())
  })

  afterEach(() => vi.unstubAllGlobals())

  it('legt die Navigation bei 768 px hinter eine Schaltfläche in der Kopfleiste', async () => {
    mitFensterbreite(768)
    renderShell()

    // Temporär heißt: geschlossen, bis sie jemand öffnet — und dann über dem Inhalt.
    expect(screen.queryByRole('navigation', { name: 'Hauptnavigation' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Navigation öffnen' }))

    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })
    expect(navigation).toHaveTextContent('Administration')
  })

  it('schließt die schmale Navigation, sobald ein Ziel gewählt ist', async () => {
    mitFensterbreite(768)
    renderShell()
    fireEvent.click(screen.getByRole('button', { name: 'Navigation öffnen' }))
    fireEvent.click(await screen.findByRole('link', { name: 'Administration' }))

    expect(screen.getByTestId('location')).toHaveTextContent('/administration')
    await waitFor(() => expect(screen.queryByRole('navigation', { name: 'Hauptnavigation' })).not.toBeInTheDocument())
  })

  it('schließt die schmale Navigation mit Escape, ohne ein Ziel zu wählen', async () => {
    mitFensterbreite(768)
    renderShell('/profil')
    fireEvent.click(screen.getByRole('button', { name: 'Navigation öffnen' }))
    const navigation = await screen.findByRole('navigation', { name: 'Hauptnavigation' })

    fireEvent.keyDown(navigation, { key: 'Escape' })

    await waitFor(() => expect(screen.queryByRole('navigation', { name: 'Hauptnavigation' })).not.toBeInTheDocument())
    expect(screen.getByTestId('location')).toHaveTextContent('/profil')
    expect(screen.getByRole('button', { name: 'Navigation öffnen' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('bietet im schmalen Zweig kein Einklappen an — die Navigation ist dort ohnehin ausgeblendet', async () => {
    mitFensterbreite(768)
    renderShell()
    fireEvent.click(screen.getByRole('button', { name: 'Navigation öffnen' }))
    await screen.findByRole('navigation', { name: 'Hauptnavigation' })

    expect(screen.queryByLabelText('Menü einklappen')).not.toBeInTheDocument()
  })

  it('lässt die Navigation bei 900 px und mehr fest stehen, ohne Schaltfläche', () => {
    mitFensterbreite(1280)
    renderShell()

    expect(screen.getByRole('navigation', { name: 'Hauptnavigation' })).toHaveTextContent('Administration')
    expect(screen.queryByRole('button', { name: 'Navigation öffnen' })).not.toBeInTheDocument()
  })

  it('schaltet genau am Breakpoint md um: 899 px schmal, 900 px breit', () => {
    mitFensterbreite(899)
    const { unmount } = renderShell()
    expect(screen.getByRole('button', { name: 'Navigation öffnen' })).toBeInTheDocument()
    unmount()

    mitFensterbreite(900)
    renderShell()
    expect(screen.queryByRole('button', { name: 'Navigation öffnen' })).not.toBeInTheDocument()
  })

  it('setzt --app-content-left unterhalb md auf 0, damit Dialoge mittig sitzen', () => {
    // CardDetailModal und NewCardModal versetzen sich um diese Variable. Mit der Drawer-Breite
    // hingen sie bei 768 px rechts versetzt, obwohl der Drawer gar nicht dasteht.
    mitFensterbreite(768)
    renderShell()

    expect(document.documentElement.style.getPropertyValue('--app-content-left')).toBe('0px')
  })

  it('behält --app-content-left ab md bei der Drawer-Breite', () => {
    mitFensterbreite(1280)
    renderShell()

    expect(document.documentElement.style.getPropertyValue('--app-content-left')).toBe('224px')
  })
})

describe('AppShell Tastatur (AK 15, #955)', () => {
  beforeEach(() => {
    useAuthMock.mockReturnValue({ user: loggedInUser, logout: logoutMock })
    vi.stubGlobal('localStorage', fakeStorage())
  })

  afterEach(() => vi.unstubAllGlobals())

  it('führt als erstes Tastaturziel eine Sprungmarke zum Inhalt', async () => {
    const user = userEvent.setup()
    renderShell()

    await user.tab()

    const marke = screen.getByRole('link', { name: 'Zum Inhalt springen' })
    expect(marke).toHaveFocus()
    expect(marke).toHaveAttribute('href', '#inhalt')
  })

  it('springt über die Sprungmarke in den Inhaltsbereich', async () => {
    const user = userEvent.setup()
    renderShell()

    await user.tab()
    await user.keyboard('{Enter}')

    const inhalt = screen.getByRole('main')
    expect(inhalt).toHaveAttribute('id', 'inhalt')
    // Ohne tabIndex nähme `main` den Fokus nicht an, und die nächste Tab-Taste begänne wieder oben.
    expect(inhalt).toHaveAttribute('tabindex', '-1')
    expect(inhalt).toHaveFocus()
  })

  it('zeigt die Sprungmarke erst, wenn sie den Fokus hat', async () => {
    const user = userEvent.setup()
    renderShellThemed()
    const marke = screen.getByRole('link', { name: 'Zum Inhalt springen' })
    const regel = () => cssRegelMit(marke, ':focus')

    // Außerhalb des sichtbaren Bereichs, aber nicht `display: none` — sonst wäre sie gar nicht fokussierbar.
    expect(cssRegel(marke)).toContain('position: absolute')
    expect(cssRegel(marke)).not.toContain('display: none')
    await user.tab()
    expect(regel()).toMatch(/top: \d/)
  })

  it('trägt an jedem Navigationsziel einen sichtbaren Fokusring', async () => {
    const user = userEvent.setup()
    renderShellThemed()
    const navigation = screen.getByRole('navigation', { name: 'Hauptnavigation' })
    const ziele = [...within(navigation).getAllByRole('button'), ...within(navigation).getAllByRole('link')]
    expect(ziele.length).toBeGreaterThan(2)

    const erreicht = new Set<HTMLElement>()
    for (let schritt = 0; schritt < 40 && erreicht.size < ziele.length; schritt++) {
      await user.tab()
      const aktiv = ziele.find((ziel) => ziel.matches(':focus'))
      if (aktiv) {
        erreicht.add(aktiv)
        // MUI setzt bei Tastaturfokus `Mui-focusVisible`; das Theme hängt daran den Ring (#953).
        expect(aktiv).toHaveClass('Mui-focusVisible')
        expect(cssRegelMit(aktiv, '.Mui-focusVisible')).toMatch(/outline: 2px solid var\(--mb-palette-primary-main/)
      }
    }
    expect([...erreicht]).toHaveLength(ziele.length)
  })
})
