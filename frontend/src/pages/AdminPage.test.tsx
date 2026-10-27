import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminApi } from '../api/admin'
import { ApiError, startseiteNeuLaden } from '../api/client'
import { formatierer } from '../i18n/format'
import { AdminPage } from './AdminPage'
import { renderMitSprache } from '../i18n/renderMitSprache'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 99, memberships: [] }, vorschau: null }),
}))
// Das Neuladen auf `/` nach dem Start der Vorschau (E13) wird beobachtet, nicht ausgeführt.
vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  startseiteNeuLaden: vi.fn(),
}))
// Editiermodus gemockt: Bestandstests laufen mit editMode=true (Namen-Bleistift sichtbar); der
// Editiermodus-aus-Test schaltet editMode.value=false.
const editMode = vi.hoisted(() => ({ value: true }))
vi.mock('../lib/EditModeContext', () => ({
  useEditMode: () => ({ editMode: editMode.value, setEditMode: vi.fn(), toggleEditMode: vi.fn() }),
}))

function makeApi(): AdminApi {
  return {
    listUsers: vi.fn().mockResolvedValue([
      {
        id: 1,
        email: 'a@x.de',
        displayName: 'Alice',
        platformRole: 'USER',
        emailVerified: true,
        approvedAt: '2026-07-13T10:00:00Z',
        disabled: false,
      },
      {
        id: 2,
        email: 'b@x.de',
        displayName: 'Bob',
        platformRole: 'ADMIN',
        emailVerified: true,
        approvedAt: '2026-07-13T10:00:00Z',
        disabled: false,
      },
      {
        id: 3,
        email: 'c@x.de',
        displayName: 'Carol',
        platformRole: 'USER',
        emailVerified: false,
        approvedAt: null,
        disabled: true,
      },
    ]),
    setRole: vi.fn().mockResolvedValue({}),
    setDisplayName: vi.fn().mockResolvedValue({}),
    approve: vi.fn().mockResolvedValue({}),
    disable: vi.fn().mockResolvedValue({}),
    enable: vi.fn().mockResolvedValue({}),
    listOverloadRejections: vi.fn().mockResolvedValue([]),
    vorschauStarten: vi.fn().mockResolvedValue(undefined),
    vorschauProtokoll: vi.fn().mockResolvedValue([]),
    bootstrap: vi.fn(),
  }
}

