import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Label } from '../api/labels'
import { BulkActionBar, type LabelOption } from './BulkActionBar'

const label = (id: number, name: string): Label => ({
  id,
  boardId: 1,
  name,
  color: '#ff0000',
  countOnEpicTile: false,
})

/** Rendert die Leiste und gibt die Rückrufe zurück, damit Tests sie prüfen können. */
function zeigeLeiste(over: Partial<Parameters<typeof BulkActionBar>[0]> = {}) {
  const eigenschaften = {
    count: 3,
    canMove: true,
    labelOptions: [] as LabelOption[],
    labelsDisabledReason: null,
    onToggleLabel: vi.fn(),
    onArchive: vi.fn(),
    onMove: vi.fn(),
    onDelete: vi.fn(),
    onCancel: vi.fn(),
    ...over,
  }
  render(<BulkActionBar {...eigenschaften} />)
  return eigenschaften
}

describe('BulkActionBar', () => {
  it('zeigt die Anzahl und ruft die passenden Callbacks', () => {
    const rueckrufe = zeigeLeiste()

    expect(screen.getByText('3 ausgewählt')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    expect(rueckrufe.onArchive).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))
    expect(rueckrufe.onMove).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'In den Papierkorb' }))
    expect(rueckrufe.onDelete).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(rueckrufe.onCancel).toHaveBeenCalledTimes(1)
  })

  it('blendet Verschieben aus, wenn der Nutzer nicht verschieben darf', () => {
    zeigeLeiste({ count: 1, canMove: false })

    expect(screen.queryByRole('button', { name: 'Verschieben' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Archivieren' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'In den Papierkorb' })).toBeInTheDocument()
  })

  it('nennt im Label-Menü je Label den Zustand alle/einige/keine im zugänglichen Namen', () => {
    zeigeLeiste({
      labelOptions: [
        { label: label(1, 'Bug'), zustand: 'alle' },
        { label: label(2, 'Nacht'), zustand: 'einige' },
        { label: label(3, 'Ux'), zustand: 'keine' },
      ],
    })

    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))

    expect(screen.getByRole('menuitem', { name: 'Bug — alle gewählten Karten' })).toBeInTheDocument()
    expect(
      screen.getByRole('menuitem', { name: 'Nacht — einige gewählte Karten' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Ux — keine gewählte Karte' })).toBeInTheDocument()
  })

  it('fügt ohne vollen Haken hinzu, nimmt beim vollen Haken ab und lässt das Menü offen', () => {
    const rueckrufe = zeigeLeiste({
      labelOptions: [
        { label: label(1, 'Bug'), zustand: 'alle' },
        { label: label(2, 'Nacht'), zustand: 'einige' },
        { label: label(3, 'Ux'), zustand: 'keine' },
      ],
    })

    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'Nacht — einige gewählte Karten' }))
    expect(rueckrufe.onToggleLabel).toHaveBeenLastCalledWith(2, 'ADD')

    // Das Menü bleibt offen — der zweite Klick geht ohne erneutes Öffnen durch.
    fireEvent.click(screen.getByRole('menuitem', { name: 'Ux — keine gewählte Karte' }))
    expect(rueckrufe.onToggleLabel).toHaveBeenLastCalledWith(3, 'ADD')

    fireEvent.click(screen.getByRole('menuitem', { name: 'Bug — alle gewählten Karten' }))
    expect(rueckrufe.onToggleLabel).toHaveBeenLastCalledWith(1, 'REMOVE')
    expect(rueckrufe.onToggleLabel).toHaveBeenCalledTimes(3)
  })

  it('schließt das Label-Menü auf Escape', () => {
    zeigeLeiste({ labelOptions: [{ label: label(1, 'Bug'), zustand: 'keine' }] })

    fireEvent.click(screen.getByRole('button', { name: 'Labels' }))
    expect(screen.getByRole('menuitem', { name: 'Bug — keine gewählte Karte' })).toBeInTheDocument()

    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' })
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('sperrt Labels mit dem Grund im Namen und im Tooltip', async () => {
    zeigeLeiste({ labelsDisabledReason: 'Vorhaben tragen keine Labels' })

    expect(screen.getByRole('button', { name: 'Labels — Vorhaben tragen keine Labels' })).toBeDisabled()

    // Der gesperrte Button meldet keine Mausereignisse; der Tooltip hängt am span-Wrapper, den MUI
    // mit dem Titel als zugänglichem Namen versieht.
    fireEvent.mouseOver(screen.getByLabelText('Vorhaben tragen keine Labels'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Vorhaben tragen keine Labels')
  })
})
