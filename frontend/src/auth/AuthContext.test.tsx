import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { notifyUnauthorized, setUnauthorizedHandler, setVorschauKennung, vorschauKopf } from '../api/client'
import { abmelden as aboAbmelden } from '../lib/pushAbo'
import { AuthProvider, useAuth } from './AuthContext'

const farbe = vi.hoisted(() => ({ setMode: vi.fn() }))
vi.mock('@mui/material/styles', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@mui/material/styles')>()),
  useColorScheme: () => ({ setMode: farbe.setMode }),
}))

vi.mock('../lib/pushAbo', () => ({ abmelden: vi.fn() }))

vi.mock('../api/auth', () => ({
  authApi: { me: vi.fn(), verifyCode: vi.fn(), linkLogin: vi.fn(), logout: vi.fn() },
}))

// Nur `setUnauthorizedHandler` wird umhüllt, damit das Abmelden beim Unmount beobachtbar ist;
// der Handler-Zustand bleibt der des echten Moduls, das `notifyUnauthorized` mitbenutzt.
vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, setUnauthorizedHandler: vi.fn(actual.setUnauthorizedHandler) }
})

const mockedApi = authApi as unknown as {
  me: ReturnType<typeof vi.fn>
  verifyCode: ReturnType<typeof vi.fn>
  linkLogin: ReturnType<typeof vi.fn>
  logout: ReturnType<typeof vi.fn>
}

const meFixture = {
  userId: 1, email: 'a@b.de', displayName: 'A', platformRole: 'USER' as const, memberships: [],
  colorScheme: 'SYSTEM' as const, vorschau: null,
}

const vorschau = { kennung: 'vk-42', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }

