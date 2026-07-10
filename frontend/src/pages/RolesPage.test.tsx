import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { RoleMatrix, RolesApi } from '../api/roles'
import { RolesPage } from './RolesPage'

const MATRIX: RoleMatrix = {
  roles: ['VIEWER', 'MEMBER', 'ADMIN', 'OWNER'],
  permissions: [
    { key: 'BOARD_READ', resource: 'BOARD', operation: 'READ' },
    { key: 'BOARD_CREATE', resource: 'BOARD', operation: 'CREATE' },
    { key: 'COMMENT_DELETE', resource: 'COMMENT', operation: 'DELETE' },
  ],
  grants: {
    VIEWER: ['BOARD_READ'],
    MEMBER: ['BOARD_READ'],
    ADMIN: ['BOARD_READ', 'BOARD_CREATE', 'COMMENT_DELETE'],
    OWNER: ['BOARD_READ', 'BOARD_CREATE', 'COMMENT_DELETE'],
  },
}

describe('RolesPage', () => {
  it('rendert das Rechte-Grid aus der Matrix mit festen (disabled) Haken', async () => {
    const api = { matrix: vi.fn().mockResolvedValue(MATRIX) } as unknown as RolesApi
    render(<RolesPage api={api} />)

    expect(screen.getByText('Rollen & Rechte')).toBeInTheDocument()

    // VIEWER hat BOARD_READ (fest gesetzt, disabled), aber nicht COMMENT_DELETE.
    const viewerRead = await screen.findByLabelText('BOARD_READ für VIEWER')
    expect(viewerRead).toBeChecked()
    expect(viewerRead).toBeDisabled()
    expect(screen.getByLabelText('COMMENT_DELETE für VIEWER')).not.toBeChecked()

    // COMMENT_DELETE: Member nein, Admin ja.
    expect(screen.getByLabelText('COMMENT_DELETE für MEMBER')).not.toBeChecked()
    expect(screen.getByLabelText('COMMENT_DELETE für ADMIN')).toBeChecked()

    // Plattform-Rollen weiterhin erklärt.
    expect(screen.getByText(/Super-User/)).toBeInTheDocument()
  })
})
