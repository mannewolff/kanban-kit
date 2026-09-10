import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { Card } from '../api/cards'
import { SnackbarProvider } from './SnackbarProvider'
import { TrashDialog } from './TrashDialog'

const base = {
  boardId: 1, columnId: 10, positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null as string | null,
  dependencies: [] as number[], type: 'CARD' as const, parentId: null as number | null,
  shortcode: null as string | null, assignees: [] as number[], dueDate: null as string | null,
  labels: [] as number[],
  derivedFrom: null as number | null,
  excerpt: null as string | null,
}
const card: Card = { ...base, id: 100, number: 5, title: 'Gelöscht', description: null }

function mkApi() {
  return {
    listTrash: vi.fn().mockResolvedValue([card]),
    restoreDeleted: vi.fn().mockResolvedValue(card),
    purge: vi.fn().mockResolvedValue(undefined),
  }
}

describe('TrashDialog', () => {
  it('listet gelöschte Karten und stellt eine wieder her', async () => {
    const api = mkApi()
    const onChanged = vi.fn()
    render(
      <TrashDialog open boardId={1} canPurge={false} onClose={vi.fn()} onChanged={onChanged} api={api} />,
    )

    expect(await screen.findByText(/Gelöscht/)).toBeInTheDocument()
    // Ohne Purge-Recht kein „Endgültig löschen".
    expect(screen.queryByLabelText('Gelöscht endgültig löschen')).not.toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Gelöscht wiederherstellen'))
    await waitFor(() => expect(api.restoreDeleted).toHaveBeenCalledWith(100))
    expect(onChanged).toHaveBeenCalled()
  })

  it('löscht mit Purge-Recht endgültig', async () => {
    const api = mkApi()
    render(<TrashDialog open boardId={1} canPurge onClose={vi.fn()} onChanged={vi.fn()} api={api} />)

    fireEvent.click(await screen.findByLabelText('Gelöscht endgültig löschen'))
    await waitFor(() => expect(api.purge).toHaveBeenCalledWith(100))
  })

  it('restore: meldet den Serverfehler und lädt nicht neu', async () => {
    const api = mkApi()
    api.restoreDeleted = vi.fn().mockRejectedValue(new ApiError(409, 'Konflikt', undefined, 'Die Spalte ist voll.'))
    const onChanged = vi.fn()
    render(
      <TrashDialog open boardId={1} canPurge={false} onClose={vi.fn()} onChanged={onChanged} api={api} />,
      { wrapper: SnackbarProvider },
    )

    fireEvent.click(await screen.findByLabelText('Gelöscht wiederherstellen'))

    // `hidden: true`: Der offene MUI-Dialog stellt alles außerhalb seines Portals auf
    // `aria-hidden` — der Toast trägt seine Rolle, wird von der Standardabfrage aber übergangen.
    const toast = await screen.findByRole('alert', { hidden: true })
    expect(toast).toHaveTextContent('Die Spalte ist voll.')
    expect(toast).toHaveClass('MuiAlert-filledError')
    // Ein Fehlschlag ändert nichts: kein zweiter Ladelauf, kein Signal an den Aufrufer.
    expect(api.listTrash).toHaveBeenCalledTimes(1)
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('restore: fällt ohne API-Kontext auf den eigenen Text zurück', async () => {
    const api = mkApi()
    api.restoreDeleted = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    render(
      <TrashDialog open boardId={1} canPurge={false} onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
      { wrapper: SnackbarProvider },
    )

    fireEvent.click(await screen.findByLabelText('Gelöscht wiederherstellen'))

    expect(await screen.findByText('Wiederherstellen fehlgeschlagen.')).toBeInTheDocument()
  })

  it('purge: meldet den Serverfehler und lädt nicht neu', async () => {
    const api = mkApi()
    api.purge = vi.fn().mockRejectedValue(new ApiError(403, 'Verboten', undefined, 'Keine Berechtigung zum Löschen.'))
    const onChanged = vi.fn()
    render(
      <TrashDialog open boardId={1} canPurge onClose={vi.fn()} onChanged={onChanged} api={api} />,
      { wrapper: SnackbarProvider },
    )

    fireEvent.click(await screen.findByLabelText('Gelöscht endgültig löschen'))

    const toast = await screen.findByRole('alert', { hidden: true })
    expect(toast).toHaveTextContent('Keine Berechtigung zum Löschen.')
    expect(toast).toHaveClass('MuiAlert-filledError')
    expect(api.listTrash).toHaveBeenCalledTimes(1)
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('purge: fällt ohne API-Kontext auf den eigenen Text zurück', async () => {
    const api = mkApi()
    api.purge = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    render(
      <TrashDialog open boardId={1} canPurge onClose={vi.fn()} onChanged={vi.fn()} api={api} />,
      { wrapper: SnackbarProvider },
    )

    fireEvent.click(await screen.findByLabelText('Gelöscht endgültig löschen'))

    expect(await screen.findByText('Endgültiges Löschen fehlgeschlagen.')).toBeInTheDocument()
  })

  it('zeigt einen Hinweis bei leerem Papierkorb', async () => {
    const api = mkApi()
    api.listTrash = vi.fn().mockResolvedValue([])
    render(<TrashDialog open boardId={1} canPurge={false} onClose={vi.fn()} onChanged={vi.fn()} api={api} />)

    expect(await screen.findByText('Der Papierkorb ist leer.')).toBeInTheDocument()
  })
})
