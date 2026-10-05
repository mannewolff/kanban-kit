import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { HINWEIS_AENDERND } from '../lib/apiAusprobieren'
import { AusprobierBestaetigung } from './AusprobierBestaetigung'

function renderDialog(offen: boolean) {
  const onAbbrechen = vi.fn()
  const onAbsenden = vi.fn()
  render(
    <AusprobierBestaetigung
      offen={offen}
      methode="DELETE"
      pfad="/api/projects/7"
      onAbbrechen={onAbbrechen}
      onAbsenden={onAbsenden}
    />,
  )
  return { onAbbrechen, onAbsenden }
}

// Der vorbelegte Fokus startet den Ripple von „Abbrechen“ asynchron; findBy wartet ihn ab.
async function oeffneDialog() {
  const { onAbbrechen, onAbsenden } = renderDialog(true)
  await screen.findByRole('dialog')
  return { onAbbrechen, onAbsenden }
}

describe('AusprobierBestaetigung', () => {
  it('ist über Titel und Hinweis benannt und zeigt Methode und Pfad', async () => {
    await oeffneDialog()
    const dialog = screen.getByRole('dialog', { name: 'Echte Daten ändern?' })
    expect(dialog).toHaveAccessibleDescription(expect.stringContaining(HINWEIS_AENDERND))
    expect(screen.getByText('DELETE')).toBeInTheDocument()
    expect(screen.getByText('/api/projects/7')).toBeInTheDocument()
  })

  it('legt den Fokus beim Öffnen auf Abbrechen', async () => {
    await oeffneDialog()
    expect(screen.getByRole('button', { name: 'Abbrechen' })).toHaveFocus()
  })

  it('ruft beim Abbrechen nur onAbbrechen auf', async () => {
    const { onAbbrechen, onAbsenden } = await oeffneDialog()
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(onAbbrechen).toHaveBeenCalledTimes(1)
    expect(onAbsenden).not.toHaveBeenCalled()
  })

  it('ruft beim Absenden nur onAbsenden auf', async () => {
    const { onAbbrechen, onAbsenden } = await oeffneDialog()
    fireEvent.click(screen.getByRole('button', { name: 'Absenden' }))
    expect(onAbsenden).toHaveBeenCalledTimes(1)
    expect(onAbbrechen).not.toHaveBeenCalled()
  })

  it('bricht beim Schließen über Escape ab', async () => {
    const { onAbbrechen, onAbsenden } = await oeffneDialog()
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
    expect(onAbbrechen).toHaveBeenCalledTimes(1)
    expect(onAbsenden).not.toHaveBeenCalled()
  })

  it('zeigt geschlossen nichts', () => {
    renderDialog(false)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
