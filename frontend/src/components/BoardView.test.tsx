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
  type: 'CARD', parentId: null, shortcode: null,
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

  it('legt über den +Dialog eine Karte mit Beschreibung an', async () => {
    const created: Card = { ...card, id: 200, number: 2, title: 'Neu', columnId: 20 }
    const api = { create: vi.fn().mockResolvedValue(created), move: vi.fn() }
    render(<BoardView board={board} initialCards={[card]} canEdit api={api} />)

    fireEvent.click(screen.getByLabelText('Karte in Done anlegen'))
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith(1, 20, 'Neu', expect.stringContaining('## Kontext'), null),
    )
    expect(within(screen.getByTestId('column-20')).getByTestId('card-200')).toBeInTheDocument()
  })

  it('blendet den +Button für Nicht-Editoren aus', () => {
    const api = { create: vi.fn(), move: vi.fn() }
    render(<BoardView board={board} initialCards={[card]} canEdit={false} api={api} />)
    expect(screen.queryByLabelText('Karte in Done anlegen')).not.toBeInTheDocument()
  })

  it('legt über Typ=Epic ein Epic an statt einer Karte', async () => {
    const api = { create: vi.fn(), move: vi.fn() }
    const epicsApi = { create: vi.fn().mockResolvedValue({ id: 5 }) }
    const onEpicsChanged = vi.fn()
    render(
      <BoardView board={board} initialCards={[card]} canEdit api={api}
        epicsApi={epicsApi} onEpicsChanged={onEpicsChanged} />,
    )

    fireEvent.click(screen.getByLabelText('Karte in Backlog anlegen'))
    fireEvent.change(screen.getByLabelText('Typ'), { target: { value: 'EPIC' } })
    fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: 'AUT' } })
    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Auth' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(epicsApi.create).toHaveBeenCalledWith(1, 'Auth', expect.any(String), 'AUT'))
    expect(api.create).not.toHaveBeenCalled()
    expect(onEpicsChanged).toHaveBeenCalled()
  })

  it('zeigt ein Epic-Badge auf zugeordneten Karten', () => {
    const assigned: Card = { ...card, parentId: 9 }
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 }]
    render(<BoardView board={board} initialCards={[assigned]} canEdit epics={epics} api={{ create: vi.fn(), move: vi.fn() }} />)
    expect(screen.getByText('AUT')).toBeInTheDocument()
  })
})
