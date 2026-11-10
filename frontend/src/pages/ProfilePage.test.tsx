import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

const MEINE = '/api/me/benachrichtigungen'
const RUHEZEIT = `${MEINE}/ruhezeit`

interface Antwort {
  status: number
  body?: unknown
}

const projekt = (projectId: number, projectName: string, projektAn: boolean, personAn: boolean) => ({
  projectId,
  projectName,
  projektAn,
  personAn,
})

/** Beantwortet die Aufrufe der Mail-Einstellungen; ohne Eintrag gelten leere Einstellungen und 204. */
function stubFetch(antworten: Record<string, Antwort> = {}) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation((pfad, init) => {
    const methode = init?.method ?? 'GET'
    const vorgabe: Antwort = methode === 'GET' ? { status: 200, body: { projekte: [], ruhezeit: null } } : { status: 204 }
    const { status, body } = antworten[`${methode} ${String(pfad)}`] ?? vorgabe
    return Promise.resolve({
      ok: status < 400,
      status,
      statusText: String(status),
      text: () => Promise.resolve(body === undefined ? '' : JSON.stringify(body)),
    } as Response)
  })
}

/** Die Bodies aller Aufrufe mit dieser Methode und diesem Pfad. */
function aufrufe(fetchSpy: ReturnType<typeof stubFetch>, methode: string, pfad: string) {
  return fetchSpy.mock.calls
    .filter(([p, init]) => String(p) === pfad && (init?.method ?? 'GET') === methode)
    .map(([, init]) => init?.body)
}

beforeEach(() => {
  stubFetch()
})

afterEach(() => {
  vi.restoreAllMocks()
})

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

