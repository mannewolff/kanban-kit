/**
 * localStorage-Schlüssel für die auf einem Board ausgeblendeten Vorhaben (Plan #620).
 *
 * Der Schlüssel steht hier und nur hier: Gesetzt wird der Zustand an der Vorhaben-Kachel
 * (`EpicsPage`), gelesen und aufgehoben wird er in `BoardPage`, die ihn an `BoardView` durchreicht.
 * Stünde der Name an beiden Stellen literal, wäre eine Umbenennung an einer Stelle kein Fehler,
 * sondern zwei getrennte Zustände, die sich nie wiederfinden.
 *
 * Das Wertformat gehört zum Schlüssel: ein JSON-Array der ausgeblendeten `Epic.id`.
 *
 * @param boardId Board, dessen Ausblendungen gemeint sind
 */
export function hiddenEpicsStorageKey(boardId: number): string {
  return `manban.boardHiddenEpics.${boardId}`
}

/**
 * Der gespeicherte Stand der ausgeblendeten Vorhaben eines Boards. Steht neben dem Schlüssel, weil
 * ihn mehrere Stellen brauchen — die Vorhaben-Seite und das Board (Plan #717, A3). Zwei Abschriften
 * desselben Lesevorgangs wären zwei Gelegenheiten, das Wertformat auseinanderlaufen zu lassen.
 *
 * Ein defektes, gesperrtes oder inhaltlich kaputtes `localStorage` liefert „nichts ausgeblendet"
 * statt die Seite scheitern zu lassen: Die Ausblendung ist reine Darstellung, ihr Verlust kostet
 * keine Daten.
 *
 * React-frei — die Anbindung an den Zustand macht die aufrufende Komponente.
 *
 * @param boardId Board, dessen Ausblendungen gemeint sind
 */
export function leseAusgeblendet(boardId: number): ReadonlySet<number> {
  try {
    const raw = localStorage.getItem(hiddenEpicsStorageKey(boardId))
    return raw ? new Set<number>(JSON.parse(raw) as number[]) : new Set<number>()
  } catch {
    return new Set<number>()
  }
}

/**
 * Schreibt den Stand der ausgeblendeten Vorhaben eines Boards fort. Die leere Menge löscht den
 * Schlüssel, statt `[]` zu hinterlassen: Beides hieße „nichts ausgeblendet", und ein Eintrag ohne
 * Aussage bliebe für immer stehen.
 *
 * Ohne funktionierendes `localStorage` fällt nur das Merken über den Seitenwechsel hinaus aus —
 * der Aufrufer hat seinen Zustand bereits gesetzt und soll daran nicht scheitern.
 *
 * @param boardId Board, dessen Ausblendungen gemeint sind
 * @param ids IDs der ausgeblendeten Vorhaben (`Epic.id`)
 */
export function schreibeAusgeblendet(boardId: number, ids: ReadonlySet<number>): void {
  try {
    if (ids.size === 0) {
      localStorage.removeItem(hiddenEpicsStorageKey(boardId))
    } else {
      localStorage.setItem(hiddenEpicsStorageKey(boardId), JSON.stringify([...ids]))
    }
  } catch {
    // localStorage nicht verfügbar
  }
}
