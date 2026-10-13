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
  await userEvent.type(screen.getByLabelText(/Passwort/, { selector: 'input' }), 'geheim123')
  await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }))
}

describe('SignupPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('rendert das Registrierungsformular', () => {
    renderSignup()
    expect(screen.getByLabelText(/Anzeigename/)).toBeInTheDocument()
    expect(screen.getByLabelText(/E-Mail/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Passwort/, { selector: 'input' })).toBeInTheDocument()
  })

  it('ruft register mit den Eingaben auf und navigiert zu /verify', async () => {
    mockedApi.register.mockResolvedValue({ id: 1, email: 'a@b.de' })
    await submitSignup()
    expect(mockedApi.register).toHaveBeenCalledWith('a@b.de', 'geheim123', 'Ada')
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/verify'))
  })

  it('zeigt bei 409 eine Meldung zur bereits registrierten E-Mail', async () => {
    mockedApi.register.mockRejectedValue(new ApiError(409, 'conflict'))
    await submitSignup()
    expect(await screen.findByText('Diese E-Mail-Adresse ist bereits registriert.')).toBeInTheDocument()
  })

  it('zeigt bei anderem Fehler eine generische Meldung', async () => {
    mockedApi.register.mockRejectedValue(new Error('network down'))
    await submitSignup()
    expect(await screen.findByText(/Registrierung fehlgeschlagen/)).toBeInTheDocument()
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
    await userEvent.type(screen.getByLabelText(/Password/, { selector: 'input' }), 'geheim123')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
  }

  it('zeigt Formular und Konflikt auf Englisch', async () => {
    mockedApi.register.mockRejectedValue(new ApiError(409, 'x'))
    await submitAufEnglisch()
    expect(screen.getByRole('heading', { name: 'Sign up', level: 2 })).toBeInTheDocument()
    expect(screen.getByText('At least 8 characters')).toBeInTheDocument()
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
    expect(
      await screen.findByText('Sign-up failed. Please check your input (password at least 8 characters).'),
    ).toBeInTheDocument()
  })
})
