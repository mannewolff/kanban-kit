import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { AuthProvider } from '../auth/AuthContext'
import { ProtectedRoute } from './ProtectedRoute'

vi.mock('../api/auth', () => ({
  authApi: { me: vi.fn(), login: vi.fn(), logout: vi.fn() },
}))

const mockedApi = authApi as unknown as { me: ReturnType<typeof vi.fn> }

/** Login-Seite, die den mitgereisten Herkunfts-State sichtbar macht. */
function Herkunft() {
  const { state } = useLocation()
  return <div data-testid="herkunft">{(state as { from?: string } | null)?.from ?? ""}</div>
}

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

  /**
   * AK 1 Satz 2 (Issue #1082): Wer vor dem Anmelden eine bestimmte Adresse aufgerufen hat, landet
   * nach dem Anmelden dort. Die Adresse reist als Router-State und nicht als Query-Parameter — ein
   * State verlässt die Anwendung nicht und landet in keinem Protokoll (Plan #1072 E9).
   */
  it('gibt die aufgerufene Adresse als state.from an die Login-Seite mit (#1082)', async () => {
    mockedApi.me.mockRejectedValue(new Error('401'))
    render(
      <MemoryRouter initialEntries={['/boards/7/leitstand?filter=x']}>
        <AuthProvider>
          <Routes>
            <Route element={<ProtectedRoute />}>
              <Route path="/boards/:boardId/leitstand" element={<div>GESCHÜTZTER INHALT</div>} />
            </Route>
            <Route path="/login" element={<Herkunft />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByTestId('herkunft')).toHaveTextContent('/boards/7/leitstand?filter=x')
  })
})
