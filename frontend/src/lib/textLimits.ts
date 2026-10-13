/**
 * Längengrenzen für lange Freitexte — Karten-Beschreibungen und Kommentare (Issue #572).
 *
 * Gegenstück zu `org.mwolff.manban.common.TextLimits`. Der Wert steht im Frontend genau hier;
 * Eingabefelder und der Spezifikationsimport importieren ihn, statt ihn zu wiederholen.
 *
 * Zählweise: eine UTF-16-Codeeinheit, also `text.length` — dieselbe Einheit, die Bean Validation
 * über `@Size` prüft. Ein Emoji außerhalb der BMP zählt als zwei.
 */
import { formatierer } from '../i18n/format'
import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

export const MAX_TEXT_LENGTH = 50_000

/**
 * Fehlertext für ein zu langes Feld, z. B. „60.000 / 50.000 Zeichen" bzw. „60,000 / 50,000 characters".
 *
 * Bewusst ohne die Differenz: Sie ist aus beiden Zahlen ablesbar, und ein drittes Zahlenfeld im
 * Fehlertext wäre eine weitere Stelle, die auseinanderlaufen kann.
 */
export function tooLongMessage(length: number, sprache: Sprache = 'de'): string {
  const { zahl } = formatierer(sprache)
  return woerterbuch(sprache).TEXT_ZU_LANG(zahl(length), zahl(MAX_TEXT_LENGTH))
}

/** Ob der Text die Grenze reißt — dieselbe Prüfung für Feldzustand und Aktions-Sperre. */
export function isTooLong(text: string): boolean {
  return text.length > MAX_TEXT_LENGTH
}
