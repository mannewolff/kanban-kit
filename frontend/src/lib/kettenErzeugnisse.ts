import { boardsApi } from '../api/boards'
import { cardsApi, type Card, type CardActivity } from '../api/cards'

/**
 * Was ein Kettenvorgang angelegt hat (Issue #1106): den technischen Plan und die Arbeitspakete,
 * je als Kartennummer, aufsteigend.
 */
export interface Erzeugnisse {
  plaene: number[]
  pakete: number[]
  /** Der Plan je Paket — daran ist eine Paketzeile als Paket dieses Plans erkennbar. */
  planJePaket: Record<number, number>
}

/** Das Zeitfenster eines Laufs in Millisekunden seit Epoche; `bis` ist bei einem laufenden offen. */
export interface Laufenster {
  von: number
  bis: number
}

/**
 * Die Kandidaten aus der Herkunft der Karten am Board: Pläne tragen die Anforderung als Herkunft,
 * Pakete einen dieser Pläne. Ob eine Karte in **diesem** Lauf entstand, entscheidet erst der
 * Anlagezeitpunkt ({@link imFenster}) — die Herkunft allein nennt auch einen Plan aus einer
 * früheren Nacht.
 */
export function kandidaten(karten: readonly Card[], anforderung: number): { plaene: Card[]; pakete: Card[] } {
  const plaene = karten.filter((karte) => karte.derivedFrom === anforderung)
  const planNummern = new Set(plaene.map((karte) => karte.number))
  const pakete = karten.filter((karte) => karte.derivedFrom !== null && planNummern.has(karte.derivedFrom))
  return { plaene, pakete }
}

/** Der Anlagezeitpunkt einer Karte aus ihrem Verlauf; `undefined`, wo der Verlauf keinen führt. */
export function anlagezeit(verlauf: readonly CardActivity[]): number | undefined {
  const angelegt = verlauf.find((eintrag) => eintrag.type === 'CREATED')
  if (angelegt === undefined) {
    return undefined
  }
  const zeit = Date.parse(angelegt.createdAt)
  return Number.isNaN(zeit) ? undefined : zeit
}

/** Liegt ein Anlagezeitpunkt im Fenster des Laufs? Eine Karte ohne bekannten Zeitpunkt zählt nicht. */
export function imFenster(zeit: number | undefined, fenster: Laufenster): boolean {
  return zeit !== undefined && zeit >= fenster.von && zeit <= fenster.bis
}

/** Die Nummern aufsteigend und ohne Doppel. */
const aufsteigend = (karten: readonly Card[]): number[] =>
  [...new Set(karten.map((karte) => karte.number))].sort((a, b) => a - b)

/**
 * Ermittelt je Anforderung, was ein Kettenlauf angelegt hat.
 *
 * <p>Gelesen wird die Herkunft am Board, nicht eine Liste aus dem Runner (Entscheidung am Task):
 * Sie steht dort ohnehin fest, und eine zweite Liste könnte ihr widersprechen. Den
 * Anlagezeitpunkt kennt allein der Verlauf einer Karte — er wird deshalb nur für die wenigen
 * Kandidaten geholt, deren Herkunft passt, nie für das ganze Board.
 *
 * <p>Die Abrufe laufen erst beim Aufklappen eines Laufs (Plan #718, A8). Scheitert einer, wirft die
 * Funktion: Ein Teilergebnis zeigte „kein Plan" für einen Plan, der nur nicht geladen wurde.
 */
export async function ermittleErzeugnisse(
  projektId: number,
  anforderungen: readonly number[],
  fenster: Laufenster,
): Promise<Map<number, Erzeugnisse>> {
  const boards = await boardsApi.list(projektId)
  const karten = (await Promise.all(boards.map((board) => cardsApi.list(board.id)))).flat()

  const je = anforderungen.map((anforderung) => ({ anforderung, ...kandidaten(karten, anforderung) }))
  const alle = [...new Map(je.flatMap(({ plaene, pakete }) => [...plaene, ...pakete]).map((k) => [k.id, k])).values()]
  const zeiten = new Map(
    await Promise.all(alle.map(async (karte) => [karte.id, anlagezeit(await cardsApi.getActivity(karte.id))] as const)),
  )
  const neu = (karte: Card) => imFenster(zeiten.get(karte.id), fenster)

  return new Map(
    je.map(({ anforderung, plaene, pakete }) => {
      // Ein Paket zählt nur unter einem Plan, der selbst in diesem Lauf entstand: Ein älterer Plan
      // mit einem neuen Paket wäre nicht die Arbeit dieser Kette.
      const neuePlaene = plaene.filter(neu)
      const planJePaket: Record<number, number> = {}
      const neuePakete = neuePlaene.flatMap((plan) => {
        const darunter = pakete.filter((karte) => karte.derivedFrom === plan.number && neu(karte))
        for (const karte of darunter) planJePaket[karte.number] = plan.number
        return darunter
      })
      return [
        anforderung,
        { plaene: aufsteigend(neuePlaene), pakete: aufsteigend(neuePakete), planJePaket },
      ] as const
    }),
  )
}
