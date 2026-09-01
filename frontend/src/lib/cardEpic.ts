import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'

/**
 * Das Vorhaben, dessen Kürzel eine Karte trägt (Plan #682, E2) — als reine Funktion, damit die
 * Regel an allen Anzeigeorten dieselbe ist und nicht je Ort nachgebaut wird (E1).
 *
 * Gerechnet wird allein auf der Vorhaben-Liste: Ein Vorhaben kommt in Frage, wenn `card.number`
 * in seinem `memberNumbers` steht — dort führt der Server beide Zugehörigkeitswege zusammen, den
 * ausdrücklich zugeordneten und den über die Herkunft geerbten.
 *
 * `card.parentId` liest die Funktion bewusst **nicht**. Eine archivierte oder in den Ideenpool
 * gelegte Karte behält ihre `parentId`, zählt aber serverseitig nicht mehr zur Zugehörigkeit
 * (`EpicMembership.zaehlt()`); die Anzeige folgt dem Server statt einer zweiten Wahrheit.
 *
 * **Herkunft vor ausdrücklicher Zuordnung:** Steht die Nummer in `memberNumbers`, aber nicht in
 * `rootNumbers`, wurde das Vorhaben über die Herkunftskette erreicht und hat Vorrang. Bleiben
 * mehrere gleichrangig, gewinnt die kleinste Vorhaben-Nummer — sonst spränge das Kürzel zwischen
 * zwei Renderings.
 *
 * @param card Karte, deren Vorhaben gesucht wird
 * @param epics Vorhaben des Boards
 * @returns das Vorhaben oder `undefined`, wenn die Karte in keinem steht
 */
export function epicOfCard(card: Card, epics: readonly Epic[]): Epic | undefined {
  const kandidaten = epics.filter((epic) => epic.memberNumbers.includes(card.number))
  // 0 = über die Herkunft erreicht, 1 = ausdrücklich zugeordnet; kleinerer Rang gewinnt.
  const rang = (epic: Epic) => (epic.rootNumbers.includes(card.number) ? 1 : 0)
  return kandidaten.sort((a, b) => rang(a) - rang(b) || a.number - b.number)[0]
}
