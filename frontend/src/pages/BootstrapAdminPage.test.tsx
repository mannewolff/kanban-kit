import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { AdminApi } from '../api/admin'
import { ApiError } from '../api/client'
import { BootstrapAdminPage } from './BootstrapAdminPage'
import { renderMitSprache } from '../i18n/renderMitSprache'

const refresh = vi.fn().mockResolvedValue(undefined)
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ refresh }) }))

function makeApi(bootstrap: AdminApi['bootstrap']): AdminApi {
  return {
    listUsers: vi.fn(),
    setRole: vi.fn(),
    setDisplayName: vi.fn(),
    approve: vi.fn(),
    disable: vi.fn(),
    enable: vi.fn(),
    listOverloadRejections: vi.fn(),
    vorschauStarten: vi.fn(),
    vorschauProtokoll: vi.fn(),
    bootstrap,
  }
}

describe('BootstrapAdminPage', () => {
  it('sendet den Token und frischt die Auth auf', async () => {
    const api = makeApi(vi.fn().mockResolvedValue({}))
    render(
      <MemoryRouter>
        <BootstrapAdminPage api={api} />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('Bootstrap-Token'), { target: { value: 'geheim' } })
    fireEvent.click(screen.getByRole('button', { name: 'Admin werden' }))

    await waitFor(() => expect(api.bootstrap).toHaveBeenCalledWith('geheim'))
    expect(refresh).toHaveBeenCalled()
  })

  it('zeigt einen Hinweis, wenn bereits ein Admin existiert (409)', async () => {
    const api = makeApi(vi.fn().mockRejectedValue(new ApiError(409, 'existiert bereits')))
    render(
      <MemoryRouter>
        <BootstrapAdminPage api={api} />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('Bootstrap-Token'), { target: { value: 'geheim' } })
    fireEvent.click(screen.getByRole('button', { name: 'Admin werden' }))

    expect(
      await screen.findByText('Es existiert bereits ein Admin — Bootstrap nicht mehr möglich.'),
    ).toBeInTheDocument()
  })

  it('bricht bei leerem Token ohne API-Aufruf ab', () => {
    const api = makeApi(vi.fn().mockResolvedValue({}))
    render(
      <MemoryRouter>
        <BootstrapAdminPage api={api} />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Admin werden' }))

    expect(api.bootstrap).not.toHaveBeenCalled()
  })

  it('zeigt eine generische Fehlermeldung bei ungültigem Token', async () => {
    const api = makeApi(vi.fn().mockRejectedValue(new Error('boom')))
    render(
      <MemoryRouter>
        <BootstrapAdminPage api={api} />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('Bootstrap-Token'), { target: { value: 'geheim' } })
    fireEvent.click(screen.getByRole('button', { name: 'Admin werden' }))

    expect(
      await screen.findByText('Ungültiger oder nicht konfigurierter Token.'),
    ).toBeInTheDocument()
  })
})

describe('BootstrapAdminPage auf Englisch', () => {
  const aufEnglisch = (api: AdminApi) =>
    renderMitSprache(
      <MemoryRouter>
        <BootstrapAdminPage api={api} />
      </MemoryRouter>,
      'en',
    )

  it('zeigt Seite und Konflikt auf Englisch', async () => {
    aufEnglisch(makeApi(vi.fn().mockRejectedValue(new ApiError(409, 'x'))))
    expect(screen.getByRole('heading', { name: 'Admin bootstrap' })).toBeInTheDocument()
    expect(screen.getByText(/On a fresh instance/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Bootstrap token'), { target: { value: 'geheim' } })
    fireEvent.click(screen.getByRole('button', { name: 'Become admin' }))
    expect(await screen.findByText('An admin already exists — bootstrap is no longer possible.')).toBeInTheDocument()
  })

  it('zeigt den Token-Fehler auf Englisch', async () => {
    aufEnglisch(makeApi(vi.fn().mockRejectedValue(new ApiError(400, 'x'))))
    fireEvent.change(screen.getByLabelText('Bootstrap token'), { target: { value: 'geheim' } })
    fireEvent.click(screen.getByRole('button', { name: 'Become admin' }))
    expect(await screen.findByText('Invalid or unconfigured token.')).toBeInTheDocument()
  })
})
