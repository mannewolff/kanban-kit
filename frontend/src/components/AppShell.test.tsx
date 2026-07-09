import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    user: { userId: 1, email: 'a@b.c', displayName: 'Manne', platformRole: 'USER', memberships: [] },
    logout: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock('../api/boards', () => ({ boardsApi: { get: vi.fn().mockResolvedValue({ id: 1, name: 'B', columns: [] }) } }))

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
    expect(screen.getByText('manban')).toBeInTheDocument()
    expect(screen.getByText('Projekte')).toBeInTheDocument()
    expect(screen.getByText('Manne')).toBeInTheDocument()
    expect(screen.getByLabelText('Abmelden')).toBeInTheDocument()
  })

  it('klappt die Seitenleiste ein und merkt den Zustand in localStorage', () => {
    renderShell()
    fireEvent.click(screen.getByLabelText('Menü einklappen'))
    expect(localStorage.getItem('sidebar-collapsed')).toBe('true')
    // Nach dem Einklappen bietet der Button das Ausklappen an.
    expect(screen.getByLabelText('Menü ausklappen')).toBeInTheDocument()
  })
})
