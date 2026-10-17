import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { AuthProvider } from '../auth/AuthContext'
import { LoginPage } from './LoginPage'
import { renderMitSprache } from '../i18n/renderMitSprache'

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
  requestCode: ReturnType<typeof vi.fn>
  verifyCode: ReturnType<typeof vi.fn>
}

const meFixture = { userId: 1, email: 'a@b.de', displayName: 'A', platformRole: 'USER', memberships: [] }
const NEUTRALE_ANTWORT = 'Falls es ein Konto mit dieser Adresse gibt, ist ein Code unterwegs.'

function renderLogin(initialEntries: Parameters<typeof MemoryRouter>[0]['initialEntries'] = ['/login']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function codeAnfordern() {
  await userEvent.type(await screen.findByLabelText(/E-Mail/), 'a@b.de')
  await userEvent.click(screen.getByRole('button', { name: 'Code anfordern' }))
  return screen.findByRole('textbox', { name: /Code aus der E-Mail/ })
}

async function codeEingeben(code = '042817') {
  const feld = await codeAnfordern()
  await userEvent.type(feld, code)
  await userEvent.click(screen.getByRole('button', { name: 'Anmelden' }))
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedApi.me.mockRejectedValue(new Error('401'))
    mockedApi.requestCode.mockResolvedValue(undefined)
  })

  it('fragt im ersten Schritt nur nach der E-Mail-Adresse', async () => {
    renderLogin()
    expect(await screen.findByRole('button', { name: 'Code anfordern' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /E-Mail/ })).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /Code aus der E-Mail/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Registrieren' })).toBeInTheDocument()
  })

  it('zeigt weder ein Passwortfeld noch einen Link „Passwort vergessen“', async () => {
    renderLogin()
    await screen.findByRole('button', { name: 'Code anfordern' })
    expect(screen.queryByLabelText(/Passwort/)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Passwort vergessen/ })).not.toBeInTheDocument()

    await codeAnfordern()
    expect(screen.queryByLabelText(/Passwort/)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Passwort vergessen/ })).not.toBeInTheDocument()
  })

  it('fordert den Code an und zeigt die neutrale Antwort samt Code-Feld', async () => {
    renderLogin()
    const feld = await codeAnfordern()
    expect(mockedApi.requestCode).toHaveBeenCalledWith('a@b.de')
    expect(screen.getByText(NEUTRALE_ANTWORT)).toBeInTheDocument()
    expect(feld).toHaveAttribute('inputmode', 'numeric')
    expect(feld).toHaveAttribute('autocomplete', 'one-time-code')
    expect(feld).toBeRequired()
  })

  it('meldet mit dem eingetippten Code über verifyCode an und leitet mit autoRoute weiter', async () => {
    mockedApi.verifyCode.mockResolvedValue(meFixture)
    renderLogin()
    await codeEingeben()
    expect(mockedApi.verifyCode).toHaveBeenCalledWith('a@b.de', '042817')
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/', { replace: true, state: { autoRoute: true } }),
    )
  })

  /**
   * AK 1 Satz 2 (Issue #1082): Die vor dem Anmelden aufgerufene Adresse gewinnt gegen die
   * Startseite. `ProtectedRoute` gibt sie als `state.from` mit.
   */
  it('steuert nach dem Anmelden die vor dem Anmelden aufgerufene Adresse an (#1082)', async () => {
    mockedApi.verifyCode.mockResolvedValue(meFixture)
    renderLogin([{ pathname: '/login', state: { from: '/boards/7/leitstand' } }])
    await codeEingeben()
    await waitFor(() =>
      expect(navigateMock).toHaveBeenCalledWith('/boards/7/leitstand', { replace: true }),
    )
  })

  /** E3 (Plan #1801): Nach der Registrierung kommt der Code sofort, die Seite beginnt im Code-Schritt. */
  it('beginnt nach der Registrierung im Code-Schritt, ohne selbst einen Code anzufordern', async () => {
    mockedApi.verifyCode.mockResolvedValue(meFixture)
    renderLogin([{ pathname: '/login', state: { codeFuer: 'neu@b.de' } }])
    const feld = await screen.findByRole('textbox', { name: /Code aus der E-Mail/ })
    expect(screen.getByText(NEUTRALE_ANTWORT)).toBeInTheDocument()
    expect(mockedApi.requestCode).not.toHaveBeenCalled()
    await userEvent.type(feld, '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Anmelden' }))
    expect(mockedApi.verifyCode).toHaveBeenCalledWith('neu@b.de', '123456')
  })

  it('fordert auf Wunsch einen neuen Code an und leert das Code-Feld', async () => {
    renderLogin()
    const feld = await codeAnfordern()
    await userEvent.type(feld, '111111')
    await userEvent.click(screen.getByRole('button', { name: 'Neuen Code anfordern' }))
    await waitFor(() => expect(mockedApi.requestCode).toHaveBeenCalledTimes(2))
    expect(mockedApi.requestCode).toHaveBeenLastCalledWith('a@b.de')
    expect(feld).toHaveValue('')
    expect(screen.getByText(NEUTRALE_ANTWORT)).toBeInTheDocument()
  })

  it('zeigt bei 401 auf den Code den Hinweis, einen neuen anzufordern', async () => {
    mockedApi.verifyCode.mockRejectedValue(new ApiError(401, 'Unauthorized'))
    renderLogin()
    await codeEingeben()
    expect(
      await screen.findByText('Code ungültig oder abgelaufen — fordere einen neuen an.'),
    ).toBeInTheDocument()
    expect(navigateMock).not.toHaveBeenCalled()
  })

  it('zeigt bei 403 die Meldung des Servers für ein gesperrtes Konto', async () => {
    mockedApi.verifyCode.mockRejectedValue(new ApiError(403, 'Konto gesperrt', undefined, 'Konto gesperrt'))
    renderLogin()
    await codeEingeben()
    expect(await screen.findByText('Konto gesperrt')).toBeInTheDocument()
  })

  it('zeigt bei 403 die Meldung des Servers für ein Konto, das auf die Freigabe wartet', async () => {
    const detail =
      'Das Konto wartet auf die Freigabe durch einen Plattform-Admin.' +
      ' Bitte wenden Sie sich an den Betreiber dieser Instanz.'
    // message UND detail, wie client.ts sie aus einem RFC-9457-Body baut: Angezeigt wird
    // ausschliesslich detail (apiErrorMessage), message bleibt der nicht anzeigbare Rohwert.
    mockedApi.verifyCode.mockRejectedValue(new ApiError(403, detail, undefined, detail))
    renderLogin()
    await codeEingeben()
    expect(await screen.findByText(detail)).toBeInTheDocument()
  })

  it('zeigt bei 429 auf die Anforderung die Grenze je Adresse und bleibt im ersten Schritt', async () => {
    const detail = 'Zu viele Code-Anforderungen für diese Adresse. Bitte versuche es später noch einmal.'
    mockedApi.requestCode.mockRejectedValue(new ApiError(429, 'Too Many Requests', undefined, detail))
    renderLogin()
    await userEvent.type(await screen.findByLabelText(/E-Mail/), 'a@b.de')
    await userEvent.click(screen.getByRole('button', { name: 'Code anfordern' }))
    expect(await screen.findByText(detail)).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /Code aus der E-Mail/ })).not.toBeInTheDocument()
  })

  it('zeigt bei 429 auf den Code die Bremse je Herkunft', async () => {
    const detail = 'Zu viele Versuche. Bitte in 15 Minuten erneut versuchen.'
    mockedApi.verifyCode.mockRejectedValue(new ApiError(429, 'Too Many Requests', undefined, detail))
    renderLogin()
    await codeEingeben()
    expect(await screen.findByText(detail)).toBeInTheDocument()
  })

  it('zeigt bei 429 ohne lesbare Meldung den allgemeinen Bremsen-Hinweis', async () => {
    mockedApi.verifyCode.mockRejectedValue(new ApiError(429, '<html>proxy</html>'))
    renderLogin()
    await codeEingeben()
    expect(await screen.findByText('Zu viele Versuche. Bitte später erneut versuchen.')).toBeInTheDocument()
  })

  it('zeigt bei Netzfehler (kein ApiError) einen generischen Fallback', async () => {
    mockedApi.requestCode.mockRejectedValue(new Error('network down'))
    renderLogin()
    await userEvent.type(await screen.findByLabelText(/E-Mail/), 'a@b.de')
    await userEvent.click(screen.getByRole('button', { name: 'Code anfordern' }))
    expect(await screen.findByText('Anmeldung fehlgeschlagen. Bitte später erneut versuchen.')).toBeInTheDocument()
  })

  it('räumt eine alte Fehlermeldung beim neuen Versuch ab', async () => {
    mockedApi.verifyCode.mockRejectedValueOnce(new ApiError(401, 'nope'))
    renderLogin()
    await codeEingeben()
    const meldung = 'Code ungültig oder abgelaufen — fordere einen neuen an.'
    expect(await screen.findByText(meldung)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Neuen Code anfordern' }))
    await waitFor(() => expect(screen.queryByText(meldung)).not.toBeInTheDocument())
  })
})

describe('LoginPage auf Englisch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedApi.me.mockRejectedValue(new Error('401'))
    mockedApi.requestCode.mockResolvedValue(undefined)
  })

  it('zeigt beide Schritte und die Fehlermeldungen auf Englisch', async () => {
    mockedApi.verifyCode
      .mockRejectedValueOnce(new ApiError(401, 'nope'))
      .mockRejectedValueOnce(new ApiError(429, ''))
      .mockRejectedValueOnce(new Error('kaputt'))
    renderMitSprache(
      <MemoryRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>,
      'en',
    )
    expect(await screen.findByRole('heading', { name: 'Sign in', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sign up' })).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText(/Email/), 'a@b.de')
    await userEvent.click(screen.getByRole('button', { name: 'Request code' }))
    expect(await screen.findByText('If an account with this address exists, a code is on its way.')).toBeInTheDocument()
    await userEvent.type(screen.getByRole('textbox', { name: /Code from the email/ }), '042817')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Code invalid or expired — request a new one.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Too many attempts. Please try again later.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Sign-in failed. Please try again later.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Request new code' })).toBeInTheDocument()
  })
})
