import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { AuthProvider } from '../auth/AuthContext'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { LinkLoginPage } from './LinkLoginPage'

vi.mock('../api/auth', () => ({
  authApi: { me: vi.fn(), requestCode: vi.fn(), verifyCode: vi.fn(), linkLogin: vi.fn(), logout: vi.fn() },
}))

const navigateMock = vi.fn()
vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigateMock }
})

const mockedApi = authApi as unknown as {
  me: ReturnType<typeof vi.fn>
  linkLogin: ReturnType<typeof vi.fn>
}

const meFixture = { userId: 1, email: 'a@b.de', displayName: 'A', platformRole: 'USER', memberships: [] }

function seite(pfad = '/login/link?token=tok-123') {
  return (
    <MemoryRouter initialEntries={[pfad]}>
      <AuthProvider>
        <LinkLoginPage />
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('LinkLoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedApi.me.mockRejectedValue(new Error('401'))
  })

  it('löst beim Laden genau einmal das Token aus dem Query-Parameter ein und leitet weiter', async () => {
    mockedApi.linkLogin.mockResolvedValue(meFixture)
    render(seite())
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/', { replace: true, state: { autoRoute: true } }))
    expect(mockedApi.linkLogin).toHaveBeenCalledTimes(1)
    expect(mockedApi.linkLogin).toHaveBeenCalledWith('tok-123')
  })

  it('zeigt eine Fortschrittsanzeige, solange das Einlösen läuft', async () => {
    mockedApi.linkLogin.mockReturnValue(new Promise(() => {}))
    render(seite())
    expect(await screen.findByRole('progressbar')).toBeInTheDocument()
    expect(screen.getByText('Anmeldung läuft …')).toBeInTheDocument()
    expect(navigateMock).not.toHaveBeenCalled()
  })

  it('zeigt bei 401 dieselbe Meldung wie die Code-Eingabe und einen Weg zurück zur Anmeldung', async () => {
    mockedApi.linkLogin.mockRejectedValue(new ApiError(401, 'x'))
    render(seite())
    expect(await screen.findByText('Code ungültig oder abgelaufen — fordere einen neuen an.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zurück zur Anmeldung' })).toHaveAttribute('href', '/login')
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(navigateMock).not.toHaveBeenCalled()
  })

  it('zeigt bei 403 die Meldung des Servers', async () => {
    mockedApi.linkLogin.mockRejectedValue(new ApiError(403, 'x', undefined, 'Konto gesperrt'))
    render(seite())
    expect(await screen.findByText('Konto gesperrt')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zurück zur Anmeldung' })).toBeInTheDocument()
  })

  it('zeigt bei 429 die Abweisung der Bremse', async () => {
    mockedApi.linkLogin.mockRejectedValue(
      new ApiError(429, 'x', undefined, 'Zu viele Versuche. Bitte in 15 Minuten erneut versuchen.'),
    )
    render(seite())
    expect(await screen.findByText('Zu viele Versuche. Bitte in 15 Minuten erneut versuchen.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zurück zur Anmeldung' })).toBeInTheDocument()
  })

  it('zeigt ohne Token den Fehler, ohne den Server zu fragen', async () => {
    render(seite('/login/link'))
    expect(await screen.findByText('Der Link enthält kein Token. Fordere auf der Anmeldung einen neuen Code an.'))
      .toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zurück zur Anmeldung' })).toBeInTheDocument()
    expect(mockedApi.linkLogin).not.toHaveBeenCalled()
  })

  it('zeigt Fortschritt und fehlendes Token auf Englisch', async () => {
    mockedApi.linkLogin.mockReturnValue(new Promise(() => {}))
    const { unmount } = renderMitSprache(seite(), 'en')
    expect(await screen.findByText('Signing in …')).toBeInTheDocument()
    unmount()
    renderMitSprache(seite('/login/link?token='), 'en')
    expect(await screen.findByText('The link contains no token. Request a new code on the sign-in page.'))
      .toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to sign-in' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Sign in', level: 2 })).toBeInTheDocument()
  })
})
