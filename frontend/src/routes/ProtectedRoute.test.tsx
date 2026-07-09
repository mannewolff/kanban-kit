import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { AuthProvider } from '../auth/AuthContext'
import { ProtectedRoute } from './ProtectedRoute'

vi.mock('../api/auth', () => ({
  authApi: { me: vi.fn(), login: vi.fn(), logout: vi.fn() },
}))

const mockedApi = authApi as unknown as { me: ReturnType<typeof vi.fn> }

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<div>GESCHÜTZTER INHALT</div>} />
          </Route>
          <Route path="/login" element={<div>LOGIN-SEITE</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  beforeEach(() => vi.clearAllMocks())

  it('leitet ohne Anmeldung auf die Login-Seite um', async () => {
    mockedApi.me.mockRejectedValue(new Error('401'))
    renderApp()
    expect(await screen.findByText('LOGIN-SEITE')).toBeInTheDocument()
  })

  it('zeigt den geschützten Inhalt für angemeldete Nutzer', async () => {
    mockedApi.me.mockResolvedValue({
      userId: 1, email: 'a@b.de', displayName: 'A', platformRole: 'USER', memberships: [],
    })
    renderApp()
    expect(await screen.findByText('GESCHÜTZTER INHALT')).toBeInTheDocument()
  })
})
