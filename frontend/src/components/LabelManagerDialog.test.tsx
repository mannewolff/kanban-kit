import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Label } from '../api/labels'
import { LabelManagerDialog } from './LabelManagerDialog'

const labels: Label[] = [{ id: 1, boardId: 9, name: 'Bug', color: '#ff0000', countOnEpicTile: false }]

function mkApi() {
  return {
    create: vi.fn().mockResolvedValue({ id: 2, boardId: 9, name: 'Ux', color: '#00ff00', countOnEpicTile: false }),
    update: vi.fn().mockResolvedValue({ id: 1, boardId: 9, name: 'Defekt', color: '#ff0000', countOnEpicTile: false }),
    remove: vi.fn().mockResolvedValue(undefined),
  }
}

describe('LabelManagerDialog', () => {
  it('legt ein neues Label mit Namen und Farbe an', async () => {
    const api = mkApi()
    const onChanged = vi.fn()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={onChanged} api={api} />,
    )

    fireEvent.change(screen.getByLabelText('Neues Label'), { target: { value: 'Ux' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(api.create).toHaveBeenCalledWith(9, 'Ux', expect.any(String)))
    expect(onChanged).toHaveBeenCalled()
  })

  it('benennt ein bestehendes Label um', async () => {
    const api = mkApi()
    const onChanged = vi.fn()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={onChanged} api={api} />,
    )

    fireEvent.change(screen.getByLabelText('Label Bug'), { target: { value: 'Defekt' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(1, 'Defekt', '#ff0000'))
  })

  it('übernimmt geänderte Farben beim Anlegen und Umfärben', async () => {
    const api = mkApi()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    fireEvent.change(screen.getByLabelText('Neues Label'), { target: { value: 'Ux' } })
    fireEvent.change(screen.getByLabelText('Neue Label-Farbe'), { target: { value: '#123456' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    await waitFor(() => expect(api.create).toHaveBeenCalledWith(9, 'Ux', '#123456'))

    fireEvent.change(screen.getByLabelText('Farbe Bug'), { target: { value: '#abcdef' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => expect(api.update).toHaveBeenCalledWith(1, 'Bug', '#abcdef'))
  })

  it('legt ohne Namen nichts an (früher Abbruch)', () => {
    const api = mkApi()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    expect(api.create).not.toHaveBeenCalled()
  })

  it('löscht ein Label', async () => {
    const api = mkApi()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    fireEvent.click(screen.getByLabelText('Label Bug löschen'))

    await waitFor(() => expect(api.remove).toHaveBeenCalledWith(1))
  })

  it('zeigt einen Fehler, wenn das Anlegen fehlschlägt', async () => {
    const api = mkApi()
    api.create = vi.fn().mockRejectedValue(new Error('dup'))
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    fireEvent.change(screen.getByLabelText('Neues Label'), { target: { value: 'Bug' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(await screen.findByText(/konnte nicht angelegt/)).toBeInTheDocument()
  })

  // --- Haekchen "auf der Vorhaben-Kachel zaehlen" (Issue #664) ---------------

  const haekchen = () =>
    screen.getByRole('checkbox', { name: /auf der Vorhaben-Kachel zählen/i })

  it('sendet nach Umschalten und „Speichern" das Zähl-Feld mit', async () => {
    const api = mkApi()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    fireEvent.click(haekchen())
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(1, 'Bug', '#ff0000', true))
  })

  /**
   * Dass der gespeicherte Wert beim Umbenennen erhalten bleibt, leistet das Backend ueber
   * "fehlend = unveraendert" (#659). Der Dialog darf das Feld deshalb gar nicht erst senden --
   * sonst schriebe jedes Umbenennen die Einstellung mit.
   */
  it('sendet beim Umbenennen kein Zähl-Feld', async () => {
    const api = mkApi()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    fireEvent.change(screen.getByLabelText('Label Bug'), { target: { value: 'Defekt' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(1, 'Defekt', '#ff0000'))
  })

  it('schreibt beim bloßen Klick ohne „Speichern" nichts', () => {
    const api = mkApi()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    fireEvent.click(haekchen())

    expect(api.update).not.toHaveBeenCalled()
  })

  /**
   * `npm run lint` (jsx-a11y) prueft MUI-`Checkbox`-Kompositionen nicht zuverlaessig -- ein
   * gruener Lint belegt die Beschriftung also nicht. Deshalb beides ausdruecklich: auffindbar
   * ueber die Rolle UND als sichtbarer Text im DOM, nicht nur als aria-label.
   */
  it('beschriftet das Häkchen sichtbar, nicht nur über aria-label', () => {
    const api = mkApi()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    expect(haekchen()).toBeInTheDocument()
    expect(screen.getByText('auf der Vorhaben-Kachel zählen')).toBeInTheDocument()
  })

  it('zeigt den gespeicherten Wert an', () => {
    const api = mkApi()
    const gezaehlt: Label[] = [{ id: 1, boardId: 9, name: 'Bug', color: '#ff0000', countOnEpicTile: true }]
    render(
      <LabelManagerDialog open boardId={9} labels={gezaehlt} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    expect(haekchen()).toBeChecked()
  })

  it('schaltet ein gesetztes Häkchen wieder ab', async () => {
    const api = mkApi()
    const gezaehlt: Label[] = [{ id: 1, boardId: 9, name: 'Bug', color: '#ff0000', countOnEpicTile: true }]
    render(
      <LabelManagerDialog open boardId={9} labels={gezaehlt} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    fireEvent.click(haekchen())
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(api.update).toHaveBeenCalledWith(1, 'Bug', '#ff0000', false))
  })

  it('lässt die Anlege-Zeile unverändert (kein neues Feld an create)', async () => {
    const api = mkApi()
    render(
      <LabelManagerDialog open boardId={9} labels={labels} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
    )

    fireEvent.change(screen.getByLabelText('Neues Label'), { target: { value: 'Ux' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(api.create).toHaveBeenCalledWith(9, 'Ux', '#1976d2'))
  })
})
