import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { AuthProvider, useAuth } from '../auth/AuthContext'
import { SnackbarProvider } from '../components/SnackbarProvider'
import { formatierer } from '../i18n/format'
import { ProfilePage } from './ProfilePage'
import { renderMitSprache } from '../i18n/renderMitSprache'

vi.mock('../api/auth', () => ({
  authApi: { me: vi.fn(), updateProfile: vi.fn(), login: vi.fn(), logout: vi.fn(), eigenesVorschauProtokoll: vi.fn() },
}))

const mockedApi = authApi as unknown as {
  me: ReturnType<typeof vi.fn>
  updateProfile: ReturnType<typeof vi.fn>
  eigenesVorschauProtokoll: ReturnType<typeof vi.fn>
}

const vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }

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
    mockedApi.eigenesVorschauProtokoll.mockResolvedValue([])
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

describe('ProfilePage auf Englisch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedApi.me.mockResolvedValue({ ...alt })
    mockedApi.eigenesVorschauProtokoll.mockResolvedValue([])
  })

  const renderEn = () =>
    renderMitSprache(
      <MemoryRouter>
        <AuthProvider>
          <SnackbarProvider>
            <Gate />
          </SnackbarProvider>
        </AuthProvider>
      </MemoryRouter>,
      'en',
    )

  it('beschriftet das Profil auf Englisch und bestätigt das Speichern', async () => {
    mockedApi.updateProfile.mockResolvedValue({ ...alt, displayName: 'Neu' })
    renderEn()
    expect(await screen.findByRole('heading', { name: 'Profile' })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('a@b.de')
    const feld = screen.getByLabelText('Display name')
    await userEvent.clear(feld)
    await userEvent.type(feld, 'Neu')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Display name saved.')).toBeInTheDocument()
  })

  it('meldet einen Fehlschlag ohne Server-Meldung auf Englisch', async () => {
    mockedApi.updateProfile.mockRejectedValue(new Error('netz'))
    renderEn()
    await userEvent.click(await screen.findByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Saving failed.')).toBeInTheDocument()
  })
})

describe('ProfilePage: Ansicht als XY', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedApi.me.mockResolvedValue({ ...alt })
    mockedApi.eigenesVorschauProtokoll.mockResolvedValue([])
  })

  it('Szenario: XY erfährt von der Ansicht', async () => {
    mockedApi.eigenesVorschauProtokoll.mockResolvedValue([
      {
        adminName: 'Ada',
        adminKontoGeloescht: false,
        gestartetAm: '2026-10-26T09:00:00Z',
        laeuftAbAm: '2026-10-26T09:30:00Z',
        beendetAm: '2026-10-26T09:12:00Z',
        endgrund: 'BEENDET',
      },
    ])
    renderProfile()

    expect(await screen.findByRole('heading', { name: 'Ansichten durch Admins' })).toBeInTheDocument()
    const eintrag = await screen.findByRole('listitem')
    expect(eintrag).toHaveTextContent('Ada')
    expect(eintrag).toHaveTextContent(`Start ${formatierer('de').datumUhrzeit('2026-10-26T09:00:00Z')}`)
    expect(mockedApi.eigenesVorschauProtokoll).toHaveBeenCalledTimes(1)
  })

  it('Szenario: Persönliches von XY bleibt verborgen', async () => {
    mockedApi.me.mockResolvedValue({ ...alt, displayName: 'Grace', email: 'grace@x.de', vorschau })
    renderProfile()

    expect(await screen.findByRole('note')).toHaveTextContent('Dieser Bereich wird in der Vorschau nicht gezeigt.')
    expect(screen.getByRole('heading', { name: 'Profil' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Anzeigename')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('E-Mail')).not.toBeInTheDocument()
    expect(screen.queryByDisplayValue('grace@x.de')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Ansichten durch Admins' })).not.toBeInTheDocument()
    // Der Server wiese den Abruf in der Vorschau ab (E5); die Seite stellt ihn gar nicht erst.
    expect(mockedApi.eigenesVorschauProtokoll).not.toHaveBeenCalled()
  })
})
