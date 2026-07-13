import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { AdminApi } from '../api/admin'
import { AdminPage } from './AdminPage'

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
      },
      {
        id: 2,
        email: 'b@x.de',
        displayName: 'Bob',
        platformRole: 'ADMIN',
        emailVerified: true,
        approvedAt: '2026-07-13T10:00:00Z',
      },
      {
        id: 3,
        email: 'c@x.de',
        displayName: 'Carol',
        platformRole: 'USER',
        emailVerified: true,
        approvedAt: null,
      },
    ]),
    setRole: vi.fn().mockResolvedValue({}),
    approve: vi.fn().mockResolvedValue({}),
    bootstrap: vi.fn(),
  }
}

describe('AdminPage', () => {
  it('listet Nutzer und schaltet die Rolle um', async () => {
    const api = makeApi()
    render(<AdminPage api={api} />)

    expect(await screen.findByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Rolle von Alice umschalten'))
    await waitFor(() => expect(api.setRole).toHaveBeenCalledWith(1, 'ADMIN'))
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
})