function Probe() {
  const { user, loading, vorschau: laufend, loginWithCode, loginWithLink, logout, refresh } = useAuth()
  return (
    <div>
      <span data-testid="loading">{String(loading)}</span>
      <span data-testid="user">{user?.email ?? 'none'}</span>
      <span data-testid="vorschau">{laufend ? `${laufend.kennung} ${laufend.zielName} ${laufend.adminName}` : 'keine'}</span>
      <button onClick={() => void refresh()}>refresh</button>
      <button onClick={() => void loginWithCode('a@b.de', '042817')}>code</button>
      <button onClick={() => void loginWithLink('tok-123')}>link</button>
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

const pushAbmelden = vi.mocked(aboAbmelden)

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    pushAbmelden.mockResolvedValue(undefined)
  })

  afterEach(() => {
    setVorschauKennung(null)
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

  it('loginWithCode meldet über verifyCode an und setzt den Nutzer aus der Antwort', async () => {
    mockedApi.me.mockRejectedValue(new Error('401'))
    mockedApi.verifyCode.mockResolvedValue(meFixture)
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByRole('button', { name: 'code' }))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))
    expect(mockedApi.verifyCode).toHaveBeenCalledWith('a@b.de', '042817')
  })

  it('loginWithLink meldet über linkLogin an und setzt den Nutzer aus der Antwort', async () => {
    mockedApi.me.mockRejectedValue(new Error('401'))
    mockedApi.linkLogin.mockResolvedValue(meFixture)
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
    await userEvent.click(screen.getByRole('button', { name: 'link' }))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))
    expect(mockedApi.linkLogin).toHaveBeenCalledWith('tok-123')
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

  it('logout beendet vorher das Abo für Benachrichtigungen auf diesem Gerät', async () => {
    mockedApi.me.mockResolvedValue(meFixture)
    const reihenfolge: string[] = []
    pushAbmelden.mockImplementation(() => {
      reihenfolge.push('abo')
      return Promise.resolve()
    })
    mockedApi.logout.mockImplementation(() => {
      reihenfolge.push('logout')
      return Promise.resolve()
    })
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))
    await userEvent.click(screen.getByRole('button', { name: 'logout' }))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
    expect(reihenfolge).toEqual(['abo', 'logout'])
  })

  it('logout meldet ab, auch wenn das Beenden des Abos scheitert', async () => {
    mockedApi.me.mockResolvedValue(meFixture)
    mockedApi.logout.mockResolvedValue(undefined)
    pushAbmelden.mockRejectedValue(new TypeError('Failed to fetch'))
    renderProbe()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))
    await userEvent.click(screen.getByRole('button', { name: 'logout' }))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
    expect(mockedApi.logout).toHaveBeenCalled()
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

  describe('Vorschau „Ansicht als XY“ (Plan #1914, E14)', () => {
    it('stellt die laufende Vorschau aus /api/me bereit und setzt die Kopfzeile', async () => {
      mockedApi.me.mockResolvedValue({ ...meFixture, vorschau })
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('vorschau')).toHaveTextContent('vk-42 Grace Ada'))
      expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'vk-42' })
    })

    it('setzt die Kopfzeile schon, wenn die Kinder den neuen Nutzer zum ersten Mal sehen', async () => {
      mockedApi.me.mockResolvedValue({ ...meFixture, vorschau })
      const gesehen: string[] = []
      function Kind() {
        const { user } = useAuth()
        // Ein Kind lädt in seinem eigenen Effekt — der läuft vor jedem Effekt des Providers.
        useEffect(() => {
          if (user) gesehen.push(vorschauKopf()['X-Vorschau'])
        }, [user])
        return null
      }
      render(
        <AuthProvider>
          <Kind />
        </AuthProvider>,
      )
      await waitFor(() => expect(gesehen).toEqual(['vk-42']))
    })

    it('meldet außerhalb der Vorschau keine und die Kopfzeile „keine“', async () => {
      setVorschauKennung('vk-alt')
      mockedApi.me.mockResolvedValue(meFixture)
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))
      expect(screen.getByTestId('vorschau')).toHaveTextContent('keine')
      expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'keine' })
    })

    it('übernimmt nach refresh den Wechsel aus der Vorschau zurück in die eigene Sicht', async () => {
      mockedApi.me.mockResolvedValueOnce({ ...meFixture, vorschau }).mockResolvedValueOnce(meFixture)
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('vorschau')).toHaveTextContent('vk-42'))
      await userEvent.click(screen.getByRole('button', { name: 'refresh' }))
      await waitFor(() => expect(screen.getByTestId('vorschau')).toHaveTextContent('keine'))
      expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'keine' })
    })

    it('setzt die Kopfzeile zurück, wenn /api/me scheitert', async () => {
      setVorschauKennung('vk-alt')
      mockedApi.me.mockRejectedValue(new Error('401'))
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
      expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'keine' })
    })

    it('setzt die Kopfzeile nach dem Abmelden zurück', async () => {
      mockedApi.me.mockResolvedValue({ ...meFixture, vorschau })
      mockedApi.logout.mockResolvedValue(undefined)
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('vorschau')).toHaveTextContent('vk-42'))
      await userEvent.click(screen.getByRole('button', { name: 'logout' }))
      await waitFor(() => expect(screen.getByTestId('vorschau')).toHaveTextContent('keine'))
      expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'keine' })
    })

    it('setzt die Kopfzeile nach einem 401 zurück', async () => {
      mockedApi.me.mockResolvedValue({ ...meFixture, vorschau })
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('vorschau')).toHaveTextContent('vk-42'))
      act(() => notifyUnauthorized(401))
      await waitFor(() => expect(screen.getByTestId('vorschau')).toHaveTextContent('keine'))
      expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'keine' })
    })

    it('übernimmt die Vorschau auch aus der Anmeldung', async () => {
      mockedApi.me.mockRejectedValue(new Error('401'))
      mockedApi.verifyCode.mockResolvedValue({ ...meFixture, vorschau })
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
      await userEvent.click(screen.getByRole('button', { name: 'code' }))
      await waitFor(() => expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'vk-42' }))
    })

    it('übernimmt die Vorschau auch aus dem Link', async () => {
      mockedApi.me.mockRejectedValue(new Error('401'))
      mockedApi.linkLogin.mockResolvedValue({ ...meFixture, vorschau })
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
      await userEvent.click(screen.getByRole('button', { name: 'link' }))
      await waitFor(() => expect(vorschauKopf()).toEqual({ 'X-Vorschau': 'vk-42' }))
    })

    it('wendet in der Vorschau die Farbwahl des Admins an, die /api/me dort liefert (E5)', async () => {
      // In der Vorschau beschreibt /api/me XY, trägt aber die Farbwahl des Admins (Issue #1921).
      mockedApi.me.mockResolvedValue({ ...meFixture, colorScheme: 'DARK', vorschau })
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('vorschau')).toHaveTextContent('vk-42'))
      expect(farbe.setMode).toHaveBeenLastCalledWith('dark')
    })
  })

  describe('Erscheinungsbild am Konto (#1911)', () => {
    it('gilt bis zum Laden der Selbstauskunft „System"', () => {
      mockedApi.me.mockReturnValue(new Promise(() => {}))
      renderProbe()
      expect(farbe.setMode).toHaveBeenLastCalledWith('system')
    })

    it('setzt nach dem Laden von /api/me die Kontowahl', async () => {
      mockedApi.me.mockResolvedValue({ ...meFixture, colorScheme: 'DARK' })
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('a@b.de'))
      expect(farbe.setMode).toHaveBeenLastCalledWith('dark')
    })

    it('setzt nach der Anmeldung die Kontowahl', async () => {
      mockedApi.me.mockRejectedValue(new Error('401'))
      mockedApi.verifyCode.mockResolvedValue({ ...meFixture, colorScheme: 'LIGHT' })
      renderProbe()
      await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'))
      await userEvent.click(screen.getByRole('button', { name: 'code' }))
      await waitFor(() => expect(farbe.setMode).toHaveBeenLastCalledWith('light'))
    })

    it('fällt nach dem Abmelden auf „System" zurück', async () => {
      mockedApi.me.mockResolvedValue({ ...meFixture, colorScheme: 'DARK' })
      mockedApi.logout.mockResolvedValue(undefined)
      renderProbe()
      await waitFor(() => expect(farbe.setMode).toHaveBeenLastCalledWith('dark'))
      await userEvent.click(screen.getByRole('button', { name: 'logout' }))
      await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'))
      expect(farbe.setMode).toHaveBeenLastCalledWith('system')
    })

    it('fällt nach einem 401 auf „System" zurück', async () => {
      mockedApi.me.mockResolvedValue({ ...meFixture, colorScheme: 'LIGHT' })
      renderProbe()
      await waitFor(() => expect(farbe.setMode).toHaveBeenLastCalledWith('light'))
      act(() => notifyUnauthorized(401))
      await waitFor(() => expect(farbe.setMode).toHaveBeenLastCalledWith('system'))
    })
  })
})
