import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { Member, MembersApi } from '../api/members'
import { ProjectMembersPage } from './ProjectMembersPage'

const members: Member[] = [
  { userId: 1, email: 'owner@x.de', displayName: 'Olga Owner', role: 'OWNER' },
  { userId: 2, email: 'member@x.de', displayName: 'Mika Member', role: 'MEMBER' },
]

function makeApi(overrides: Partial<MembersApi> = {}): MembersApi {
  return {
    list: vi.fn().mockResolvedValue(members),
    changeRole: vi.fn(),
    remove: vi.fn().mockResolvedValue(undefined),
    invite: vi.fn().mockResolvedValue(undefined),
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
  it('sperrt Entfernen für den letzten Owner, erlaubt es für andere', async () => {
    renderPage(makeApi(), 'OWNER')
    await waitFor(() => expect(screen.getByText('Olga Owner')).toBeInTheDocument())

    expect(screen.getByLabelText('Olga Owner entfernen')).toBeDisabled()
    expect(screen.getByLabelText('Mika Member entfernen')).toBeEnabled()
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
    let resolveInvite: (() => void) | undefined
    const invite = vi.fn(() => new Promise<void>((resolve) => { resolveInvite = () => resolve() }))
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

    resolveInvite?.()
    expect(await screen.findByText('Einladung verschickt.')).toBeInTheDocument()
  })
})
