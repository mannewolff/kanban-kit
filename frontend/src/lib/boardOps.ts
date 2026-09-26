import type { Card } from '../api/cards'

/**
 * Aktive Karten einer Spalte, nach Position sortiert. Archivierte Karten fallen aus der
 * Board-/Spaltenansicht — sie halten keinen aktiven Positions-Anspruch.
 */
export function activeCardsInColumn(cards: Card[], columnId: number): Card[] {
  return cards
    .filter((c) => c.columnId === columnId && !c.archived)
    .sort((a, b) => a.positionInColumn - b.positionInColumn)
}

/** Wie weit die angezeigten Karten einer Spalte in der Auswahl vertreten sind. */
export type SpaltenAuswahlZustand = 'alle' | 'einige' | 'keine'

/**
 * Zustand des Spalten-Kästchens. Gemessen wird ausschließlich an den **angezeigten** Karten
 * (`angezeigteIds`) — dieselbe Regel wie bei jeder Massenaktion: Was ein Filter oder ein
 * ausgeblendetes Vorhaben verdeckt, zählt nicht mit und wird nicht getroffen. Eine Spalte ohne
 * angezeigte Karten meldet `keine`.
 */
export function spaltenAuswahl(
  angezeigteIds: number[],
  auswahl: ReadonlySet<number>,
): SpaltenAuswahlZustand {
  const treffer = angezeigteIds.filter((id) => auswahl.has(id)).length
  if (treffer === 0) return 'keine'
  return treffer === angezeigteIds.length ? 'alle' : 'einige'
}

/**
 * Umschalten der Auswahl einer Spalte: Sind nicht alle angezeigten Karten gewählt, kommen die
 * fehlenden dazu; sind alle gewählt, fallen genau sie heraus. Karten anderer Spalten bleiben
 * unberührt — so lassen sich Spalten nacheinander einsammeln. Reine Funktion, das Eingabe-Set
 * bleibt unverändert.
 */
export function spaltenAuswahlUmschalten(
  angezeigteIds: number[],
  auswahl: ReadonlySet<number>,
): Set<number> {
  const next = new Set(auswahl)
  const abwaehlen = spaltenAuswahl(angezeigteIds, auswahl) === 'alle'
  for (const id of angezeigteIds) {
    if (abwaehlen) next.delete(id)
    else next.add(id)
  }
  return next
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
