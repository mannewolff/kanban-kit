import { formatierer } from '../i18n/format'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Die Formatierer der Nachtlauf-Anzeigen (Issue #968).
 *
 * Auswertung (`NightRunPage`) und Karten-Detail (`KartenAnlaeufe`) nutzen dieselben — ein zweites
 * Paar liefe beim nächsten Wortwechsel auseinander. Die Dauer formatiert weiterhin
 * `formatDuration`. Seit Issue #1658 schreiben sie in der Sprache, Vorgabe Deutsch.
 */

/** Die Kosten des Nachtlaufs stehen im Ergebnisstand in US-Dollar. */
const kostenFormat = (wert: number, sprache: Sprache): string => formatierer(sprache).geld(wert)

/** Die Mengen des Verbrauchs stehen als Token da — „Zuege" ist im Leitstand die Zahl der Sitzungen. */
const tokenFormat = (wert: number, sprache: Sprache): string => formatierer(sprache).zahl(wert)

/**
 * Ein Betrag oder die ausdrückliche Auskunft, dass der Stand keinen führt. „0 $" wäre eine
 * Behauptung über etwas, das gar nicht gemeldet wurde.
 */
export const betrag = (wert: number | undefined, sprache: Sprache = 'de'): string =>
  wert === undefined ? woerterbuch(sprache).NACHTLAUF_FORMAT.NICHT_ANGEGEBEN : kostenFormat(wert, sprache)

/**
 * Eine Verbrauchszahl mit ihrer Einheit, oder die ausdrueckliche Fehlanzeige. „0 Token" waere eine
 * Behauptung ueber etwas, das gar nicht gemessen wurde — dieselbe Linie wie bei {@link betrag}.
 */
export const menge = (wert: number | undefined, sprache: Sprache = 'de'): string => {
  const t = woerterbuch(sprache).NACHTLAUF_FORMAT
  return wert === undefined ? t.NICHT_GEMESSEN : t.TOKEN(tokenFormat(wert, sprache))
}

/** Ein gemessener Kostenbetrag oder „nicht gemessen" — die Verbrauchs-Schwester von {@link betrag}. */
export const kosten = (wert: number | undefined, sprache: Sprache = 'de'): string =>
  wert === undefined ? woerterbuch(sprache).NACHTLAUF_FORMAT.NICHT_GEMESSEN : kostenFormat(wert, sprache)

/**
 * `null` aus der Antwort heißt „nicht gemessen" — die Formatierer oben kennen dafür `undefined`.
 *
 * <p>Steht hier und nicht dreimal in den Verbrauchs-Komponenten (Plan #1042, E5): Die Umdeutung
 * gehört zu den Formatierern, die sie erwarten, und eine dreifache Kopie derselben Zeile läuft
 * beim nächsten Gedanken über die 0 auseinander. Die 0 bleibt eine Zahl — sie wurde gemessen.
 */
export const ohneNull = (wert: number | null): number | undefined => wert ?? undefined
