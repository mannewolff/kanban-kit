import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { RoleMatrix, RolesApi } from '../api/roles'
import { RolesPage } from './RolesPage'

const MATRIX: RoleMatrix = {
  roles: ['VIEWER', 'MEMBER', 'ADMIN', 'OWNER'],
  permissions: [
    { key: 'BOARD_READ', resource: 'BOARD', operation: 'READ' },
    { key: 'BOARD_CREATE', resource: 'BOARD', operation: 'CREATE' },
    { key: 'EPIC_CREATE', resource: 'EPIC', operation: 'CREATE' },
    { key: 'COMMENT_DELETE', resource: 'COMMENT', operation: 'DELETE' },
  ],
  grants: {
    VIEWER: ['BOARD_READ'],
    MEMBER: ['BOARD_READ'],
    ADMIN: ['BOARD_READ', 'BOARD_CREATE', 'EPIC_CREATE', 'COMMENT_DELETE'],
    OWNER: ['BOARD_READ', 'BOARD_CREATE', 'EPIC_CREATE', 'COMMENT_DELETE'],
  },
}

describe('RolesPage', () => {
  it('nennt die Ressource „Vorhaben" und laesst den Schluessel EPIC_* technisch stehen', async () => {
    const api = { matrix: vi.fn().mockResolvedValue(MATRIX) } as unknown as RolesApi
    render(<RolesPage api={api} />)

    // Angezeigt wird der neue Begriff, nicht der gespeicherte Schluessel.
    expect(await screen.findByText('Vorhaben')).toBeInTheDocument()
    expect(screen.queryByText('Epic')).not.toBeInTheDocument()

    // Der Schluessel bleibt unveraendert — Beleg, dass nur die Anzeige umbenannt wurde.
    expect(screen.getByLabelText('EPIC_CREATE für ADMIN')).toBeInTheDocument()
  })

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

  it('nutzt Fallbacks für unbekannte Ressource/Operation und fehlende Grants', async () => {
    const matrix: RoleMatrix = {
      roles: ['GUEST'],
      permissions: [{ key: 'MYSTERY_PONDER', resource: 'MYSTERY', operation: 'PONDER' }],
      grants: {}, // GUEST fehlt -> grants['GUEST'] undefined
    }
    const api = { matrix: vi.fn().mockResolvedValue(matrix) } as unknown as RolesApi
    render(<RolesPage api={api} />)

    // Ohne Label-Eintrag fällt die Anzeige auf den rohen Schlüssel zurück.
    expect(await screen.findByText('MYSTERY')).toBeInTheDocument()
    expect(screen.getByText('PONDER')).toBeInTheDocument()
    // Fehlender Grant-Eintrag -> Checkbox nicht gesetzt (?? false).
    expect(screen.getByLabelText('MYSTERY_PONDER für GUEST')).not.toBeChecked()
  })
})
