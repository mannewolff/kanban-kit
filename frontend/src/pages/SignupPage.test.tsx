import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { SignupPage } from './SignupPage'

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
})
