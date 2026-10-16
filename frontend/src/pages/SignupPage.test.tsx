import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { SignupPage } from './SignupPage'
import { renderMitSprache } from '../i18n/renderMitSprache'

vi.mock('../api/auth', () => ({
  authApi: { register: vi.fn() },
}))

const navigateMock = vi.fn()
vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigateMock }
})

const mockedApi = authApi as unknown as {
  register: ReturnType<typeof vi.fn>
}

function renderSignup() {
  return render(
    <MemoryRouter>
      <SignupPage />
    </MemoryRouter>,
  )
}

async function submitSignup() {
  renderSignup()
  await userEvent.type(screen.getByLabelText(/Anzeigename/), 'Ada')
  await userEvent.type(screen.getByLabelText(/E-Mail/), 'a@b.de')
  await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }))
}

describe('SignupPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fragt nur nach E-Mail und Anzeigename, nicht nach einem Passwort', () => {
    renderSignup()
    expect(screen.getByLabelText(/Anzeigename/)).toBeInTheDocument()
    expect(screen.getByLabelText(/E-Mail/)).toBeInTheDocument()
    expect(screen.queryByLabelText(/Passwort/)).not.toBeInTheDocument()
    expect(screen.getAllByRole('textbox')).toHaveLength(2)
  })

  it('registriert mit E-Mail und Anzeigename und wechselt in den Code-Schritt der Anmeldung (E3)', async () => {
    mockedApi.register.mockResolvedValue({ id: 1, email: 'a@b.de' })
    await submitSignup()
    expect(mockedApi.register).toHaveBeenCalledWith('a@b.de', 'Ada')
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/login', { state: { codeFuer: 'a@b.de' } }))
  })

  it('zeigt bei 409 eine Meldung zur bereits registrierten E-Mail', async () => {
    mockedApi.register.mockRejectedValue(new ApiError(409, 'conflict'))
    await submitSignup()
    expect(await screen.findByText('Diese E-Mail-Adresse ist bereits registriert.')).toBeInTheDocument()
    expect(navigateMock).not.toHaveBeenCalled()
  })

  it('zeigt bei anderem Fehler eine generische Meldung', async () => {
    mockedApi.register.mockRejectedValue(new Error('network down'))
    await submitSignup()
    expect(await screen.findByText('Registrierung fehlgeschlagen. Bitte Eingaben prüfen.')).toBeInTheDocument()
  })

  it('zeigt bei 429 die Abweisung der Zaehlbremse', async () => {
    mockedApi.register.mockRejectedValue(
      new ApiError(429, 'Too Many Requests', undefined, 'Zu viele Versuche. Bitte in 15 Minuten erneut versuchen.'),
    )
    await submitSignup()
    expect(await screen.findByText('Zu viele Versuche. Bitte in 15 Minuten erneut versuchen.')).toBeInTheDocument()
  })
})

describe('SignupPage auf Englisch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  async function submitAufEnglisch() {
    renderMitSprache(
      <MemoryRouter>
        <SignupPage />
      </MemoryRouter>,
      'en',
    )
    await userEvent.type(screen.getByLabelText(/Display name/), 'Ada')
    await userEvent.type(screen.getByLabelText(/Email/), 'a@b.de')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
  }

  it('zeigt Formular und Konflikt auf Englisch', async () => {
    mockedApi.register.mockRejectedValue(new ApiError(409, 'x'))
    await submitAufEnglisch()
    expect(screen.getByRole('heading', { name: 'Sign up', level: 2 })).toBeInTheDocument()
    expect(screen.queryByLabelText(/Password/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to sign-in' })).toBeInTheDocument()
    expect(await screen.findByText('This email address is already registered.')).toBeInTheDocument()
  })

  it('zeigt Zählbremse und allgemeinen Fehler auf Englisch', async () => {
    mockedApi.register.mockRejectedValue(new ApiError(429, ''))
    await submitAufEnglisch()
    expect(await screen.findByText('Too many attempts. Please try again later.')).toBeInTheDocument()
  })

  it('zeigt den allgemeinen Fehler auf Englisch', async () => {
    mockedApi.register.mockRejectedValue(new ApiError(400, 'x'))
    await submitAufEnglisch()
    expect(await screen.findByText('Sign-up failed. Please check your input.')).toBeInTheDocument()
  })
})
