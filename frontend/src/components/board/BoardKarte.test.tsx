import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Card } from '../../api/cards'
import { BoardKarte } from './BoardKarte'
import { renderMitSprache } from '../../i18n/renderMitSprache'

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

describe('BoardKarte auf Englisch', () => {
  const zeigenEn = (over: Partial<Card>, weiter: { selectionMode?: boolean; spaltenname?: string } = {}) =>
    renderMitSprache(
      <BoardKarte
        card={{ ...card, ...over }} epic={undefined} done={false} spaltenname={weiter.spaltenname ?? 'Backlog'}
        dichte="normal" bewegt={false} selectionMode={weiter.selectionMode ?? false} selected={false} canEdit
        members={[{ userId: 7, email: 'a@b.c', displayName: 'Ada Lovelace', role: 'MEMBER' }]}
        boardLabels={[{ id: 3, boardId: 1, name: 'Fehler', color: '#ff0000', countOnEpicTile: false }]}
        retentionDays={30}
        onDragStart={vi.fn()} onDragEnd={vi.fn()} onSelect={vi.fn()} onOpen={vi.fn()} onMenu={vi.fn()}
      />,
      'en',
    )

  it('benennt Menü, Labels, Zuständige, Status und Frist auf Englisch', () => {
    zeigenEn({ labels: [3], assignees: [7], dueDate: '2026-10-13', status: 'READY' })

    expect(screen.getByLabelText('Menu Aufgabe')).toBeInTheDocument()
    expect(screen.getByLabelText('Labels Aufgabe')).toBeInTheDocument()
    expect(screen.getByLabelText('Assignees Aufgabe')).toBeInTheDocument()
    expect(screen.getByLabelText('Status Ready')).toBeInTheDocument()
    expect(screen.getByLabelText('Due Aufgabe')).toHaveTextContent('due Oct 13, 2026')
    // Daten bleiben unverändert (Kriterium 4).
    expect(screen.getByText('Fehler')).toBeInTheDocument()
  })

  it('benennt das Auswahlkästchen auf Englisch', () => {
    zeigenEn({}, { selectionMode: true })

    expect(screen.getByRole('checkbox', { name: 'Select card Aufgabe' })).toBeInTheDocument()
  })
})
