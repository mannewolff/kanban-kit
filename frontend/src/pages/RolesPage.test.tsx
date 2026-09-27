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
    // Die drei vormaligen Sonderregeln als echte Rechte (Issue #1165) — nur beim OWNER.
    { key: 'CARD_MOVE', resource: 'CARD', operation: 'MOVE' },
    { key: 'CARD_MOVE_PROJECT', resource: 'CARD', operation: 'MOVE_PROJECT' },
    { key: 'NIGHT_RUN_READ', resource: 'NIGHT', operation: 'RUN_READ' },
    { key: 'NIGHT_RUN_SUBMIT', resource: 'NIGHT', operation: 'RUN_SUBMIT' },
  ],
  grants: {
    VIEWER: ['BOARD_READ'],
    MEMBER: ['BOARD_READ', 'CARD_MOVE'],
    ADMIN: ['BOARD_READ', 'BOARD_CREATE', 'EPIC_CREATE', 'COMMENT_DELETE', 'CARD_MOVE'],
    OWNER: [
      'BOARD_READ',
      'BOARD_CREATE',
      'EPIC_CREATE',
      'COMMENT_DELETE',
      'CARD_MOVE',
      'CARD_MOVE_PROJECT',
      'NIGHT_RUN_READ',
      'NIGHT_RUN_SUBMIT',
    ],
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

  it('zeigt die drei vormaligen Sonderregeln als Zeilen mit Haken nur beim OWNER', async () => {
    const api = { matrix: vi.fn().mockResolvedValue(MATRIX) } as unknown as RolesApi
    render(<RolesPage api={api} />)

    // Die Läufe-Rechte bekommen eine eigene, lesbare Ressource statt des rohen „NIGHT".
    expect(await screen.findByText('Läufe')).toBeInTheDocument()
    expect(screen.queryByText('NIGHT')).not.toBeInTheDocument()
    expect(screen.getByText(/^Lesen/)).toBeInTheDocument()
    expect(screen.getByText(/^Einliefern/)).toBeInTheDocument()
    // CARD_MOVE_PROJECT steht neben CARD_MOVE unter „Karte".
    expect(screen.getByText('Karte')).toBeInTheDocument()
    expect(screen.getByText(/^Projektwechsel/)).toBeInTheDocument()

    for (const key of ['NIGHT_RUN_READ', 'NIGHT_RUN_SUBMIT', 'CARD_MOVE_PROJECT']) {
      expect(screen.getByLabelText(`${key} für OWNER`)).toBeChecked()
      for (const rolle of ['VIEWER', 'MEMBER', 'ADMIN']) {
        expect(screen.getByLabelText(`${key} für ${rolle}`)).not.toBeChecked()
      }
    }
  })

  it('nennt die beiden Fussnoten zu den Laeufen und zum Projektwechsel', async () => {
    const api = { matrix: vi.fn().mockResolvedValue(MATRIX) } as unknown as RolesApi
    render(<RolesPage api={api} />)

    // Erst die geladene Tabelle abwarten — die Fussnotentexte stehen schon vorher da.
    await screen.findByText('Läufe')

    // Marke ¹ an beiden Läufe-Rechten, Marke ² am Projektwechsel — je plus der Fussnote selbst.
    expect(screen.getAllByText('¹')).toHaveLength(3)
    expect(screen.getAllByText('²')).toHaveLength(2)

    expect(screen.getByText(/nur, wenn das Projekt am Plattform-Leitstand teilnimmt/)).toBeInTheDocument()
    expect(screen.getByText(/Einliefern darf er immer/)).toBeInTheDocument()
    expect(screen.getByText(/im Quell- und im Zielprojekt/)).toBeInTheDocument()
  })

  it('fuehrt Leitstand und Projekt-Anlage als Plattform-Rolle, nicht als Projekt-Recht', async () => {
    const api = { matrix: vi.fn().mockResolvedValue(MATRIX) } as unknown as RolesApi
    render(<RolesPage api={api} />)

    expect(await screen.findByText(/Nur mit der Plattform-Rolle ADMIN/)).toBeInTheDocument()
    expect(screen.getByText(/Plattform-Leitstand ansehen/)).toBeInTheDocument()
    expect(screen.getByText(/Projekt anlegen und löschen/)).toBeInTheDocument()
    expect(screen.getByText(/Keine Projekt-Rolle gewährt das/)).toBeInTheDocument()

    // Kein Häkchen dazu — beides ist kein Recht der Projekt-Matrix.
    expect(screen.queryByLabelText(/PROJECT_CREATE/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/PROJECT_DELETE/)).not.toBeInTheDocument()
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
