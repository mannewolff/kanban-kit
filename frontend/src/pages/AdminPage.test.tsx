import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { AdminApi } from '../api/admin'
import { AdminPage } from './AdminPage'

function makeApi(): AdminApi {
  return {
    listUsers: vi.fn().mockResolvedValue([
      { id: 1, email: 'a@x.de', displayName: 'Alice', platformRole: 'USER', emailVerified: true },
      { id: 2, email: 'b@x.de', displayName: 'Bob', platformRole: 'ADMIN', emailVerified: true },
    ]),
    setRole: vi.fn().mockResolvedValue({}),
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
})
