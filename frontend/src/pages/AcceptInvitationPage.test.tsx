import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { Member, MembersApi } from '../api/members'
import { AcceptInvitationPage } from './AcceptInvitationPage'

const member: Member = { userId: 9, email: 'neu@x.de', displayName: 'Neu', role: 'MEMBER' }

function makeApi(accept: MembersApi['accept']): MembersApi {
  return {
    list: vi.fn(),
    changeRole: vi.fn(),
    changeDisplayName: vi.fn(),
    remove: vi.fn(),
    invite: vi.fn(),
    accept,
  }
}

function renderAt(entry: string, api: MembersApi) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/invitations/accept" element={<AcceptInvitationPage api={api} />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AcceptInvitationPage', () => {
  it('nimmt eine gültige Einladung an und meldet den Beitritt', async () => {
    const api = makeApi(vi.fn().mockResolvedValue(member))
    renderAt('/invitations/accept?token=gut', api)

    expect(await screen.findByText(/als MEMBER beigetreten/)).toBeInTheDocument()
    expect(api.accept).toHaveBeenCalledWith('gut')
  })

  it('zeigt einen Fehler bei ungültigem Token', async () => {
    const api = makeApi(vi.fn().mockRejectedValue(new Error('nope')))
    renderAt('/invitations/accept?token=schlecht', api)

    expect(await screen.findByText(/ungültig oder abgelaufen/)).toBeInTheDocument()
  })
})
