import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Card } from '../api/cards'
import { TrashDialog } from './TrashDialog'

const base = {
  boardId: 1, columnId: 10, positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null as string | null,
  dependencies: [] as number[], type: 'CARD' as const, parentId: null as number | null,
  shortcode: null as string | null, assignees: [] as number[], dueDate: null as string | null,
  labels: [] as number[],
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

  it('zeigt einen Hinweis bei leerem Papierkorb', async () => {
    const api = mkApi()
    api.listTrash = vi.fn().mockResolvedValue([])
    render(<TrashDialog open boardId={1} canPurge={false} onClose={vi.fn()} onChanged={vi.fn()} api={api} />)

    expect(await screen.findByText('Der Papierkorb ist leer.')).toBeInTheDocument()
  })
})
