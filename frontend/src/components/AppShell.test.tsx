import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import pkg from '../../package.json'
import { boardsApi } from '../api/boards'
import { projectsApi } from '../api/projects'
import { AppShell } from './AppShell'

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

describe('AppShell', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', fakeStorage())
    vi.clearAllMocks()
    logoutMock.mockResolvedValue(undefined)
    useAuthMock.mockReturnValue({ user: loggedInUser, logout: logoutMock })
    mockedBoards.get.mockResolvedValue({ id: 1, name: 'B', projectId: 5, columns: [] })
    mockedBoards.list.mockResolvedValue([])
    mockedProjects.list.mockResolvedValue([
      { id: 5, name: 'P1', role: 'OWNER', createdAt: '' },
      { id: 6, name: 'P2', role: 'MEMBER', createdAt: '' },
    ])
  })

  it('rendert Marke, Projekte-Navigation und den angemeldeten Nutzer', () => {
    renderShell()
    expect(screen.getByText('kanban-kit')).toBeInTheDocument()
    expect(screen.getByText('Projekte')).toBeInTheDocument()
    expect(screen.getByText('Manne')).toBeInTheDocument()
    expect(screen.getByLabelText('Abmelden')).toBeInTheDocument()
  })

  it('zeigt die aktuelle App-Version im Header', () => {
    renderShell()
    expect(screen.getByText(`v${pkg.version}`)).toBeInTheDocument()
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
    expect(screen.getByText('kanban-kit')).toBeInTheDocument()
    expect(screen.queryByLabelText('Abmelden')).not.toBeInTheDocument()
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
})
