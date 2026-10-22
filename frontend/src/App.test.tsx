import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'
import { authApi } from './api/auth'
import { AuthProvider } from './auth/AuthContext'

vi.mock('./api/auth', () => ({
  authApi: { me: vi.fn(), requestCode: vi.fn(), verifyCode: vi.fn(), linkLogin: vi.fn(), logout: vi.fn() },
}))

const mockedApi = authApi as unknown as {
  me: ReturnType<typeof vi.fn>
  linkLogin: ReturnType<typeof vi.fn>
}

function renderApp(pfad: string) {
  return render(
    <MemoryRouter initialEntries={[pfad]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('App-Routen der Anmeldung', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedApi.me.mockRejectedValue(new Error('401'))
  })

  it.each(['/forgot', '/reset?token=alt', '/verify?token=alt'])('leitet den alten Pfad %s auf die Anmeldung', async (pfad) => {
    renderApp(pfad)
    expect(await screen.findByRole('button', { name: 'Code anfordern' }, { timeout: 5000 })).toBeInTheDocument()
    expect(mockedApi.linkLogin).not.toHaveBeenCalled()
  })

  it('lädt unter /login/link die Link-Seite und löst das Token ein', async () => {
    mockedApi.linkLogin.mockReturnValue(new Promise(() => {}))
    renderApp('/login/link?token=tok-9')
    expect(await screen.findByText('Anmeldung läuft …', undefined, { timeout: 5000 })).toBeInTheDocument()
    await waitFor(() => expect(mockedApi.linkLogin).toHaveBeenCalledWith('tok-9'))
    expect(screen.queryByRole('button', { name: 'Code anfordern' })).not.toBeInTheDocument()
  })
})
