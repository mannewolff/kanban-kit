import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import pkg from '../../package.json'
import { AppShell } from './AppShell'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { userId: 1, email: 'a@b.c', displayName: 'Manne', platformRole: 'USER', memberships: [] },
    logout: vi.fn().mockResolvedValue(undefined),
  }),
}))

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

function renderShell(entry = '/') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <AppShell />
    </MemoryRouter>,
  )
}

describe('AppShell', () => {
  beforeEach(() => vi.stubGlobal('localStorage', fakeStorage()))

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
    await waitFor(() => expect(screen.getByText('B')).toBeInTheDocument())
  })
})
