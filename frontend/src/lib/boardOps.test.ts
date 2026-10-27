import { describe, expect, it } from 'vitest'
import type { Card } from '../api/cards'
import {
  ablagePosition,
  activeCardsInColumn,
  applyMove,
  applyMoveAt,
  spaltenAuswahl,
  spaltenAuswahlUmschalten,
} from './boardOps'

function card(id: number, columnId: number, position: number, archived = false): Card {
  return {
    id, boardId: 1, columnId, number: id, title: `#${id}`, description: null, excerpt: null,
    positionInColumn: position, archived, movedToDoneAt: null, dependencies: [],
    type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [],
    derivedFrom: null,
    status: null,
    canSetStatus: false,
    herkunft: null,
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

  it('applyMove verschiebt genau die gewählte Karte, auch wenn sie nicht vorn steht', () => {
    const cards = [card(1, 10, 0), card(2, 20, 0)]
    const moved = applyMove(cards, 2, 10)
    expect(moved.find((c) => c.id === 2)).toMatchObject({ columnId: 10, positionInColumn: 1 })
    expect(moved.find((c) => c.id === 1)).toMatchObject({ columnId: 10, positionInColumn: 0 })
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

describe('applyMoveAt — Ablage an einer Stelle (Plan #1707, E3)', () => {
  const reihe = (cards: Card[], columnId: number) =>
    activeCardsInColumn(cards, columnId).map((c) => [c.id, c.positionInColumn])

  it('sortiert die Karte in der Zielspalte an der Stelle ein und schließt die Lücke in der Quelle', () => {
    const cards = [card(1, 10, 0), card(2, 10, 1), card(3, 10, 2), card(4, 20, 0), card(5, 20, 1)]

    const moved = applyMoveAt(cards, 2, 20, 1)

    expect(reihe(moved, 20)).toEqual([[4, 0], [2, 1], [5, 2]])
    expect(reihe(moved, 10)).toEqual([[1, 0], [3, 1]])
    expect(moved.find((c) => c.id === 2)!.columnId).toBe(20)
    expect(cards[1]).toEqual(card(2, 10, 1)) // Original unverändert
  })

  it('ordnet innerhalb derselben Spalte um', () => {
    const cards = [card(1, 10, 0), card(2, 10, 1), card(3, 10, 2)]

    expect(reihe(applyMoveAt(cards, 3, 10, 0), 10)).toEqual([[3, 0], [1, 1], [2, 2]])
    expect(reihe(applyMoveAt(cards, 1, 10, 2), 10)).toEqual([[2, 0], [3, 1], [1, 2]])
  })

  it('begrenzt die Stelle auf den Bereich der Zielspalte', () => {
    const cards = [card(1, 10, 0), card(2, 20, 0)]

    expect(reihe(applyMoveAt(cards, 1, 20, 9), 20)).toEqual([[2, 0], [1, 1]])
    expect(reihe(applyMoveAt(cards, 1, 20, -3), 20)).toEqual([[1, 0], [2, 1]])
  })

  it('lässt archivierte Karten und fremde Spalten unberührt', () => {
    const archiviert = card(3, 20, 7, true)
    const fremd = card(4, 30, 5)
    const cards = [card(1, 10, 0), card(2, 20, 0), archiviert, fremd]

    const moved = applyMoveAt(cards, 1, 20, 0)

    expect(moved.find((c) => c.id === 3)).toBe(archiviert)
    expect(moved.find((c) => c.id === 4)).toBe(fremd)
  })

  it('liefert für eine unbekannte Karte dieselbe Referenz', () => {
    const cards = [card(1, 10, 0)]
    expect(applyMoveAt(cards, 99, 10, 0)).toBe(cards)
  })
})

describe('ablagePosition — angezeigte Stelle auf die ganze Spalte abbilden', () => {
  const ziel = [card(1, 20, 0), card(2, 20, 1), card(3, 20, 2), card(4, 20, 3)]

  it('setzt vor die angezeigte Karte an der Stelle, auch wenn davor verdeckte liegen', () => {
    const angezeigt = [ziel[0], ziel[2]]

    expect(ablagePosition(ziel, angezeigt, 0)).toBe(0)
    expect(ablagePosition(ziel, angezeigt, 1)).toBe(2)
  })

  it('legt hinter der letzten angezeigten Karte ans Ende der Spalte', () => {
    expect(ablagePosition(ziel, [ziel[0], ziel[2]], 2)).toBe(4)
    expect(ablagePosition([], [], 0)).toBe(0)
  })
})
