import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'
import { epicShortcode } from './epicMeta'
import { epicAuswahl, type EpicOption } from './epicTiles'

/**
 * Das Vorhaben, dessen Kürzel eine Karte trägt (Plan #682, E2) — als reine Funktion, damit die
 * Regel an allen Anzeigeorten dieselbe ist und nicht je Ort nachgebaut wird (E1).
 *
 * Gerechnet wird allein auf der Vorhaben-Liste: Ein Vorhaben kommt in Frage, wenn `card.number`
 * in seinem `memberNumbers` steht — dort führt der Server beide Zugehörigkeitswege zusammen, den
 * ausdrücklich zugeordneten und den über die Herkunft geerbten.
 *
 * `card.parentId` liest die Funktion bewusst **nicht**. Eine archivierte Karte behält ihre
 * `parentId`, zählt aber serverseitig nicht mehr zur Zugehörigkeit (`EpicMembership.zaehlt()`);
 * die Anzeige folgt dem Server statt einer zweiten Wahrheit.
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
  kandidaten.sort((a, b) => rang(a) - rang(b) || a.number - b.number)
  return kandidaten[0]
}

/** Ohne Angabe ist kein Vorhaben ausgeblendet. */
const KEINE_AUSBLENDUNG: ReadonlySet<number> = new Set()

/** Was der Kartendialog über das Vorhaben-Feld wissen muss (Plan #1042, E11). */
export interface VorhabenFeldEingabe {
  /** Ob die Zuordnung überhaupt geändert werden darf — `false` heißt: Optionsvorrat nicht ladbar. */
  canEditEpic: boolean
  /** Aktuell zugeordnetes Vorhaben (dessen `id`) oder `null`. */
  parentId: number | null
  /** IDs der auf dem Board ausgeblendeten Vorhaben; ohne Angabe keines. */
  hiddenEpics?: ReadonlySet<number>
  /** Alle Vorhaben des Boards — Optionsvorrat und Quelle des angezeigten Titels. */
  epics: Epic[]
}

/** Optionsvorrat und Lesezustand des Vorhaben-Felds im Kartendialog. */
export interface VorhabenFeldAnsicht {
  /** Optionen des Auswahlfelds, ausgeblendete markiert und nach den eingeblendeten. */
  epicOptionen: EpicOption[]
  /** Ob das Feld nur gelesen werden kann. */
  epicLesend: boolean
  /** Beschriftung im Lesezustand („Kürzel – Titel"), oder `undefined` ohne bekanntes Vorhaben. */
  epicLesendText?: string
}

/**
 * Optionsvorrat, Lesezustand und Lesetext des Vorhaben-Felds — als reine Funktion, damit die drei
 * Regeln nicht als Ternär-Kette in der Kartenmaske stehen (Plan #1042, E11).
 *
 * Zur Wahl stehen alle Vorhaben, auch ausgeblendete (Issue #1981) — über `epicAuswahl`, dieselbe
 * Liste wie in der Massenaktion „Zuordnen".
 *
 * Zeigt die Karte auf ein Vorhaben, das in `epics` fehlt (fehlende Liste, fremdes oder gelöschtes
 * Vorhaben), bleibt das Feld lesend: Ein Dropdown ohne diesen Eintrag böte nur an, die Zuordnung
 * zu löschen, ohne sie je gezeigt zu haben (#586). Fehlt das Vorhaben, gibt es auch keinen
 * Lesetext — der Aufrufer zeigt dann die nackte Nummer.
 */
export function vorhabenFeld({
  canEditEpic,
  parentId,
  hiddenEpics = KEINE_AUSBLENDUNG,
  epics,
}: VorhabenFeldEingabe): VorhabenFeldAnsicht {
  const epicOptionen = epicAuswahl(epics, hiddenEpics)
  const zugeordnetesVorhaben = epics.find((e) => e.id === parentId)
  const epicLesend = !canEditEpic || (parentId !== null && zugeordnetesVorhaben === undefined)
  const epicLesendText = zugeordnetesVorhaben
    ? `${epicShortcode(zugeordnetesVorhaben.title, zugeordnetesVorhaben.shortcode)} – ${zugeordnetesVorhaben.title}`
    : undefined
  return { epicOptionen, epicLesend, epicLesendText }
}
