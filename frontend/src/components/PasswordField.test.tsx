import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PasswordField } from './PasswordField'

describe('PasswordField', () => {
  it('startet verborgen (type=password) mit Anzeige-Auge', () => {
    render(<PasswordField label="Passwort" />)
    expect(screen.getByLabelText('Passwort')).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Passwort anzeigen' })).toBeInTheDocument()
  })

  it('zeigt den Klartext nach Klick aufs Auge und verbirgt ihn wieder', async () => {
    render(<PasswordField label="Passwort" />)

    await userEvent.click(screen.getByRole('button', { name: 'Passwort anzeigen' }))
    expect(screen.getByLabelText('Passwort')).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Passwort verbergen' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Passwort verbergen' }))
    expect(screen.getByLabelText('Passwort')).toHaveAttribute('type', 'password')
  })

  it('reicht Wert und Änderungen wie ein TextField durch', async () => {
    render(<PasswordField label="Passwort" />)
    const input = screen.getByLabelText('Passwort')
    await userEvent.type(input, 'geheim')
    expect(input).toHaveValue('geheim')
  })

  it('nimmt das Auge aus dem Tab-Fluss', () => {
    render(<PasswordField label="Passwort" />)
    expect(screen.getByRole('button', { name: 'Passwort anzeigen' })).toHaveAttribute('tabindex', '-1')
  })
})
