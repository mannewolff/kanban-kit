import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import pkg from '../../package.json'
import { APP_NAME } from '../appMeta'
import { boardsApi } from '../api/boards'
import { ApiError } from '../api/client'
import { projectsApi } from '../api/projects'
import { AppShell } from './AppShell'
import { ThemeProvider } from '@mui/material/styles'
import { theme } from '../theme'

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
    <MemoryRouter initialEntries={[entry]}>
      <LocationProbe />
      <AppShell />
    </MemoryRouter>,
  )
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
  await screen.findByRole('button', { name: BOARDS[id].name })
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
    expect(screen.getByText('Manne')).toBeInTheDocument()
    expect(screen.getByLabelText('Abmelden')).toBeInTheDocument()
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
    expect(root.style.getPropertyValue('--app-content-left')).toBe('240px')
    expect(root.style.getPropertyValue('--app-content-top')).toBe('64px')

    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    expect(root.style.getPropertyValue('--app-content-left')).toBe('56px')
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
    fireEvent.click(screen.getByLabelText('Abmelden'))
    expect(logoutMock).toHaveBeenCalled()
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'))
  })

  it('klappt eine Nav-Gruppe bei ausgeklappter Sidebar zu und wieder auf', async () => {
    renderShell('/boards/1')
    expect(await screen.findByText('B')).toBeInTheDocument()
    // Die Board-Gruppe ist wegen der aktiven Route automatisch aufgeklappt.
    expect(screen.getByText('Liste')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'B' }))
    await waitFor(() => expect(screen.queryByText('Liste')).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'B' }))
    expect(await screen.findByText('Liste')).toBeInTheDocument()
  })

  it('öffnet bei eingeklappter Sidebar ein Flyout-Menü für eine Nav-Gruppe und navigiert darüber', async () => {
    renderShell('/boards/1')
    expect(await screen.findByText('B')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    fireEvent.click(screen.getByRole('button', { name: 'B' }))

    const menu = await screen.findByRole('menu')
    expect(menu).toBeInTheDocument()

    fireEvent.click(screen.getByRole('menuitem', { name: 'Liste' }))

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/boards/1/list'),
    )
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('schließt das Flyout-Menü per Escape, ohne zu navigieren', async () => {
    renderShell('/boards/1')
    expect(await screen.findByText('B')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    fireEvent.click(screen.getByRole('button', { name: 'B' }))
    await screen.findByRole('menu')

    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape', code: 'Escape' })

    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument())
    expect(screen.getByTestId('location')).toHaveTextContent('/boards/1')
  })

  it('navigiert bei eingeklappter Sidebar über einen einfachen Link', async () => {
    renderShell('/boards/1')
    expect(await screen.findByText('B')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    fireEvent.click(screen.getByRole('button', { name: 'Projekte' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'))
  })

  it('navigiert bei ausgeklappter Sidebar über einen einfachen Link', async () => {
    renderShell('/boards/1')
    expect(await screen.findByText('B')).toBeInTheDocument()
    fireEvent.click(screen.getByText('Projekte'))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'))
  })

  it('navigiert über den Profil-Avatar zur Profilseite', async () => {
    renderShell()
    fireEvent.click(screen.getByLabelText('Profil von Manne bearbeiten'))
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
    fireEvent.click(screen.getByRole('button', { name: 'Administration' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/administration'))
  })

  it('zeigt ohne angemeldeten Nutzer weder Avatar noch Abmelden-Button', () => {
    useAuthMock.mockReturnValue({ user: null, logout: logoutMock })
    renderShell()
    expect(screen.getByText(APP_NAME)).toBeInTheDocument()
    expect(screen.queryByLabelText('Abmelden')).not.toBeInTheDocument()
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

  describe('Nachtlauf-Eintrag', () => {
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

    it('zeigt „Nachtlauf" auf einer Board-Route, wenn man Owner des Projekts ist', async () => {
      renderShell('/boards/1')

      fireEvent.click(await screen.findByText('Nachtlauf'))

      await waitFor(() =>
        expect(screen.getByTestId('location')).toHaveTextContent('/projects/5/nachtlauf'),
      )
    })

    it('blendet „Nachtlauf" für eine Rolle unterhalb OWNER aus', async () => {
      mockedProjects.list.mockResolvedValue([
        { id: 5, name: 'P1', role: 'MEMBER', createdAt: '' },
        { id: 6, name: 'P2', role: 'MEMBER', createdAt: '' },
      ])
      renderShell('/boards/1')
      await waitForLoadedBoardContext()

      expect(screen.queryByText('Nachtlauf')).not.toBeInTheDocument()
    })

    it('zeigt „Nachtlauf" auf einer Projekt-Route ohne offenes Board (routeProjectId)', async () => {
      renderShell('/projects/5')

      fireEvent.click(await screen.findByText('Nachtlauf'))

      await waitFor(() =>
        expect(screen.getByTestId('location')).toHaveTextContent('/projects/5/nachtlauf'),
      )
    })

    it('zeigt „Nachtlauf" dem Plattform-Admin trotz Rolle unterhalb OWNER', async () => {
      // Plan-Entscheidung A6: `PermissionChecker.requireOwner` lässt den Plattform-Admin passieren —
      // ein eigenes `isOwner` in der Shell blendete ihm den Bereich aus, den der Server ihm öffnet.
      useAuthMock.mockReturnValue({
        user: { ...loggedInUser, platformRole: 'ADMIN' as const },
        logout: logoutMock,
      })
      mockedProjects.list.mockResolvedValue([
        { id: 5, name: 'P1', role: 'MEMBER', createdAt: '' },
        { id: 6, name: 'P2', role: 'MEMBER', createdAt: '' },
      ])
      renderShell('/boards/1')

      expect(await screen.findByText('Nachtlauf')).toBeInTheDocument()
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
})

describe('AppShell auf der weissen Kopfleiste', () => {
  beforeEach(() => {
    useAuthMock.mockReturnValue({ user: loggedInUser, logout: logoutMock })
    // Ein Verlaufseintrag aktiviert den Board-Wechsel-Knopf. Ohne ihn ist er deaktiviert und
    // traegt MUIs Disabled-Farbe — die Zusicherung liefe an der Umstellung vorbei.
    window.localStorage.setItem(
      `manban.boardHistory.v1.${loggedInUser.userId}`,
      JSON.stringify([{ id: 1, name: 'B', projectName: 'P1' }]),
    )
  })

  afterEach(() => window.localStorage.clear())

  // Die Kopfleiste ist seit #653 weiss. Ohne diese Umstellung stuenden die Bedienelemente weiss
  // auf weiss — geprueft wird deshalb die BERECHNETE Farbe, nicht die Abwesenheit von
  // color="inherit": zwei der Elemente trugen nie ein color-Attribut.
  it.each([
    ['Board wechseln', 'Board wechseln'],
    ['Abmelden', 'Abmelden'],
    ['Profil-Knopf', 'Profil von Manne bearbeiten'],
    ['Kartensuche', 'Karte suchen'],
  ])('faerbt %s in der Kopfleiste mit text.primary', (_name, label) => {
    renderShellThemed()
    expect(screen.getByLabelText(label)).toHaveStyle({ color: theme.palette.text.primary })
  })

  it('faerbt auch die Versionsangabe mit text.primary', () => {
    renderShellThemed()
    expect(screen.getByText(`v${pkg.version}`)).toHaveStyle({ color: theme.palette.text.primary })
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
