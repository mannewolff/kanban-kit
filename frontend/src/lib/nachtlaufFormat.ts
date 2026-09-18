/**
 * Die Formatierer der Nachtlauf-Anzeigen (Issue #968).
 *
 * Auswertung (`NightRunPage`) und Karten-Detail (`KartenAnlaeufe`) nutzen dieselben — ein zweites
 * Paar liefe beim nächsten Wortwechsel auseinander. Die Dauer formatiert weiterhin
 * `formatDuration`.
 */

/** Die Kosten des Nachtlaufs stehen im Ergebnisstand in US-Dollar. */
const KOSTEN_FORMAT = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'USD' })

/** Die Mengen des Verbrauchs stehen als Token da — „Zuege" ist im Leitstand die Zahl der Sitzungen. */
const TOKEN_FORMAT = new Intl.NumberFormat('de-DE')

/**
 * Ein Betrag oder die ausdrückliche Auskunft, dass der Stand keinen führt. „0 $" wäre eine
 * Behauptung über etwas, das gar nicht gemeldet wurde.
 */
export const betrag = (wert: number | undefined): string =>
  wert === undefined ? 'nicht angegeben' : KOSTEN_FORMAT.format(wert)

/**
 * Eine Verbrauchszahl mit ihrer Einheit, oder die ausdrueckliche Fehlanzeige. „0 Token" waere eine
 * Behauptung ueber etwas, das gar nicht gemessen wurde — dieselbe Linie wie bei {@link betrag}.
 */
export const menge = (wert: number | undefined): string =>
  wert === undefined ? 'nicht gemessen' : `${TOKEN_FORMAT.format(wert)} Token`

/** Ein gemessener Kostenbetrag oder „nicht gemessen" — die Verbrauchs-Schwester von {@link betrag}. */
export const kosten = (wert: number | undefined): string =>
  wert === undefined ? 'nicht gemessen' : KOSTEN_FORMAT.format(wert)

/**
 * `null` aus der Antwort heißt „nicht gemessen" — die Formatierer oben kennen dafür `undefined`.
 *
 * <p>Steht hier und nicht dreimal in den Verbrauchs-Komponenten (Plan #1042, E5): Die Umdeutung
 * gehört zu den Formatierern, die sie erwarten, und eine dreifache Kopie derselben Zeile läuft
 * beim nächsten Gedanken über die 0 auseinander. Die 0 bleibt eine Zahl — sie wurde gemessen.
 */
export const ohneNull = (wert: number | null): number | undefined => wert ?? undefined
