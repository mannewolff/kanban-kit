/** Reine Hilfsfunktionen für die Listen-Ansicht (portiert aus der Toolbox), frei von React. */

/**
 * Strippt Markdown-Steuerzeichen aus einem Body und macht daraus eine einzeilige Vorschau:
 * entfernt Überschriften-Hashes, Betonungszeichen und Backticks, kollabiert Zeilenumbrüche.
 */
export function stripMarkdown(raw: string): string {
  return raw
    .replace(/\r?\n/g, ' ')
    .replace(/#+\s*/g, '')
    .replace(/[*_`]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}
