import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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

  it('reicht Epic-Zuordnung und Beschreibung durch', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 }]
    render(
      <NewCardModal open columnName="Backlog" epics={epics} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.change(screen.getByLabelText('Epic'), { target: { value: '9' } })
    // Wieder abwählen -> deckt den `? null`-Zweig der Epic-Auswahl ab.
    fireEvent.change(screen.getByLabelText('Epic'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Epic'), { target: { value: '9' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Text' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Karte', parentId: 9, description: 'Text' }),
    )
    await Promise.resolve()
  })

  it('legt per Ctrl+Enter bei leerem Titel nichts an (canSubmit-Guard)', () => {
    const onSubmit = vi.fn()
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.keyDown(screen.getByLabelText('Titel'), { key: 'Enter', ctrlKey: true })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('deaktiviert „Anlegen" während des Speicherns', async () => {
    let resolve: () => void = () => {}
    const onSubmit = vi.fn().mockReturnValue(
      new Promise<void>((r) => {
        resolve = r
      }),
    )
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Anlegen' })).toBeDisabled())
    resolve()
  })

  it('selektiert den Titel, wenn sich initialValues bei offenem Dialog ändern (Ref gesetzt)', async () => {
    const { rerender } = render(
      <NewCardModal
        open
        columnName="Backlog"
        epics={[]}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        initialValues={{ title: 'Alt', description: 'x', parentId: null }}
      />,
    )
    const titleField = (await screen.findByLabelText('Titel')) as HTMLInputElement
    const selectSpy = vi.spyOn(titleField, 'select')

    // Neuer initialValues-Objektbezug bei weiterhin offenem Dialog → Effekt läuft erneut, diesmal
    // ist die Ref gesetzt und select() wird aufgerufen (deckt den non-null-Zweig von Zeile 76).
    rerender(
      <NewCardModal
        open
        columnName="Backlog"
        epics={[]}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        initialValues={{ title: 'Neu', description: 'y', parentId: null }}
      />,
    )

    expect(selectSpy).toHaveBeenCalled()
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
