import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Reine Logik fuers Ausprobieren aus der API-Uebersicht (Plan #1437, E1/E10): das Kennzeichen,
 * an dem der Server einen ausprobierten Aufruf erkennt, die Unterscheidung lesend/aendernd und die
 * Texte rund um die Rueckfrage. Ohne React, damit sie ohne die Swagger UI pruefbar bleibt.
 */

/** Header, den der Server nur einem aktiven Plattform-Admin durchlaesst; der Wert ist gleichgueltig. */
export const AUSPROBIER_HEADER = 'X-Api-Ausprobieren'

/** Fehlertext, wenn der Admin die Rueckfrage vor einem aendernden Aufruf abbricht. */
export function abgebrochenText(sprache: Sprache): string {
  return woerterbuch(sprache).API_UEBERSICHT.ABGEBROCHEN
}

/** Hinweis an der Stelle des Absendens eines aendernden Aufrufs. */
export function hinweisAendernd(sprache: Sprache): string {
  return woerterbuch(sprache).API_UEBERSICHT.HINWEIS_AENDERND
}

/** Der Abbruchtext, deutsche Fassung. */
export const ABGEBROCHEN = abgebrochenText('de')

/** Der Hinweis vor einem aendernden Aufruf, deutsche Fassung. */
export const HINWEIS_AENDERND = hinweisAendernd('de')

const LESEND = new Set(['GET', 'HEAD'])

/** Aendernd ist jede Methode ausser GET und HEAD, gleich in welcher Schreibweise. */
export function istAendernd(methode: string): boolean {
  return !LESEND.has(methode.toUpperCase())
}

/** Eine ausgehende Anfrage, wie sie der Abfang-Haken der Swagger UI erhaelt. */
export interface Anfrage {
  headers?: Record<string, string>
}

/** Liefert die Anfrage mit gesetztem Ausprobier-Kennzeichen; alle uebrigen Header bleiben erhalten. */
export function markiere<T extends Anfrage>(anfrage: T): Omit<T, 'headers'> & { headers: Record<string, string> } {
  return { ...anfrage, headers: { ...anfrage.headers, [AUSPROBIER_HEADER]: '1' } }
}
