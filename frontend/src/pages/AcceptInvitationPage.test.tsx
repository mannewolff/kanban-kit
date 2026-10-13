import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { Member, MembersApi } from '../api/members'
import { AcceptInvitationPage } from './AcceptInvitationPage'
import { renderMitSprache } from '../i18n/renderMitSprache'

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

  it('zeigt einen Fehler, wenn kein Token in der URL steht', async () => {
    const accept = vi.fn()
    const api = makeApi(accept)
    renderAt('/invitations/accept', api)

    expect(await screen.findByText(/ungültig oder abgelaufen/)).toBeInTheDocument()
    expect(accept).not.toHaveBeenCalled()
  })
})

describe('AcceptInvitationPage auf Englisch', () => {
  const aufEnglisch = (entry: string, api: MembersApi) =>
    renderMitSprache(
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/invitations/accept" element={<AcceptInvitationPage api={api} />} />
        </Routes>
      </MemoryRouter>,
      'en',
    )

  it('meldet den Beitritt auf Englisch', async () => {
    aufEnglisch('/invitations/accept?token=gut', makeApi(vi.fn().mockResolvedValue(member)))
    expect(screen.getByRole('heading', { name: 'Accept invitation' })).toBeInTheDocument()
    expect(await screen.findByText('You have joined the project as MEMBER.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to projects' })).toBeInTheDocument()
  })

  it('zeigt den Fehler auf Englisch', async () => {
    aufEnglisch('/invitations/accept?token=x', makeApi(vi.fn().mockRejectedValue(new Error('x'))))
    expect(await screen.findByText('The invitation is invalid or has expired.')).toBeInTheDocument()
  })
})
