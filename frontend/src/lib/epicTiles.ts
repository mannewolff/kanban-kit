import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'
import type { Label } from '../api/labels'
import { epicShortcode } from './epicMeta'

/**
 * Rechnung hinter der Vorhaben-Kachel (Issue #662): woraus ein Vorhaben besteht, was daran liegen
 * geblieben ist, und in welcher Reihenfolge die Kacheln stehen.
 *
 * Reine Datei ohne React — dasselbe Muster wie `boardOps.ts`. Die Regel ist damit unabhängig von
 * der Darstellung prüfbar; ein Komponententest bewiese sie nur mittelbar.
 *
 * Alle drei Funktionen rechnen auf `Epic.memberNumbers` (alle zugehörigen Karten, direkt
 * zugeordnete und über die Herkunft geerbte), **nicht** auf `rootNumbers`.
 */

/** Zusammensetzung eines Vorhabens nach Art seiner Karten. */
export interface KindCounts {
  requirements: number
  plans: number
  workItems: number
}

/** Ein Zustand auf der Kachel: Label samt Anzahl der Karten, die ihn tragen. */
export interface Mark {
  name: string
  color: string
  count: number
}

/**
 * Die Karten eines Vorhabens. Eine Mitgliedsnummer ohne zugehörige Karte wird übergangen —
 * möglich durch den Done-Retention-Job oder eine Ladelücke; als Arbeitspaket gezählt zeigte die
 * Kachel eine Karte an, die niemand öffnen kann.
 */
function memberCards(epic: Epic, cards: Card[]): Card[] {
  const byNumber = new Map(cards.map((c) => [c.number, c]))
  return epic.memberNumbers.map((n) => byNumber.get(n)).filter((c): c is Card => c !== undefined)
}

/**
 * Anzahl je Art. Die Ableitung liest **Titel-Präfixe** und ist gutmütig (Plan #657, E4): Eine rein
 * strukturelle Ableitung aus dem Herkunftsbaum — Knoten mit Kindern gleich Dokument, Blatt gleich
 * Arbeitspaket — wurde verworfen, weil ein Plan, aus dem noch keine Arbeitspakete geschnitten sind,
 * ein Blatt wäre. Genau diesen Fall will der Nutzer sehen.
 *
 * Ein Präfix zählt nur **am Anfang** des Titels und in exakt dieser Schreibweise (Konvention des
 * Arbeitsablaufs). Alles andere ist ein Arbeitspaket — ausdrücklich auch `[Idee]` (Entscheidung
 * Manne, 2026-08-31): Eine eingeplante Idee ist faktisch ein Arbeitspaket, und Ideen im Pool
 * erscheinen ohnehin nicht, sie tragen `ideaStored`.
 */
export function countKinds(epic: Epic, cards: Card[]): KindCounts {
  const counts: KindCounts = { requirements: 0, plans: 0, workItems: 0 }
  for (const card of memberCards(epic, cards)) {
    if (card.title.startsWith('[Fachlich]')) {
      counts.requirements++
    } else if (card.title.startsWith('[Plan]')) {
      counts.plans++
    } else {
      counts.workItems++
    }
  }
  return counts
}

/**
 * Die Zustände eines Vorhabens als Paare aus Label und Anzahl. Berücksichtigt werden ausschließlich
 * Labels mit `countOnEpicTile === true` (Issue #659) — welche das sind, entscheidet der Betreiber
 * je Board. Eine Karte mit zwei gezählten Labels zählt bei beiden.
 *
 * Ein gezähltes Label ohne Karte erscheint nicht: Eine Marke „0" wäre keine Aussage über den
 * Zustand, sondern über die Label-Verwaltung. Die Reihenfolge folgt der Label-ID aufsteigend,
 * dieselbe Ordnung wie im Herkunftsbaum (#661).
 */
export function aggregateMarks(epic: Epic, cards: Card[], labels: Label[]): Mark[] {
  const counted = labels.filter((l) => l.countOnEpicTile).sort((a, b) => a.id - b.id)
  const members = memberCards(epic, cards)
  return counted
    .map((label) => ({
      name: label.name,
      color: label.color,
      count: members.filter((c) => c.labels.includes(label.id)).length,
    }))
    .filter((mark) => mark.count > 0)
}

