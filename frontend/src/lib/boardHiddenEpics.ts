/**
 * localStorage-Schlüssel für die auf einem Board ausgeblendeten Vorhaben (Plan #620).
 *
 * Der Schlüssel steht hier und nur hier: Gesetzt wird der Zustand an der Vorhaben-Kachel
 * (`EpicsPage`), gelesen und aufgehoben wird er am Board (`BoardView`). Stünde der Name an beiden
 * Stellen literal, wäre eine Umbenennung an einer Stelle kein Fehler, sondern zwei getrennte
 * Zustände, die sich nie wiederfinden.
 *
 * Das Wertformat gehört zum Schlüssel: ein JSON-Array der ausgeblendeten `Epic.id`.
 *
 * @param boardId Board, dessen Ausblendungen gemeint sind
 */
export function hiddenEpicsStorageKey(boardId: number): string {
  return `manban.boardHiddenEpics.${boardId}`
}
