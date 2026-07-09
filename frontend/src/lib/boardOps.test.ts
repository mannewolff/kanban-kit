import { describe, expect, it } from 'vitest'
import type { Card } from '../api/cards'
import { activeCardsInColumn, applyMove } from './boardOps'

function card(id: number, columnId: number, position: number, archived = false): Card {
  return {
    id, boardId: 1, columnId, number: id, title: `#${id}`, description: null,
    positionInColumn: position, archived, movedToDoneAt: null, dependencies: [],
  }
}

describe('boardOps', () => {
  it('activeCardsInColumn filtert archivierte und sortiert nach Position', () => {
    const cards = [card(1, 10, 1), card(2, 10, 0), card(3, 10, 2, true), card(4, 20, 0)]
    const result = activeCardsInColumn(cards, 10).map((c) => c.id)
    expect(result).toEqual([2, 1])
  })

  it('applyMove verschiebt die Karte ans Ende der Zielspalte', () => {
    const cards = [card(1, 10, 0), card(2, 20, 0)]
    const moved = applyMove(cards, 1, 20)
    const card1 = moved.find((c) => c.id === 1)!
    expect(card1.columnId).toBe(20)
    expect(card1.positionInColumn).toBe(1) // hinter der bestehenden Karte in Spalte 20
    expect(cards[0].columnId).toBe(10) // Original unverändert
  })

  it('applyMove in dieselbe Spalte liefert unveränderte Referenz', () => {
    const cards = [card(1, 10, 0)]
    expect(applyMove(cards, 1, 10)).toBe(cards)
  })
})
