import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { BulkActionBar } from './BulkActionBar'

describe('BulkActionBar', () => {
  it('zeigt die Anzahl und ruft die passenden Callbacks', () => {
    const onArchive = vi.fn()
    const onMove = vi.fn()
    const onCancel = vi.fn()
    render(
      <BulkActionBar
        count={3}
        canMove
        onArchive={onArchive}
        onMove={onMove}
        onCancel={onCancel}
      />,
    )

    expect(screen.getByText('3 ausgewählt')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Archivieren' }))
    expect(onArchive).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Verschieben' }))
    expect(onMove).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('blendet Verschieben aus, wenn der Nutzer nicht verschieben darf', () => {
    render(
      <BulkActionBar
        count={1}
        canMove={false}
        onArchive={vi.fn()}
        onMove={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.queryByRole('button', { name: 'Verschieben' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Archivieren' })).toBeInTheDocument()
  })
})