describe('ProfilePage: Mail-Benachrichtigungen', () => {
  const einstellungen = (projekte: unknown[], ruhezeit: unknown = null) => ({
    [`GET ${MEINE}`]: { status: 200, body: { projekte, ruhezeit } },
  })
  const gespeicherteRuhezeit = { beginn: '22:00', ende: '07:00', zeitzone: 'Asia/Tokyo' }
  const browserZone = 'Pacific/Auckland'

  beforeEach(() => {
    vi.clearAllMocks()
    mockedApi.me.mockResolvedValue({ ...alt })
    mockedApi.eigenesVorschauProtokoll.mockResolvedValue([])
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({
      timeZone: browserZone,
    } as Intl.ResolvedDateTimeFormatOptions)
  })

  it('schreibt den Schalter je Projekt per PUT an das richtige Projekt', async () => {
    const fetchSpy = stubFetch(einstellungen([projekt(7, 'Alpha', true, false), projekt(8, 'Beta', true, false)]))
    renderProfile()

    const schalter = await screen.findByRole('switch', { name: 'Beta' })
    await userEvent.click(schalter)

    await waitFor(() => expect(aufrufe(fetchSpy, 'PUT', `${MEINE}/projekte/8`)).toEqual(['{"an":true}']))
    expect(aufrufe(fetchSpy, 'PUT', `${MEINE}/projekte/7`)).toEqual([])
    expect(schalter).toBeChecked()
    expect(screen.getByRole('switch', { name: 'Alpha' })).not.toBeChecked()
  })

  it('schaltet einen eingeschalteten Schalter aus', async () => {
    const fetchSpy = stubFetch(einstellungen([projekt(7, 'Alpha', true, true)]))
    renderProfile()

    const schalter = await screen.findByRole('switch', { name: 'Alpha' })
    expect(schalter).toBeChecked()
    await userEvent.click(schalter)

    await waitFor(() => expect(aufrufe(fetchSpy, 'PUT', `${MEINE}/projekte/7`)).toEqual(['{"an":false}']))
    expect(schalter).not.toBeChecked()
  })

  it('nimmt den Schalter zurück und meldet den Fehler, wenn das Schalten scheitert', async () => {
    stubFetch({
      ...einstellungen([projekt(7, 'Alpha', true, false), projekt(8, 'Beta', true, false)]),
      [`PUT ${MEINE}/projekte/8`]: { status: 404, body: { detail: 'Projekt nicht gefunden' } },
    })
    renderProfile()

    const schalter = await screen.findByRole('switch', { name: 'Beta' })
    await userEvent.click(schalter)

    expect(await screen.findByText('Projekt nicht gefunden')).toBeInTheDocument()
    expect(schalter).not.toBeChecked()
    expect(screen.getByRole('switch', { name: 'Alpha' })).not.toBeChecked()
  })

  it('zeigt bei ausgeschaltetem Projekt den Schalter mit einem Hinweis', async () => {
    stubFetch(einstellungen([projekt(7, 'Alpha', false, false)]))
    renderProfile()

    const schalter = await screen.findByRole('switch', { name: 'Alpha' })
    expect(schalter).toBeEnabled()
    expect(schalter).toHaveAccessibleDescription(
      'Für dieses Projekt sind Mails ausgeschaltet. Erst wenn Owner oder Projekt-Admin sie einschalten, kommen Mails an.',
    )
  })

  it('zeigt bei eingeschaltetem Projekt keinen Hinweis', async () => {
    stubFetch(einstellungen([projekt(7, 'Alpha', true, false)]))
    renderProfile()

    const schalter = await screen.findByRole('switch', { name: 'Alpha' })
    expect(schalter).not.toHaveAttribute('aria-describedby')
    expect(screen.queryByText(/Für dieses Projekt sind Mails ausgeschaltet/)).not.toBeInTheDocument()
  })

  it('sagt, wenn es kein schaltbares Projekt gibt', async () => {
    renderProfile()

    expect(await screen.findByText('Es gibt kein Projekt, für das du Mails einschalten kannst.')).toBeInTheDocument()
    expect(screen.queryByRole('switch')).not.toBeInTheDocument()
  })

  it('zeigt eine Fehlermeldung, wenn die Einstellungen nicht geladen werden können', async () => {
    stubFetch({ [`GET ${MEINE}`]: { status: 500 } })
    renderProfile()

    expect(await screen.findByText('Die Einstellungen ließen sich nicht laden.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Zeitzone')).not.toBeInTheDocument()
  })

  it.each<[string, Antwort]>([
    ['gelieferten', { status: 200, body: { projekte: [], ruhezeit: null } }],
    ['gescheiterten', { status: 500 }],
  ])('ignoriert einen %s Abruf nach dem Verlassen der Seite', async (_fall, antwort) => {
    let antworte: (wert: Response) => void = () => {}
    vi.spyOn(globalThis, 'fetch').mockReturnValue(
      new Promise<Response>((resolve) => {
        antworte = resolve
      }),
    )
    const { unmount } = renderProfile()
    await screen.findByLabelText('Anzeigename')

    unmount()
    antworte({
      ok: antwort.status < 400,
      status: antwort.status,
      statusText: String(antwort.status),
      text: () => Promise.resolve(JSON.stringify(antwort.body ?? {})),
    } as Response)
    await Promise.resolve()

    expect(screen.queryByLabelText('Zeitzone')).not.toBeInTheDocument()
    expect(screen.queryByText('Die Einstellungen ließen sich nicht laden.')).not.toBeInTheDocument()
  })

  it('belegt die Zeitzone ohne gespeicherte Ruhezeit aus dem Browser vor', async () => {
    renderProfile()

    expect(await screen.findByLabelText('Zeitzone')).toHaveValue(browserZone)
    expect(screen.getByLabelText('Beginn')).toHaveValue('')
    expect(screen.getByLabelText('Ende')).toHaveValue('')
    expect(screen.queryByRole('button', { name: 'Ruhezeit löschen' })).not.toBeInTheDocument()
  })

  it('gibt einer gespeicherten Zone Vorrang vor dem Browser', async () => {
    stubFetch(einstellungen([], gespeicherteRuhezeit))
    renderProfile()

    expect(await screen.findByLabelText('Zeitzone')).toHaveValue('Asia/Tokyo')
    expect(screen.getByLabelText('Beginn')).toHaveValue('22:00')
    expect(screen.getByLabelText('Ende')).toHaveValue('07:00')
  })

  it('speichert die Ruhezeit und bietet danach das Löschen an', async () => {
    const fetchSpy = stubFetch()
    renderProfile()

    const zone = await screen.findByLabelText('Zeitzone')
    fireEvent.change(screen.getByLabelText('Beginn'), { target: { value: '22:30' } })
    fireEvent.change(screen.getByLabelText('Ende'), { target: { value: '06:15' } })
    fireEvent.change(zone, { target: { value: ' Europe/Berlin ' } })
    await userEvent.click(screen.getByRole('button', { name: 'Ruhezeit speichern' }))

    expect(await screen.findByText('Ruhezeit gespeichert.')).toBeInTheDocument()
    expect(aufrufe(fetchSpy, 'PUT', RUHEZEIT)).toEqual([
      JSON.stringify({ beginn: '22:30', ende: '06:15', zeitzone: 'Europe/Berlin' }),
    ])
    expect(screen.getByRole('button', { name: 'Ruhezeit löschen' })).toBeInTheDocument()
  })

  it.each([
    ['ohne Beginn', '', '07:00', browserZone],
    ['ohne Ende', '22:00', '', browserZone],
    ['ohne Zeitzone', '22:00', '07:00', '   '],
  ])('sperrt das Speichern %s', async (_fall, beginn, ende, zeitzone) => {
    renderProfile()

    const zone = await screen.findByLabelText('Zeitzone')
    fireEvent.change(screen.getByLabelText('Beginn'), { target: { value: beginn } })
    fireEvent.change(screen.getByLabelText('Ende'), { target: { value: ende } })
    fireEvent.change(zone, { target: { value: zeitzone } })

    expect(screen.getByRole('button', { name: 'Ruhezeit speichern' })).toBeDisabled()
  })

  it('löscht die Ruhezeit und belegt die Felder wieder vor', async () => {
    const fetchSpy = stubFetch(einstellungen([], gespeicherteRuhezeit))
    renderProfile()

    const zone = await screen.findByLabelText('Zeitzone')
    await userEvent.click(screen.getByRole('button', { name: 'Ruhezeit löschen' }))

    expect(await screen.findByText('Ruhezeit gelöscht.')).toBeInTheDocument()
    expect(aufrufe(fetchSpy, 'DELETE', RUHEZEIT)).toHaveLength(1)
    expect(zone).toHaveValue(browserZone)
    expect(screen.getByLabelText('Beginn')).toHaveValue('')
    expect(screen.getByLabelText('Ende')).toHaveValue('')
    expect(screen.queryByRole('button', { name: 'Ruhezeit löschen' })).not.toBeInTheDocument()
  })

  it('zeigt die Server-Meldung, wenn das Speichern mit 400 scheitert', async () => {
    stubFetch({
      ...einstellungen([], gespeicherteRuhezeit),
      [`PUT ${RUHEZEIT}`]: { status: 400, body: { detail: 'Beginn und Ende der Ruhezeit dürfen nicht gleich sein.' } },
    })
    renderProfile()

    await screen.findByLabelText('Zeitzone')
    await userEvent.click(screen.getByRole('button', { name: 'Ruhezeit speichern' }))

    expect(await screen.findByText('Beginn und Ende der Ruhezeit dürfen nicht gleich sein.')).toBeInTheDocument()
    expect(screen.queryByText('Ruhezeit gespeichert.')).not.toBeInTheDocument()
  })

  it('nennt ohne Server-Meldung den allgemeinen Fehler und gibt die Knöpfe wieder frei', async () => {
    stubFetch({ ...einstellungen([], gespeicherteRuhezeit), [`PUT ${RUHEZEIT}`]: { status: 500 } })
    renderProfile()

    await screen.findByLabelText('Zeitzone')
    const speichern = screen.getByRole('button', { name: 'Ruhezeit speichern' })
    await userEvent.click(speichern)

    expect(await screen.findByText('Speichern fehlgeschlagen.')).toBeInTheDocument()
    expect(speichern).toBeEnabled()
  })

  it('behält die Ruhezeit, wenn das Löschen scheitert', async () => {
    stubFetch({
      ...einstellungen([], gespeicherteRuhezeit),
      [`DELETE ${RUHEZEIT}`]: { status: 500, body: { detail: 'Löschen abgewiesen' } },
    })
    renderProfile()

    const zone = await screen.findByLabelText('Zeitzone')
    await userEvent.click(screen.getByRole('button', { name: 'Ruhezeit löschen' }))

    expect(await screen.findByText('Löschen abgewiesen')).toBeInTheDocument()
    expect(zone).toHaveValue('Asia/Tokyo')
    expect(screen.getByRole('button', { name: 'Ruhezeit löschen' })).toBeEnabled()
  })

  it('zeigt den Abschnitt in der Vorschau nicht und ruft die Einstellungen nicht ab', async () => {
    const fetchSpy = stubFetch()
    mockedApi.me.mockResolvedValue({ ...alt, vorschau })
    renderProfile()

    await screen.findByRole('note')
    expect(screen.queryByRole('heading', { name: 'Mail-Benachrichtigungen' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Zeitzone')).not.toBeInTheDocument()
    expect(fetchSpy.mock.calls.filter(([pfad]) => String(pfad).startsWith(MEINE))).toEqual([])
  })

  it('beschriftet den Abschnitt auf Englisch', async () => {
    stubFetch(einstellungen([projekt(7, 'Alpha', false, false)]))
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

    expect(await screen.findByRole('heading', { name: 'Email notifications' })).toBeInTheDocument()
    expect(await screen.findByRole('switch', { name: 'Alpha' })).toHaveAccessibleDescription(
      'Emails are turned off for this project. You will only get emails once an owner or project admin turns them on.',
    )
    expect(screen.getByRole('heading', { name: 'Quiet hours' })).toBeInTheDocument()
    expect(screen.getByLabelText('Start')).toHaveValue('')
    expect(screen.getByLabelText('End')).toHaveValue('')
    expect(screen.getByLabelText('Time zone')).toHaveValue(browserZone)
    expect(screen.getByRole('button', { name: 'Save quiet hours' })).toBeDisabled()
  })
})
