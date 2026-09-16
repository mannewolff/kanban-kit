import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { notifyUnauthorized, setUnauthorizedHandler } from '../api/client'
import { AuthProvider, useAuth } from './AuthContext'

vi.mock('../api/auth', () => ({
  authApi: { me: vi.fn(), login: vi.fn(), logout: vi.fn() },
}))

// Nur `setUnauthorizedHandler` wird umhüllt, damit das Abmelden beim Unmount beobachtbar ist;
// der Handler-Zustand bleibt der des echten Moduls, das `notifyUnauthorized` mitbenutzt.
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, setUnauthorizedHandler: vi.fn(actual.setUnauthorizedHandler) }
})

const mockedApi = authApi as unknown as {
  me: ReturnType<typeof vi.fn>
  login: ReturnType<typeof vi.fn>
  logout: ReturnType<typeof vi.fn>
}

const meFixture = {
  userId: 1, email: 'a@b.de', displayName: 'A', platformRole: 'USER' as const, memberships: [],
}

function Probe() {
  const { user, loading, login, logout } = useAuth()
  return (
    <div>
      <span data-testid="loading">{String(loading)}</span>
      <span data-testid="user">{user?.email ?? 'none'}</span>
      <button onClick={() => void login('a@b.de', 'geheim123')}>login</button>
      <button onClick={() => void logout()}>logout</button>
    </div>
  )
}

function renderProbe() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('lädt beim Mount den Nutzer per refresh (me erfolgreich)', async () => {
    mockedApi.me.mockResolvedValue(meFixture)
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('user')).toHaveTextContent('a@b.de')
  })

  it('setzt den Nutzer auf null, wenn refresh (me) fehlschlägt', async () => {
    mockedApi.me.mockRejectedValue(new Error('401'))
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    expect(screen.getByTestId('user')).toHaveTextContent('none')
  })

  it('login setzt den Nutzer aus der API-Antwort', async () => {
    mockedApi.me.mockRejectedValue(new Error('401'))
    mockedApi.login.mockResolvedValue(meFixture)
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByRole('button', { name: 'login' }))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))
  })

  it('logout ruft die API auf und setzt den Nutzer auf null', async () => {
    mockedApi.me.mockResolvedValue(meFixture)
    mockedApi.logout.mockResolvedValue(undefined)
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))
    await userEvent.click(screen.getByRole('button', { name: 'logout' }))
    expect(mockedApi.logout).toHaveBeenCalled()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
  })

  it('setzt den Nutzer auf null, wenn ein API-Aufruf mit 401 antwortet', async () => {
    mockedApi.me.mockResolvedValue(meFixture)
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))

    act(() => notifyUnauthorized(401))

    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
  })

  it('meldet den Haken beim Unmount wieder ab', async () => {
    mockedApi.me.mockResolvedValue(meFixture)
    const { unmount } = renderProbe()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))

    unmount()

    expect(vi.mocked(setUnauthorizedHandler)).toHaveBeenLastCalledWith(null)
    expect(() => notifyUnauthorized(401)).not.toThrow()
  })

  describe('useAuth außerhalb von AuthProvider', () => {
    let consoleErrorSpy: ReturnType<typeof vi.spyOn>

    beforeEach(() => {
      consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    })

    afterEach(() => {
      consoleErrorSpy.mockRestore()
    })

    it('wirft einen Fehler', () => {
      function BareProbe() {
        useAuth()
        return null
      }
      expect(() => render(<BareProbe />)).toThrow(
        'useAuth muss innerhalb von AuthProvider verwendet werden',
      )
    })
  })
})
