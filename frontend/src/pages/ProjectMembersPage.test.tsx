import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { Member, MembersApi } from '../api/members'
import { projectsApi } from '../api/projects'
import { ProjectMembersPage } from './ProjectMembersPage'

vi.mock('../api/projects', () => ({ projectsApi: { list: vi.fn(), transferOwner: vi.fn() } }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { userId: 1 } }) }))
const mProjects = projectsApi as unknown as {
  list: ReturnType<typeof vi.fn>
  transferOwner: ReturnType<typeof vi.fn>
}

const members: Member[] = [
  { userId: 1, email: 'owner@x.de', displayName: 'Olga Owner', role: 'OWNER' },
  { userId: 2, email: 'member@x.de', displayName: 'Mika Member', role: 'MEMBER' },
]

function makeApi(overrides: Partial<MembersApi> = {}): MembersApi {
  return {
    list: vi.fn().mockResolvedValue(members),
    changeRole: vi.fn(),
    changeDisplayName: vi.fn().mockResolvedValue({}),
    remove: vi.fn().mockResolvedValue(undefined),
    invite: vi.fn().mockResolvedValue({ status: 'invited' }),
    accept: vi.fn(),
    ...overrides,
  }
}

function renderPage(api: MembersApi, role: string) {
  return render(
    <MemoryRouter initialEntries={['/projects/5/members']}>
      <Routes>
        <Route
          path="/projects/:projectId/members"
          element={<ProjectMembersPage api={api} loadRole={() => Promise.resolve(role)} />}
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProjectMembersPage', () => {
  it('zeigt den Breadcrumb-Pfad ab Projekte', async () => {
    renderPage(makeApi(), 'OWNER')
    expect(await screen.findByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/')
  })

  it('sperrt Entfernen für den letzten Owner, erlaubt es für andere', async () => {
    renderPage(makeApi(), 'OWNER')
    await waitFor(() => expect(screen.getByText('Olga Owner')).toBeInTheDocument())

    expect(screen.getByLabelText('Olga Owner entfernen')).toBeDisabled()
    expect(screen.getByLabelText('Mika Member entfernen')).toBeEnabled()
  })

  it('bearbeitet den Anzeigenamen eines Mitglieds inline (Owner)', async () => {
    const changeDisplayName = vi.fn().mockResolvedValue({})
    renderPage(makeApi({ changeDisplayName }), 'OWNER')

    fireEvent.click(await screen.findByLabelText('Namen von Mika Member bearbeiten'))
    fireEvent.change(screen.getByLabelText('Anzeigename von member@x.de'), {
      target: { value: 'Mika M.' },
    })
    fireEvent.click(screen.getByLabelText('Namen speichern'))

    await waitFor(() => expect(changeDisplayName).toHaveBeenCalledWith(5, 2, 'Mika M.'))
  })

  it('zeigt keine Namens-Bearbeitung für Nicht-Verwalter (Viewer)', async () => {
    renderPage(makeApi(), 'VIEWER')
    await waitFor(() => expect(screen.getByText('Mika Member')).toBeInTheDocument())
    expect(screen.queryByLabelText('Namen von Mika Member bearbeiten')).not.toBeInTheDocument()
  })

  it('verschickt eine Einladung', async () => {
    const api = makeApi()
    renderPage(api, 'OWNER')
    await waitFor(() => expect(screen.getByText('Olga Owner')).toBeInTheDocument())

    fireEvent.change(screen.getByLabelText(/E-Mail einladen/), { target: { value: 'neu@x.de' } })
    fireEvent.click(screen.getByRole('button', { name: 'Einladen' }))

    await waitFor(() => expect(api.invite).toHaveBeenCalledWith(5, 'neu@x.de', 'MEMBER'))
    expect(await screen.findByText('Einladung verschickt.')).toBeInTheDocument()
  })

  it('blendet Verwaltungsaktionen für VIEWER aus', async () => {
    renderPage(makeApi(), 'VIEWER')
    await waitFor(() => expect(screen.getByText('Olga Owner')).toBeInTheDocument())

    expect(screen.queryByLabelText(/E-Mail einladen/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Mika Member entfernen')).not.toBeInTheDocument()
  })

  it('zeigt bei ungültiger Projekt-ID einen Fehler und ruft keine API auf', async () => {
    const api = makeApi()
    const loadRole = vi.fn().mockResolvedValue('OWNER')
    render(
      <MemoryRouter initialEntries={['/projects/abc/members']}>
        <Routes>
          <Route
            path="/projects/:projectId/members"
            element={<ProjectMembersPage api={api} loadRole={loadRole} />}
          />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('Ungültige Projekt-ID.')).toBeInTheDocument()
    expect(api.list).not.toHaveBeenCalled()
    expect(loadRole).not.toHaveBeenCalled()
  })

  it('verhindert Doppel-Submit: Button während Pending deaktiviert, Invite feuert nur einmal', async () => {
    let resolveInvite: ((result: { status: 'added' | 'invited' }) => void) | undefined
    const invite = vi.fn(
      () =>
        new Promise<{ status: 'added' | 'invited' }>((resolve) => {
          resolveInvite = resolve
        }),
    )
    const api = makeApi({ invite })
    renderPage(api, 'OWNER')
    await waitFor(() => expect(screen.getByText('Olga Owner')).toBeInTheDocument())

    fireEvent.change(screen.getByLabelText(/E-Mail einladen/), { target: { value: 'neu@x.de' } })
    const button = screen.getByRole('button', { name: 'Einladen' })
    fireEvent.click(button)

    // Pending: Button deaktiviert -> zweiter Klick löst keinen weiteren Invite aus
    await waitFor(() => expect(button).toBeDisabled())
    fireEvent.click(button)
    expect(invite).toHaveBeenCalledTimes(1)

    resolveInvite?.({ status: 'invited' })
    expect(await screen.findByText('Einladung verschickt.')).toBeInTheDocument()
  })

  it('meldet direkte Zuordnung als "hinzugefügt" und lädt die Mitgliederliste neu', async () => {
    const invite = vi.fn().mockResolvedValue({ status: 'added' })
    const list = vi.fn().mockResolvedValue(members)
    const api = makeApi({ invite, list })
    renderPage(api, 'OWNER')
    await waitFor(() => expect(screen.getByText('Olga Owner')).toBeInTheDocument())

    fireEvent.change(screen.getByLabelText(/E-Mail einladen/), { target: { value: 'bob@x.de' } })
    fireEvent.click(screen.getByRole('button', { name: 'Einladen' }))

    expect(await screen.findByText('Nutzer wurde hinzugefügt.')).toBeInTheDocument()
    // Erster Aufruf beim Laden der Seite, zweiter nach der direkten Zuordnung.
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
  })

  it('zeigt einen Freigabe-Hinweis, wenn der Nutzer noch nicht freigegeben ist (422)', async () => {
    const invite = vi.fn().mockRejectedValue(new ApiError(422, 'Nutzer ist noch nicht freigegeben'))
    const api = makeApi({ invite })
    renderPage(api, 'OWNER')
    await waitFor(() => expect(screen.getByText('Olga Owner')).toBeInTheDocument())

    fireEvent.change(screen.getByLabelText(/E-Mail einladen/), { target: { value: 'pending@x.de' } })
    fireEvent.click(screen.getByRole('button', { name: 'Einladen' }))

    expect(
      await screen.findByText('Nutzer ist noch nicht vom Admin freigegeben.'),
    ).toBeInTheDocument()
  })

  it('überträgt die Eigentümerschaft nach Bestätigung (Owner)', async () => {
    mProjects.transferOwner.mockResolvedValue(undefined)
    renderPage(makeApi(), 'OWNER')

    // Nicht beim eigenen Eintrag (Olga, userId 1), aber bei Mika (userId 2).
    await waitFor(() => expect(screen.getByText('Mika Member')).toBeInTheDocument())
    expect(screen.queryByLabelText('Olga Owner zum Eigentümer machen')).not.toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Mika Member zum Eigentümer machen'))
    fireEvent.click(screen.getByRole('button', { name: 'Übertragen' }))
    await waitFor(() => expect(mProjects.transferOwner).toHaveBeenCalledWith(5, 2))
  })

  it('zeigt Nicht-Ownern kein Zum-Eigentümer-machen', async () => {
    renderPage(makeApi(), 'MEMBER')
    await waitFor(() => expect(screen.getByText('Mika Member')).toBeInTheDocument())
    expect(screen.queryByLabelText('Mika Member zum Eigentümer machen')).not.toBeInTheDocument()
  })
})
