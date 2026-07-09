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
})