describe('AdminPage', () => {
  beforeEach(() => {
    editMode.value = true
  })

  it('zeigt unter der Nutzerverwaltung den Abschnitt zu Abweisungen wegen Last', async () => {
    const api = makeApi()
    render(<AdminPage api={api} />)

    expect(await screen.findByRole('heading', { name: 'Abweisungen wegen Last' })).toBeInTheDocument()
    expect(api.listOverloadRejections).toHaveBeenCalledTimes(1)
  })

  it('blendet bei ausgeschaltetem Editiermodus den Namen-Bleistift aus, behält aber die Aktionen', async () => {
    editMode.value = false
    const api = makeApi()
    render(<AdminPage api={api} />)

    expect(await screen.findByText('Alice')).toBeInTheDocument()
    // Namen-Bleistift weg ...
    expect(screen.queryByLabelText('Namen von Alice bearbeiten')).not.toBeInTheDocument()
    // ... die eigenständigen Admin-Aktionen bleiben sichtbar.
    expect(screen.getByLabelText('Rolle von Alice umschalten')).toBeInTheDocument()
    expect(screen.getByLabelText('Alice sperren')).toBeInTheDocument()
    expect(screen.getByLabelText('Carol freigeben')).toBeInTheDocument()
  })

  it('listet Nutzer und schaltet die Rolle um', async () => {
    const api = makeApi()
    render(<AdminPage api={api} />)

    expect(await screen.findByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Rolle von Alice umschalten'))
    await waitFor(() => expect(api.setRole).toHaveBeenCalledWith(1, 'ADMIN'))
  })

  it('bearbeitet den Anzeigenamen eines Nutzers inline', async () => {
    const api = makeApi()
    render(<AdminPage api={api} />)

    fireEvent.click(await screen.findByLabelText('Namen von Alice bearbeiten'))
    const input = screen.getByLabelText('Anzeigename von a@x.de')
    fireEvent.change(input, { target: { value: 'Alicia' } })
    fireEvent.click(screen.getByLabelText('Namen speichern'))

    await waitFor(() => expect(api.setDisplayName).toHaveBeenCalledWith(1, 'Alicia'))
  })

  it('zeigt den Freigabe-Status und gibt einen wartenden Nutzer frei', async () => {
    const api = makeApi()
    render(<AdminPage api={api} />)

    expect(await screen.findByText('Carol')).toBeInTheDocument()
    expect(screen.getAllByText('Freigegeben')).toHaveLength(2)
    expect(screen.getByText('Wartet auf Freigabe')).toBeInTheDocument()

    // Freigegebene Nutzer haben keinen Freigeben-Button.
    expect(screen.queryByLabelText('Alice freigeben')).not.toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Carol freigeben'))
    await waitFor(() => expect(api.approve).toHaveBeenCalledWith(3))
    // Nach Erfolg wird neu geladen (zweiter listUsers-Aufruf).
    await waitFor(() => expect(api.listUsers).toHaveBeenCalledTimes(2))
  })

  it('sperrt einen aktiven und entsperrt einen gesperrten Nutzer', async () => {
    const api = makeApi()
    render(<AdminPage api={api} />)
    await screen.findByText('Alice')

    fireEvent.click(screen.getByLabelText('Alice sperren'))
    await waitFor(() => expect(api.disable).toHaveBeenCalledWith(1))

    fireEvent.click(screen.getByLabelText('Carol entsperren'))
    await waitFor(() => expect(api.enable).toHaveBeenCalledWith(3))
  })

  it('zeigt die Server-Meldung, wenn das Herabstufen abgelehnt wird', async () => {
    const api = makeApi()
    api.setRole = vi
      .fn()
      .mockRejectedValue(
        new ApiError(
          409,
          'Conflict',
          undefined,
          'Mindestens ein aktiver Plattform-Administrator muss bestehen bleiben.',
        ),
      )
    render(<AdminPage api={api} />)
    await screen.findByText('Bob')

    fireEvent.click(screen.getByLabelText('Rolle von Bob umschalten'))
    expect(
      await screen.findByText(
        'Mindestens ein aktiver Plattform-Administrator muss bestehen bleiben.',
      ),
    ).toBeInTheDocument()
  })

  it('zeigt bei einem 409 ohne Server-Meldung den eigenen Text zur Rollenänderung', async () => {
    const api = makeApi()
    api.setRole = vi.fn().mockRejectedValue(new ApiError(409, 'Conflict'))
    render(<AdminPage api={api} />)
    await screen.findByText('Bob')

    fireEvent.click(screen.getByLabelText('Rolle von Bob umschalten'))
    expect(await screen.findByText('Rollenänderung fehlgeschlagen.')).toBeInTheDocument()
  })

  it('zeigt eine generische Fehlermeldung bei fehlgeschlagener Rollenänderung', async () => {
    const api = makeApi()
    api.setRole = vi.fn().mockRejectedValue(new Error('boom'))
    render(<AdminPage api={api} />)
    await screen.findByText('Alice')

    fireEvent.click(screen.getByLabelText('Rolle von Alice umschalten'))
    expect(await screen.findByText('Rollenänderung fehlgeschlagen.')).toBeInTheDocument()
  })

  it('zeigt die Server-Meldung, wenn die Freigabe abgelehnt wird', async () => {
    const api = makeApi()
    // Server-Ablehnung mit einer für den Nutzer formulierten Meldung in `detail` (RFC 9457).
    api.approve = vi
      .fn()
      .mockRejectedValue(new ApiError(409, 'Conflict', undefined, 'Carol ist bereits freigegeben.'))
    render(<AdminPage api={api} />)
    await screen.findByText('Carol')

    fireEvent.click(screen.getByLabelText('Carol freigeben'))
    // Der Darstellungsort bleibt der Inline-Alert der Seite; nur der Text kommt vom Server.
    expect(await screen.findByText('Carol ist bereits freigegeben.')).toBeInTheDocument()
  })

  it('zeigt ohne Server-Meldung den eigenen Text zur Freigabe', async () => {
    const api = makeApi()
    api.approve = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    render(<AdminPage api={api} />)
    await screen.findByText('Carol')

    fireEvent.click(screen.getByLabelText('Carol freigeben'))
    expect(await screen.findByText('Freigabe fehlgeschlagen.')).toBeInTheDocument()
  })

  it('zeigt eine Fehlermeldung, wenn das Ändern des Anzeigenamens fehlschlägt', async () => {
    const api = makeApi()
    api.setDisplayName = vi.fn().mockRejectedValue(new ApiError(400, 'Name ungültig'))
    render(<AdminPage api={api} />)

    fireEvent.click(await screen.findByLabelText('Namen von Alice bearbeiten'))
    fireEvent.change(screen.getByLabelText('Anzeigename von a@x.de'), { target: { value: 'X' } })
    fireEvent.click(screen.getByLabelText('Namen speichern'))

    expect(await screen.findByText('Name ungültig')).toBeInTheDocument()
  })

  it('zeigt eine generische Meldung, wenn die Namensänderung ohne ApiError scheitert', async () => {
    const api = makeApi()
    api.setDisplayName = vi.fn().mockRejectedValue(new Error('boom'))
    render(<AdminPage api={api} />)

    fireEvent.click(await screen.findByLabelText('Namen von Alice bearbeiten'))
    fireEvent.change(screen.getByLabelText('Anzeigename von a@x.de'), { target: { value: 'X' } })
    fireEvent.click(screen.getByLabelText('Namen speichern'))

    expect(await screen.findByText('Namensänderung fehlgeschlagen.')).toBeInTheDocument()
  })

  it('speichert keinen leeren Anzeigenamen', async () => {
    const api = makeApi()
    render(<AdminPage api={api} />)

    fireEvent.click(await screen.findByLabelText('Namen von Alice bearbeiten'))
    fireEvent.change(screen.getByLabelText('Anzeigename von a@x.de'), { target: { value: '   ' } })
    fireEvent.click(screen.getByLabelText('Namen speichern'))

    expect(api.setDisplayName).not.toHaveBeenCalled()
  })

  it('bricht das Bearbeiten des Anzeigenamens ab', async () => {
    const api = makeApi()
    render(<AdminPage api={api} />)

    fireEvent.click(await screen.findByLabelText('Namen von Alice bearbeiten'))
    fireEvent.click(screen.getByLabelText('Bearbeiten abbrechen'))

    expect(screen.queryByLabelText('Anzeigename von a@x.de')).not.toBeInTheDocument()
  })

  it('zeigt die Server-Meldung, wenn das Sperren abgelehnt wird', async () => {
    const api = makeApi()
    api.disable = vi
      .fn()
      .mockRejectedValue(
        new ApiError(
          409,
          'Conflict',
          undefined,
          'Mindestens ein aktiver Plattform-Administrator muss bestehen bleiben.',
        ),
      )
    render(<AdminPage api={api} />)
    await screen.findByText('Alice')

    fireEvent.click(screen.getByLabelText('Alice sperren'))
    expect(
      await screen.findByText(
        'Mindestens ein aktiver Plattform-Administrator muss bestehen bleiben.',
      ),
    ).toBeInTheDocument()
  })

  it('zeigt ohne Server-Meldung den eigenen Text zum Sperren/Entsperren', async () => {
    const api = makeApi()
    api.disable = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    api.enable = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    render(<AdminPage api={api} />)
    await screen.findByText('Alice')

    fireEvent.click(screen.getByLabelText('Alice sperren'))
    expect(await screen.findByText('Sperren fehlgeschlagen.')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Carol entsperren'))
    expect(await screen.findByText('Entsperren fehlgeschlagen.')).toBeInTheDocument()
  })
})

describe('AdminPage auf Englisch', () => {
  beforeEach(() => {
    editMode.value = true
  })

  it('zeigt Titel, Spalten, Zustände und Aktionen auf Englisch, Rollen und Namen unverändert', async () => {
    renderMitSprache(<AdminPage api={makeApi()} />, 'en')

    expect(screen.getByRole('heading', { name: 'Admin — User management' })).toBeInTheDocument()
    expect(await screen.findByText('Alice')).toBeInTheDocument()
    for (const kopf of ['Name', 'Email', 'Verified', 'Approval', 'Status', 'Role', 'Action']) {
      expect(screen.getByRole('columnheader', { name: new RegExp(kopf) })).toBeInTheDocument()
    }
    expect(screen.getAllByText('Approved')).toHaveLength(2)
    expect(screen.getByText('Awaiting approval')).toBeInTheDocument()
    expect(screen.getByText('Blocked')).toBeInTheDocument()
    expect(screen.getAllByText('yes')).toHaveLength(2)
    expect(screen.getByText('no')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Approve Carol' })).toHaveTextContent('Approve')
    expect(screen.getByRole('button', { name: 'Toggle role of Alice' })).toHaveTextContent('To ADMIN')
    expect(screen.getByRole('button', { name: 'Toggle role of Bob' })).toHaveTextContent('To USER')
    expect(screen.getByRole('button', { name: 'Block Alice' })).toHaveTextContent('Block')
    expect(screen.getByRole('button', { name: 'Unblock Carol' })).toHaveTextContent('Unblock')
    expect(screen.getByRole('heading', { name: 'Rejections due to load' })).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Edit name of Alice'))
    expect(screen.getByLabelText('Display name of a@x.de')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save name' })).toHaveTextContent('Save')
    expect(screen.getByRole('button', { name: 'Cancel editing' })).toHaveTextContent('Cancel')
  })

  it('meldet Fehler auf Englisch', async () => {
    const api = makeApi()
    api.listUsers = vi.fn().mockRejectedValue(new ApiError(403, 'Forbidden'))
    const { unmount } = renderMitSprache(<AdminPage api={api} />, 'en')
    expect(await screen.findByText('No admin access.')).toBeInTheDocument()
    unmount()

    const zweite = makeApi()
    zweite.setRole = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    zweite.approve = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    zweite.disable = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    zweite.enable = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    zweite.setDisplayName = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    renderMitSprache(<AdminPage api={zweite} />, 'en')
    await screen.findByText('Alice')

    fireEvent.click(screen.getByLabelText('Toggle role of Alice'))
    expect(await screen.findByText('Changing the role failed.')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Approve Carol'))
    expect(await screen.findByText('Approval failed.')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Block Alice'))
    expect(await screen.findByText('Blocking failed.')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Unblock Carol'))
    expect(await screen.findByText('Unblocking failed.')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Edit name of Alice'))
    fireEvent.click(screen.getByLabelText('Save name'))
    expect(await screen.findByText('Changing the name failed.')).toBeInTheDocument()
  })

  it('meldet einen sonstigen Ladefehler auf Englisch', async () => {
    const api = makeApi()
    api.listUsers = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    renderMitSprache(<AdminPage api={api} />, 'en')

    expect(await screen.findByText('Loading failed.')).toBeInTheDocument()
  })
})

describe('AdminPage: Ansicht als …', () => {
  beforeEach(() => {
    editMode.value = true
    vi.mocked(startseiteNeuLaden).mockClear()
  })

  const ich = {
    id: 99,
    email: 'ich@x.de',
    displayName: 'Ich',
    platformRole: 'ADMIN',
    emailVerified: true,
    approvedAt: '2026-07-13T10:00:00Z',
    disabled: false,
  }

  it('bietet den Knopf nur für Konten, die weder das eigene noch ein Plattform-Admin sind', async () => {
    const api = makeApi()
    const liste = await api.listUsers()
    api.listUsers = vi.fn().mockResolvedValue([...liste, ich])
    render(<AdminPage api={api} />)
    await screen.findByText('Ich')

    expect(screen.getByRole('button', { name: 'Ansicht als Alice' })).toHaveTextContent('Ansicht als …')
    // Gesperrt und wartend kommt in Frage: Die Ansicht zeigt, was XY nach der Freigabe sähe (AK 1).
    expect(screen.getByRole('button', { name: 'Ansicht als Carol' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ansicht als Bob' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ansicht als Ich' })).not.toBeInTheDocument()
  })

  it('Szenario: Admin prüft die Sicht eines neuen Mitglieds', async () => {
    const api = makeApi()
    render(<AdminPage api={api} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Ansicht als Alice' }))

    await waitFor(() => expect(startseiteNeuLaden).toHaveBeenCalledTimes(1))
    expect(api.vorschauStarten).toHaveBeenCalledWith(1)
    // Erst nach dem Start wird neu geladen: `StartRedirect` wählt dann die Startseite von XY.
    expect(vi.mocked(api.vorschauStarten).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(startseiteNeuLaden).mock.invocationCallOrder[0],
    )
  })

  it('lädt nicht neu und meldet den Grund, wenn der Start abgewiesen wird', async () => {
    const api = makeApi()
    api.vorschauStarten = vi
      .fn()
      .mockRejectedValue(new ApiError(409, 'Conflict', undefined, 'Es läuft schon eine Ansicht.'))
    render(<AdminPage api={api} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Ansicht als Alice' }))

    expect(await screen.findByText('Es läuft schon eine Ansicht.')).toBeInTheDocument()
    expect(startseiteNeuLaden).not.toHaveBeenCalled()
  })

  it('meldet einen Start ohne Server-Meldung mit eigenem Text', async () => {
    const api = makeApi()
    api.vorschauStarten = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    render(<AdminPage api={api} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Ansicht als Alice' }))

    expect(await screen.findByText('Die Ansicht ließ sich nicht starten.')).toBeInTheDocument()
  })

  it('Szenario: XY erfährt von der Ansicht — Start und Ende stehen in der Nutzerliste', async () => {
    const api = makeApi()
    api.vorschauProtokoll = vi.fn().mockResolvedValue([
      {
        adminName: 'Ada',
        adminKontoGeloescht: false,
        gestartetAm: '2026-10-26T09:00:00Z',
        laeuftAbAm: '2026-10-26T09:30:00Z',
        beendetAm: '2026-10-26T09:12:00Z',
        endgrund: 'BEENDET',
      },
    ])
    render(<AdminPage api={api} />)
    await screen.findByText('Alice')
    // Die Einträge laden erst beim Aufklappen.
    expect(api.vorschauProtokoll).not.toHaveBeenCalled()

    const knopf = screen.getByRole('button', { name: 'Ansichten als Alice' })
    expect(knopf).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(knopf)

    const de = formatierer('de').datumUhrzeit
    const eintrag = await screen.findByRole('listitem')
    expect(api.vorschauProtokoll).toHaveBeenCalledWith(1)
    expect(knopf).toHaveAttribute('aria-expanded', 'true')
    expect(eintrag).toHaveTextContent('Ada')
    expect(eintrag).toHaveTextContent(`Start ${de('2026-10-26T09:00:00Z')}`)
    expect(eintrag).toHaveTextContent(`Ende ${de('2026-10-26T09:12:00Z')}`)
    expect(eintrag).toHaveTextContent('beendet')

    fireEvent.click(knopf)
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
  })

  it('nennt einen gelöschten Admin mit Namen und dem Vermerk „Konto gelöscht“', async () => {
    const api = makeApi()
    api.vorschauProtokoll = vi.fn().mockResolvedValue([
      {
        adminName: 'Ada',
        adminKontoGeloescht: true,
        gestartetAm: '2026-10-26T09:00:00Z',
        laeuftAbAm: '2026-10-26T09:30:00Z',
        beendetAm: '2026-10-26T09:30:00Z',
        endgrund: 'ABLAUF',
      },
    ])
    render(<AdminPage api={api} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Ansichten als Carol' }))

    expect(await screen.findByRole('listitem')).toHaveTextContent('Ada (Konto gelöscht)')
    expect(api.vorschauProtokoll).toHaveBeenCalledWith(3)
  })

  it('beschriftet Knopf und Spalte auf Englisch', async () => {
    renderMitSprache(<AdminPage api={makeApi()} />, 'en')
    await screen.findByText('Alice')

    expect(screen.getByRole('button', { name: 'View as Alice' })).toHaveTextContent('View as …')
    expect(screen.getByRole('columnheader', { name: /Views/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Views as Alice' })).toHaveTextContent('Entries')
  })
})
