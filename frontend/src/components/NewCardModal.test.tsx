import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NewCardModal } from './NewCardModal'

describe('NewCardModal', () => {
  it('legt per Cmd/Ctrl+Enter im Titel-Feld an', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const onClose = vi.fn()
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={onClose} onSubmit={onSubmit} />,
    )

    const titleField = screen.getByLabelText('Titel')
    fireEvent.change(titleField, { target: { value: 'Neue Karte' } })
    fireEvent.keyDown(titleField, { key: 'Enter', ctrlKey: true })

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'CARD', title: 'Neue Karte' }),
    )
    await Promise.resolve()
  })

  it('legt ohne Ctrl/Cmd bei Enter nichts an', () => {
    const onSubmit = vi.fn()
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    const titleField = screen.getByLabelText('Titel')
    fireEvent.change(titleField, { target: { value: 'Neue Karte' } })
    fireEvent.keyDown(titleField, { key: 'Enter' })

    expect(onSubmit).not.toHaveBeenCalled()
  })
})
