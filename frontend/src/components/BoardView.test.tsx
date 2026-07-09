import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Board } from '../api/boards'
import type { Card } from '../api/cards'
import { BoardView } from './BoardView'

const board: Board = {
  id: 1,
  projectId: 1,
  name: 'Board',
  createdAt: '2026-01-01T00:00:00Z',
  columns: [
    { id: 10, name: 'Backlog', position: 0, wipLimit: null },
    { id: 20, name: 'Done', position: 1, wipLimit: null },
  ],
}

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null,
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
}

function dropOnColumn(columnId: number, cardId: number) {
  fireEvent.drop(screen.getByTestId(`column-${columnId}`), {
    dataTransfer: { getData: () => String(cardId) },
  })
}

describe('BoardView Drag & Drop', () => {
  it('verschiebt die Karte optimistisch in die Zielspalte', async () => {
    const api = { create: vi.fn(), move: vi.fn().mockResolvedValue(undefined) }
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    dropOnColumn(20, 100)

    await waitFor(() => expect(within(screen.getByTestId('column-20')).getByTestId('card-100')).toBeInTheDocument())
    expect(api.move).toHaveBeenCalledWith(100, 20, 0)
  })

  it('rollt bei einem API-Fehler auf den vorigen Stand zurück', async () => {
    const api = { create: vi.fn(), move: vi.fn().mockRejectedValue(new Error('fail')) }
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    dropOnColumn(20, 100)

    // Nach dem fehlgeschlagenen Move liegt die Karte wieder in der Ausgangsspalte.
    await waitFor(() => expect(within(screen.getByTestId('column-10')).getByTestId('card-100')).toBeInTheDocument())
    expect(within(screen.getByTestId('column-20')).queryByTestId('card-100')).not.toBeInTheDocument()
  })
})
