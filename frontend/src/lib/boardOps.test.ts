import { describe, expect, it } from 'vitest'
import type { Card } from '../api/cards'
import { activeCardsInColumn, applyMove, spaltenAuswahl, spaltenAuswahlUmschalten } from './boardOps'

function card(id: number, columnId: number, position: number, archived = false): Card {
  return {
    id, boardId: 1, columnId, number: id, title: `#${id}`, description: null, excerpt: null,
    positionInColumn: position, archived, movedToDoneAt: null, dependencies: [],
    type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
    derivedFrom: null,
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

  it('spaltenAuswahl meldet alle, einige und keine', () => {
    const ids = [1, 2, 3]
    expect(spaltenAuswahl(ids, new Set([1, 2, 3]))).toBe('alle')
    expect(spaltenAuswahl(ids, new Set([2]))).toBe('einige')
    expect(spaltenAuswahl(ids, new Set([9]))).toBe('keine')
  })

  it('spaltenAuswahl meldet für eine leere Spalte keine', () => {
    expect(spaltenAuswahl([], new Set([1]))).toBe('keine')
  })

  it('spaltenAuswahlUmschalten ergänzt die fehlenden Karten der Spalte', () => {
    const auswahl = new Set([1, 9])
    expect([...spaltenAuswahlUmschalten([1, 2], auswahl)]).toEqual([1, 9, 2])
    expect([...auswahl]).toEqual([1, 9]) // Eingabe unverändert
  })

  it('spaltenAuswahlUmschalten nimmt bei voller Spalte nur deren Karten heraus', () => {
    const auswahl = new Set([1, 2, 9])
    expect([...spaltenAuswahlUmschalten([1, 2], auswahl)]).toEqual([9])
  })
})
