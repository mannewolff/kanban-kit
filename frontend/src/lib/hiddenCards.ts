import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'

/**
 * Nummern der Karten, die auf dem Board verdeckt sind — als Vereinigung beider Achsen, damit
 * eine Spalte eine Zahl meldet und nicht zwei (Plan #620, E4).
 *
 * Die Achsen bleiben getrennt (E3): Die Ausblende-Achse rechnet über `Epic.memberNumbers` und
 * erfasst damit auch die über die Herkunft geerbten Karten; die Filter-Achse behält ihre
 * `parentId`-Semantik und ist die Umkehrung des sichtbaren Bestands. Ein stilles Upgrade des
 * Filters auf `memberNumbers` wäre eine Bedeutungsänderung, keine Vereinheitlichung.
 *
 * Die Karten sind zwingend: Ohne sie ist die Filter-Achse nicht berechenbar, und Karten ganz
 * ohne Vorhaben tauchen in keinem `memberNumbers` auf.
 *
 * @param cards Karten des Boards
 * @param epics Vorhaben des Boards
 * @param hiddenEpicIds IDs der ausgeblendeten Vorhaben (`Epic.id`, nicht `Epic.number`)
 * @param epicFilter aktiver Vorhaben-Filter (`Epic.id`) oder `null`
 */
export function hiddenCardNumbers(
  cards: readonly Pick<Card, 'number' | 'parentId'>[],
  epics: readonly Pick<Epic, 'id' | 'memberNumbers'>[],
  hiddenEpicIds: ReadonlySet<number>,
  epicFilter: number | null,
): Set<number> {
  const hidden = new Set<number>()
  // Über die vorhandenen Vorhaben iterieren, nicht über die ausgeblendeten IDs: Ein inzwischen
  // gelöschtes Vorhaben liefert so einen leeren Beitrag statt eines Fehlers.
  for (const epic of epics) {
    if (hiddenEpicIds.has(epic.id)) {
      for (const number of epic.memberNumbers) {
        hidden.add(number)
      }
    }
  }
  if (epicFilter != null) {
    for (const card of cards) {
      if (card.parentId !== epicFilter) {
        hidden.add(card.number)
      }
    }
  }
  return hidden
}
