/**
 * Längengrenzen für lange Freitexte — Karten-Beschreibungen und Kommentare (Issue #572).
 *
 * Gegenstück zu `org.mwolff.manban.common.TextLimits`. Der Wert steht im Frontend genau hier;
 * Eingabefelder und der Spezifikationsimport importieren ihn, statt ihn zu wiederholen.
 *
 * Zählweise: eine UTF-16-Codeeinheit, also `text.length` — dieselbe Einheit, die Bean Validation
 * über `@Size` prüft. Ein Emoji außerhalb der BMP zählt als zwei.
 */
export const MAX_TEXT_LENGTH = 50_000

/**
 * Fehlertext für ein zu langes Feld, z. B. „60.000 / 50.000 Zeichen".
 *
 * Bewusst ohne die Differenz: Sie ist aus beiden Zahlen ablesbar, und ein drittes Zahlenfeld im
 * Fehlertext wäre eine weitere Stelle, die auseinanderlaufen kann.
 */
export function tooLongMessage(length: number): string {
  const format = (value: number) => value.toLocaleString('de-DE')
  return `${format(length)} / ${format(MAX_TEXT_LENGTH)} Zeichen`
}

/** Ob der Text die Grenze reißt — dieselbe Prüfung für Feldzustand und Aktions-Sperre. */
export function isTooLong(text: string): boolean {
  return text.length > MAX_TEXT_LENGTH
}
