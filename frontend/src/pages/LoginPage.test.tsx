import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { AuthProvider } from '../auth/AuthContext'
import { LoginPage } from './LoginPage'

vi.mock('../api/auth', () => ({
  authApi: { me: vi.fn(), login: vi.fn(), logout: vi.fn() },
}))

const mockedApi = authApi as unknown as {
  me: ReturnType<typeof vi.fn>
  login: ReturnType<typeof vi.fn>
}

function renderLogin() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedApi.me.mockRejectedValue(new Error('401'))
  })

  it('rendert das Anmeldeformular', async () => {
    renderLogin()
    expect(await screen.findByRole('button', { name: 'Anmelden' })).toBeInTheDocument()
    expect(screen.getByLabelText(/E-Mail/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Passwort/)).toBeInTheDocument()
  })

  it('ruft login mit den Eingaben auf', async () => {
    mockedApi.login.mockResolvedValue({
      userId: 1, email: 'a@b.de', displayName: 'A', platformRole: 'USER', memberships: [],
    })
    renderLogin()
    await userEvent.type(screen.getByLabelText(/E-Mail/), 'a@b.de')
    await userEvent.type(screen.getByLabelText(/Passwort/), 'geheim123')
    await userEvent.click(screen.getByRole('button', { name: 'Anmelden' }))
    expect(mockedApi.login).toHaveBeenCalledWith('a@b.de', 'geheim123')
  })
})