/** Karten des Vorhabens mit mindestens einer gezählten Marke — je Karte höchstens einmal. */
function handlungsbedarf(epic: Epic, cards: Card[], labels: Label[]): number {
  const countedIds = new Set(labels.filter((l) => l.countOnEpicTile).map((l) => l.id))
  return memberCards(epic, cards).filter((c) => c.labels.some((id) => countedIds.has(id))).length
}

/**
 * Rang der Gruppe, in der ein Vorhaben steht: 0 unfertig, 1 abgeschlossen, 2 leer. Innerhalb einer
 * Gruppe entscheidet der Handlungsbedarf.
 *
 * Leer heißt `total === 0` **und** ohne Anforderung: Ein Vorhaben, das eine Anforderung trägt, aber
 * noch keine Karten hat, ist eröffnet und gehört nicht ans Ende.
 */
function gruppe(epic: Epic): number {
  if (epic.total === 0 && epic.requirementCardNumber === null) return 2
  if (epic.done === epic.total && epic.total > 0) return 1
  return 0
}

/**
 * Vorhaben nach Handlungsbedarf (Plan #657, E6): absteigend nach der Zahl der Karten mit
 * mindestens einer gezählten Marke; abgeschlossene stehen hinter den unfertigen, leere ganz am
 * Ende. Bei Gleichstand entscheidet das Anzeige-Kürzel per `localeCompare`.
 *
 * **Auf Labelnamen wird nicht geprüft.** Naheliegend wäre, ein „fertig"-Label von
 * „Handlungsbedarf" auszunehmen — aber Issue #659 gibt die Namen gerade frei, und eine Sortierung,
 * die auf einem bestimmten Namen rechnete, bräche auf jedem fremden Board.
 *
 * Reine Funktion: Das Eingabe-Array bleibt unverändert.
 */
export function sortEpics(epics: Epic[], cards: Card[], labels: Label[]): Epic[] {
  return [...epics].sort((a, b) => {
    const gruppenUnterschied = gruppe(a) - gruppe(b)
    if (gruppenUnterschied !== 0) return gruppenUnterschied
    const bedarfUnterschied = handlungsbedarf(b, cards, labels) - handlungsbedarf(a, cards, labels)
    if (bedarfUnterschied !== 0) return bedarfUnterschied
    return epicShortcode(a.title, a.shortcode).localeCompare(epicShortcode(b.title, b.shortcode))
  })
}

/**
 * Die im Kachelraster sichtbaren Vorhaben (Plan #703, E5). Dasselbe Muster wie
 * `hiddenCardNumbers` in `hiddenCards.ts`: Die Sichtbarkeitsregel liegt hier testbar, ohne dass
 * eine Seite gerendert werden muss.
 *
 * Gefiltert wird über `Epic.id`, **nicht** über `Epic.number` — so ist der Zustand geschlüsselt
 * (`boardHiddenEpics.ts`), und so liest ihn das Board. Eine zweite Schlüsselung wäre ein stiller
 * Bruch mit der Board-Wirkung desselben Zustands.
 *
 * Eine ID in `hidden` ohne zugehöriges Vorhaben wird übergangen: Der `localStorage`-Zustand
 * überlebt das Löschen eines Vorhabens, der Fall ist der Regelfall und kein Fehler.
 *
 * Aufgerufen wird **nach** `sortEpics` — `visibleEpics(sortEpics(...), …)`. Reine Funktion: Das
 * Eingabe-Array bleibt unverändert und wird in keinem Zweig durchgereicht.
 *
 * @param epics Vorhaben des Boards, bereits sortiert
 * @param hidden IDs der ausgeblendeten Vorhaben (`Epic.id`)
 * @param zeigeAusgeblendete `true` blendet nichts aus — der Zeige-Modus der Seite
 */
export function visibleEpics(
  epics: readonly Epic[],
  hidden: ReadonlySet<number>,
  zeigeAusgeblendete: boolean,
): Epic[] {
  if (zeigeAusgeblendete) return [...epics]
  return epics.filter((epic) => !hidden.has(epic.id))
}
