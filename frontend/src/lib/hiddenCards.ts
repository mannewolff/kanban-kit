import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'

/**
 * Nummern der Karten, die auf dem Board verdeckt sind — als Vereinigung beider Achsen, damit
 * eine Spalte eine Zahl meldet und nicht zwei (Plan #620, E4).
 *
 * Beide Achsen rechnen über `Epic.memberNumbers` (Task #1103). Das ist genau die Menge, deren
 * Karten das Kürzel des Vorhabens tragen und aus der der Server `done/total` zählt: Wurzel,
 * technischer Plan und die Arbeitspakete dahinter. Die Filter-Achse rechnete früher über
 * `card.parentId` und zeigte damit nur die direkt zugeordneten Karten; Plan #620, E3 hielt diese
 * eigene Bedeutung ausdrücklich fest. Dieser Task hebt E3 auf — der Filter soll das ganze
 * Vorhaben zeigen, nicht nur seine Wurzel.
 *
 * Die Karten sind zwingend: Ohne sie ist die Filter-Achse nicht berechenbar, und Karten ganz
 * ohne Vorhaben tauchen in keinem `memberNumbers` auf.
 *
 * Ein Filter auf ein Vorhaben, das es nicht (mehr) gibt, verdeckt alle Karten — seine Menge an
 * Mitgliedern ist leer. Das Zurücksetzen eines ungültigen Filters regelt `BoardView` an eigener
 * Stelle, diese Funktion rät nicht.
 *
 * @param cards Karten des Boards
 * @param epics Vorhaben des Boards
 * @param hiddenEpicIds IDs der ausgeblendeten Vorhaben (`Epic.id`, nicht `Epic.number`)
 * @param epicFilter aktiver Vorhaben-Filter (`Epic.id`) oder `null`
 */
export function hiddenCardNumbers(
  cards: readonly Pick<Card, 'number'>[],
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
    const mitglieder = new Set(epics.find((epic) => epic.id === epicFilter)?.memberNumbers ?? [])
    for (const card of cards) {
      if (!mitglieder.has(card.number)) {
        hidden.add(card.number)
      }
    }
  }
  return hidden
}
