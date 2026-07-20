import type { Card } from '../api/cards'

/**
 * Aktive Karten einer Spalte, nach Position sortiert. Archivierte Karten und Ideen (Ideen-Speicher)
 * fallen aus der Board-/Spaltenansicht — sie halten keinen aktiven Positions-Anspruch.
 */
export function activeCardsInColumn(cards: Card[], columnId: number): Card[] {
  return cards
    .filter((c) => c.columnId === columnId && !c.archived && !c.ideaStored)
    .sort((a, b) => a.positionInColumn - b.positionInColumn)
}

/**
 * Optimistische Verschiebung: setzt die Karte ans Ende der Zielspalte. Reine Funktion —
 * ändert das Eingabe-Array nicht. Gleiche Spalte -> unveränderte Referenz.
 */
export function applyMove(cards: Card[], cardId: number, toColumnId: number): Card[] {
  const card = cards.find((c) => c.id === cardId)
  if (!card || card.columnId === toColumnId) {
    return cards
  }
  const endPosition = activeCardsInColumn(cards, toColumnId).length
  return cards.map((c) => (c.id === cardId ? { ...c, columnId: toColumnId, positionInColumn: endPosition } : c))
}
