import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RolesPage } from './RolesPage'

describe('RolesPage', () => {
  it('zeigt die Projekt-Rollen-Matrix und Plattform-Rollen', () => {
    render(<RolesPage />)

    expect(screen.getByText('Rollen & Rechte')).toBeInTheDocument()
    // Projekt umbenennen/löschen nur für OWNER.
    expect(screen.getByLabelText('Projekt umbenennen / löschen für OWNER')).toHaveTextContent('✓')
    expect(screen.getByLabelText('Projekt umbenennen / löschen für VIEWER')).toHaveTextContent('–')
    // Karten anlegen ab MEMBER.
    expect(screen.getByLabelText('Karten anlegen / verschieben / löschen für MEMBER')).toHaveTextContent('✓')
    // Plattform-Rollen erklärt.
    expect(screen.getByText(/Super-User/)).toBeInTheDocument()
  })
})
