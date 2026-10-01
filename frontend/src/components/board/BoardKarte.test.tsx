import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Card } from '../../api/cards'
import { BoardKarte } from './BoardKarte'

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 1, title: 'Aufgabe', description: null, excerpt: null,
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
  derivedFrom: null, status: null, canSetStatus: false,
}

const zeigen = (over: { selectionMode: boolean; selected: boolean; canEdit: boolean }) =>
  render(
    <BoardKarte
      card={card} epic={undefined} done={false} spaltenname="Backlog" dichte="normal" bewegt={false}
      members={[]} boardLabels={[]} retentionDays={30}
      onDragStart={vi.fn()} onDragEnd={vi.fn()} onSelect={vi.fn()} onOpen={vi.fn()} onMenu={vi.fn()}
      {...over}
    />,
  )

describe('BoardKarte — Ziehbarkeit (#1324)', () => {
  it.each([
    { fall: 'außerhalb des Auswahlmodus mit Bearbeitungsrecht', selectionMode: false, selected: false, canEdit: true, ziehbar: true },
    { fall: 'ausgewählt im Auswahlmodus mit Bearbeitungsrecht', selectionMode: true, selected: true, canEdit: true, ziehbar: true },
    { fall: 'nicht ausgewählt im Auswahlmodus', selectionMode: true, selected: false, canEdit: true, ziehbar: false },
    { fall: 'ausgewählt, aber ohne Bearbeitungsrecht', selectionMode: true, selected: true, canEdit: false, ziehbar: false },
    { fall: 'ohne Bearbeitungsrecht außerhalb des Auswahlmodus', selectionMode: false, selected: false, canEdit: false, ziehbar: false },
  ])('ist $fall ziehbar: $ziehbar', ({ selectionMode, selected, canEdit, ziehbar }) => {
    zeigen({ selectionMode, selected, canEdit })

    expect(screen.getByTestId('card-100')).toHaveAttribute('draggable', String(ziehbar))
  })

  it('zeigt im Auswahlmodus kein ⋮-Menü, auch nicht an einer ziehbaren Karte', () => {
    zeigen({ selectionMode: true, selected: true, canEdit: true })

    expect(screen.queryByLabelText('Menü Aufgabe')).not.toBeInTheDocument()
  })
})
