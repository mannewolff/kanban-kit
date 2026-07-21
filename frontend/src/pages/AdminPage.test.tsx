import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminApi } from '../api/admin'
import { ApiError } from '../api/client'
import { AdminPage } from './AdminPage'

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 99, memberships: [] } }),
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
    bootstrap: vi.fn(),
  }
}

describe('AdminPage', () => {
  beforeEach(() => {
    editMode.value = true
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

  it('zeigt beim letzten Admin eine spezifische Fehlermeldung (409)', async () => {
    const api = makeApi()
    api.setRole = vi.fn().mockRejectedValue(new ApiError(409, 'letzter Admin'))
    render(<AdminPage api={api} />)
    await screen.findByText('Bob')

    fireEvent.click(screen.getByLabelText('Rolle von Bob umschalten'))
    expect(
      await screen.findByText('Der letzte Admin kann nicht degradiert werden.'),
    ).toBeInTheDocument()
  })

  it('zeigt eine generische Fehlermeldung bei fehlgeschlagener Rollenänderung', async () => {
    const api = makeApi()
    api.setRole = vi.fn().mockRejectedValue(new Error('boom'))
    render(<AdminPage api={api} />)
    await screen.findByText('Alice')

    fireEvent.click(screen.getByLabelText('Rolle von Alice umschalten'))
    expect(await screen.findByText('Rollenänderung fehlgeschlagen.')).toBeInTheDocument()
  })

  it('zeigt eine Fehlermeldung, wenn die Freigabe fehlschlägt', async () => {
    const api = makeApi()
    api.approve = vi.fn().mockRejectedValue(new Error('boom'))
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

  it('zeigt eine Fehlermeldung, wenn das Sperren/Entsperren fehlschlägt', async () => {
    const api = makeApi()
    api.disable = vi.fn().mockRejectedValue(new Error('boom'))
    api.enable = vi.fn().mockRejectedValue(new Error('boom'))
    render(<AdminPage api={api} />)
    await screen.findByText('Alice')

    fireEvent.click(screen.getByLabelText('Alice sperren'))
    expect(await screen.findByText('Sperren fehlgeschlagen.')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Carol entsperren'))
    expect(await screen.findByText('Entsperren fehlgeschlagen.')).toBeInTheDocument()
  })
})
