import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { AuthProvider, useAuth } from '../auth/AuthContext'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { ProfilePage } from './ProfilePage'

vi.mock('../api/auth', () => ({
  authApi: { me: vi.fn(), updateProfile: vi.fn(), login: vi.fn(), logout: vi.fn() },
}))

const mockedApi = authApi as unknown as {
  me: ReturnType<typeof vi.fn>
  updateProfile: ReturnType<typeof vi.fn>
}

const alt = { userId: 1, email: 'a@b.de', displayName: 'Alt', platformRole: 'USER', memberships: [] }

// Bildet ProtectedRoute nach: ProfilePage rendert erst, wenn der Benutzer geladen ist.
function Gate() {
  const { user, loading } = useAuth()
  if (loading || !user) return null
  return <ProfilePage />
}

function renderProfile() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <SnackbarProvider>
          <Gate />
        </SnackbarProvider>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedApi.me.mockResolvedValue({ ...alt })
  })

  it('zeigt leere Felder, solange kein Nutzer geladen ist', () => {
    mockedApi.me.mockReturnValue(new Promise(() => {})) // bleibt pending → user null
    render(
      <MemoryRouter>
        <AuthProvider>
          <ProfilePage />
        </AuthProvider>
      </MemoryRouter>,
    )
    expect(screen.getByLabelText('Anzeigename')).toHaveValue('')
    expect(screen.getByLabelText('E-Mail')).toHaveValue('')
  })

  it('lädt den aktuellen Anzeigenamen vorbefüllt', async () => {
    renderProfile()
    expect(await screen.findByLabelText('Anzeigename')).toHaveValue('Alt')
    expect(screen.getByLabelText('E-Mail')).toHaveValue('a@b.de')
  })

  it('speichert einen neuen Anzeigenamen und zeigt Erfolg', async () => {
    mockedApi.updateProfile.mockResolvedValue({ ...alt, displayName: 'Neu' })
    renderProfile()
    const input = await screen.findByLabelText('Anzeigename')
    await userEvent.clear(input)
    await userEvent.type(input, 'Neu')
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(mockedApi.updateProfile).toHaveBeenCalledWith('Neu')
    expect(await screen.findByText('Anzeigename gespeichert.')).toBeInTheDocument()
  })

  it('zeigt die Backend-Meldung bei fehlgeschlagenem Speichern', async () => {
    mockedApi.updateProfile.mockRejectedValue(new ApiError(400, 'Validierung fehlgeschlagen'))
    renderProfile()
    const input = await screen.findByLabelText('Anzeigename')
    await userEvent.clear(input)
    await userEvent.type(input, 'X')
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Validierung fehlgeschlagen')).toBeInTheDocument()
  })

  it('zeigt eine generische Meldung bei unerwartetem Fehler', async () => {
    mockedApi.updateProfile.mockRejectedValue(new Error('boom'))
    renderProfile()
    const input = await screen.findByLabelText('Anzeigename')
    await userEvent.clear(input)
    await userEvent.type(input, 'X')
    await userEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    expect(await screen.findByText('Speichern fehlgeschlagen.')).toBeInTheDocument()
  })
})
