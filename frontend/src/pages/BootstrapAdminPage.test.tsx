import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { AdminApi } from '../api/admin'
import { BootstrapAdminPage } from './BootstrapAdminPage'

const refresh = vi.fn().mockResolvedValue(undefined)
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ refresh }) }))

describe('BootstrapAdminPage', () => {
  it('sendet den Token und frischt die Auth auf', async () => {
    const api = { listUsers: vi.fn(), setRole: vi.fn(), bootstrap: vi.fn().mockResolvedValue({}) } satisfies AdminApi
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
})
